package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;

class SpotProfileTest {
	private static final Path FIXTURE = Path.of("..", "examples", "getting-started", "building-inventory", "model.ifc");
	private static final String BASE = "https://example.org/spot-demo/";
	private static final String PROJECT = "https://example.org/assets/demo/";
	private static final String REVISION = "https://example.org/models/demo/revision-1";
	private static final String ASSET = "https://example.org/assets/demo/building";
	private static final String QUERY = """
		PREFIX spot: <https://w3id.org/spot#>
		SELECT DISTINCT ?element WHERE {
		  ?model a spot:VolumeSpace ; spot:represents ?revision .
		  ?space a spot:EntitySpace ;
		         spot:represents ?element ;
		         spot:hasAppearance ?appearance .
		  ?appearance a spot:EntityAppearance ; spot:appearsIn ?model .
		}
		""";

	@Test void geometryProfileRunsTheExistingGeometryProviderAndKeepsSpotLookup() {
		AtomicInteger loads = new AtomicInteger();
		GeometryProvider provider = new GeometryProvider() {
			@Override public String id() { return "spot-test"; }
			@Override public String version() { return "1"; }
			@Override public GeometryResult load(Path ifcFile) {
				assertEquals(FIXTURE.toAbsolutePath().normalize(), ifcFile.toAbsolutePath().normalize());
				loads.incrementAndGet();
				return GeometryResult.unavailable();
			}
		};
		assertEquals(ConversionProfiles.SPOT_GEOMETRY, ConversionProfiles.named("spot-geometry"));
		assertFalse(ConversionProfiles.SPOT.toConversionProperties().isHasGeometry());
		assertTrue(ConversionProfiles.SPOT_GEOMETRY.toConversionProperties().isHasGeometry());
		try (ConversionSession session = new ConversionSession(provider);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, BASE);
				ConversionResult result = converter.convert(request(ConversionProfiles.SPOT_GEOMETRY)
						.withSpotModelRevisionIri(REVISION))) {
			Model serialized = turtle(result);
			try {
				assertEquals(2, lookup(serialized, REVISION).size());
			} finally { serialized.close(); }
			assertEquals(1, loads.get());
		}
	}

	@Test void serializedAndDatasetLookupUseExactlyTheExportedWallUris() {
		Model baseline = snapshot(request(ConversionProfiles.CORE));
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, BASE);
				ConversionResult spot = converter.convert(request(ConversionProfiles.SPOT)
						.withSpotModelRevisionIri(REVISION).withSpotAssetIri(ASSET))) {
			Set<String> walls = elementUris(baseline);
			assertEquals(2, walls.size());
			assertEquals(walls, lookup(spot.getDataset().getDefaultModel(), REVISION));
			assertEquals(walls, lookup(spot.getModel(), REVISION));
			Model reloaded = turtle(spot);
			try {
				assertEquals(walls, lookup(reloaded, REVISION));
				Resource modelSpace = reloaded.listResourcesWithProperty(reloaded.createProperty(SpotStage.NS + "represents"),
						reloaded.createResource(REVISION)).next();
				Resource appearance = modelSpace.getPropertyResourceValue(reloaded.createProperty(SpotStage.NS + "hasAppearance"));
				assertTrue(appearance.hasProperty(RDF.type, reloaded.createResource(SpotStage.NS + "AssetAppearance")));
				Resource assetSpace = appearance.getPropertyResourceValue(reloaded.createProperty(SpotStage.NS + "appearsIn"));
				assertTrue(assetSpace.hasProperty(RDF.type, reloaded.createResource(SpotStage.NS + "AssetSpace")));
				assertTrue(assetSpace.hasProperty(reloaded.createProperty(SpotStage.NS + "represents"),
						reloaded.createResource(ASSET)));
			} finally { reloaded.close(); }
		} finally { baseline.close(); }
	}

	@Test void absentAssetKeepsLookupAndReportsTheMissingIdentity() {
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, BASE);
				ConversionResult result = converter.convert(request(ConversionProfiles.SPOT)
						.withSpotModelRevisionIri(REVISION))) {
			assertEquals(2, lookup(result.getModel(), REVISION).size());
			assertFalse(result.getModel().contains(null, RDF.type,
					result.getModel().createResource(SpotStage.NS + "AssetSpace")));
			assertTrue(result.getValidationModel().contains(null,
					result.getValidationModel().createProperty("https://w3id.org/ifctolbd/spot#code"),
					"AssetIdentityMissing"));
		}
	}

	@Test void defaultRevisionReusesTheManifestSourceChecksum() {
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, BASE);
				ConversionResult result = converter.convert(request(ConversionProfiles.SPOT))) {
			String sourceIri = "urn:sha256:" + ConversionManifest.sha256(FIXTURE);
			assertEquals(2, lookup(result.getModel(), sourceIri).size());
			assertTrue(result.getManifestModel().contains(null,
					result.getManifestModel().createProperty("http://www.w3.org/ns/prov#wasDerivedFrom"),
					result.getManifestModel().createResource(sourceIri)));
		}
	}

	@Test void multipleModelContextsAreReportedWithoutClaimingOneVolume(@TempDir Path temp) throws Exception {
		String source = Files.readString(FIXTURE)
				.replace("(#101),#96)", "(#101,#999),#96)")
				.replace("#101= IFCGEOMETRICREPRESENTATIONCONTEXT($,'Model',3,1.00000000000000E-5,#98,#99);",
						"#101= IFCGEOMETRICREPRESENTATIONCONTEXT($,'Model',3,1.00000000000000E-5,#98,#99);\n"
								+ "#999= IFCGEOMETRICREPRESENTATIONCONTEXT($,'Other',3,1.00000000000000E-5,#98,#99);");
		Path input = Files.writeString(temp.resolve("two-contexts.ifc"), source);
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, BASE);
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(), ConversionProfiles.SPOT)
						.withSpotModelRevisionIri(REVISION))) {
			assertTrue(spaceUris(result.getModel(), "VolumeSpace").isEmpty());
			assertTrue(result.getValidationModel().contains(null,
					result.getValidationModel().createProperty("https://w3id.org/ifctolbd/spot#code"),
					"UnsupportedModelExtent"));
		}
	}

	@Test void spotIdentityInputsMustBeAbsoluteIris() {
		assertThrows(IllegalArgumentException.class, () -> request(ConversionProfiles.SPOT)
				.withSpotModelRevisionIri("model.ifc"));
		assertThrows(IllegalArgumentException.class, () -> request(ConversionProfiles.SPOT)
				.withSpotAssetIri("file:/tmp/asset"));
		assertThrows(IllegalArgumentException.class, () -> request(ConversionProfiles.CORE)
				.withSpotModelRevisionIri(REVISION));
	}

	@Test void stableIdentityKeepsElementsAndSeparatesRevisionAppearances() {
		Model first = snapshot(request(ConversionProfiles.SPOT)
				.withStableProjectIdentity(PROJECT).withSpotModelRevisionIri(REVISION));
		Model repeat = snapshot(request(ConversionProfiles.SPOT)
				.withStableProjectIdentity(PROJECT).withSpotModelRevisionIri(REVISION));
		Model second = snapshot(request(ConversionProfiles.SPOT)
				.withStableProjectIdentity(PROJECT).withSpotModelRevisionIri(REVISION + "-2"));
		try {
			Model firstSpot = spotGraph(first);
			Model repeatSpot = spotGraph(repeat);
			try {
				assertTrue(firstSpot.isIsomorphicWith(repeatSpot));
				assertEquals(spaceUris(firstSpot, "EntityAppearance"), spaceUris(repeatSpot, "EntityAppearance"));
				assertEquals(lookup(first, REVISION), lookup(second, REVISION + "-2"));
				assertNotEquals(spaceUris(firstSpot, "VolumeSpace"), spaceUris(second, "VolumeSpace"));
				assertNotEquals(spaceUris(firstSpot, "EntityAppearance"), spaceUris(second, "EntityAppearance"));
			} finally { firstSpot.close(); repeatSpot.close(); }
		} finally { first.close(); repeat.close(); second.close(); }
	}

	@Test void coreProfileStaysSpotFreeAndIsomorphic() {
		Model first = snapshot(request(ConversionProfiles.CORE));
		Model repeat = snapshot(request(ConversionProfiles.CORE));
		try {
			assertTrue(first.isIsomorphicWith(repeat));
			Model firstSpot = spotGraph(first);
			Model repeatSpot = spotGraph(repeat);
			try {
				assertTrue(firstSpot.isEmpty());
				assertTrue(repeatSpot.isEmpty());
			} finally { firstSpot.close(); repeatSpot.close(); }
		} finally { first.close(); repeat.close(); }
	}

	@Test void existingIdentityPolicyAlsoKeepsTheElementUrisAcrossRevisions() {
		Model first = snapshot(request(ConversionProfiles.SPOT).withSpotModelRevisionIri(REVISION));
		Model second = snapshot(request(ConversionProfiles.SPOT).withSpotModelRevisionIri(REVISION + "-2"));
		try {
			assertEquals(lookup(first, REVISION), lookup(second, REVISION + "-2"));
			assertNotEquals(spaceUris(first, "EntityAppearance"), spaceUris(second, "EntityAppearance"));
		} finally { first.close(); second.close(); }
	}

	@Test void selectedTypeMapsOnlyTheRemainingWall(@TempDir Path temp) throws Exception {
		Model baseline = snapshot(request(ConversionProfiles.CORE));
		String source = Files.readString(FIXTURE);
		// The sample has two WallStandardCase instances. Convert one to a slab so
		// the existing selected-type filter can select precisely one wall.
		source = source.replace("#274= IFCWALLSTANDARDCASE(", "#274= IFCSLAB(");
		Path input = Files.writeString(temp.resolve("one-wall-one-slab.ifc"), source);
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, BASE);
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(), ConversionProfiles.SPOT)
						.withSelectedTypes(Set.of("Wall")).withSpotModelRevisionIri(REVISION))) {
			Set<String> selected = lookup(result.getModel(), REVISION);
			assertEquals(1, selected.size());
			assertTrue(elementUris(baseline).containsAll(selected));
			assertEquals(elementUris(result.getGeneralModel()), selected);
		} finally { baseline.close(); }
	}

	private static ConversionRequest request(ConversionProfile profile) {
		return new ConversionRequest(FIXTURE.toString(), profile);
	}

	private static Model snapshot(ConversionRequest request) {
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, BASE);
				ConversionResult result = converter.convert(request)) {
			return ModelFactory.createDefaultModel().add(result.getGeneralModel())
					.add(result.getProductModel()).add(result.getPropertyModel());
		}
	}

	private static Set<String> elementUris(Model model) {
		Set<String> result = new HashSet<>();
		model.listResourcesWithProperty(RDF.type, BOT.element).forEachRemaining(element -> result.add(element.getURI()));
		return result;
	}

	private static Set<String> lookup(Model model, String revision) {
		ParameterizedSparqlString query = new ParameterizedSparqlString(QUERY);
		query.setIri("revision", revision);
		Set<String> result = new HashSet<>();
		try (var execution = QueryExecutionFactory.create(query.asQuery(), model)) {
			var rows = execution.execSelect();
			while (rows.hasNext()) result.add(rows.next().getResource("element").getURI());
		}
		return result;
	}

	private static Model turtle(ConversionResult result) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		IFCtoLBDConverter_CLI.writeTurtle(bytes, result);
		Model reloaded = ModelFactory.createDefaultModel();
		RDFDataMgr.read(reloaded, new ByteArrayInputStream(bytes.toByteArray()), Lang.TURTLE);
		return reloaded;
	}

	private static Model spotGraph(Model source) {
		Model graph = ModelFactory.createDefaultModel();
		source.listStatements().forEachRemaining(statement -> {
			if (statement.getPredicate().getURI().startsWith(SpotStage.NS)
					|| statement.getObject().isURIResource()
					&& statement.getResource().getURI().startsWith(SpotStage.NS)) graph.add(statement);
		});
		return graph;
	}

	private static Set<String> spaceUris(Model model, String type) {
		Set<String> uris = new HashSet<>();
		model.listResourcesWithProperty(RDF.type, model.createResource(SpotStage.NS + type))
				.forEachRemaining(resource -> uris.add(resource.getURI()));
		return uris;
	}
}
