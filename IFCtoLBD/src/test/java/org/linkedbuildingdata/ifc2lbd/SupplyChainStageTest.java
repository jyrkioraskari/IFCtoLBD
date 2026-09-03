package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URI;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

class SupplyChainStageTest {
	private static final String IFC = "https://example.com/ifc#";
	private static final String SUPPLY = SupplyChainStage.NS;

	@Test
	void extractsClassificationAndUsesOnlyResolverControlledConceptUris() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var ifc = new IfcOWL(IFC);
		var ifcObject = source.createResource("urn:ifc:wall");
		var lbdObject = output.createResource("urn:lbd:wall");
		var system = source.createResource("urn:ifc:system")
				.addProperty(ifc.getProperty("name_IfcClassification"), wrapped(source, "ECLASS"));
		var reference = source.createResource("urn:ifc:reference")
				.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(source, "AAE123"))
				.addProperty(ifc.getProperty("referencedSource_IfcClassificationReference"), system);
		source.createResource("urn:ifc:relation")
				.addProperty(RDF.type, source.createResource(IFC + "IfcRelAssociatesClassification"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), ifcObject)
				.addProperty(ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"), reference);

		ClassificationResolver resolver = request -> Optional.of(new ClassificationResolver.Resolution(
				"https://example.org/eclass/AAE123", "eclass-test", "1", 1.0));
		SupplyChainStage.enrich(source, ifc, output, Map.of(ifcObject, lbdObject), resolver);

		var assertion = lbdObject.getPropertyResourceValue(output.createProperty(SUPPLY + "hasClassification"));
		assertEquals("AAE123", assertion.getProperty(output.createProperty(SUPPLY + "code")).getString());
		assertTrue(assertion.hasProperty(output.createProperty(SUPPLY + "resolvedConcept"),
				output.createResource("https://example.org/eclass/AAE123")));
	}

	@Test
	void unresolvedClassificationDoesNotMintAnAuthoritativeUri() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var ifc = new IfcOWL(IFC);
		var ifcObject = source.createResource("urn:ifc:item");
		var lbdObject = output.createResource("urn:lbd:item");
		var reference = source.createResource("urn:ifc:reference")
				.addProperty(ifc.getProperty("name_IfcExternalReference"), wrapped(source, "Name only"));
		source.createResource("urn:ifc:relation")
				.addProperty(RDF.type, source.createResource(IFC + "IfcRelAssociatesClassification"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), ifcObject)
				.addProperty(ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"), reference);

		SupplyChainStage.enrich(source, ifc, output, Map.of(ifcObject, lbdObject), ClassificationResolver.none());
		var assertion = lbdObject.getPropertyResourceValue(output.createProperty(SUPPLY + "hasClassification"));
		assertFalse(assertion.hasProperty(output.createProperty(SUPPLY + "resolvedConcept")));
	}

	@Test
	void validatesGtinBeforeCreatingDigitalLink() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var product = output.createResource("urn:lbd:product");
		product.addLiteral(output.createProperty("urn:props:globalTradeItemNumber_property_simple"), "09506000134352");

		SupplyChainStage.enrich(source, new IfcOWL(IFC), output, Map.of(), ClassificationResolver.none());
		assertTrue(product.hasProperty(output.createProperty(SUPPLY + "gs1DigitalLink"),
				output.createResource("https://id.gs1.org/01/09506000134352")));
	}

	@Test
	void createsEpdLifecycleIndicatorAndProvenancedValue() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var product = output.createResource("urn:lbd:product");
		product.addLiteral(output.createProperty("urn:props:declaredUnit_property_simple"), "m2");
		product.addLiteral(output.createProperty("urn:props:gwpA1A3_property_simple"), 12.5);

		SupplyChainStage.enrich(source, new IfcOWL(IFC), output, Map.of(), ClassificationResolver.none());
		var declaration = product.getPropertyResourceValue(output.createProperty(
				"https://w3id.org/ifctolbd/sustainability#hasDeclaration"));
		var indicator = declaration.getPropertyResourceValue(output.createProperty(
				"https://w3id.org/ifctolbd/sustainability#hasIndicator"));
		assertEquals("A1-A3", indicator.getProperty(output.createProperty(
				"https://w3id.org/ifctolbd/sustainability#lifeCycleModule")).getString());
		var value = indicator.getPropertyResourceValue(output.createProperty(SUPPLY + "indicatorValue"));
		assertTrue(value.hasProperty(output.createProperty("http://qudt.org/schema/qudt/unit"),
				output.createResource("http://qudt.org/vocab/unit/M2")));
	}

	@Test
	void versionedResolverCacheAvoidsRepeatedLookups() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		ClassificationResolver delegate = request -> {
			calls.incrementAndGet();
			return Optional.of(new ClassificationResolver.Resolution("https://example.org/concept/1", "test", "1", 1));
		};
		var directory = java.nio.file.Files.createTempDirectory("resolver-cache-test-");
		var request = new ClassificationResolver.Request("ETIM", "10", "EC0001", null, null);
		var first = new VersionedClassificationResolverCache(directory, "v1", delegate);
		assertTrue(first.resolve(request).isPresent());
		var second = new VersionedClassificationResolverCache(directory, "v1", delegate);
		assertTrue(second.resolve(request).isPresent());
		assertEquals(1, calls.get());
	}

	@Test
	void extractsKnownValuesFromTheirIfcPropertySetContext() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var ifc = new IfcOWL(IFC);
		var ifcObject = source.createResource("urn:ifc:product");
		var lbdObject = output.createResource("urn:lbd:product");
		var name = wrapped(source, "SerialNumber");
		var value = wrapped(source, "SN-42");
		var property = source.createResource("urn:ifc:serial-property")
				.addProperty(ifc.getProperty("name_IfcProperty"), name)
				.addProperty(ifc.getProperty("nominalValue_IfcPropertySingleValue"), value);
		var pset = source.createResource("urn:ifc:pset")
				.addProperty(RDF.type, source.createResource(IFC + "IfcPropertySet"))
				.addProperty(ifc.getProperty("name_IfcRoot"), wrapped(source, "Pset_ManufacturerOccurrence"))
				.addProperty(ifc.getProperty("hasProperties_IfcPropertySet"), property);
		source.createResource("urn:ifc:defines")
				.addProperty(RDF.type, source.createResource(IFC + "IfcRelDefinesByProperties"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelDefines"), ifcObject)
				.addProperty(ifc.getProperty("relatingPropertyDefinition_IfcRelDefinesByProperties"), pset);

		SupplyChainStage.enrich(source, ifc, output, Map.of(ifcObject, lbdObject), ClassificationResolver.none());
		assertTrue(lbdObject.hasLiteral(output.createProperty(SUPPLY + "serialNumber"), "SN-42"));
		var evidence = lbdObject.getPropertyResourceValue(output.createProperty(SUPPLY + "hasMappingEvidence"));
		assertEquals("Pset_ManufacturerOccurrence",
				evidence.getProperty(output.createProperty(SUPPLY + "sourcePropertySet")).getString());
		assertEquals("exact-ifc-pset-property",
				evidence.getProperty(output.createProperty(SUPPLY + "mappingMethod")).getString());
	}

	@Test
	void mockedBsddResponseResolvesExactCodeAndAdaptersFilterSystems() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		String fixture;
		try (var input = getClass().getResourceAsStream("/registry/bsdd-etim-classes.json")) {
			fixture = new String(java.util.Objects.requireNonNull(input).readAllBytes(),
					java.nio.charset.StandardCharsets.UTF_8);
		}
		BsddClassificationResolver.RegistryTransport transport = uri -> {
			calls.incrementAndGet();
			assertTrue(uri.getQuery().contains("SearchText=EC0001"));
			return new BsddClassificationResolver.RegistryResponse(200, fixture);
		};
		ClassificationResolver bsdd = new BsddClassificationResolver(URI.create("https://registry.test/"),
				Map.of("ETIM", "https://dictionary.test/etim"), transport);
		var etim = new EtimClassificationResolver(bsdd);
		assertTrue(etim.resolve(new ClassificationResolver.Request("ETIM", "10", "EC0001", null, null)).isPresent());
		assertFalse(etim.resolve(new ClassificationResolver.Request("ECLASS", "14", "EC0001", null, null)).isPresent());
		assertEquals(1, calls.get());
	}

	@Test
	void eclassAdapterAcceptsSpellingVariantButRejectsEtim() {
		AtomicInteger calls = new AtomicInteger();
		var adapter = new EclassClassificationResolver(request -> {
			calls.incrementAndGet();
			return Optional.of(new ClassificationResolver.Resolution("https://example.org/eclass/1", "mock", "1", 1));
		});
		assertTrue(adapter.resolve(new ClassificationResolver.Request("E-Class", null, "1", null, null)).isPresent());
		assertFalse(adapter.resolve(new ClassificationResolver.Request("ETIM", null, "1", null, null)).isPresent());
		assertEquals(1, calls.get());
	}

	private org.apache.jena.rdf.model.Resource wrapped(org.apache.jena.rdf.model.Model model, String value) {
		return model.createResource().addLiteral(model.createProperty("https://w3id.org/express#hasString"), value);
	}
}
