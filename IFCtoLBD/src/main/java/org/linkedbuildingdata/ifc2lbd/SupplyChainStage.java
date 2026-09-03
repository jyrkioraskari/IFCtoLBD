package org.linkedbuildingdata.ifc2lbd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Non-mutating IFC-source enrichment for classifications and product identity. */
public final class SupplyChainStage {
	static final String NS = "https://w3id.org/ifctolbd/supply-chain#";
	private static final String PROV = "http://www.w3.org/ns/prov#";
	private static final String QUDT = "http://qudt.org/schema/qudt/";
	private static final String QUDT_UNIT = "http://qudt.org/vocab/unit/";
	private static final String SUSTAINABILITY = "https://w3id.org/ifctolbd/sustainability#";
	private static final Map<String, String> NORMALIZED_PROPERTIES = Map.ofEntries(
			Map.entry("globaltradeitemnumber", "gtin"), Map.entry("manufacturer", "manufacturer"),
			Map.entry("modelreference", "manufacturerModelId"), Map.entry("articlenumber", "articleNumber"),
			Map.entry("batchreference", "batchId"), Map.entry("productionlotid", "productionLotId"),
			Map.entry("serialnumber", "serialNumber"), Map.entry("warrantyidentifier", "warrantyId"),
			Map.entry("warrantystartdate", "warrantyStartDate"), Map.entry("warrantyenddate", "warrantyEndDate"),
			Map.entry("warrantyperiod", "warrantyPeriod"), Map.entry("globalwarmingpotential", "globalWarmingPotential"),
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
		output.setNsPrefix("supply", NS);
		output.setNsPrefix("prov", PROV);
		output.setNsPrefix("qudt", QUDT);
		output.setNsPrefix("sust", SUSTAINABILITY);
		extractClassifications(ifcModel, ifc, output, resources, resolver);
		Set<String> directlyExtracted = extractPropertySets(ifcModel, ifc, output, resources);
		normalizeExistingProperties(output, directlyExtracted);
	}

	private static Set<String> extractPropertySets(Model ifcModel, IfcOWL ifc, Model output,
			Map<Resource, Resource> resources) {
		Set<String> extracted = new HashSet<>();
		Resource relationType = ifcModel.createResource(ifc.getIfcURI() + "IfcRelDefinesByProperties");
		ifcModel.listResourcesWithProperty(RDF.type, relationType).forEachRemaining(relation -> {
			Resource pset = resourceObject(relation, ifc.getRelatingPropertyDefinition_IfcRelDefinesByProperties());
			if (pset == null || !pset.hasProperty(RDF.type,
					ifcModel.createResource(ifc.getIfcURI() + "IfcPropertySet"))) return;
			String psetName = firstText(pset, ifc, "name_IfcRoot");
			var targets = ifcModel.listObjectsOfProperty(relation, ifc.getRelatedObjects_IfcRelDefines()).toList();
			ifcModel.listObjectsOfProperty(pset, ifc.getHasProperties_IfcPropertySet()).forEachRemaining(node -> {
				if (!node.isResource()) return;
				Resource property = node.asResource();
				String propertyName = firstText(property, ifc, "name_IfcProperty");
				RDFNode value = propertyValue(property, ifc.getNominalValue_IfcPropertySingleValue());
				String normalized = NORMALIZED_PROPERTIES.get(normalizeName(propertyName));
				String epdProperty = EPD_PROPERTIES.get(normalizeName(propertyName));
				if (value == null || (normalized == null && epdProperty == null)) return;
				for (RDFNode targetNode : targets) {
					Resource target = targetNode.isResource() ? resources.get(targetNode.asResource()) : null;
					if (target == null) continue;
					String unitUri = declaredUnitUri(ifcModel, ifc, pset);
					if (epdProperty != null) normalizeEpdProperty(output, target, value,
							property.getURI(), epdProperty, unitUri);
					if (normalized != null) addNormalizedProperty(output, target, value, property.getURI(),
							normalized, psetName, "exact-ifc-pset-property");
					extracted.add(target.getURI() + "\u0000" + normalizeName(propertyName));
				}
			});
		});
		return extracted;
	}

	private static void extractClassifications(Model ifcModel, IfcOWL ifc, Model output,
			Map<Resource, Resource> resources, ClassificationResolver resolver) {
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
			Optional<ClassificationResolver.Resolution> resolution = Optional.empty();
			if (location != null && (location.startsWith("https://") || location.startsWith("http://"))) {
				resolution = Optional.of(new ClassificationResolver.Resolution(location, "ifc-source-location", edition, 1.0));
			} else {
				resolution = resolver.resolve(request);
			}
			final Optional<ClassificationResolver.Resolution> resolved = resolution;
			ifcModel.listObjectsOfProperty(relation, ifc.getProperty("relatedObjects_IfcRelAssociates")).forEachRemaining(node -> {
				Resource target = node.isResource() ? resources.get(node.asResource()) : null;
				if (target != null) addAssertion(output, target, relation, request, resolved);
			});
		});
	}

	private static void addAssertion(Model output, Resource target, Resource source,
			ClassificationResolver.Request request, Optional<ClassificationResolver.Resolution> resolution) {
		String signature = String.join("|", safe(request.system()), safe(request.edition()), safe(request.code()), safe(request.label()));
		String sourceUri = source.isURIResource() ? source.getURI() : "urn:ifctolbd:ifc-source:" + shortHash(source.toString());
		Resource assertion = output.createResource("urn:ifctolbd:classification:" + shortHash(signature))
				.addProperty(RDF.type, output.createResource(NS + "ClassificationAssertion"))
				.addProperty(output.createProperty(PROV + "wasDerivedFrom"), output.createResource(sourceUri));
		target.addProperty(output.createProperty(NS + "hasClassification"), assertion);
		addLiteral(assertion, output, "system", request.system());
		addLiteral(assertion, output, "edition", request.edition());
		addLiteral(assertion, output, "code", request.code());
		addLiteral(assertion, output, "label", request.label());
		addLiteral(assertion, output, "sourceLocation", request.sourceLocation());
		resolution.ifPresent(value -> {
			assertion.addProperty(output.createProperty(NS + "resolvedConcept"), output.createResource(value.conceptUri()));
			addLiteral(assertion, output, "resolutionAuthority", value.authority());
			addLiteral(assertion, output, "authorityVersion", value.authorityVersion());
			assertion.addLiteral(output.createProperty(NS + "confidence"), value.confidence());
		});
	}

	private static void normalizeExistingProperties(Model output, Set<String> directlyExtracted) {
		output.listStatements().toList().forEach(statement -> {
			if (!statement.getObject().isLiteral()) return;
			String key = statement.getPredicate().getLocalName().replaceAll("_(property|attribute)_simple$", "")
					.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
			String normalized = NORMALIZED_PROPERTIES.get(key);
			String epdProperty = EPD_PROPERTIES.get(key);
			if (!statement.getSubject().isURIResource()) return;
			if (directlyExtracted.contains(statement.getSubject().getURI() + "\u0000" + key)) return;
			if (epdProperty != null) normalizeEpdProperty(output, statement.getSubject(), statement.getObject(),
					statement.getPredicate().getURI(), epdProperty, declaredUnitUri(output, statement.getSubject()));
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
			String property, String unitUri) {
		Resource declaration = output.createResource("urn:ifctolbd:epd:" + shortHash(product.getURI()))
				.addProperty(RDF.type, output.createResource(SUSTAINABILITY + "EnvironmentalProductDeclaration"));
		product.addProperty(output.createProperty(SUSTAINABILITY + "hasDeclaration"), declaration);
		if (property.matches("A1-A3|A4|C3|D")) {
			Resource indicator = output.createResource("urn:ifctolbd:epd-indicator:"
					+ shortHash(product.getURI() + property));
			indicator.addProperty(RDF.type, output.createResource(SUSTAINABILITY + "EmbodiedCarbonIndicator"))
					.addLiteral(output.createProperty(SUSTAINABILITY + "lifeCycleModule"), property);
			NormalizedValueWriter.write(output, indicator, "indicator", new NormalizedValue(value, unitUri,
					sourceProperty, "exact-ifc-property-name", 1.0), shortHash(product.getURI() + property + value));
			declaration.addProperty(output.createProperty(SUSTAINABILITY + "hasIndicator"), indicator);
		} else {
			declaration.addProperty(output.createProperty(SUSTAINABILITY + property), value);
		}
	}

	private static String declaredUnitUri(Model output, Resource product) {
		var statements = product.listProperties().toList();
		for (var statement : statements) {
			String key = statement.getPredicate().getLocalName().replaceAll("_(property|attribute)_simple$", "")
					.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
			if (!"declaredunit".equals(key)) continue;
			RDFNode unit = statement.getObject();
			if (unit.isURIResource()) return unit.asResource().getURI();
			if (!unit.isLiteral()) return null;
			return switch (unit.asLiteral().getString().replace("²", "2").replace("³", "3")
					.replaceAll("\\s", "").toLowerCase()) {
			case "kg", "kilogram", "kilograms" -> QUDT_UNIT + "KiloGM";
			case "m2", "sqm" -> QUDT_UNIT + "M2";
			case "m3", "cbm" -> QUDT_UNIT + "M3";
			case "m", "metre", "meter" -> QUDT_UNIT + "M";
			case "piece", "pieces", "item", "each" -> QUDT_UNIT + "Each";
			default -> null;
			};
		}
		return null;
	}

	private static String declaredUnitUri(Model ifcModel, IfcOWL ifc, Resource pset) {
		var properties = ifcModel.listObjectsOfProperty(pset, ifc.getHasProperties_IfcPropertySet()).toList();
		for (RDFNode node : properties) {
			if (!node.isResource() || !"declaredunit".equals(normalizeName(firstText(node.asResource(), ifc,
					"name_IfcProperty")))) continue;
			RDFNode value = propertyValue(node.asResource(), ifc.getNominalValue_IfcPropertySingleValue());
			if (value != null && value.isLiteral()) return unitUri(value.asLiteral().getString());
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

	private static String unitUri(String unit) {
		if (unit == null) return null;
		return switch (unit.replace("²", "2").replace("³", "3").replaceAll("\\s", "").toLowerCase()) {
		case "kg", "kilogram", "kilograms" -> QUDT_UNIT + "KiloGM";
		case "m2", "sqm" -> QUDT_UNIT + "M2";
		case "m3", "cbm" -> QUDT_UNIT + "M3";
		case "m", "metre", "meter" -> QUDT_UNIT + "M";
		case "piece", "pieces", "item", "each" -> QUDT_UNIT + "Each";
		default -> null;
		};
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
