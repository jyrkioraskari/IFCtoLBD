package org.linkedbuildingdata.ifc2lbd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Adds non-destructive ETIM annotations to IFC property output. */
final class EtimPropertyAnnotationStage {
	private static final String NS = SupplyChainStage.NS;
	private static final String PROV = "http://www.w3.org/ns/prov#";

	private EtimPropertyAnnotationStage() { }

	static void annotate(Model ifcModel, IfcOWL ifc, Model output, Resource ifcElement, Resource lbdElement,
			ClassificationDefinition definition, List<EtimPropertyMapping> mappings) {
		output.setNsPrefix("supply", NS);
		output.setNsPrefix("prov", PROV);
		UnitResolver units = UnitResolver.fromProject(ifcModel, ifc);
		List<Candidate> candidates = new ArrayList<>();
		collectType(ifcModel, ifc, ifcElement, candidates);
		collectOccurrence(ifcModel, ifc, ifcElement, candidates);
		String entity = entityName(ifcElement);
		Map<String, Resource> typeAnnotations = new HashMap<>();
		for (Candidate candidate : candidates) {
			Match match = match(ifcModel, ifc, candidate, definition, mappings, entity);
			if (match == null || !compatible(ifc, candidate, match.classProperty(), units)) continue;
			Resource annotation = write(output, ifc, lbdElement, candidate, match, units);
			String key = key(candidate);
			if ("type".equals(candidate.sourceKind())) typeAnnotations.put(key, annotation);
			else {
				Resource overridden = typeAnnotations.get(key);
				if (overridden != null) annotation.addProperty(output.createProperty(NS + "overrides"), overridden);
			}
		}
	}

	private static void collectOccurrence(Model model, IfcOWL ifc, Resource element, List<Candidate> result) {
		IfcMappingSupport.subjectsWithObject(model, ifc, element, "relatedObjects_IfcRelDefines",
				"relatedObjects_IfcRelDefinesByProperties").forEach(relation -> {
			if (!relation.hasProperty(RDF.type, model.createResource(ifc.getIfcURI() + "IfcRelDefinesByProperties"))) return;
			Resource set = resource(relation, ifc.getRelatingPropertyDefinition_IfcRelDefinesByProperties());
			collectSet(model, ifc, set, element, "occurrence", result);
		});
	}

	private static void collectType(Model model, IfcOWL ifc, Resource element, List<Candidate> result) {
		var related = ifc.getProperty("relatedObjects_IfcRelDefinesByType");
		model.listResourcesWithProperty(related, element).forEachRemaining(relation -> {
			Resource type = resource(relation, ifc.getProperty("relatingType_IfcRelDefinesByType"));
			if (type == null) return;
			IfcMappingSupport.objectResources(type, ifc, "hasPropertySets_IfcTypeObject")
					.forEach(set -> collectSet(model, ifc, set, type, "type", result));
		});
	}

	private static void collectSet(Model model, IfcOWL ifc, Resource set, Resource sourceOwner, String sourceKind,
			List<Candidate> result) {
		if (set == null) return;
		String setName = text(set, ifc, "name_IfcRoot");
		IfcMappingSupport.objectResources(set, ifc, "hasProperties_IfcPropertySet").forEach(property -> {
			String propertyName = text(property, ifc, "name_IfcProperty");
			RDFNode wrappedValue = object(property, ifc.getNominalValue_IfcPropertySingleValue());
			RDFNode value = literalValue(wrappedValue);
			RDFNode sourceType = wrappedValue != null && wrappedValue.isResource()
					? wrappedValue.asResource().getPropertyResourceValue(RDF.type) : null;
			Resource explicitUnit = resource(property, ifc.getUnit_IfcPropertySingleValue());
			if (propertyName != null && value != null)
				result.add(new Candidate(set, setName, property, propertyName, value, sourceType, explicitUnit,
						sourceOwner, sourceKind));
		});
	}

	private static Match match(Model model, IfcOWL ifc, Candidate candidate,
			ClassificationDefinition definition, List<EtimPropertyMapping> mappings, String entity) {
		Set<String> identifiers = explicitIdentifiers(model, ifc, candidate.property());
		for (ClassificationDefinition.ClassProperty cp : definition.classProperties()) {
			String propertyUri = cp.property() == null ? null : cp.property().uri();
			String propertyCode = cp.property() == null ? null : cp.property().code();
			if (contains(identifiers, cp.uri(), propertyUri, propertyCode))
				return new Match(cp, "explicit-identifier", null);
		}
		for (EtimPropertyMapping mapping : mappings) {
			if (!same(mapping.etimClassUri(), definition.uri())
					|| !same(mapping.propertySet(), candidate.setName())
					|| !same(mapping.propertyName(), candidate.propertyName())
					|| (!mapping.ifcEntity().isBlank() && !same(mapping.ifcEntity(), entity))) continue;
			for (ClassificationDefinition.ClassProperty cp : definition.classProperties()) {
				String propertyUri = cp.property() == null ? null : cp.property().uri();
				if ((!blank(mapping.classPropertyUri()) && same(mapping.classPropertyUri(), cp.uri()))
						|| (!blank(mapping.propertyUri()) && same(mapping.propertyUri(), propertyUri)))
					return new Match(cp, "reviewed-context-mapping", mapping.reviewReference());
			}
		}
		return null;
	}

	private static boolean compatible(IfcOWL ifc, Candidate candidate,
			ClassificationDefinition.ClassProperty cp, UnitResolver units) {
		if (!datatypeCompatible(candidate.value(), cp.dataType())) return false;
		UnitResolver.Resolution sourceUnit = units.resolve(candidate.explicitUnit(), candidate.sourceType(), ifc);
		if (!cp.units().isEmpty() && cp.units().stream().noneMatch(unit -> unitCompatible(sourceUnit, unit))) return false;
		if (!cp.allowedValues().isEmpty()) {
			String actual = candidate.value().isLiteral() ? candidate.value().asLiteral().getLexicalForm()
					: candidate.value().toString();
			if (cp.allowedValues().stream().noneMatch(value -> same(actual, value.value()) || same(actual, value.code())))
				return false;
		}
		if (!blank(cp.predefinedValue())
				&& !same(candidate.value().asLiteral().getLexicalForm(), cp.predefinedValue())) return false;
		return true;
	}

	private static Resource write(Model output, IfcOWL ifc, Resource element, Candidate candidate, Match match,
			UnitResolver units) {
		ClassificationDefinition.ClassProperty cp = match.classProperty();
		String id = shortHash(element.getURI() + "|" + candidate.property() + "|" + cp.uri());
		Resource annotation = output.createResource("urn:ifctolbd:etim-property:" + id)
				.addProperty(RDF.type, output.createResource(NS + "EtimPropertyAnnotation"))
				.addProperty(output.createProperty(PROV + "wasDerivedFrom"), candidate.property())
				.addProperty(output.createProperty(NS + "sourcePropertySet"), candidate.set())
				.addProperty(output.createProperty(NS + "sourceObject"), candidate.sourceOwner())
				.addLiteral(output.createProperty(NS + "sourceKind"), candidate.sourceKind())
				.addLiteral(output.createProperty(NS + "sourcePropertySetName"), candidate.setName())
				.addLiteral(output.createProperty(NS + "sourcePropertyName"), candidate.propertyName())
				.addProperty(output.createProperty(NS + "observedValue"), output.asRDFNode(candidate.value().asNode()))
				.addLiteral(output.createProperty(NS + "mappingMethod"), match.method())
				.addLiteral(output.createProperty(NS + "mappingConfidence"), 1.0);
		element.inModel(output).addProperty(output.createProperty(NS + "hasPropertyAnnotation"), annotation);
		if (!blank(cp.uri())) annotation.addProperty(output.createProperty(NS + "classProperty"), output.createResource(cp.uri()));
		if (cp.property() != null && !blank(cp.property().uri()))
			annotation.addProperty(output.createProperty(NS + "property"), output.createResource(cp.property().uri()));
		if (match.reviewReference() != null)
			annotation.addLiteral(output.createProperty(NS + "reviewReference"), match.reviewReference());
		if (candidate.sourceType() != null)
			annotation.addProperty(output.createProperty(UnitResolver.META + "sourceIFCType"),
					output.asRDFNode(candidate.sourceType().asNode()));
		if (candidate.explicitUnit() != null)
			annotation.addProperty(output.createProperty(NS + "sourceUnit"), candidate.explicitUnit());
		UnitResolver.write(output, annotation,
				units.resolve(candidate.explicitUnit(), candidate.sourceType(), ifc));
		return annotation;
	}

	static Set<String> explicitIdentifiers(Model model, IfcOWL ifc, Resource property) {
		Set<String> values = new HashSet<>();
		if (property.isURIResource()) values.add(property.getURI());
		// Some IFC authoring workflows carry the external property identifier in
		// IfcProperty.Description. Accept it only when the complete value is a URI;
		// descriptive prose must never become identifier evidence.
		String description = text(property, ifc, "description_IfcProperty");
		if (isHttpUri(description)) values.add(description);
		var related = ifc.getProperty("relatedResourceObjects_IfcExternalReferenceRelationship");
		model.listResourcesWithProperty(related, property).forEachRemaining(relation -> {
			Resource reference = resource(relation, ifc.getProperty("relatingReference_IfcExternalReferenceRelationship"));
			addReferenceIds(values, reference, ifc);
		});
		var associated = ifc.getProperty("relatedObjects_IfcRelAssociates");
		model.listResourcesWithProperty(associated, property).forEachRemaining(relation ->
				addReferenceIds(values, resource(relation,
						ifc.getProperty("relatingClassification_IfcRelAssociatesClassification")), ifc));
		return values;
	}

	private static void addReferenceIds(Set<String> values, Resource reference, IfcOWL ifc) {
		if (reference == null) return;
		if (reference.isURIResource()) values.add(reference.getURI());
		add(values, text(reference, ifc, "location_IfcExternalReference"));
		add(values, text(reference, ifc, "identification_IfcExternalReference", "itemReference_IfcExternalReference"));
	}

	static boolean datatypeCompatible(RDFNode value, String expected) {
		if (blank(expected) || !value.isLiteral()) return true;
		String type = expected.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
		Object actual = value.asLiteral().getValue();
		return switch (type) {
		case "boolean", "logical" -> actual instanceof Boolean;
		case "integer", "int", "whole" -> actual instanceof Byte || actual instanceof Short
				|| actual instanceof Integer || actual instanceof Long || actual instanceof java.math.BigInteger;
		case "real", "number", "numeric", "decimal", "double" -> actual instanceof Number;
		case "string", "text", "character", "alphanumeric", "time", "date", "datetime" -> actual instanceof String
				|| actual instanceof org.apache.jena.datatypes.xsd.XSDDateTime;
		default -> false;
		};
	}

	static boolean unitCompatible(UnitResolver.Resolution source, ClassificationDefinition.Unit target) {
		if (source == null) return false;
		UnitResolver.Resolution expected = UnitResolver.fromBsdd(target.qudtCode(), target.code());
		if (source.isResolved() && expected.isResolved()) return source.qudtUri().equals(expected.qudtUri());
		return same(source.originalCode(), target.code());
	}

	static RDFNode literalValue(RDFNode node) {
		if (node == null || node.isLiteral()) return node;
		if (!node.isResource()) return null;
		var literals = node.asResource().listProperties().filterKeep(statement -> statement.getObject().isLiteral());
		try { return literals.hasNext() ? literals.next().getObject() : null; }
		finally { literals.close(); }
	}

	private static RDFNode object(Resource subject, org.apache.jena.rdf.model.Property property) {
		var statement = subject.getProperty(property);
		return statement == null ? null : statement.getObject();
	}
	private static Resource resource(Resource subject, org.apache.jena.rdf.model.Property property) {
		RDFNode value = object(subject, property);
		return value != null && value.isResource() ? value.asResource() : null;
	}
	private static String text(Resource subject, IfcOWL ifc, String... properties) {
		if (subject == null) return null;
		for (String property : properties) {
			RDFNode value = object(subject, ifc.getProperty(property));
			RDFNode literal = literalValue(value);
			if (literal != null && literal.isLiteral()) return literal.asLiteral().getString();
		}
		return null;
	}
	private static String entityName(Resource element) {
		Resource type = element.getPropertyResourceValue(RDF.type);
		return type == null ? "" : type.getLocalName();
	}
	private static String key(Candidate candidate) {
		return candidate.setName() + "\u0000" + candidate.propertyName();
	}
	private static boolean contains(Set<String> values, String... candidates) {
		for (String value : values) for (String candidate : candidates) if (same(value, candidate)) return true;
		return false;
	}
	private static void add(Set<String> values, String value) { if (!blank(value)) values.add(value); }
	private static boolean isHttpUri(String value) {
		if (blank(value)) return false;
		try {
			java.net.URI uri = java.net.URI.create(value.trim());
			return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme())
					|| "https".equalsIgnoreCase(uri.getScheme()));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
	private static boolean same(String left, String right) {
		return !blank(left) && !blank(right) && left.trim().equals(right.trim());
	}
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static String shortHash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	private record Candidate(Resource set, String setName, Resource property, String propertyName,
			RDFNode value, RDFNode sourceType, Resource explicitUnit, Resource sourceOwner, String sourceKind) { }
	private record Match(ClassificationDefinition.ClassProperty classProperty, String method,
			String reviewReference) { }
}
