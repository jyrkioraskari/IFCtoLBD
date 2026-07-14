package de.rwth_aachen.dc.lbd;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.vecmath.Point3d;

import org.apache.commons.lang3.tuple.ImmutableTriple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class IfcOpenShellGeometryIteratorModel {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String SCRIPT_RESOURCE = "/python/ifcopenshell_geometry_iterator.py";
	private static final int TIMEOUT_MINUTES = Integer.getInteger("ifctolbd.ifcopenshell.timeoutMinutes", 12);

	private final Map<String, GeometryData> geometryByGuid = new HashMap<>();

	IfcOpenShellGeometryIteratorModel(File ifcFile) throws IOException, InterruptedException {
		Path script = copyScriptToTempFile();
		try {
			runIterator(script, ifcFile);
		} finally {
			Files.deleteIfExists(script);
		}
	}

	BoundingBox getBoundingBox(String guid) {
		GeometryData geometry = this.geometryByGuid.get(guid);
		if (geometry == null || geometry.bbox == null) {
			return null;
		}
		BoundingBox boundingBox = new BoundingBox();
		boundingBox.add(new Point3d(geometry.bbox[0], geometry.bbox[1], geometry.bbox[2]));
		boundingBox.add(new Point3d(geometry.bbox[3], geometry.bbox[4], geometry.bbox[5]));
		return boundingBox;
	}

	ObjDescription getOBJ(String guid) {
		GeometryData geometry = this.geometryByGuid.get(guid);
		if (geometry == null || geometry.vertices.length == 0 || geometry.faces.length == 0) {
			return null;
		}
		ObjDescription obj = new ObjDescription();
		for (int i = 0; i + 2 < geometry.vertices.length; i += 3) {
			obj.addVertex(new Point3d(geometry.vertices[i], geometry.vertices[i + 1], geometry.vertices[i + 2]));
		}
		for (int i = 0; i + 2 < geometry.faces.length; i += 3) {
			obj.addFace(new ImmutableTriple<>(geometry.faces[i] + 1, geometry.faces[i + 1] + 1, geometry.faces[i + 2] + 1));
		}
		return obj;
	}

	MTLDescription getMTL(String guid) {
		GeometryData geometry = this.geometryByGuid.get(guid);
		if (geometry == null || geometry.materials.length == 0) {
			return null;
		}
		MTLDescription mtl = new MTLDescription();
		for (MaterialData material : geometry.materials) {
			double[] specular = new double[] { 0.0, 0.0, 0.0 };
			mtl.addMaterial(new MTLDescription.MTLMaterial(material.name, material.diffuse, material.diffuse, specular,
					material.alpha));
		}
		return mtl;
	}

	private static Path copyScriptToTempFile() throws IOException {
		try (InputStream input = IfcOpenShellGeometryIteratorModel.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (input == null) {
				throw new IOException("Missing resource " + SCRIPT_RESOURCE);
			}
			Path script = Files.createTempFile("ifctolbd-ifcopenshell-geometry-", ".py");
			Files.copy(input, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return script;
		}
	}

	private void runIterator(Path script, File ifcFile) throws IOException, InterruptedException {
		List<List<String>> candidates = pythonCommandCandidates();
		List<String> failures = new ArrayList<>();
		for (List<String> candidate : candidates) {
			try {
				runIteratorWithCommand(candidate, script, ifcFile);
				return;
			} catch (IOException e) {
				failures.add(String.join(" ", candidate) + ": " + e.getMessage());
			}
		}
		throw new IOException("IfcOpenShell geometry iterator failed for all Python commands: "
				+ String.join(" | ", failures));
	}

	private void runIteratorWithCommand(List<String> pythonCommand, Path script, File ifcFile)
			throws IOException, InterruptedException {
		List<String> command = new ArrayList<>(pythonCommand);
		command.add(script.toString());
		command.add(ifcFile.getAbsolutePath());
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);
		Process process = processBuilder.start();

		ExecutorService outputReader = Executors.newSingleThreadExecutor();
		Future<String> diagnosticsFuture = outputReader.submit(() -> readOutput(process));

		boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
			outputReader.shutdownNow();
			throw new IOException("IfcOpenShell geometry iterator timed out after " + TIMEOUT_MINUTES + " minutes");
		}
		String diagnostics = readDiagnostics(diagnosticsFuture, outputReader);
		if (process.exitValue() != 0) {
			throw new IOException("IfcOpenShell geometry iterator failed with exit code " + process.exitValue()
					+ diagnosticsMessage(diagnostics));
		}
	}

	private static List<List<String>> pythonCommandCandidates() {
		String configuredPython = System.getProperty("ifctolbd.ifcopenshell.python");
		if (configuredPython == null || configuredPython.isBlank()) {
			configuredPython = System.getenv("IFCTOLBD_IFCOPENSHELL_PYTHON");
		}
		if (configuredPython == null || configuredPython.isBlank()) {
			configuredPython = System.getenv("PYTHON");
		}
		if (configuredPython != null && !configuredPython.isBlank()) {
			return List.of(splitCommand(configuredPython));
		}

		List<List<String>> candidates = new ArrayList<>();
		if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
			candidates.add(Arrays.asList("py", "-3"));
			candidates.add(List.of("python"));
			candidates.add(List.of("python3"));
		} else {
			candidates.add(List.of("python3"));
			candidates.add(List.of("python"));
		}
		return candidates;
	}

	private static List<String> splitCommand(String command) {
		String[] parts = command.trim().split("\\s+");
		return Arrays.asList(parts);
	}

	private String readOutput(Process process) throws IOException {
		StringBuilder diagnostics = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.startsWith("{")) {
					readGeometry(trimmed);
				} else if (!trimmed.isEmpty()) {
					diagnostics.append(trimmed).append(System.lineSeparator());
				}
			}
		}
		return diagnostics.toString();
	}

	private static String readDiagnostics(Future<String> diagnosticsFuture, ExecutorService outputReader)
			throws IOException, InterruptedException {
		try {
			return diagnosticsFuture.get(5, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			throw new IOException("Unable to read IfcOpenShell geometry iterator output", e.getCause());
		} catch (TimeoutException e) {
			throw new IOException("Timed out reading IfcOpenShell geometry iterator output", e);
		} finally {
			outputReader.shutdownNow();
		}
	}

	private void readGeometry(String jsonLine) throws IOException {
		JsonNode root = OBJECT_MAPPER.readTree(jsonLine);
		String guid = root.path("guid").asText(null);
		if (guid == null || guid.isBlank()) {
			return;
		}
		double[] bbox = readDoubleArray(root.path("bbox"));
		if (bbox.length != 6) {
			bbox = null;
		}
		this.geometryByGuid.put(guid, new GeometryData(bbox, readDoubleArray(root.path("vertices")),
				readIntArray(root.path("faces")), readMaterials(root.path("materials"))));
	}

	private static MaterialData[] readMaterials(JsonNode materialsNode) {
		if (!materialsNode.isArray()) {
			return new MaterialData[0];
		}
		MaterialData[] materials = new MaterialData[materialsNode.size()];
		int index = 0;
		for (Iterator<JsonNode> it = materialsNode.elements(); it.hasNext();) {
			JsonNode materialNode = it.next();
			double[] diffuse = readDoubleArray(materialNode.path("diffuse"));
			if (diffuse.length < 3) {
				diffuse = new double[] { 0.8, 0.8, 0.8 };
			}
			materials[index] = new MaterialData(materialNode.path("name").asText("material_" + index),
					new double[] { diffuse[0], diffuse[1], diffuse[2] }, materialNode.path("alpha").asDouble(1.0));
			index++;
		}
		return materials;
	}

	private static double[] readDoubleArray(JsonNode node) {
		if (!node.isArray()) {
			return new double[0];
		}
		double[] values = new double[node.size()];
		for (int i = 0; i < node.size(); i++) {
			values[i] = node.get(i).asDouble();
		}
		return values;
	}

	private static int[] readIntArray(JsonNode node) {
		if (!node.isArray()) {
			return new int[0];
		}
		int[] values = new int[node.size()];
		for (int i = 0; i < node.size(); i++) {
			values[i] = node.get(i).asInt();
		}
		return values;
	}

	private static String diagnosticsMessage(String diagnostics) {
		if (diagnostics == null || diagnostics.isBlank()) {
			return "";
		}
		return ": " + diagnostics;
	}

	private static class GeometryData {
		private final double[] bbox;
		private final double[] vertices;
		private final int[] faces;
		private final MaterialData[] materials;

		private GeometryData(double[] bbox, double[] vertices, int[] faces, MaterialData[] materials) {
			this.bbox = bbox;
			this.vertices = vertices;
			this.faces = faces;
			this.materials = materials;
		}
	}

	private static class MaterialData {
		private final String name;
		private final double[] diffuse;
		private final double alpha;

		private MaterialData(String name, double[] diffuse, double alpha) {
			this.name = name;
			this.diffuse = diffuse;
			this.alpha = alpha;
		}
	}
}
