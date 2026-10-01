package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.rwth_aachen.dc.lbd.TessellatedMesh;

class TessellatedMeshTest {
	@Test
	void defensivelyCopiesDataAndComputesWorldBounds() {
		double[] positions = { 0, 0, 0, 2, 0, 0, 0, 3, 0 };
		double[] transform = TessellatedMesh.identityTransform();
		transform[12] = 10;
		transform[13] = -4;
		TessellatedMesh mesh = new TessellatedMesh("guid", positions, null, new int[] { 0, 1, 2 },
				List.of(new TessellatedMesh.Material("default", new double[] { 0.2, 0.3, 0.4 }, 1)),
				new int[] { 0 }, transform);

		positions[0] = 99;
		transform[12] = 99;
		assertArrayEquals(new double[] { 0, 0, 0, 2, 0, 0, 0, 3, 0 }, mesh.positions());
		assertEquals(3, mesh.vertexCount());
		assertEquals(1, mesh.triangleCount());
		assertEquals(10, mesh.worldBoundingBox().getMin().x);
		assertEquals(-4, mesh.worldBoundingBox().getMin().y);
		assertEquals(12, mesh.worldBoundingBox().getMax().x);
		assertEquals(-1, mesh.worldBoundingBox().getMax().y);
	}

	@Test
	void rejectsMalformedTriangleData() {
		assertThrows(IllegalArgumentException.class, () -> new TessellatedMesh("guid",
				new double[] { 0, 0, 0 }, null, new int[] { 0, 1, 2 }, List.of(), null, null));
	}
}
