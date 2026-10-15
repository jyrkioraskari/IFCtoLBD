package org.linkedbuildingdata.ifc2lbd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Non-mutating IFC-source enrichment for classifications and product identity. */
public final class SupplyChainStage {
	static final String NS = "https://w3id.org/ifctolbd/supply-chain#";
	private static final String PROV = "http://www.w3.org/ns/prov#";
	private static final String DCTERMS = "http://purl.org/dc/terms/";
	private static final String QUDT = "http://qudt.org/schema/qudt/";
	private static final String SUSTAINABILITY = "https://w3id.org/ifctolbd/sustainability#";
	private static final Map<String, String> NORMALIZED_PROPERTIES = Map.ofEntries(
			Map.entry("globaltradeitemnumber", "gtin"), Map.entry("manufacturer", "manufacturer"),
			Map.entry("modelreference", "manufacturerModelId"), Map.entry("articlenumber", "articleNumber"),
			Map.entry("batchreference", "batchId"), Map.entry("productionlotid", "productionLotId"),
			Map.entry("serialnumber", "serialNumber"), Map.entry("warrantyidentifier", "warrantyId"),
			Map.entry("warrantystartdate", "warrantyStartDate"), Map.entry("warrantyenddate", "warrantyEndDate"),
			Map.entry("warrantyperiod", "warrantyPeriod"));
	private static final Map<String, String> SUSTAINABILITY_PROPERTIES = Map.ofEntries(
			Map.entry("globalwarmingpotential", "globalWarmingPotential"),
			Map.entry("embodiedcarbon", "embodiedCarbon"));
	private static final Map<String, String> EPD_PROPERTIES = Map.ofEntries(
			Map.entry("environmentalproductdeclarationidentifier", "declarationId"),
			Map.entry("epdregistrationnumber", "declarationId"), Map.entry("epdprogramoperator", "programOperator"),
			Map.entry("epdvaliduntil", "validUntil"), Map.entry("declaredunit", "declaredUnit"),
			Map.entry("referencequantity", "referenceQuantity"), Map.entry("gwpa1a3", "A1-A3"),
			Map.entry("globalwarmingpotentiala1a3", "A1-A3"), Map.entry("gwpa4", "A4"),
			Map.entry("gwpc3", "C3"), Map.entry("gwpd", "D"));

	private SupplyChainStage() { }

	public static void enrich(Model ifcModel, IfcOWL ifc, Model output, Map<Resource, Resource> resources,
			ClassificationResolver resolver) {
		enrichSupplyChain(ifcModel, ifc, output, resources, resolver);
		enrichSustainability(ifcModel, ifc, output, resources);
	}

	public static void enrichSupplyChain(Model ifcModel, IfcOWL ifc, Model output,
			Map<Resource, Resource> resources, ClassificationResolver resolver) {
		enrichSupplyChain(ifcModel, ifc, output, output, resources, resolver, List.of());
	}

	public static void enrichSupplyChain(Model ifcModel, IfcOWL ifc, Model output, Model propertyOutput,
			Map<Resource, Resource> resources, ClassificationResolver resolver,
			List<EtimPropertyMapping> propertyMappings) {
		enrichSupplyChain(ifcModel, ifc, output, propertyOutput, resources, resolver, propertyMappings, false,
				List.of());
	}

	public static void enrichSupplyChain(Model ifcModel, IfcOWL ifc, Model output, Model propertyOutput,
			Map<Resource, Resource> resources, ClassificationResolver resolver,
			List<EtimPropertyMapping> propertyMappings, boolean suggestEtimClassifications,
			List<EtimClassificationAcceptance> acceptedEtimClassifications) {
		output.setNsPrefix("supply", NS);
		output.setNsPrefix("prov", PROV);
		output.setNsPrefix("dcterms", DCTERMS);
		output.setNsPrefix("qudt", QUDT);
		resolver.dictionaries().forEach(value -> writeDictionaryMetadata(output, value));
		Set<Resource> suppliedEtim = extractClassifications(ifcModel, ifc, output, propertyOutput, resources,
				resolver, propertyMappings);
		if (suggestEtimClassifications)
			EtimClassificationSuggestionStage.suggest(ifcModel, ifc, output, propertyOutput, resources, resolver,
					suppliedEtim, acceptedEtimClassifications, propertyMappings);
		Set<String> directlyExtracted = extractPropertySets(ifcModel, ifc, output, resources, false);
		normalizeExistingProperties(output, directlyExtracted, false);
	}

	static void enrichSustainability(Model ifcModel, IfcOWL ifc, Model output,
			Map<Resource, Resource> resources) {
		output.setNsPrefix("sust", SUSTAINABILITY);
		output.setNsPrefix("qudt", QUDT);
		Set<String> directlyExtracted = extractPropertySets(ifcModel, ifc, output, resources, true);
		normalizeExistingProperties(output, directlyExtracted, true);
	}

	private static Set<String> extractPropertySets(Model ifcModel, IfcOWL ifc, Model output,
			Map<Resource, Resource> resources, boolean sustainability) {
		Set<String> extracted = new HashSet<>();
		UnitResolver unitResolver = UnitResolver.fromProject(ifcModel, ifc);
		Resource relationType = ifcModel.createResource(ifc.getIfcURI() + "IfcRelDefinesByProperties");
		ifcModel.listResourcesWithProperty(RDF.type, relationType).forEachRemaining(relation -> {
			Resource pset = resourceObject(relation, ifc.getRelatingPropertyDefinition_IfcRelDefinesByProperties());
			if (pset == null || !pset.hasProperty(RDF.type,
					ifcModel.createResource(ifc.getIfcURI() + "IfcPropertySet"))) return;
			String psetName = firstText(pset, ifc, "name_IfcRoot");
			var targets = IfcMappingSupport.objectResources(relation, ifc, "relatedObjects_IfcRelDefines",
					"relatedObjects_IfcRelDefinesByProperties");
			IfcMappingSupport.objectResources(pset, ifc, "hasProperties_IfcPropertySet").forEach(property -> {
				String propertyName = firstText(property, ifc, "name_IfcProperty");
				RDFNode value = propertyValue(property, ifc.getNominalValue_IfcPropertySingleValue());
				String normalized = (sustainability ? SUSTAINABILITY_PROPERTIES : NORMALIZED_PROPERTIES)
						.get(normalizeName(propertyName));
				String epdProperty = sustainability ? EPD_PROPERTIES.get(normalizeName(propertyName)) : null;
				if (value == null || (normalized == null && epdProperty == null)) return;
				for (Resource targetNode : targets) {
					Resource target = resources.get(targetNode);
					if (target == null) continue;
					Resource explicitUnit = resourceObject(property, ifc.getUnit_IfcPropertySingleValue());
					RDFNode measurementType = propertyValueResource(property,
							ifc.getNominalValue_IfcPropertySingleValue());
					RDFNode sourceType = measurementType != null && measurementType.isResource()
							? measurementType.asResource().getPropertyResourceValue(RDF.type) : null;
					UnitResolver.Resolution unit = unitResolver.resolve(explicitUnit, sourceType, ifc);
					if (!unit.isResolved() && unit.originalCode() == null)
						unit = declaredUnit(ifcModel, ifc, pset);
					if (epdProperty != null) normalizeEpdProperty(output, target, value,
							property.getURI(), epdProperty, unit, uri(sourceType), futureIfcDataType(measurementType));
					if (normalized != null) addNormalizedProperty(output, target, value, property.getURI(),
							normalized, psetName, "exact-ifc-pset-property");
					extracted.add(target.getURI() + "\u0000" + normalizeName(propertyName));
				}
			});
		});
		return extracted;
	}

	private static Set<Resource> extractClassifications(Model ifcModel, IfcOWL ifc, Model output, Model propertyOutput,
			Map<Resource, Resource> resources, ClassificationResolver resolver,
			List<EtimPropertyMapping> propertyMappings) {
		Set<Resource> suppliedEtim = new HashSet<>();
		Resource relationType = ifcModel.createResource(ifc.getIfcURI() + "IfcRelAssociatesClassification");
		ifcModel.listResourcesWithProperty(RDF.type, relationType).forEachRemaining(relation -> {
			Resource classification = resourceObject(relation, ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"));
			if (classification == null) return;
			String code = firstText(classification, ifc, "identification_IfcExternalReference", "itemReference_IfcExternalReference");
			String label = firstText(classification, ifc, "name_IfcExternalReference", "name_IfcClassification");
			String location = firstText(classification, ifc, "location_IfcExternalReference", "location_IfcClassification");
			Resource source = resourceObject(classification, ifc.getProperty("referencedSource_IfcClassificationReference"));
			String system = firstText(source, ifc, "name_IfcClassification", "source_IfcClassification");
			String edition = firstText(source, ifc, "edition_IfcClassification");
			ClassificationResolver.Request request = new ClassificationResolver.Request(system, edition, code, label, location);
			Optional<ClassificationResolver.Resolution> resolution = resolver.resolve(request);
			if (resolution.isEmpty() && location != null
					&& (location.startsWith("https://") || location.startsWith("http://")))
				resolution = Optional.of(new ClassificationResolver.Resolution(location, "ifc-source-location", null, 0.0,
						ClassificationResolver.IdentifierVerification.SUPPLIED_UNVERIFIED, edition, null,
						ClassificationResolver.EditionCompatibility.CONFIGURED_UNSPECIFIED));
			final Optional<ClassificationResolver.Resolution> resolved = resolution;
			final Optional<ClassificationDefinition> definition = resolved
					.filter(value -> value.isRegistryVerified() && !value.hasEditionConflict())
					.flatMap(resolver::definition);
			ifcModel.listObjectsOfProperty(relation, ifc.getProperty("relatedObjects_IfcRelAssociates")).forEachRemaining(node -> {
				Resource ifcTarget = node.isResource() ? node.asResource() : null;
				Resource target = ifcTarget == null ? null : resources.get(ifcTarget);
				if (target != null) {
					addAssertion(output, target, relation, request, resolved, definition);
					if (isEtimSystem(request)) suppliedEtim.add(ifcTarget);
					definition.filter(value -> isEtim(request, value)).ifPresent(value -> {
						suppliedEtim.add(ifcTarget);
						EtimPropertyAnnotationStage.annotate(ifcModel, ifc, propertyOutput, ifcTarget,
								target, value, propertyMappings);
					});
				}
			});
		});
		return suppliedEtim;
	}

	private static boolean isEtim(ClassificationResolver.Request request, ClassificationDefinition definition) {
		if (isEtimSystem(request)) return true;
		String dictionary = safe(definition.dictionaryUri()).toLowerCase(java.util.Locale.ROOT);
		return dictionary.contains("/etim/") || dictionary.endsWith("/etim");
	}
	private static boolean isEtimSystem(ClassificationResolver.Request request) {
		return "etim".equals(normalizeName(request.system()));
	}

	private static void addAssertion(Model output, Resource target, Resource source,
			ClassificationResolver.Request request, Optional<ClassificationResolver.Resolution> resolution,
			Optional<ClassificationDefinition> definition) {
		String signature = String.join("|", safe(request.system()), safe(request.edition()), safe(request.code()), safe(request.label()));
		String sourceUri = source.isURIResource() ? source.getURI() : "urn:ifctolbd:ifc-source:" + shortHash(source.toString());
		Resource assertion = output.createResource("urn:ifctolbd:classification:" + shortHash(signature))
				.addProperty(RDF.type, output.createResource(NS + "ClassificationAssertion"))
				.addProperty(output.createProperty(PROV + "wasDerivedFrom"), output.createResource(sourceUri))
				.addLiteral(output.createProperty(NS + "classificationStatus"), "supplied")
				.addLiteral(output.createProperty(NS + "classificationCorrectness"), "not-assessed");
		target.addProperty(output.createProperty(NS + "hasClassification"), assertion);
		addLiteral(assertion, output, "system", request.system());
		addLiteral(assertion, output, "edition", request.edition());
		addLiteral(assertion, output, "code", request.code());
		addLiteral(assertion, output, "label", request.label());
		addLiteral(assertion, output, "sourceLocation", request.sourceLocation());
		resolution.ifPresent(value -> {
			if (value.verification() == ClassificationResolver.IdentifierVerification.SUPPLIED_UNVERIFIED)
				assertion.addProperty(output.createProperty(NS + "suppliedIdentifier"),
						output.createResource(value.conceptUri()));
			else if (value.hasEditionConflict())
				assertion.addProperty(output.createProperty(NS + "registryMatchedIdentifier"),
						output.createResource(value.conceptUri()));
			else assertion.addProperty(output.createProperty(NS + "resolvedConcept"),
					output.createResource(value.conceptUri()));
			addLiteral(assertion, output, "resolutionAuthority", value.authority());
			addLiteral(assertion, output, "authorityVersion", value.authorityVersion());
			assertion.addLiteral(output.createProperty(NS + "identifierVerification"),
					value.verification().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
			assertion.addLiteral(output.createProperty(NS + "identifierResolutionConfidence"),
					value.identifierResolutionConfidence());
			// Preserve the established predicate while clarifying its scope for new consumers.
			assertion.addLiteral(output.createProperty(NS + "confidence"), value.identifierResolutionConfidence());
			addLiteral(assertion, output, "sourceEdition", value.sourceEdition());
			addLiteral(assertion, output, "dictionaryEdition", value.dictionaryEdition());
			assertion.addLiteral(output.createProperty(NS + "editionCompatibility"),
					value.editionCompatibility().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
			if (value.hasEditionConflict()) assertion.addLiteral(output.createProperty(NS + "editionConflict"), true);
		});
		definition.ifPresent(value -> writeDefinition(output, assertion, value));
	}

	static void writeDefinition(Model model, Resource assertion, ClassificationDefinition definition) {
		Resource classificationClass = identified(model, definition.uri());
		classificationClass.addProperty(RDF.type, model.createResource(NS + "ClassificationClass"));
		assertion.addProperty(model.createProperty(NS + "resolvedDefinition"), classificationClass);
		addLiteral(classificationClass, model, "code", definition.code());
		addLiteral(classificationClass, model, "name", definition.name());
		addLiteral(classificationClass, model, "description", definition.description());
		if (definition.dictionaryUri() != null)
			classificationClass.addProperty(model.createProperty(NS + "dictionary"),
					model.createResource(definition.dictionaryUri()));
		for (String ifcEntity : definition.relatedIfcEntities())
			addLiteral(classificationClass, model, "relatedIfcEntityName", ifcEntity);
		for (ClassificationDefinition.Relationship relationship : definition.relationships()) {
			Resource relation = identified(model, relationship.uri())
					.addProperty(RDF.type, model.createResource(NS + "ClassificationRelationship"));
			addLiteral(relation, model, "relationshipType", relationship.type());
			addLiteral(relation, model, "relatedClassName", relationship.relatedClassName());
			if (relationship.relatedClassUri() != null)
				relation.addProperty(model.createProperty(NS + "relatedClass"),
						model.createResource(relationship.relatedClassUri()));
			classificationClass.addProperty(model.createProperty(NS + "hasRelationship"), relation);
		}
		for (ClassificationDefinition.ClassProperty classProperty : definition.classProperties())
			writeClassProperty(model, classificationClass, classProperty);
	}

	static Resource writeDictionaryMetadata(Model model, ClassificationResolver.DictionaryMetadata metadata) {
		Resource dictionary = model.createResource(metadata.uri())
				.addProperty(RDF.type, model.createResource(NS + "ClassificationDictionary"));
		addLiteral(dictionary, model, "code", metadata.code());
		addLiteral(dictionary, model, "name", metadata.name());
		addLiteral(dictionary, model, "version", metadata.version());
		addLiteral(dictionary, model, "status", metadata.status());
		addLiteral(dictionary, model, "organizationCodeOwner", metadata.organizationCodeOwner());
		addLiteral(dictionary, model, "organizationNameOwner", metadata.organizationNameOwner());
		if (metadata.organizationNameOwner() != null && !metadata.organizationNameOwner().isBlank())
			dictionary.addLiteral(model.createProperty(DCTERMS + "rightsHolder"),
					metadata.organizationNameOwner());
		if (metadata.license() != null && !metadata.license().isBlank())
			dictionary.addLiteral(model.createProperty(DCTERMS + "rights"), metadata.license());
		if (metadata.licenseUrl() != null && metadata.licenseUrl().startsWith("http"))
			dictionary.addProperty(model.createProperty(DCTERMS + "license"),
					model.createResource(metadata.licenseUrl()));
		return dictionary;
	}

	private static void writeClassProperty(Model model, Resource classificationClass,
			ClassificationDefinition.ClassProperty definition) {
		String propertyUri = definition.property() == null ? null : definition.property().uri();
		// Some list responses use "uri" for the reusable Property. Do not collapse
		// that identifier into the class-specific assignment.
		Resource classProperty = identified(model,
				definition.uri() != null && !definition.uri().equals(propertyUri) ? definition.uri() : null)
				.addProperty(RDF.type, model.createResource(NS + "ClassProperty"));
		classificationClass.addProperty(model.createProperty(NS + "hasClassProperty"), classProperty);
		addLiteral(classProperty, model, "propertySet", definition.propertySet());
		addLiteral(classProperty, model, "dataType", definition.dataType());
		addLiteral(classProperty, model, "description", definition.description());
		addLiteral(classProperty, model, "predefinedValue", definition.predefinedValue());
		if (definition.required() != null) classProperty.addLiteral(model.createProperty(NS + "required"), definition.required());
		if (definition.writable() != null) classProperty.addLiteral(model.createProperty(NS + "writable"), definition.writable());
		if (definition.property() != null) {
			Resource property = identified(model, propertyUri)
					.addProperty(RDF.type, model.createResource(NS + "Property"));
			classProperty.addProperty(model.createProperty(NS + "property"), property);
			addLiteral(property, model, "code", definition.property().code());
			addLiteral(property, model, "name", definition.property().name());
			addLiteral(property, model, "description", definition.property().description());
		}
		for (ClassificationDefinition.AllowedValue value : definition.allowedValues()) {
			Resource allowedValue = identified(model, value.uri())
					.addProperty(RDF.type, model.createResource(NS + "AllowedValue"));
			addLiteral(allowedValue, model, "code", value.code());
			addLiteral(allowedValue, model, "value", value.value());
			addLiteral(allowedValue, model, "description", value.description());
			classProperty.addProperty(model.createProperty(NS + "allowedValue"), allowedValue);
		}
		for (ClassificationDefinition.Unit unit : definition.units()) {
			Resource unitAssignment = model.createResource().addProperty(RDF.type, model.createResource(NS + "UnitAssignment"));
			UnitResolver.write(model, unitAssignment, UnitResolver.fromBsdd(unit.qudtCode(), unit.code()));
			classProperty.addProperty(model.createProperty(NS + "unit"), unitAssignment);
		}
	}

	private static Resource identified(Model model, String uri) {
		return uri == null || uri.isBlank() ? model.createResource() : model.createResource(uri);
	}

	private static void normalizeExistingProperties(Model output, Set<String> directlyExtracted,
			boolean sustainability) {
		output.listStatements().toList().forEach(statement -> {
			if (!statement.getObject().isLiteral()) return;
			String key = statement.getPredicate().getLocalName().replaceAll("_(property|attribute)_simple$", "")
					.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
			String normalized = (sustainability ? SUSTAINABILITY_PROPERTIES : NORMALIZED_PROPERTIES).get(key);
			String epdProperty = sustainability ? EPD_PROPERTIES.get(key) : null;
			if (!statement.getSubject().isURIResource()) return;
			if (directlyExtracted.contains(statement.getSubject().getURI() + "\u0000" + key)) return;
			if (epdProperty != null) normalizeEpdProperty(output, statement.getSubject(), statement.getObject(),
					statement.getPredicate().getURI(), epdProperty, declaredUnit(output, statement.getSubject()), null, null);
			if (normalized == null) return;
			addNormalizedProperty(output, statement.getSubject(), statement.getObject(), statement.getPredicate().getURI(),
					normalized, null, "exact-ifc-property-name");
		});
	}

	private static void addNormalizedProperty(Model output, Resource subject, RDFNode value, String sourceProperty,
			String normalized, String psetName, String method) {
		var normalizedProperty = output.createProperty(NS + normalized);
		subject.addProperty(normalizedProperty, value);
		Resource evidence = output.createResource("urn:ifctolbd:mapping:" + shortHash(subject + "|" + sourceProperty + "|" + normalized))
					.addProperty(RDF.type, output.createResource(NS + "MappingEvidence"))
					.addProperty(output.createProperty(PROV + "wasDerivedFrom"), output.createResource(sourceProperty))
					.addProperty(output.createProperty(NS + "mappedProperty"), normalizedProperty)
					.addLiteral(output.createProperty(NS + "mappingMethod"), method)
					.addLiteral(output.createProperty(NS + "confidence"), 1.0);
		if (psetName != null) evidence.addLiteral(output.createProperty(NS + "sourcePropertySet"), psetName);
		subject.addProperty(output.createProperty(NS + "hasMappingEvidence"), evidence);
		NormalizedValueWriter.write(output, subject, normalized,
				new NormalizedValue(value, null, sourceProperty, method, 1.0),
				shortHash(subject + "|" + sourceProperty + "|" + normalized));
		if ("gtin".equals(normalized) && value.isLiteral()) addDigitalLink(output, subject, value.asLiteral().getString());
	}

	private static void normalizeEpdProperty(Model output, Resource product, RDFNode value, String sourceProperty,
			String property, UnitResolver.Resolution unit, String sourceIfcType, String ifcDataType) {
		Resource declaration = output.createResource("urn:ifctolbd:epd:" + shortHash(product.getURI()))
				.addProperty(RDF.type, output.createResource(SUSTAINABILITY + "EnvironmentalProductDeclaration"));
		product.addProperty(output.createProperty(SUSTAINABILITY + "hasDeclaration"), declaration);
		if (property.matches("A1-A3|A4|C3|D")) {
			Resource indicator = output.createResource("urn:ifctolbd:epd-indicator:"
					+ shortHash(product.getURI() + property));
			indicator.addProperty(RDF.type, output.createResource(SUSTAINABILITY + "EmbodiedCarbonIndicator"))
					.addLiteral(output.createProperty(SUSTAINABILITY + "lifeCycleModule"), property);
			RDFNode normalizedValue = UnitResolver.normalizeValue(output, value, unit);
			NormalizedValueWriter.write(output, indicator, "indicator", new NormalizedValue(normalizedValue,
					unit == null ? null : unit.qudtUri(), sourceProperty, "exact-ifc-property-name", 1.0,
					unit == null ? null : unit.originalCode(), sourceIfcType, ifcDataType),
					shortHash(product.getURI() + property + value));
			declaration.addProperty(output.createProperty(SUSTAINABILITY + "hasIndicator"), indicator);
		} else {
			declaration.addProperty(output.createProperty(SUSTAINABILITY + property), value);
		}
	}

	private static UnitResolver.Resolution declaredUnit(Model output, Resource product) {
		var statements = product.listProperties().toList();
		for (var statement : statements) {
			String key = statement.getPredicate().getLocalName().replaceAll("_(property|attribute)_simple$", "")
					.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
			if (!"declaredunit".equals(key)) continue;
			RDFNode unit = statement.getObject();
			if (unit.isURIResource()) return new UnitResolver.Resolution(unit.asResource().getURI(),
					unit.asResource().getURI(), java.math.BigDecimal.ONE);
			if (!unit.isLiteral()) return null;
			return UnitResolver.fromText(unit.asLiteral().getString());
		}
		return null;
	}

	private static UnitResolver.Resolution declaredUnit(Model ifcModel, IfcOWL ifc, Resource pset) {
		var properties = IfcMappingSupport.objectResources(pset, ifc, "hasProperties_IfcPropertySet");
		for (Resource node : properties) {
			if (!"declaredunit".equals(normalizeName(firstText(node, ifc,
					"name_IfcProperty")))) continue;
			RDFNode value = propertyValue(node, ifc.getNominalValue_IfcPropertySingleValue());
			if (value != null && value.isLiteral()) return UnitResolver.fromText(value.asLiteral().getString());
		}
		return null;
	}

	private static RDFNode propertyValue(Resource property, org.apache.jena.rdf.model.Property predicate) {
		var statement = property.getProperty(predicate);
		if (statement == null) return null;
		RDFNode value = statement.getObject();
		if (value.isLiteral()) return value;
		if (!value.isResource()) return null;
		var literals = value.asResource().listProperties().filterKeep(s -> s.getObject().isLiteral());
		try { return literals.hasNext() ? literals.next().getObject() : null; }
		finally { literals.close(); }
	}

	private static RDFNode propertyValueResource(Resource property,
			org.apache.jena.rdf.model.Property predicate) {
		var statement = property.getProperty(predicate);
		return statement == null ? null : statement.getObject();
	}

	private static String futureIfcDataType(RDFNode valueResource) {
		if (valueResource == null || !valueResource.isResource()) return null;
		var statements = valueResource.asResource().listProperties();
		try {
			while (statements.hasNext()) {
				var statement = statements.next();
				if (!"IfcDataType".equalsIgnoreCase(statement.getPredicate().getLocalName())) continue;
				RDFNode value = statement.getObject();
				return value.isURIResource() ? value.asResource().getURI() : value.toString();
			}
			return null;
		} finally { statements.close(); }
	}

	private static String uri(RDFNode node) {
		return node != null && node.isURIResource() ? node.asResource().getURI() : null;
	}

	private static String normalizeName(String name) {
		return name == null ? "" : name.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
	}

	private static void addDigitalLink(Model output, Resource subject, String rawGtin) {
		String digits = rawGtin.replaceAll("[^0-9]", "");
		if (!digits.matches("[0-9]{8}|[0-9]{12,14}") || !validGtin(digits)) return;
		String gtin14 = "0".repeat(14 - digits.length()) + digits;
		subject.addProperty(output.createProperty(NS + "gs1DigitalLink"),
				output.createResource("https://id.gs1.org/01/" + gtin14));
	}

	private static boolean validGtin(String value) {
		int sum = 0;
		for (int i = value.length() - 2, position = 1; i >= 0; i--, position++)
			sum += (value.charAt(i) - '0') * (position % 2 == 1 ? 3 : 1);
		return (10 - sum % 10) % 10 == value.charAt(value.length() - 1) - '0';
	}

	private static Resource resourceObject(Resource subject, org.apache.jena.rdf.model.Property property) {
		var statement = subject == null ? null : subject.getProperty(property);
		return statement != null && statement.getObject().isResource() ? statement.getResource() : null;
	}

	private static String firstText(Resource subject, IfcOWL ifc, String... properties) {
		if (subject == null) return null;
		for (String property : properties) {
			var statement = subject.getProperty(ifc.getProperty(property));
			if (statement == null) continue;
			RDFNode node = statement.getObject();
			if (node.isLiteral()) return node.asLiteral().getString();
			if (node.isResource()) {
				var values = node.asResource().listProperties().filterKeep(s -> s.getObject().isLiteral());
				try {
					if (values.hasNext()) return values.next().getString();
				} finally { values.close(); }
			}
		}
		return null;
	}

	private static void addLiteral(Resource resource, Model model, String name, String value) {
		if (value != null && !value.isBlank()) resource.addLiteral(model.createProperty(NS + name), value);
	}
	private static String safe(String value) { return value == null ? "" : value; }
	private static String shortHash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}
