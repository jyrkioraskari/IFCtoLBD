package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.junit.jupiter.api.Test;

import de.rwth_aachen.dc.lbd.TessellatedMesh;

class SurfaceClearanceTest {
	private final SurfaceClearance operation = new SurfaceClearance();
	private static final SurfaceClearance.Element DUCT = new SurfaceClearance.Element("model-a", "urn:duct", "revision-1", "duct");
	private static final SurfaceClearance.Element SLAB = new SurfaceClearance.Element("model-b", "urn:slab", "revision-2", "slab");
	private static final GeometryResult.Metadata METRES = GeometryResult.Metadata.ifcOpenShellWorldMetres();

	@Test void distinguishesGapContactIntersectionAndContainment() {
		assertMeasurement(box("slab", 1.2, 0, 0, 1), 0.2, SurfaceClearance.Status.SEPARATED);
		assertMeasurement(box("slab", 1, 0, 0, 1), 0, SurfaceClearance.Status.CONTACT);
		assertMeasurement(box("slab", 0.8, 0, 0, 1), 0, SurfaceClearance.Status.PENETRATION);
		assertMeasurement(box("slab", 0.2, 0.2, 0.2, 0.2), 0.2, SurfaceClearance.Status.PENETRATION);
	}

	@Test void missingMeshAndAlignmentHaveNoNumericZero() {
		var noMesh = operation.measureMeshes(DUCT, box("duct", 0, 0, 0, 1), METRES,
				SLAB, null, METRES, SurfaceClearance.Alignment.identity(), 0.001);
		assertEquals(SurfaceClearance.Status.UNAVAILABLE, noMesh.status());
		assertNull(noMesh.distanceMetres());
		var noAlignment = operation.measureMeshes(DUCT, box("duct", 0, 0, 0, 1), METRES,
				SLAB, box("slab", 1.2, 0, 0, 1), METRES, null, 0.001);
		assertEquals(SurfaceClearance.Status.UNAVAILABLE, noAlignment.status());
		assertNull(noAlignment.distanceMetres());
	}

	@Test void placementsUnitsAndEndpointOrderPreserveDistance() throws Exception {
		var metre = operation.measureMeshes(DUCT, box("duct", 0, 0, 0, 1), METRES,
				SLAB, box("slab", 1.2, 0, 0, 1), METRES, SurfaceClearance.Alignment.identity(), 0.001);
		var millimetre = new GeometryResult.Metadata(GeometryResult.CoordinateSpace.LOCAL_ENGINEERING,
				null, "millimetre", 0.001, true, false, List.of());
		var transformed = operation.measureMeshes(DUCT, box("duct", 0, 0, 0, 1000), millimetre,
				SLAB, translatedBox("slab", 1200, 0, 0, 1000), millimetre,
				SurfaceClearance.Alignment.identity(), 0.001);
		var reversed = operation.measureMeshes(SLAB, box("slab", 1.2, 0, 0, 1), METRES,
				DUCT, box("duct", 0, 0, 0, 1), METRES, SurfaceClearance.Alignment.identity(), 0.001);
		assertEquals(metre.status(), transformed.status());
		assertEquals(metre.status(), reversed.status());
		assertEquals(metre.distanceMetres(), transformed.distanceMetres(), 1e-9);
		assertEquals(metre.distanceMetres(), reversed.distanceMetres(), 1e-9);
		try (var graph = new CloseableModel(metre.toModel())) {
			Model rdf = graph.model;
			assertTrue(rdf.contains(null, rdf.createProperty("https://w3id.org/ifctolbd/mapping#distanceMetres")));
			assertTrue(rdf.contains(null, rdf.createProperty("https://w3id.org/ifctolbd/mapping#clearanceStatus"),
					"separated"));
			String queryText = Files.readString(Path.of("..", "examples", "clearance", "query-all.rq"));
			try (var execution = QueryExecutionFactory.create(QueryFactory.create(queryText), rdf)) {
				var results = execution.execSelect();
				assertTrue(results.hasNext());
				var row = results.next();
				String actual = row.getResource("first").getURI() + "|" + row.getResource("second").getURI()
						+ "|" + String.format(java.util.Locale.ROOT, "%.3f", row.getLiteral("distance").getDouble()) + "|m|"
						+ row.getLiteral("status").getString() + "|" + row.getLiteral("method").getString()
						+ "|" + row.getLiteral("tolerance").getDouble() + "|"
						+ row.getLiteral("firstRevision").getString() + "|"
						+ row.getLiteral("secondRevision").getString();
				assertEquals(Files.readString(Path.of("..", "examples", "clearance", "expected-synthetic.txt")).trim(), actual);
			assertTrue(!results.hasNext());
			}
		}
	}

	@Test void openSurfaceDoesNotClaimSolidContact() {
		TessellatedMesh sheet = new TessellatedMesh("slab",
				new double[]{1,0,0, 1,1,0, 1,0,1}, null, new int[]{0,1,2}, List.of(), null,
				TessellatedMesh.identityTransform());
		var result = operation.measureMeshes(DUCT, box("duct", 0, 0, 0, 1), METRES,
				SLAB, sheet, METRES, SurfaceClearance.Alignment.identity(), 0.001);
		assertEquals(SurfaceClearance.Status.INDETERMINATE, result.status());
		assertTrue(result.limitation().contains("Open"));
	}

	private void assertMeasurement(TessellatedMesh other, double expected, SurfaceClearance.Status status) {
		var result = operation.measureMeshes(DUCT, box("duct", 0, 0, 0, 1), METRES,
				SLAB, other, METRES, SurfaceClearance.Alignment.identity(), 0.001);
		assertEquals(status, result.status());
		assertEquals(expected, result.distanceMetres(), 1e-8);
	}

	private static TessellatedMesh translatedBox(String guid, double x, double y, double z, double size) {
		double[] transform = TessellatedMesh.identityTransform();
		transform[12]=x; transform[13]=y; transform[14]=z;
		return mesh(guid, 0, 0, 0, size, transform);
	}
	private static TessellatedMesh box(String guid, double x, double y, double z, double size) {
		return mesh(guid, x, y, z, size, TessellatedMesh.identityTransform());
	}
	private static TessellatedMesh mesh(String guid, double x, double y, double z, double s, double[] transform) {
		double[] vertices = {x,y,z, x+s,y,z, x+s,y+s,z, x,y+s,z,
			x,y,z+s, x+s,y,z+s, x+s,y+s,z+s, x,y+s,z+s};
		int[] faces = {0,2,1,0,3,2,4,5,6,4,6,7,0,1,5,0,5,4,
			3,7,6,3,6,2,0,4,7,0,7,3,1,2,6,1,6,5};
		return new TessellatedMesh(guid,vertices,null,faces,List.of(),null,transform);
	}
	private record CloseableModel(Model model) implements AutoCloseable {
		@Override public void close() { model.close(); }
	}
}
