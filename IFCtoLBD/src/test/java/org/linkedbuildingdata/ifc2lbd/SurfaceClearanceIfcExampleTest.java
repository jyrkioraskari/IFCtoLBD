package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurfaceClearanceIfcExampleTest {
	@Test void queriesDuctToSlabClearanceFromConvertedIfc(@TempDir Path temp) throws Exception {
		Path slabFile = Files.copy(Path.of("..", "examples", "clearance", "slab.ifc"), temp.resolve("slab.ifc"));
		Path ductFile = Files.copy(Path.of("..", "examples", "clearance", "duct.ifc"), temp.resolve("duct.ifc"));
		try (ConversionSession slabSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				ConversionSession ductSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter slabConverter = new IFCtoLBDConverter(slabSession, "https://example.org/clearance/");
				IFCtoLBDConverter ductConverter = new IFCtoLBDConverter(ductSession, "https://example.org/clearance/");
				ConversionResult slabResult = slabConverter.convert(new ConversionRequest(slabFile.toString(),
						ConversionProfiles.REVISION_READY).withModelScope("slab"));
				ConversionResult ductResult = ductConverter.convert(new ConversionRequest(ductFile.toString(),
						ConversionProfiles.REVISION_READY).withModelScope("duct"));
				GeometryResult slabGeometry = IfcOpenShellGeometryProvider.INSTANCE.load(slabFile);
				GeometryResult ductGeometry = IfcOpenShellGeometryProvider.INSTANCE.load(ductFile)) {
			var duct = new SurfaceClearance.Element("duct", element(ductResult.getModel(), "Clearance duct").getURI(),
					revision(ductResult), "1PUMP00000000000000009");
			var slab = new SurfaceClearance.Element("slab", element(slabResult.getModel(), "Existing slab").getURI(),
					revision(slabResult), "1PUMP00000000000000008");
			var result = new SurfaceClearance().measure(duct, ductGeometry, slab, slabGeometry,
					SurfaceClearance.Alignment.identity(), 0.001);
			assertEquals(SurfaceClearance.Status.SEPARATED, result.status());
			assertEquals(0.2, result.distanceMetres(), 1e-6);
			Model graph = result.toModel();
			Path companion = temp.resolve("duct-slab-clearance.ttl");
			try (var output = Files.newOutputStream(companion)) {
				RDFDataMgr.write(output, graph, RDFFormat.TURTLE_PRETTY);
			} finally { graph.close(); }
			Model reloaded = ModelFactory.createDefaultModel();
			try {
				RDFDataMgr.read(reloaded, companion.toUri().toString(), Lang.TURTLE);
				String sparql = Files.readString(Path.of("..", "examples", "clearance", "query.rq"));
				try (var query = QueryExecutionFactory.create(QueryFactory.create(sparql), reloaded)) {
					var rows = query.execSelect();
					assertTrue(rows.hasNext());
					var row = rows.next();
					assertEquals(duct.iri(), row.getResource("duct").getURI());
					assertEquals(slab.iri(), row.getResource("slab").getURI());
					assertEquals(0.2, row.getLiteral("distance").getDouble(), 1e-6);
					String actual = row.getResource("duct").getURI() + "|" + row.getResource("slab").getURI()
							+ "|" + String.format(java.util.Locale.ROOT, "%.3f", row.getLiteral("distance").getDouble())
							+ "|m|" + row.getLiteral("status").getString() + "|"
							+ row.getLiteral("method").getString() + "|"
							+ row.getLiteral("tolerance").getDouble() + "|"
							+ row.getLiteral("ductRevision").getString() + "|"
							+ row.getLiteral("slabRevision").getString();
					assertEquals(Files.readString(Path.of("..", "examples", "clearance", "expected.txt")).trim(),
							actual);
					assertTrue(!rows.hasNext());
				}
			} finally { reloaded.close(); }
		}
	}

	@Test void measuresThePositiveGapBetweenAlignedConvertedModels(@TempDir Path temp) throws Exception {
		Path existing = Files.copy(Path.of("..", "examples", "verified-interfaces", "existing.ifc"),
				temp.resolve("existing.ifc"));
		Path renovation = Files.copy(Path.of("..", "examples", "verified-interfaces", "renovation.ifc"),
				temp.resolve("renovation.ifc"));
		try (ConversionSession aSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				ConversionSession bSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter aConverter = new IFCtoLBDConverter(aSession, "https://example.org/interface/");
				IFCtoLBDConverter bConverter = new IFCtoLBDConverter(bSession, "https://example.org/interface/");
				ConversionResult a = aConverter.convert(new ConversionRequest(existing.toString(),
						ConversionProfiles.REVISION_READY).withModelScope("existing"));
				ConversionResult b = bConverter.convert(new ConversionRequest(renovation.toString(),
						ConversionProfiles.REVISION_READY).withModelScope("renovation"));
				GeometryResult wallGeometry = IfcOpenShellGeometryProvider.INSTANCE.load(existing);
				GeometryResult insulationGeometry = IfcOpenShellGeometryProvider.INSTANCE.load(renovation)) {
			assertNotNull(wallGeometry.getTessellatedMesh("1PUMP00000000000000008"));
			assertNotNull(insulationGeometry.getTessellatedMesh("1PUMP00000000000000009"));
			var wall = new SurfaceClearance.Element("existing", element(a.getModel(), "Existing wall").getURI(),
					revision(a), "1PUMP00000000000000008");
			var insulation = new SurfaceClearance.Element("renovation", element(b.getModel(), "Gap insulation").getURI(),
					revision(b),
					"1PUMP00000000000000009");
			var touching = new SurfaceClearance.Element("renovation", element(b.getModel(), "Renovation insulation").getURI(),
					revision(b),
					"1PUMP00000000000000008");
			var result = new SurfaceClearance().measure(wall, wallGeometry, insulation, insulationGeometry,
					SurfaceClearance.Alignment.identity(), 0.001);
			assertEquals(SurfaceClearance.Status.SEPARATED, result.status());
			assertEquals(0.2, result.distanceMetres(), 1e-6);
			Path companion = temp.resolve("clearance.ttl");
			Model graph = result.toModel();
			try (var output = Files.newOutputStream(companion)) {
				RDFDataMgr.write(output, graph, RDFFormat.TURTLE_PRETTY);
			} finally { graph.close(); }
			Model reloaded = ModelFactory.createDefaultModel();
			try {
				RDFDataMgr.read(reloaded, companion.toUri().toString(), Lang.TURTLE);
				String sparql = Files.readString(Path.of("..", "examples", "clearance", "query-all.rq"));
				try (var query = QueryExecutionFactory.create(QueryFactory.create(sparql), reloaded)) {
					var rows = query.execSelect();
					assertTrue(rows.hasNext());
					var row = rows.next();
					assertEquals(wall.iri(), row.getResource("first").getURI());
					assertEquals(insulation.iri(), row.getResource("second").getURI());
					assertEquals(0.2, row.getLiteral("distance").getDouble(), 1e-6);
					assertEquals(wall.revision(), row.getLiteral("firstRevision").getString());
					assertEquals(insulation.revision(), row.getLiteral("secondRevision").getString());
					assertTrue(!rows.hasNext());
				}
			} finally { reloaded.close(); }
			var contact = new SurfaceClearance().measure(wall, wallGeometry, touching, insulationGeometry,
					SurfaceClearance.Alignment.identity(), 0.001);
			assertEquals(SurfaceClearance.Status.CONTACT, contact.status());
			try (var discovered = new GeometryInterfaceCandidateDiscovery().discover(List.of(wall), wallGeometry,
					List.of(touching, insulation), insulationGeometry, SurfaceClearance.Alignment.identity(), 0.001)) {
				assertTrue(discovered.diagnostics().isEmpty());
				assertEquals(1, discovered.graph().listResourcesWithProperty(RDF.type,
						discovered.graph().createResource("https://w3id.org/ifctolbd/mapping#GeometryInterfaceCandidate"))
						.toList().size());
				assertEquals(0, discovered.graph().listResourcesWithProperty(RDF.type, BOT.bot_interface).toList().size());
			}
			try (var missingAlignment = new GeometryInterfaceCandidateDiscovery().discover(List.of(wall),
					wallGeometry, List.of(touching), insulationGeometry, null, 0.001)) {
				assertEquals("MISSING_ALIGNMENT", missingAlignment.diagnostics().getFirst().code());
			}
			var absent = new SurfaceClearance.Element("renovation", touching.iri(), touching.revision(),
					"nonexistent-global-id");
			try (var unusable = new GeometryInterfaceCandidateDiscovery().discover(List.of(wall),
					wallGeometry, List.of(absent), insulationGeometry, SurfaceClearance.Alignment.identity(), 0.001)) {
				assertEquals("UNUSABLE_GEOMETRY", unusable.diagnostics().getFirst().code());
				assertEquals(0, unusable.graph().size());
			}
		}
	}

	private static Resource element(Model model, String label) {
		return model.listResourcesWithProperty(RDFS.label, label).toList().stream()
				.filter(value -> value.hasProperty(RDF.type, BOT.element)).findFirst().orElseThrow();
	}
	private static String revision(ConversionResult result) {
		Resource conversion = result.getManifestModel().listResourcesWithProperty(RDF.type,
				result.getManifestModel().createResource(ConversionManifest.NS + "Conversion")).next();
		return conversion.getProperty(result.getManifestModel().createProperty(
				ConversionManifest.NS + "sourceChecksum")).getString();
	}
}
