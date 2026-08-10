package de.rwth_aachen.dc.lbd;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.vecmath.Point3d;

class IfcOpenShellGeometryIteratorModel {

	private static final String SCRIPT_RESOURCE = "/python/ifcopenshell_geometry_iterator.py";
	private static final int TIMEOUT_MINUTES = Integer.getInteger("ifctolbd.ifcopenshell.timeoutMinutes", 12);
	private static final int PROTOCOL_MAGIC = 0x49464347; // IFCG
	private static final int PROTOCOL_VERSION = 1;
	private static final int RECORD_MAGIC = 0x47454F4D; // GEOM
	private static final int MAX_GUID_BYTES = 4096;
	private static final int MAX_MATERIAL_NAME_BYTES = 1_048_576;
	private static final int MAX_ARRAY_VALUES = 300_000_000;
	private static final int MAX_MATERIALS = 1_000_000;

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
		return geometry.getBoundingBox();
	}

	ObjDescription getOBJ(String guid) {
		GeometryData geometry = this.geometryByGuid.get(guid);
		if (geometry == null || geometry.vertices.length == 0 || geometry.faces.length == 0) {
			return null;
		}
		return geometry.getOBJ();
	}

	String getWireframeWKT(String guid) {
		GeometryData geometry = this.geometryByGuid.get(guid);
		if (geometry == null || geometry.vertices.length == 0 || geometry.faces.length == 0) {
			return null;
		}
		return geometry.getWireframeWKT();
	}

	MTLDescription getMTL(String guid) {
		GeometryData geometry = this.geometryByGuid.get(guid);
		if (geometry == null || geometry.materials.length == 0) {
			return null;
		}
		return geometry.getMTL();
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
				this.geometryByGuid.clear();
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
		Process process = processBuilder.start();

		ExecutorService outputReaders = Executors.newFixedThreadPool(2);
		Future<Integer> geometryFuture = outputReaders.submit(() -> readGeometryOutput(process));
		Future<String> diagnosticsFuture = outputReaders.submit(() -> readDiagnosticsOutput(process));

		boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
			outputReaders.shutdownNow();
			throw new IOException("IfcOpenShell geometry iterator timed out after " + TIMEOUT_MINUTES + " minutes");
		}
		String diagnostics = getFuture(diagnosticsFuture, "diagnostics");
		try {
			getFuture(geometryFuture, "geometry");
		} catch (IOException e) {
			if (process.exitValue() != 0) {
				throw new IOException("IfcOpenShell geometry iterator failed with exit code " + process.exitValue()
						+ diagnosticsMessage(diagnostics), e);
			}
			throw e;
		} finally {
			outputReaders.shutdownNow();
		}
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

	private String readDiagnosticsOutput(Process process) throws IOException {
		StringBuilder diagnostics = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					diagnostics.append(trimmed).append(System.lineSeparator());
				}
			}
		}
		return diagnostics.toString();
	}

	private static <T> T getFuture(Future<T> future, String streamName) throws IOException, InterruptedException {
		try {
			return future.get(5, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			throw new IOException("Unable to read IfcOpenShell " + streamName + " output", e.getCause());
		} catch (TimeoutException e) {
			throw new IOException("Timed out reading IfcOpenShell " + streamName + " output", e);
		}
	}

	private int readGeometryOutput(Process process) throws IOException {
		try (DataInputStream input = new DataInputStream(new BufferedInputStream(process.getInputStream()))) {
			int magic = input.readInt();
			if (magic != PROTOCOL_MAGIC) {
				throw new IOException("Invalid geometry protocol magic 0x" + Integer.toHexString(magic));
			}
			int version = input.readInt();
			if (version != PROTOCOL_VERSION) {
				throw new IOException("Unsupported geometry protocol version " + version);
			}
			int records = 0;
			while (true) {
				int recordMagic;
				try {
					recordMagic = input.readInt();
				} catch (EOFException e) {
					return records;
				}
				if (recordMagic != RECORD_MAGIC) {
					throw new IOException("Invalid geometry record magic 0x" + Integer.toHexString(recordMagic));
				}
				readGeometryRecord(input);
				records++;
			}
		}
	}

	private void readGeometryRecord(DataInputStream input) throws IOException {
		String guid = readString(input, MAX_GUID_BYTES, "GUID");
		double[] bbox = null;
		int hasBoundingBox = input.readUnsignedByte();
		if (hasBoundingBox == 1) {
			bbox = readDoubleArray(input, 6, "bounding box");
		} else if (hasBoundingBox != 0) {
			throw new IOException("Invalid bounding-box flag " + hasBoundingBox + " for " + guid);
		}
		double[] vertices = readDoubleArray(input, readCount(input, MAX_ARRAY_VALUES, "vertex values"), "vertices");
		int[] faces = readIntArray(input, readCount(input, MAX_ARRAY_VALUES, "face indices"));
		int materialCount = readCount(input, MAX_MATERIALS, "materials");
		MaterialData[] materials = new MaterialData[materialCount];
		for (int i = 0; i < materialCount; i++) {
			String name = readString(input, MAX_MATERIAL_NAME_BYTES, "material name");
			double[] diffuse = readDoubleArray(input, 3, "material diffuse color");
			materials[i] = new MaterialData(name, diffuse, input.readDouble());
		}
		if (!guid.isBlank()) {
			this.geometryByGuid.put(guid, new GeometryData(bbox, vertices, faces, materials));
		}
	}

	private static int readCount(DataInputStream input, int maximum, String field) throws IOException {
		int count = input.readInt();
		if (count < 0 || count > maximum) {
			throw new IOException("Invalid " + field + " count " + count);
		}
		return count;
	}

	private static String readString(DataInputStream input, int maximumBytes, String field) throws IOException {
		int length = readCount(input, maximumBytes, field + " bytes");
		byte[] bytes = input.readNBytes(length);
		if (bytes.length != length) {
			throw new EOFException("Incomplete " + field);
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static double[] readDoubleArray(DataInputStream input, int count, String field) throws IOException {
		double[] values = new double[count];
		for (int i = 0; i < count; i++) {
			values[i] = input.readDouble();
		}
		return values;
	}

	private static int[] readIntArray(DataInputStream input, int count) throws IOException {
		int[] values = new int[count];
		for (int i = 0; i < count; i++) {
			values[i] = input.readInt();
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
		private BoundingBox boundingBox;
		private ObjDescription obj;
		private MTLDescription mtl;
		private String wireframeWKT;
		private boolean boundingBoxComputed;
		private boolean objComputed;
		private boolean mtlComputed;
		private boolean wireframeComputed;

		private GeometryData(double[] bbox, double[] vertices, int[] faces, MaterialData[] materials) {
			this.bbox = bbox;
			this.vertices = vertices;
			this.faces = faces;
			this.materials = materials;
		}

		private synchronized BoundingBox getBoundingBox() {
			if (!this.boundingBoxComputed) {
				this.boundingBoxComputed = true;
				if (this.bbox != null) {
					this.boundingBox = new BoundingBox();
					this.boundingBox.add(new Point3d(this.bbox[0], this.bbox[1], this.bbox[2]));
					this.boundingBox.add(new Point3d(this.bbox[3], this.bbox[4], this.bbox[5]));
				}
			}
			return this.boundingBox;
		}

		private synchronized ObjDescription getOBJ() {
			if (!this.objComputed) {
				this.objComputed = true;
				if (this.vertices.length > 0 && this.faces.length > 0) {
					this.obj = ObjDescription.fromMesh(this.vertices, this.faces, false);
				}
			}
			return this.obj;
		}

		private synchronized MTLDescription getMTL() {
			if (!this.mtlComputed) {
				this.mtlComputed = true;
				if (this.materials.length > 0) {
					this.mtl = new MTLDescription();
					for (MaterialData material : this.materials) {
						double[] specular = new double[] { 0.0, 0.0, 0.0 };
						this.mtl.addMaterial(new MTLDescription.MTLMaterial(material.name, material.diffuse,
								material.diffuse, specular, material.alpha));
					}
				}
			}
			return this.mtl;
		}

		private synchronized String getWireframeWKT() {
			if (!this.wireframeComputed) {
				this.wireframeComputed = true;
				if (this.vertices.length > 0 && this.faces.length > 0) {
					this.wireframeWKT = WireframeWKT.fromMesh(this.vertices, this.faces, false);
				}
			}
			return this.wireframeWKT;
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
