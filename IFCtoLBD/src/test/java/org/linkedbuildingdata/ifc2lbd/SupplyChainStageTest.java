package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.List;
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
		assertEquals("supplied", assertion.getProperty(output.createProperty(SUPPLY + "classificationStatus")).getString());
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
	void suppliedHttpLocationRemainsUnverified() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var ifc = new IfcOWL(IFC);
		var ifcObject = source.createResource("urn:ifc:item");
		var lbdObject = output.createResource("urn:lbd:item");
		var reference = source.createResource("urn:ifc:reference")
				.addProperty(ifc.getProperty("location_IfcExternalReference"),
						wrapped(source, "https://supplier.test/classification/EC0001"));
		source.createResource("urn:ifc:relation")
				.addProperty(RDF.type, source.createResource(IFC + "IfcRelAssociatesClassification"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), ifcObject)
				.addProperty(ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"), reference);

		SupplyChainStage.enrich(source, ifc, output, Map.of(ifcObject, lbdObject), ClassificationResolver.none());

		var assertion = lbdObject.getPropertyResourceValue(output.createProperty(SUPPLY + "hasClassification"));
		assertFalse(assertion.hasProperty(output.createProperty(SUPPLY + "resolvedConcept")));
		assertTrue(assertion.hasProperty(output.createProperty(SUPPLY + "suppliedIdentifier"),
				output.createResource("https://supplier.test/classification/EC0001")));
		assertEquals("supplied-unverified",
				assertion.getProperty(output.createProperty(SUPPLY + "identifierVerification")).getString());
		assertEquals("not-assessed",
				assertion.getProperty(output.createProperty(SUPPLY + "classificationCorrectness")).getString());
	}

	@Test
	void reportsSourceAndDictionaryEditionConflictWithoutResolvingConcept() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var ifc = new IfcOWL(IFC);
		var ifcObject = source.createResource("urn:ifc:pump");
		var lbdObject = output.createResource("urn:lbd:pump");
		var system = source.createResource("urn:ifc:system")
				.addProperty(ifc.getProperty("name_IfcClassification"), wrapped(source, "ETIM"))
				.addProperty(ifc.getProperty("edition_IfcClassification"), wrapped(source, "9.0"));
		var reference = source.createResource("urn:ifc:reference")
				.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(source, "EC0001"))
				.addProperty(ifc.getProperty("referencedSource_IfcClassificationReference"), system);
		source.createResource("urn:ifc:relation")
				.addProperty(RDF.type, source.createResource(IFC + "IfcRelAssociatesClassification"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), ifcObject)
				.addProperty(ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"), reference);
		String dictionary = "https://dictionary.test/etim/10.0";
		String classUri = dictionary + "/class/EC0001";
		BsddClassificationResolver.RegistryTransport transport = uri ->
				new BsddClassificationResolver.RegistryResponse(200,
						"{\"classes\":[{\"uri\":\"" + classUri + "\",\"code\":\"EC0001\"}]}");
		ClassificationResolver resolver = new BsddClassificationResolver(URI.create("https://registry.test/"),
				Map.of("ETIM", dictionary), transport);

		SupplyChainStage.enrich(source, ifc, output, Map.of(ifcObject, lbdObject), resolver);

		var assertion = lbdObject.getPropertyResourceValue(output.createProperty(SUPPLY + "hasClassification"));
		assertFalse(assertion.hasProperty(output.createProperty(SUPPLY + "resolvedConcept")));
		assertTrue(assertion.hasProperty(output.createProperty(SUPPLY + "registryMatchedIdentifier"),
				output.createResource(classUri)));
		assertTrue(assertion.getProperty(output.createProperty(SUPPLY + "editionConflict")).getBoolean());
		assertEquals("9.0", assertion.getProperty(output.createProperty(SUPPLY + "sourceEdition")).getString());
		assertEquals("10.0", assertion.getProperty(output.createProperty(SUPPLY + "dictionaryEdition")).getString());
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
		assertTrue(first.refresh(request).isPresent());
		assertTrue(first.resolve(request).isPresent());
		var second = new VersionedClassificationResolverCache(directory, "v1", delegate);
		assertTrue(second.resolve(request).isPresent());
		assertTrue(second.resolve(new ClassificationResolver.Request("ETIM", "10", "EC0001",
				"IFC-supplied label", "https://source.example/class/EC0001")).isPresent());
		assertEquals(1, calls.get());
	}

	@Test
	void cacheOnlyModeNeverCallsDelegateOnAMiss() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		ClassificationResolver delegate = request -> {
			calls.incrementAndGet();
			return Optional.of(new ClassificationResolver.Resolution(
					"https://example.org/concept/1", "test", "1", 1));
		};
		var directory = java.nio.file.Files.createTempDirectory("resolver-offline-test-");
		var cache = new VersionedClassificationResolverCache(directory, "v1", delegate);

		assertTrue(cache.resolve(new ClassificationResolver.Request("ETIM", "10", "EC0001", null, null))
				.isEmpty());
		assertEquals(0, calls.get());
	}

	@Test
	void versionedCachePersistsDictionaryRightsForOfflineConversion() throws Exception {
		String uri = "https://identifier.buildingsmart.org/uri/etim/etim/9.0";
		var metadata = new ClassificationResolver.DictionaryMetadata(uri, "etim", "ETIM", "9.0", "Active",
				"etim", "ETIM International", "No license (rights reserved)",
				"https://technical.buildingsmart.org/services/bsdd/license/");
		ClassificationResolver delegate = new ClassificationResolver() {
			@Override public Optional<Resolution> resolve(Request request) { return Optional.empty(); }
			@Override public Optional<DictionaryMetadata> dictionary(String system) { return Optional.of(metadata); }
		};
		var directory = java.nio.file.Files.createTempDirectory("dictionary-cache-test-");
		var loader = new VersionedClassificationResolverCache(directory, "etim-9", delegate);
		assertEquals(metadata, loader.refreshDictionary("ETIM").orElseThrow());

		var offline = new VersionedClassificationResolverCache(directory, "etim-9");
		assertEquals(metadata, offline.dictionary("ETIM").orElseThrow());
		assertEquals(List.of(metadata), offline.dictionaries());
	}

	@Test
	void enrichmentEmitsCachedDictionaryRightsIntoTheGeneratedRdf() {
		String uri = "https://identifier.buildingsmart.org/uri/etim/etim/9.0";
		var metadata = new ClassificationResolver.DictionaryMetadata(uri, "etim", "ETIM", "9.0", "Active",
				"etim", "ETIM International", "No license (rights reserved)",
				"https://technical.buildingsmart.org/services/bsdd/license/");
		ClassificationResolver offline = new ClassificationResolver() {
			@Override public boolean allowsNetworkAccess() { return false; }
			@Override public Optional<Resolution> resolve(Request request) { return Optional.empty(); }
			@Override public List<DictionaryMetadata> dictionaries() { return List.of(metadata); }
		};
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();

		SupplyChainStage.enrichSupplyChain(source, new IfcOWL(IFC), output, Map.of(), offline);

		var dictionary = output.createResource(uri);
		assertTrue(dictionary.hasLiteral(output.createProperty("http://purl.org/dc/terms/rights"),
				"No license (rights reserved)"));
		assertTrue(dictionary.hasProperty(output.createProperty("http://purl.org/dc/terms/license"),
				output.createResource("https://technical.buildingsmart.org/services/bsdd/license/")));
	}

	@Test
	@SuppressWarnings("removal")
	void networkFallbackModeIsRejected() throws Exception {
		var directory = java.nio.file.Files.createTempDirectory("resolver-network-mode-test-");
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new VersionedClassificationResolverCache(directory, "v1", ClassificationResolver.none(),
						VersionedClassificationResolverCache.AccessMode.CACHE_THEN_DELEGATE));
	}

	@Test
	void versionedResolverCachePersistsCompleteDefinitions() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		var resolution = new ClassificationResolver.Resolution("https://example.org/class/1", "test", "1", 1);
		var definition = new ClassificationDefinition(resolution.conceptUri(), "https://example.org/dictionary/1",
				"C1", "Class", null, List.of("IfcPump"), List.of(new ClassificationDefinition.ClassProperty(
						"https://example.org/class-property/1", new ClassificationDefinition.Property(
								"https://example.org/property/1", "P1", "Property", null),
						"Dimensions", "Real", null, null, null, null,
						List.of(new ClassificationDefinition.AllowedValue("https://example.org/value/1", "V1", "1", null)),
						List.of(new ClassificationDefinition.Unit("mm", "unit:MILLIMETER")))),
				List.of(new ClassificationDefinition.Relationship("https://example.org/relation/1", "IsSimilarTo",
						"https://example.org/class/2", "Related")));
		ClassificationResolver delegate = new ClassificationResolver() {
			@Override public Optional<Resolution> resolve(Request request) { return Optional.of(resolution); }
			@Override public Optional<ClassificationDefinition> definition(Resolution ignored) {
				calls.incrementAndGet();
				return Optional.of(definition);
			}
		};
		var directory = java.nio.file.Files.createTempDirectory("definition-cache-test-");
		var populated = new VersionedClassificationResolverCache(directory, "v1", delegate);
		populated.refresh(new ClassificationResolver.Request("ETIM", "1", "C1", null, resolution.conceptUri()));
		assertEquals(definition, populated.definition(resolution).orElseThrow());
		assertEquals(definition, new VersionedClassificationResolverCache(directory, "v1", delegate)
				.definition(resolution).orElseThrow());
		assertEquals(1, calls.get());
	}

	@Test
	void versionedResolverCachePersistsIfcCandidates() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		var definition = new ClassificationDefinition("https://example.org/class/1",
				"https://example.org/etim/10", "EC1", "Pump", null, List.of("IfcPump"), List.of(), List.of());
		ClassificationResolver delegate = new ClassificationResolver() {
			@Override public Optional<Resolution> resolve(Request request) { return Optional.empty(); }
			@Override public List<Candidate> candidates(CandidateRequest request) {
				calls.incrementAndGet();
				return List.of(new Candidate(new Resolution(definition.uri(), "test", "10", 1), definition,
						"published-ifc-relationship", null));
			}
		};
		var directory = java.nio.file.Files.createTempDirectory("candidate-cache-test-");
		var request = new ClassificationResolver.CandidateRequest("ETIM", "IfcPump");
		var populated = new VersionedClassificationResolverCache(directory, "v1", delegate);
		assertEquals(1, populated.refreshCandidates(request).size());
		assertEquals(1, new VersionedClassificationResolverCache(directory, "v1", delegate)
				.candidates(request).size());
		assertEquals(1, calls.get());
	}

	@Test
	void discoversCandidatesFromIfcAndEtimModellingClassRelationships() {
		String etim = "https://dictionary.test/etim/10";
		String modelling = "https://dictionary.test/etim-mc/2025";
		String direct = etim + "/class/EC0001";
		String linked = etim + "/class/EC0002";
		String modellingClass = modelling + "/class/MC000001";
		BsddClassificationResolver.RegistryTransport transport = uri -> {
			String query = uri.getQuery();
			if (uri.getPath().endsWith("/Dictionary/v1/Classes")) {
				assertTrue(query.contains("RelatedIfcEntity=IfcPump"));
				assertFalse(query.contains("RelatedIfcEntities="));
				if (query.contains(modelling)) return new BsddClassificationResolver.RegistryResponse(200,
						"{\"classes\":[{\"uri\":\"" + modellingClass + "\"}]}");
				return new BsddClassificationResolver.RegistryResponse(200,
						"{\"classes\":[{\"uri\":\"" + direct + "\"}]}");
			}
			if (uri.getPath().endsWith("/Class/Relations/v1"))
				return new BsddClassificationResolver.RegistryResponse(200, "{\"classRelations\":[]}");
			if (query.contains(modellingClass)) return new BsddClassificationResolver.RegistryResponse(200,
					classJson(modellingClass, modelling, "MC000001", "Pump shape",
							"[{\"relationType\":\"HasReference\",\"relatedClassUri\":\"" + linked
									+ "\",\"relatedClassName\":\"Linked pump\"}]"));
			if (query.contains(linked)) return new BsddClassificationResolver.RegistryResponse(200,
					classJson(linked, etim, "EC0002", "Linked pump", "[]"));
			return new BsddClassificationResolver.RegistryResponse(200,
					classJson(direct, etim, "EC0001", "Direct pump", "[]"));
		};
		var resolver = new BsddClassificationResolver(URI.create("https://registry.test/"),
				Map.of("ETIM", etim, "ETIM-MC", modelling), transport);

		List<ClassificationResolver.Candidate> candidates = resolver.candidates(
				new ClassificationResolver.CandidateRequest("ETIM", "IfcPump"));

		assertEquals(2, candidates.size());
		assertTrue(candidates.stream().anyMatch(value -> value.resolution().conceptUri().equals(direct)
				&& value.discoveryMethod().equals("published-ifc-relationship")));
		assertTrue(candidates.stream().anyMatch(value -> value.resolution().conceptUri().equals(linked)
				&& value.discoveryMethod().equals("etim-modelling-class-link")
				&& modellingClass.equals(value.supportingClassUri())));
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
	void retrievesDictionaryRightsFromTheVersionedBsddEndpoint() {
		String dictionaryUri = "https://identifier.buildingsmart.org/uri/etim/etim/9.0";
		BsddClassificationResolver.RegistryTransport transport = uri -> {
			assertTrue(uri.getPath().endsWith("/Dictionary/v1"));
			assertTrue(uri.getQuery().contains("Uri="));
			return new BsddClassificationResolver.RegistryResponse(200, """
					{"dictionaries":[{"code":"etim","uri":"%s","name":"ETIM","version":"9.0",
					"organizationCodeOwner":"etim","organizationNameOwner":"ETIM International",
					"license":"No license (rights reserved)","licenseUrl":"https://technical.buildingsmart.org/services/bsdd/license/",
					"status":"Active"}],"totalCount":1}
					""".formatted(dictionaryUri));
		};
		var resolver = new BsddClassificationResolver(URI.create("https://registry.test/"),
				Map.of("ETIM", dictionaryUri), transport);

		var metadata = resolver.dictionary("ETIM").orElseThrow();
		assertEquals("No license (rights reserved)", metadata.license());
		assertEquals("ETIM International", metadata.organizationNameOwner());
		assertEquals("https://technical.buildingsmart.org/services/bsdd/license/", metadata.licenseUrl());
	}

	@Test
	void retrievesEtimDefinitionWithoutConflatingPropertyAndClassPropertyUris() throws Exception {
		String fixture;
		try (var input = getClass().getResourceAsStream("/registry/bsdd-etim-class.json")) {
			fixture = new String(java.util.Objects.requireNonNull(input).readAllBytes(),
					java.nio.charset.StandardCharsets.UTF_8);
		}
		BsddClassificationResolver.RegistryTransport transport = uri ->
				new BsddClassificationResolver.RegistryResponse(200, fixture);
		var bsdd = new BsddClassificationResolver(URI.create("https://registry.test/"),
				Map.of("ETIM", "https://identifier.example/etim/10"), transport);
		var resolution = new ClassificationResolver.Resolution(
				"https://identifier.example/etim/class/EC0001", "buildingSMART-bSDD-v1", "10", 1);

		ClassificationDefinition definition = bsdd.definition(resolution).orElseThrow();
		assertEquals("https://identifier.example/etim/class-property/EC0001-EF0001",
				definition.classProperties().getFirst().uri());
		assertEquals("https://identifier.example/etim/property/EF0001",
				definition.classProperties().getFirst().property().uri());
		assertEquals("ETIM dimensions", definition.classProperties().getFirst().propertySet());
		assertEquals("unit:MILLIMETER", definition.classProperties().getFirst().units().getFirst().qudtCode());
		assertEquals("https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/class/IfcPump",
				definition.relationships().getFirst().relatedClassUri());
	}

	@Test
	void writesDefinitionIdentifiersAndKeepsPropertySetAsLiteral() {
		var source = ModelFactory.createDefaultModel();
		var output = ModelFactory.createDefaultModel();
		var ifc = new IfcOWL(IFC);
		var ifcObject = source.createResource("urn:ifc:pump");
		var lbdObject = output.createResource("urn:lbd:pump");
		var classification = source.createResource("urn:ifc:etim")
				.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(source, "EC0001"));
		source.createResource("urn:ifc:relation")
				.addProperty(RDF.type, source.createResource(IFC + "IfcRelAssociatesClassification"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), ifcObject)
				.addProperty(ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"), classification);
		var resolution = new ClassificationResolver.Resolution(
				"https://identifier.example/etim/class/EC0001", "mock", "10", 1);
		var property = new ClassificationDefinition.Property(
				"https://identifier.example/etim/property/EF0001", "EF0001", "Diameter", null);
		var classProperty = new ClassificationDefinition.ClassProperty(
				"https://identifier.example/etim/class-property/EC0001-EF0001", property,
				"Dimensions", "Real", null, null, true, null, List.of(),
				List.of(new ClassificationDefinition.Unit("mm", "unit:MILLIMETER")));
		var definition = new ClassificationDefinition(resolution.conceptUri(),
				"https://identifier.example/etim/10", "EC0001", "Pump", null,
				List.of("IfcPump"), List.of(classProperty), List.of());
		ClassificationResolver resolver = new ClassificationResolver() {
			@Override public Optional<Resolution> resolve(Request request) { return Optional.of(resolution); }
			@Override public Optional<ClassificationDefinition> definition(Resolution ignored) { return Optional.of(definition); }
		};

		SupplyChainStage.enrichSupplyChain(source, ifc, output, Map.of(ifcObject, lbdObject), resolver);
		var cp = output.createResource(classProperty.uri());
		assertTrue(output.contains(cp, output.createProperty(SUPPLY + "property"),
				output.createResource(property.uri())));
		assertTrue(output.contains(cp, output.createProperty(SUPPLY + "propertySet"), "Dimensions"));
		assertFalse(output.containsResource(output.createResource(SUPPLY + "Dimensions")));
		assertTrue(output.contains(cp, output.createProperty(SUPPLY + "unit")));
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

	private static String classJson(String uri, String dictionary, String code, String name, String relations) {
		return "{\"uri\":\"" + uri + "\",\"dictionaryUri\":\"" + dictionary + "\",\"code\":\""
				+ code + "\",\"name\":\"" + name + "\",\"relatedIfcEntityNames\":[\"IfcPump\"],"
				+ "\"classProperties\":[],\"classRelations\":" + relations + "}";
	}
}
