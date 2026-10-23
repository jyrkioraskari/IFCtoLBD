package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.rwth_aachen.dc.lbd.TessellatedMesh;

class GltfGeometryWriterTest {
	private static TessellatedMesh triangle() {
		return new TessellatedMesh("0123456789012345678901",
				new double[] { 0, 0, 0, 2, 0, 0, 0, 3, 0 },
				new double[] { 0, 0, 1, 0, 0, 1, 0, 0, 1 }, new int[] { 0, 1, 2 },
				List.of(), new int[0], TessellatedMesh.identityTransform());
	}

	@Test
	void writesSelfContainedGltf() throws Exception {
		byte[] output = GltfGeometryWriter.write(triangle(), GeometryArtifactFormat.GLTF);
		var json = new ObjectMapper().readTree(output);
		assertEquals("2.0", json.path("asset").path("version").asText());
		assertTrue(json.path("buffers").get(0).path("uri").asText().startsWith(
				"data:application/octet-stream;base64,"));
		assertEquals(3, json.path("accessors").get(0).path("count").asInt());
	}

	@Test
	void writesValidGlbContainerHeaders() throws Exception {
		byte[] output = GltfGeometryWriter.write(triangle(), GeometryArtifactFormat.GLB);
		ByteBuffer buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals(0x46546c67, buffer.getInt());
		assertEquals(2, buffer.getInt());
		assertEquals(output.length, buffer.getInt());
		int jsonLength = buffer.getInt();
		assertEquals(0x4E4F534A, buffer.getInt());
		byte[] json = new byte[jsonLength]; buffer.get(json);
		assertEquals("2.0", new ObjectMapper().readTree(new String(json, StandardCharsets.UTF_8).trim())
				.path("asset").path("version").asText());
	}
}
