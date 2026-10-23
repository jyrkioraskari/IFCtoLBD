package org.linkedbuildingdata.ifc2lbd;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.rwth_aachen.dc.lbd.TessellatedMesh;

/** Dependency-free glTF 2.0 serializer for one IFC triangle mesh. */
public final class GltfGeometryWriter {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final int ARRAY_BUFFER = 34962;
	private static final int ELEMENT_ARRAY_BUFFER = 34963;
	private static final int FLOAT = 5126;
	private static final int UNSIGNED_INT = 5125;

	public static byte[] write(TessellatedMesh mesh, GeometryArtifactFormat format) {
		if (format != GeometryArtifactFormat.GLB && format != GeometryArtifactFormat.GLTF)
			throw new IllegalArgumentException("Not a glTF format: " + format);
		Encoded encoded = encode(mesh);
		try {
			if (format == GeometryArtifactFormat.GLTF) {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> buffers = (List<Map<String, Object>>) encoded.document().get("buffers");
				buffers.getFirst().put("uri", "data:application/octet-stream;base64,"
						+ Base64.getEncoder().encodeToString(encoded.binary()));
				return JSON.writeValueAsBytes(encoded.document());
			}
			return glb(JSON.writeValueAsBytes(encoded.document()), encoded.binary());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not serialize glTF geometry", e);
		}
	}

	private static Encoded encode(TessellatedMesh mesh) {
		double[] sourcePositions = mesh.positions();
		double[] sourceNormals = mesh.normals();
		int[] indices = mesh.indices();
		ByteArrayOutputStream binary = new ByteArrayOutputStream();
		int positionOffset = binary.size();
		float[] min = { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY };
		float[] max = { Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY };
		for (int i = 0; i < sourcePositions.length; i++) {
			float value = finiteFloat(sourcePositions[i], "position");
			writeFloat(binary, value); int axis = i % 3;
			min[axis] = Math.min(min[axis], value); max[axis] = Math.max(max[axis], value);
		}
		int normalOffset = -1;
		if (sourceNormals.length > 0) {
			normalOffset = binary.size();
			for (double value : sourceNormals) writeFloat(binary, finiteFloat(value, "normal"));
		}
		int indexOffset = binary.size();
		for (int index : indices) writeInt(binary, index);

		List<Map<String, Object>> views = new ArrayList<>();
		views.add(map("buffer", 0, "byteOffset", positionOffset, "byteLength", sourcePositions.length * 4,
				"target", ARRAY_BUFFER));
		int normalView = -1;
		if (normalOffset >= 0) {
			normalView = views.size();
			views.add(map("buffer", 0, "byteOffset", normalOffset, "byteLength", sourceNormals.length * 4,
					"target", ARRAY_BUFFER));
		}
		int indexView = views.size();
		views.add(map("buffer", 0, "byteOffset", indexOffset, "byteLength", indices.length * 4,
				"target", ELEMENT_ARRAY_BUFFER));

		List<Map<String, Object>> accessors = new ArrayList<>();
		accessors.add(map("bufferView", 0, "componentType", FLOAT, "count", mesh.vertexCount(), "type", "VEC3",
				"min", List.of(min[0], min[1], min[2]), "max", List.of(max[0], max[1], max[2])));
		int normalAccessor = -1;
		if (normalView >= 0) {
			normalAccessor = accessors.size();
			accessors.add(map("bufferView", normalView, "componentType", FLOAT, "count", mesh.vertexCount(),
					"type", "VEC3"));
		}
		int indexAccessor = accessors.size();
		accessors.add(map("bufferView", indexView, "componentType", UNSIGNED_INT, "count", indices.length,
				"type", "SCALAR"));
		Map<String, Object> attributes = map("POSITION", 0);
		if (normalAccessor >= 0) attributes.put("NORMAL", normalAccessor);
		Map<String, Object> primitive = map("attributes", attributes, "indices", indexAccessor, "mode", 4);
		List<Map<String, Object>> materials = new ArrayList<>();
		if (!mesh.materials().isEmpty()) {
			TessellatedMesh.Material material = mesh.materials().getFirst();
			double[] color = material.diffuse();
			double alpha = finiteUnit(material.alpha(), "material alpha");
			Map<String, Object> gltfMaterial = map("name", material.name(), "pbrMetallicRoughness",
					map("baseColorFactor", List.of(finiteUnit(color[0], "material red"),
							finiteUnit(color[1], "material green"), finiteUnit(color[2], "material blue"), alpha),
							"metallicFactor", 0, "roughnessFactor", 1), "doubleSided", true);
			if (alpha < 1) gltfMaterial.put("alphaMode", "BLEND");
			materials.add(gltfMaterial); primitive.put("material", 0);
		}
		Map<String, Object> node = map("mesh", 0, "name", mesh.guid());
		double[] transform = mesh.localToWorld();
		if (!isIdentity(transform)) {
			List<Double> matrix = new ArrayList<>(16);
			for (double value : transform) {
				if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite glTF transform");
				matrix.add(value);
			}
			node.put("matrix", matrix);
		}
		Map<String, Object> document = map(
				"asset", map("version", "2.0", "generator", "IFCtoLBD"),
				"scene", 0,
				"scenes", List.of(map("nodes", List.of(0))),
				"nodes", List.of(node),
				"meshes", List.of(map("name", mesh.guid(), "primitives", List.of(primitive))),
				"buffers", List.of(map("byteLength", binary.size())),
				"bufferViews", views,
				"accessors", accessors);
		if (!materials.isEmpty()) document.put("materials", materials);
		return new Encoded(document, binary.toByteArray());
	}

	private static byte[] glb(byte[] json, byte[] binary) {
		byte[] paddedJson = pad(json, (byte) 0x20);
		byte[] paddedBinary = pad(binary, (byte) 0);
		ByteBuffer result = ByteBuffer.allocate(12 + 8 + paddedJson.length + 8 + paddedBinary.length)
				.order(ByteOrder.LITTLE_ENDIAN);
		result.putInt(0x46546c67).putInt(2).putInt(result.capacity());
		result.putInt(paddedJson.length).putInt(0x4E4F534A).put(paddedJson);
		result.putInt(paddedBinary.length).putInt(0x004E4942).put(paddedBinary);
		return result.array();
	}

	private static byte[] pad(byte[] source, byte value) {
		int length = (source.length + 3) & ~3;
		byte[] result = java.util.Arrays.copyOf(source, length);
		java.util.Arrays.fill(result, source.length, result.length, value);
		return result;
	}

	private static float finiteFloat(double value, String name) {
		float result = (float) value;
		if (!Float.isFinite(result)) throw new IllegalArgumentException("Non-finite glTF " + name);
		return result;
	}
	private static double finiteUnit(double value, String name) {
		if (!Double.isFinite(value) || value < 0 || value > 1)
			throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
		return value;
	}
	private static void writeFloat(ByteArrayOutputStream output, float value) {
		output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array());
	}
	private static void writeInt(ByteArrayOutputStream output, int value) {
		output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
	}
	private static boolean isIdentity(double[] matrix) {
		return java.util.Arrays.equals(matrix, TessellatedMesh.identityTransform());
	}
	private static Map<String, Object> map(Object... values) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
		return result;
	}
	private record Encoded(Map<String, Object> document, byte[] binary) { }
	private GltfGeometryWriter() { }
}
