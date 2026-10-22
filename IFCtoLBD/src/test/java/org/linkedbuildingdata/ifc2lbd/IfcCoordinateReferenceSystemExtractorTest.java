package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linkedbuildingdata.ifc2lbd.core.IFCtoRDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

class IfcCoordinateReferenceSystemExtractorTest {
	private static final String NS = "https://standards.buildingsmart.org/IFC/DEV/IFC4/ADD2/OWL#";

	@Test
	void followsInheritedSourceAndTargetAndReadsWrappedAndDirectValues() {
		Model model = ModelFactory.createDefaultModel(); IfcOWL ifc = new IfcOWL(NS);
		addMetreProject(model, ifc);
		Resource context = model.createResource("urn:context").addProperty(RDF.type, model.createResource(NS + "IfcGeometricRepresentationContext"));
		Resource selected = projected(model, "urn:selected", "urn:ogc:def:crs:EPSG::25832");
		projected(model, "urn:unrelated", "EPSG:9999");
		Resource operation = model.createResource("urn:operation").addProperty(RDF.type, model.createResource(NS + "IfcMapConversion"))
				.addProperty(ifc.getProperty("sourceCRS_IfcCoordinateOperation"), context)
				.addProperty(ifc.getProperty("targetCRS_IfcCoordinateOperation"), selected)
				.addLiteral(ifc.getProperty("eastings_IfcMapConversion"), 500000);
		wrapped(model, operation, ifc, "northings_IfcMapConversion", "6000000");
		wrapped(model, operation, ifc, "orthogonalHeight_IfcMapConversion", "100");
		var result = IfcCoordinateReferenceSystemExtractor.extract(model, ifc);
		assertEquals("http://www.opengis.net/def/crs/EPSG/0/25832", result.identifier());
		assertEquals("urn:context", result.parameters().get("sourceCRS"));
		assertEquals("urn:selected", result.parameters().get("targetCRS"));
		assertEquals("6000000", result.parameters().get("northings"));
		assertTrue(result.canTransform());
	}

	@Test
	void preservesUnknownNameAndDoesNotInferEpsgFromDatumOrDigits() {
		Model model = baseOperationModel("Local grid 25832", "EPSG 25833 datum text");
		var result = IfcCoordinateReferenceSystemExtractor.extract(model, new IfcOWL(NS));
		assertEquals("Local grid 25832", result.parameters().get("name"));
		assertEquals(IfcCoordinateReferenceSystemInfo.ENGINEERING_CRS, result.identifier());
		assertFalse(result.canTransform());
	}

	@Test
	void rejectsMultipleContextOperationsRatherThanPairingFirstProjectedCrs() {
		Model model = baseOperationModel("EPSG:25832", null); IfcOWL ifc = new IfcOWL(NS);
		Resource secondContext = model.createResource("urn:context-2");
		Resource secondTarget = projected(model, "urn:target-2", "EPSG:25833");
		model.createResource("urn:operation-2").addProperty(RDF.type, model.createResource(NS + "IfcMapConversion"))
				.addProperty(ifc.getProperty("sourceCRS_IfcCoordinateOperation"), secondContext)
				.addProperty(ifc.getProperty("targetCRS_IfcCoordinateOperation"), secondTarget)
				.addLiteral(ifc.getProperty("eastings_IfcMapConversion"), 1)
				.addLiteral(ifc.getProperty("northings_IfcMapConversion"), 2)
				.addLiteral(ifc.getProperty("orthogonalHeight_IfcMapConversion"), 3);
		var result = IfcCoordinateReferenceSystemExtractor.extract(model, ifc);
		assertFalse(result.canTransform());
		assertTrue(result.warnings().stream().anyMatch(w -> w.contains("ambiguous")));
	}

	@Test
	void reportsRigidOperationAsUnsupported() {
		Model model = ModelFactory.createDefaultModel(); IfcOWL ifc = new IfcOWL(NS);
		model.createResource("urn:rigid").addProperty(RDF.type, model.createResource(NS + "IfcRigidOperation"));
		var result = IfcCoordinateReferenceSystemExtractor.extract(model, ifc);
		assertFalse(result.canTransform());
		assertTrue(result.warnings().stream().anyMatch(w -> w.contains("IfcRigidOperation")));
	}

	@Test
	void retainsLocalGeometryWhenTargetMapUnitIsUnresolved() {
		Model model = baseOperationModel("EPSG:25832", null); IfcOWL ifc = new IfcOWL(NS);
		Resource target = model.getResource("urn:target");
		target.removeAll(ifc.getProperty("mapUnit_IfcProjectedCRS"));
		var result = IfcCoordinateReferenceSystemExtractor.extract(model, ifc);
		assertFalse(result.canTransform());
		assertTrue(result.warnings().stream().anyMatch(w -> w.contains("map length unit")));
	}

	@Test
	void extractsSmallIfcFixtureAndMatchesIndependentExpectedCoordinate(@TempDir Path temporary) throws Exception {
		Path input = Path.of(getClass().getResource("/georeferenced-small.ifc").toURI());
		Model model = ModelFactory.createDefaultModel(); IFCtoRDF converter = new IFCtoRDF();
		String ontology = converter.convert_into_rdf(input.toString(), StreamRDFLib.graph(model.getGraph()),
				"https://example.test/model/", false).orElseThrow();
		var result = IfcCoordinateReferenceSystemExtractor.extract(model, new IfcOWL(ontology));
		assertEquals("http://www.opengis.net/def/crs/EPSG/0/25832", result.identifier());
		var point = result.transform(2, 3, 4, 1);
		assertEquals(499997, point.x(), 1e-8);
		assertEquals(6000002, point.y(), 1e-8);
		assertEquals(104, point.z(), 1e-8);
	}

	@Test
	void keepsObjLocalButLabelsSuccessfullyTransformedWktProjected() throws Exception {
		Path input = Path.of(getClass().getResource("/georeferenced-small.ifc").toURI());
		try {
			Assumptions.assumeTrue(new ProcessBuilder("python3", "-c", "import ifcopenshell").start().waitFor() == 0,
					"IfcOpenShell 0.8.5 is not available");
		} catch (Exception e) {
			Assumptions.assumeTrue(false, "IfcOpenShell 0.8.5 is not available");
		}
		ConversionProperties properties = ConversionProfiles.GEOMETRY_ENVELOPE.toConversionProperties();
		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.test/");
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(), properties))) {
			Model output = result.getModel();
			Resource geometry = output.listResourcesWithProperty(output.createProperty("http://www.opengis.net/ont/geosparql#asWKT"))
					.nextResource();
			String ns = "https://w3id.org/ifctolbd/geometry#";
			String wkt = geometry.getProperty(output.createProperty("http://www.opengis.net/ont/geosparql#asWKT")).getString();
			assertTrue(wkt.startsWith("<http://www.opengis.net/def/crs/EPSG/0/25832>"));
			assertEquals("projected", geometry.getProperty(output.createProperty(ns + "wktCoordinateSpace")).getString());
			assertEquals("local-engineering", geometry.getProperty(output.createProperty(ns + "objCoordinateSpace")).getString());
			assertEquals("https://example.test/ifc-local",
					geometry.getProperty(output.createProperty(ns + "objCoordinateReferenceSystem")).getString());
			assertEquals("METRE", geometry.getProperty(output.createProperty(ns + "wktLengthUnit")).getString());
			assertEquals(1.0, geometry.getProperty(output.createProperty(ns + "wktUnitToMetres")).getDouble());
			assertEquals("metre", geometry.getProperty(output.createProperty(ns + "objLengthUnit")).getString());
			assertEquals(1.0, geometry.getProperty(output.createProperty(ns + "objUnitToMetres")).getDouble());
			assertEquals("metre", geometry.getProperty(output.createProperty(ns + "lengthUnit")).getString());
			assertEquals(1.0, geometry.getProperty(output.createProperty(ns + "unitToMetres")).getDouble());
		}
	}

	private static Model baseOperationModel(String name, String datum) {
		Model model = ModelFactory.createDefaultModel(); IfcOWL ifc = new IfcOWL(NS); addMetreProject(model, ifc);
		Resource target = projected(model, "urn:target", name);
		if (datum != null) target.addLiteral(ifc.getProperty("geodeticDatum_IfcProjectedCRS"), datum);
		model.createResource("urn:operation").addProperty(RDF.type, model.createResource(NS + "IfcMapConversion"))
				.addProperty(ifc.getProperty("sourceCRS_IfcCoordinateOperation"), model.createResource("urn:context"))
				.addProperty(ifc.getProperty("targetCRS_IfcCoordinateOperation"), target)
				.addLiteral(ifc.getProperty("eastings_IfcMapConversion"), 0)
				.addLiteral(ifc.getProperty("northings_IfcMapConversion"), 0)
				.addLiteral(ifc.getProperty("orthogonalHeight_IfcMapConversion"), 0);
		return model;
	}

	private static Resource projected(Model model, String uri, String name) {
		IfcOWL ifc = new IfcOWL(NS);
		Resource target = model.createResource(uri).addProperty(RDF.type, model.createResource(NS + "IfcProjectedCRS"));
		wrapped(model, target, ifc, "name_IfcCoordinateReferenceSystem", name);
		target.addProperty(ifc.getProperty("mapUnit_IfcProjectedCRS"), model.createResource("urn:metre"));
		return target;
	}

	private static void addMetreProject(Model model, IfcOWL ifc) {
		Resource metre = model.createResource("urn:metre").addProperty(RDF.type, model.createResource(NS + "IfcSIUnit"))
				.addProperty(ifc.getUnitType_IfcNamedUnit(), model.createResource(NS + "LENGTHUNIT"))
				.addProperty(ifc.getName_IfcSIUnit(), model.createResource(NS + "METRE"));
		Resource assignment = model.createResource("urn:units").addProperty(ifc.getUnits_IfcUnitAssignment(), metre);
		model.createResource("urn:project").addProperty(RDF.type, model.createResource(ifc.getIfcProject()))
				.addProperty(ifc.getUnitsInContext_IfcProject(), assignment);
	}

	private static void wrapped(Model model, Resource subject, IfcOWL ifc, String predicate, String value) {
		subject.addProperty(ifc.getProperty(predicate), model.createResource().addLiteral(IfcOWL.Express.getHasString(), value));
	}
}
