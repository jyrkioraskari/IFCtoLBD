package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linkedbuildingdata.ifc2lbd.namespace.BMO;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

@Tag("integration")
class MaterialsBmoIntegrationTest {
	private static final String QUDT = UnitResolver.QUDT_SCHEMA;
	private static final String MATERIAL_SHAPES = "shacl/materials-bmo-v1.0.0.ttl";

	@Test
	void convertsIfc4AssignmentsLayersUsagesAndMillimetres() throws Exception {
		try (Converted converted = convert("/materials/materials-ifc4.ifc")) {
			ConversionResult result = converted.result();
			Model products = result.getProductModel();
			Model properties = result.getPropertyModel();

			Resource directAssignment = assignment(products, "Direct wall");
			Resource directMaterial = directAssignment.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterial);
			assertNotNull(directMaterial);
			assertTrue(directMaterial.hasProperty(RDF.type, BMO.Material));
			assertTrue(directMaterial.hasProperty(RDFS.label, "Concrete"));
			assertTrue(directAssignment.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterialDefinition)
					.hasProperty(RDF.type, BMO.MaterialDefinition));

			Resource orderedAssignment = assignment(products, "Ordered wall");
			Resource layerSet = orderedAssignment
					.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterialDefinition);
			List<Resource> memberships = layerSet.listProperties(IFCtoLBDMapping.layerMembership)
					.mapWith(statement -> statement.getResource()).toList().stream()
					.sorted(Comparator.comparingInt(MaterialsBmoIntegrationTest::position)).toList();
			assertEquals(List.of(1, 2, 3), memberships.stream()
					.map(MaterialsBmoIntegrationTest::position).toList());
			assertEquals(3, layerSet.listProperties(BMO.hasLayers).toList().size());
			Resource firstLayer = memberships.get(0).getPropertyResourceValue(IFCtoLBDMapping.layer);
			Resource secondLayer = memberships.get(1).getPropertyResourceValue(IFCtoLBDMapping.layer);
			Resource thirdLayer = memberships.get(2).getPropertyResourceValue(IFCtoLBDMapping.layer);
			assertEquals(firstLayer.getPropertyResourceValue(BMO.hasMaterial),
					thirdLayer.getPropertyResourceValue(BMO.hasMaterial));
			assertNotEquals(firstLayer.getPropertyResourceValue(BMO.hasMaterial),
					secondLayer.getPropertyResourceValue(BMO.hasMaterial));

			Resource firstLayerValues = properties.createResource(firstLayer.getURI());
			assertNotNull(firstLayerValues.getProperty(BMO.hasThickness),
					() -> result.getValidationModel().listStatements().toList().toString());
			assertEquals("0.1", firstLayerValues.getProperty(BMO.hasThickness).getLiteral().getLexicalForm());
			Resource thickness = firstLayerValues.getPropertyResourceValue(IFCtoLBDMapping.thicknessQuantity);
			assertEquals("0.1", thickness.getProperty(properties.createProperty(QUDT + "numericValue"))
					.getLiteral().getLexicalForm());
			assertEquals(UnitResolver.QUDT_UNIT + "M",
					thickness.getPropertyResourceValue(properties.createProperty(QUDT + "unit")).getURI());

			Resource sharedAssignment = assignment(products, "Shared usage wall");
			assertEquals(layerSet,
					sharedAssignment.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterialDefinition));
			Resource firstUsage = orderedAssignment.getPropertyResourceValue(IFCtoLBDMapping.layerSetUsage);
			Resource secondUsage = sharedAssignment.getPropertyResourceValue(IFCtoLBDMapping.layerSetUsage);
			assertNotEquals(firstUsage, secondUsage);
			assertTrue(firstUsage.hasProperty(IFCtoLBDMapping.directionSense, "POSITIVE"));
			assertTrue(secondUsage.hasProperty(IFCtoLBDMapping.directionSense, "NEGATIVE"));
			assertEquals("12.5", properties.createResource(firstUsage.getURI())
					.getProperty(IFCtoLBDMapping.offsetFromReferenceLine).getLiteral().getLexicalForm());

			Resource inherited = assignment(products, "Inherited wall");
			assertTrue(inherited.getProperty(IFCtoLBDMapping.materialAssignmentInherited).getBoolean());
			assertTrue(inherited.hasProperty(IFCtoLBDMapping.sourceIfcType, "IfcWallType"));
			Resource overridden = assignment(products, "Override wall");
			assertFalse(overridden.getProperty(IFCtoLBDMapping.materialAssignmentInherited).getBoolean());
			assertTrue(overridden.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterial)
					.hasProperty(RDFS.label, "Occurrence material"));

			Resource sameA = assignment(products, "Same name wall A")
					.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterial);
			Resource sameB = assignment(products, "Same name wall B")
					.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterial);
			assertNotEquals(sameA, sameB);
			assertTrue(sameA.hasProperty(RDFS.label, "Same name"));
			assertTrue(sameB.hasProperty(RDFS.label, "Same name"));

			assertDiagnostic(result, "MISSING_LAYER_MATERIAL");
			assertDiagnostic(result, "MISSING_LAYER_THICKNESS");
			assertValidationConforms(result);
			assertTrue(result.getManifestModel().contains(null,
					result.getManifestModel().createProperty(ConversionManifest.NS + "ifcSchema"), "IFC4_ADD2"));
		}
	}

	@Test
	void convertsIfc2x3InheritanceAndDoesNotFallbackFromUnsupportedOccurrence() throws Exception {
		try (Converted converted = convert("/materials/materials-ifc2x3.ifc")) {
			ConversionResult result = converted.result();
			Model products = result.getProductModel();
			Resource direct = assignment(products, "Legacy direct wall");
			assertTrue(direct.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterial)
					.hasProperty(RDFS.label, "Legacy direct material"));

			Resource inherited = assignment(products, "Legacy inherited wall");
			assertTrue(inherited.getProperty(IFCtoLBDMapping.materialAssignmentInherited).getBoolean());
			assertTrue(inherited.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterialDefinition)
					.hasProperty(RDF.type, BMO.LayerSet));

			Resource unsupported = assignment(products, "Unsupported occurrence wall");
			assertFalse(unsupported.hasProperty(IFCtoLBDMapping.assignedMaterialDefinition));
			assertFalse(products.contains(null, RDFS.label, "Fallback must not appear"));
			assertDiagnostic(result, "UNSUPPORTED_MATERIAL_STRUCTURE");
			assertValidationConforms(result);
			assertTrue(result.getManifestModel().contains(null,
					result.getManifestModel().createProperty(ConversionManifest.NS + "ifcSchema"), "IFC2X3_TC1"));
		}
	}

	@Test
	void identifiersAreDeterministicAndCoreProfileRemainsMaterialFree() throws Exception {
		File coreFixture = fixture("/materials/materials-ifc2x3.ifc");
		try (Converted firstConversion = convert("/materials/materials-ifc4.ifc");
				Converted secondConversion = convert("/materials/materials-ifc4.ifc");
				ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult core = converter.convert(
						new ConversionRequest(coreFixture.getAbsolutePath(), ConversionProfiles.CORE))) {
			ConversionResult first = firstConversion.result();
			ConversionResult second = secondConversion.result();
			assertEquals(materialUris(first.getModel()), materialUris(second.getModel()));
			assertFalse(core.getModel().contains(null, IFCtoLBDMapping.materialAssignment));
			assertFalse(core.getManifestModel().contains(null,
					core.getManifestModel().createProperty(ConversionManifest.NS + "shapePack")));
		}
	}

	@Test
	void legacyDesktopPhaseIncludesMaterialsInMergedTurtleModel(@TempDir Path tempDir) throws Exception {
		ConversionProperties properties = new ConversionProperties();
		properties.setHasPerformanceBoost(false);
		properties.setExportIfcOWL(false);
		properties.setHasGeometry(false);
		properties.setHasGeolocation(false);
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/")) {
			Path outputFile = tempDir.resolve("materials.ttl");
			converter.setTargetFile(outputFile.toString());
			converter.setMaterialsBmoEnabled(true);
			Model output = converter.convert(fixture("/materials/materials-ifc4.ifc").getAbsolutePath(), properties);
			assertTrue(output.contains(null, IFCtoLBDMapping.materialAssignment));
			assertTrue(output.contains(null, RDF.type, BMO.Material));
			assertTrue(output.contains(null, BMO.hasThickness));
			Model turtle = RDFDataMgr.loadModel(outputFile.toString());
			assertTrue(turtle.contains(null, IFCtoLBDMapping.materialAssignment));
			assertTrue(turtle.contains(null, RDF.type, BMO.Material));
			assertTrue(turtle.contains(null, BMO.hasThickness));
		}
	}

	@Test
	void shaclRejectsBadTargetsDuplicatePositionsAndUnitlessNormalizedThickness() {
		Model data = ModelFactory.createDefaultModel();
		Resource wrong = data.createResource("urn:test:wrong");
		Resource assignment = data.createResource("urn:test:assignment")
				.addProperty(RDF.type, IFCtoLBDMapping.MaterialAssignment)
				.addProperty(IFCtoLBDMapping.sourceIfcAssociation, data.createResource("urn:test:association"))
				.addProperty(IFCtoLBDMapping.assignedMaterialDefinition, wrong);
		Resource layerSet = data.createResource("urn:test:set").addProperty(RDF.type, BMO.LayerSet);
		Resource layer = data.createResource("urn:test:layer").addProperty(RDF.type, BMO.Layer)
				.addProperty(BMO.hasMaterial, wrong);
		for (String suffix : List.of("a", "b")) {
			Resource membership = data.createResource("urn:test:membership:" + suffix)
					.addProperty(RDF.type, IFCtoLBDMapping.LayerMembership)
					.addLiteral(IFCtoLBDMapping.position, 1)
					.addProperty(IFCtoLBDMapping.layer, layer);
			layerSet.addProperty(IFCtoLBDMapping.layerMembership, membership);
		}
		Resource quantity = data.createResource("urn:test:quantity")
				.addLiteral(data.createProperty(QUDT + "numericValue"),
						data.createTypedLiteral("0.2", XSDDatatype.XSDdecimal));
		layer.addProperty(IFCtoLBDMapping.thicknessQuantity, quantity);

		ConversionRequest request = new ConversionRequest("offline.ifc", ConversionProfiles.CORE);
		ValidationStage.Result result = ValidationStage.validate(request, Set.of(MATERIAL_SHAPES), data);
		assertEquals("violations", result.status());
		assertTrue(result.report().contains(null,
				result.report().createProperty("http://www.w3.org/ns/shacl#resultMessage")));
		assertTrue(result.report().listStatements(null,
				result.report().createProperty("http://www.w3.org/ns/shacl#resultMessage"),
				(org.apache.jena.rdf.model.RDFNode) null).toList().stream()
				.anyMatch(statement -> statement.getString().contains("unique within a layer set")));
		var focusNode = result.report().createProperty("http://www.w3.org/ns/shacl#focusNode");
		assertTrue(result.report().contains(null, focusNode, assignment));
		assertTrue(result.report().contains(null, focusNode, layerSet));
		assertTrue(result.report().contains(null, focusNode, layer));
		assertTrue(result.report().contains(null, focusNode, quantity));
	}

	private Converted convert(String resource) throws Exception {
		ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
		IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
		try {
			return new Converted(session, converter, converter.convert(new ConversionRequest(
					fixture(resource).getAbsolutePath(), ConversionProfiles.MATERIALS_BMO)));
		} catch (Exception e) {
			converter.close();
			session.close();
			throw e;
		}
	}

	private record Converted(ConversionSession session, IFCtoLBDConverter converter,
			ConversionResult result) implements AutoCloseable {
		@Override
		public void close() {
			result.close();
			converter.close();
			session.close();
		}
	}

	private File fixture(String resource) throws Exception {
		return new File(getClass().getResource(resource).toURI());
	}

	private static Resource assignment(Model products, String elementLabel) {
		Resource element = products.listResourcesWithProperty(RDFS.label, elementLabel).nextResource();
		Resource assignment = element.getPropertyResourceValue(IFCtoLBDMapping.materialAssignment);
		assertNotNull(assignment, "No material assignment for " + elementLabel);
		return assignment;
	}

	private static int position(Resource membership) {
		return membership.getProperty(IFCtoLBDMapping.position).getInt();
	}

	private static Set<String> materialUris(Model model) {
		return model.listResourcesWithProperty(RDF.type).toList().stream()
				.filter(Resource::isURIResource)
				.filter(resource -> resource.hasProperty(RDF.type, BMO.Material)
						|| resource.hasProperty(RDF.type, BMO.Layer)
						|| resource.hasProperty(RDF.type, BMO.LayerSet)
						|| resource.hasProperty(RDF.type, IFCtoLBDMapping.MaterialAssignment)
						|| resource.hasProperty(RDF.type, IFCtoLBDMapping.LayerMembership))
				.map(Resource::getURI).collect(java.util.stream.Collectors.toSet());
	}

	private static void assertDiagnostic(ConversionResult result, String code) {
		Model validation = result.getValidationModel();
		assertTrue(validation.contains(null,
				validation.createProperty(MaterialConversionStage.REPORT_NS + "code"), code));
		assertTrue(validation.listStatements(null,
				validation.createProperty(MaterialConversionStage.REPORT_NS + "code"), code).toList().stream()
				.allMatch(statement -> statement.getSubject().hasProperty(
						validation.createProperty(MaterialConversionStage.REPORT_NS + "message"))));
	}

	private static void assertValidationConforms(ConversionResult result) {
		Model manifest = result.getManifestModel();
		assertTrue(manifest.contains(null,
				manifest.createProperty(ConversionManifest.NS + "validationStatus"), "conforms"),
				() -> result.getValidationModel().listStatements().toList().toString());
		assertTrue(manifest.contains(null,
				manifest.createProperty(ConversionManifest.NS + "shapePack"), MATERIAL_SHAPES));
	}
}
