package org.linkedbuildingdata.ifc2lbd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Deterministically ranks authoritative ETIM candidates from independent IFC observations. */
final class EtimClassificationSuggestionStage {
	private static final String NS = SupplyChainStage.NS;
	private static final String PROV = "http://www.w3.org/ns/prov#";
	private static final int MIN_SCORE = 55;
	private static final int MIN_INDEPENDENT_EVIDENCE = 10;
	private static final int MAX_CANDIDATES = 5;
	private static final Set<String> STOP_WORDS = Set.of("and", "the", "for", "with", "from", "this",
			"that", "type", "object", "element", "product", "undefined", "userdefined");

	private EtimClassificationSuggestionStage() { }

	static void suggest(Model ifcModel, IfcOWL ifc, Model output, Model propertyOutput,
			Map<Resource, Resource> resources, ClassificationResolver resolver, Set<Resource> suppliedEtim,
			List<EtimClassificationAcceptance> acceptances, List<EtimPropertyMapping> propertyMappings) {
		output.setNsPrefix("supply", NS);
		output.setNsPrefix("prov", PROV);
		UnitResolver units = UnitResolver.fromProject(ifcModel, ifc);
		Map<String, List<ClassificationResolver.Candidate>> candidatesByEntity = new java.util.HashMap<>();
		for (Map.Entry<Resource, Resource> entry : resources.entrySet()) {
			Resource source = entry.getKey();
			Resource target = entry.getValue();
			if (suppliedEtim.contains(source)) continue;
			if (!target.hasProperty(RDF.type, BOT.element)) continue;
			String entity = entityName(source);
			if (entity == null || target == null || !target.isURIResource()) continue;
			List<Observation> observations = observations(ifcModel, ifc, source);
			String description = observedDescription(ifc, source);
			List<ClassificationResolver.Candidate> discovered = candidatesByEntity.computeIfAbsent(entity,
					value -> resolver.candidates(new ClassificationResolver.CandidateRequest("ETIM", value)));
			List<RankedCandidate> ranked = discovered.stream()
					.map(candidate -> rank(candidate, entity, description, observations, units, ifc))
					.filter(candidate -> candidate.score() >= MIN_SCORE
							&& candidate.independentEvidence() >= MIN_INDEPENDENT_EVIDENCE)
					.sorted(Comparator.comparingInt(RankedCandidate::score).reversed()
							.thenComparing(value -> value.candidate().resolution().conceptUri()))
					.limit(MAX_CANDIDATES).toList();
			if (ranked.isEmpty()) {
				writeAbstention(output, target, entity, description, observations.size());
				continue;
			}
			int rank = 1;
			for (RankedCandidate candidate : ranked) {
				Resource suggestion = writeSuggestion(output, target, candidate, rank++);
				acceptance(target, candidate, acceptances).ifPresent(acceptance -> {
					writeAcceptance(output, target, suggestion, candidate, acceptance);
					EtimPropertyAnnotationStage.annotate(ifcModel, ifc, propertyOutput, source, target,
							candidate.candidate().definition(), propertyMappings);
				});
			}
		}
	}

	private static RankedCandidate rank(ClassificationResolver.Candidate candidate, String entity,
			String observedDescription, List<Observation> observations, UnitResolver units, IfcOWL ifc) {
		List<String> evidence = new ArrayList<>();
		List<String> contradictions = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		int score = candidate.discoveryMethod().contains("published-ifc-relationship") ? 45 : 35;
		evidence.add(candidate.discoveryMethod() + ": " + entity
				+ (candidate.supportingClassUri() == null ? "" : " via " + candidate.supportingClassUri()));
		int independent = 0;
		Set<String> overlap = tokenOverlap(observedDescription,
				candidate.definition().name() + " " + safe(candidate.definition().description()));
		if (overlap.size() >= 2) {
			int points = Math.min(20, 5 + overlap.size() * 3);
			score += points;
			independent += points;
			evidence.add("observed-description-overlap: " + String.join(", ", overlap));
		} else if (blank(observedDescription)) missing.add("IFC name or description");

		Set<String> matchedRequired = new HashSet<>();
		Set<String> evaluatedProperties = new HashSet<>();
		int propertyPoints = 0;
		for (Observation observation : observations) {
			for (ClassificationDefinition.ClassProperty property : candidate.definition().classProperties()) {
				MatchKind kind = matchKind(observation, property);
				if (kind == MatchKind.NONE) continue;
				String propertyId = propertyId(property);
				// Occurrence observations are collected before type observations. Evaluate each
				// candidate property once so an inherited value cannot double-count, or
				// contradict, an applicable occurrence override.
				if (!evaluatedProperties.add(propertyId)) continue;
				Compatibility compatibility = compatible(observation, property, units, ifc);
				if (compatibility == Compatibility.CONTRADICTION) {
					score -= kind == MatchKind.IDENTIFIER ? 20 : 10;
					contradictions.add(kind.label + " property " + observation.name()
							+ " has incompatible datatype, unit, or allowed value");
				} else {
					int points = kind == MatchKind.IDENTIFIER ? 20 : 5;
					if (compatibility == Compatibility.MISSING) {
						points = Math.max(2, points / 2);
						missing.add("complete constraints for observed property " + observation.name());
					}
					propertyPoints = Math.min(40, propertyPoints + points);
					evidence.add(kind.label + " property match: " + observation.name()
							+ " value=" + observation.lexicalValue());
					if (Boolean.TRUE.equals(property.required())) matchedRequired.add(propertyId);
				}
			}
		}
		score += propertyPoints;
		independent += propertyPoints;
		if (observations.isEmpty()) missing.add("IFC property observations");
		int missingRequired = 0;
		for (ClassificationDefinition.ClassProperty property : candidate.definition().classProperties()) {
			if (Boolean.TRUE.equals(property.required()) && !matchedRequired.contains(propertyId(property))) {
				if (missingRequired++ < 5) missing.add("required candidate property " + propertyId(property));
			}
		}
		if (!candidate.definition().relatedIfcEntities().isEmpty()
				&& candidate.definition().relatedIfcEntities().stream().noneMatch(entity::equals)) {
			score -= 20;
			contradictions.add("candidate definition does not list observed IFC entity " + entity);
		}
		return new RankedCandidate(candidate, Math.max(0, Math.min(100, score)), independent,
				List.copyOf(new LinkedHashSet<>(evidence)), List.copyOf(new LinkedHashSet<>(contradictions)),
				List.copyOf(new LinkedHashSet<>(missing)));
	}

	private static MatchKind matchKind(Observation observation, ClassificationDefinition.ClassProperty property) {
		String propertyUri = property.property() == null ? null : property.property().uri();
		String propertyCode = property.property() == null ? null : property.property().code();
		if (observation.identifiers().stream().anyMatch(value -> same(value, property.uri())
				|| same(value, propertyUri) || same(value, propertyCode))) return MatchKind.IDENTIFIER;
		String propertyName = property.property() == null ? null : property.property().name();
		return sameName(observation.name(), propertyName) ? MatchKind.EXACT_NAME : MatchKind.NONE;
	}

	private static Compatibility compatible(Observation observation,
			ClassificationDefinition.ClassProperty property, UnitResolver units, IfcOWL ifc) {
		if (!blank(property.dataType())
				&& !EtimPropertyAnnotationStage.datatypeCompatible(observation.value(), property.dataType()))
			return Compatibility.CONTRADICTION;
		UnitResolver.Resolution sourceUnit = units.resolve(observation.explicitUnit(), observation.sourceType(), ifc);
		if (!property.units().isEmpty()) {
			if (!sourceUnit.isResolved() && sourceUnit.originalCode() == null) return Compatibility.MISSING;
			if (property.units().stream().noneMatch(unit ->
					EtimPropertyAnnotationStage.unitCompatible(sourceUnit, unit))) return Compatibility.CONTRADICTION;
		}
		String actual = observation.lexicalValue();
		if (!property.allowedValues().isEmpty() && property.allowedValues().stream().noneMatch(value ->
				same(actual, value.value()) || same(actual, value.code()))) return Compatibility.CONTRADICTION;
		if (!blank(property.predefinedValue()) && !same(actual, property.predefinedValue()))
			return Compatibility.CONTRADICTION;
		return Compatibility.COMPATIBLE;
	}

	private static Resource writeSuggestion(Model output, Resource target, RankedCandidate ranked, int rank) {
		ClassificationResolver.Candidate candidate = ranked.candidate();
		String uri = candidate.resolution().conceptUri();
		Resource suggestion = output.createResource("urn:ifctolbd:etim-suggestion:"
				+ shortHash(target.getURI() + "|" + uri))
				.addProperty(RDF.type, output.createResource(NS + "ClassificationSuggestion"))
				.addLiteral(output.createProperty(NS + "classificationStatus"), "suggested")
				.addLiteral(output.createProperty(NS + "classificationCorrectness"), "unconfirmed")
				.addProperty(output.createProperty(NS + "suggestedConcept"), output.createResource(uri))
				.addLiteral(output.createProperty(NS + "score"), ranked.score())
				.addLiteral(output.createProperty(NS + "rank"), rank)
				.addLiteral(output.createProperty(NS + "rankingMethod"), "deterministic-etim-v1");
		target.addProperty(output.createProperty(NS + "hasClassificationSuggestion"), suggestion);
		addLiteral(suggestion, output, "candidateCode", candidate.definition().code());
		addLiteral(suggestion, output, "candidateName", candidate.definition().name());
		if (candidate.supportingClassUri() != null)
			suggestion.addProperty(output.createProperty(NS + "supportingModellingClass"),
					output.createResource(candidate.supportingClassUri()));
		ranked.evidence().forEach(value -> suggestion.addLiteral(output.createProperty(NS + "evidence"), value));
		ranked.contradictions().forEach(value -> suggestion.addLiteral(output.createProperty(NS + "contradiction"), value));
		ranked.missing().forEach(value -> suggestion.addLiteral(output.createProperty(NS + "missingInformation"), value));
		return suggestion;
	}

	private static void writeAbstention(Model output, Resource target, String entity, String description,
			int propertyCount) {
		Resource assessment = output.createResource("urn:ifctolbd:etim-assessment:"
				+ shortHash(target.getURI() + "|abstained"))
				.addProperty(RDF.type, output.createResource(NS + "ClassificationSuggestionAssessment"))
				.addLiteral(output.createProperty(NS + "classificationStatus"), "abstained")
				.addLiteral(output.createProperty(NS + "classificationCorrectness"), "not-assessed")
				.addLiteral(output.createProperty(NS + "reason"), "insufficient-independent-evidence")
				.addLiteral(output.createProperty(NS + "observedIfcEntity"), entity)
				.addLiteral(output.createProperty(NS + "observedPropertyCount"), propertyCount);
		if (blank(description)) assessment.addLiteral(output.createProperty(NS + "missingInformation"),
				"IFC name or description");
		target.addProperty(output.createProperty(NS + "hasClassificationAssessment"), assessment);
	}

	private static java.util.Optional<EtimClassificationAcceptance> acceptance(Resource target,
			RankedCandidate candidate, List<EtimClassificationAcceptance> acceptances) {
		return acceptances.stream().filter(value -> value.elementUri().equals(target.getURI())
				&& value.classUri().equals(candidate.candidate().resolution().conceptUri())).findFirst();
	}

	private static void writeAcceptance(Model output, Resource target, Resource suggestion,
			RankedCandidate candidate, EtimClassificationAcceptance acceptance) {
		Resource assertion = output.createResource("urn:ifctolbd:etim-acceptance:"
				+ shortHash(target.getURI() + "|" + acceptance.classUri()))
				.addProperty(RDF.type, output.createResource(NS + "ClassificationAssertion"))
				.addLiteral(output.createProperty(NS + "classificationStatus"), "accepted")
				.addLiteral(output.createProperty(NS + "classificationCorrectness"), "reviewed-accepted")
				.addProperty(output.createProperty(NS + "resolvedConcept"), output.createResource(acceptance.classUri()))
				.addProperty(output.createProperty(PROV + "wasDerivedFrom"), suggestion)
				.addLiteral(output.createProperty(NS + "reviewReference"), acceptance.reviewReference())
				.addLiteral(output.createProperty(NS + "resolutionAuthority"),
						candidate.candidate().resolution().authority());
		target.addProperty(output.createProperty(NS + "hasClassification"), assertion);
		SupplyChainStage.writeDefinition(output, assertion, candidate.candidate().definition());
	}

	private static List<Observation> observations(Model model, IfcOWL ifc, Resource element) {
		List<Observation> result = new ArrayList<>();
		IfcMappingSupport.subjectsWithObject(model, ifc, element, "relatedObjects_IfcRelDefines",
				"relatedObjects_IfcRelDefinesByProperties").forEach(relation -> {
			if (relation.hasProperty(RDF.type, model.createResource(ifc.getIfcURI() + "IfcRelDefinesByProperties")))
				collectSet(model, ifc, resource(relation, ifc.getRelatingPropertyDefinition_IfcRelDefinesByProperties()), result);
		});
		model.listResourcesWithProperty(ifc.getProperty("relatedObjects_IfcRelDefinesByType"), element)
				.forEachRemaining(relation -> {
					Resource type = resource(relation, ifc.getProperty("relatingType_IfcRelDefinesByType"));
					if (type != null) IfcMappingSupport.objectResources(type, ifc, "hasPropertySets_IfcTypeObject")
							.forEach(set -> collectSet(model, ifc, set, result));
				});
		return result;
	}

	private static void collectSet(Model model, IfcOWL ifc, Resource set, List<Observation> result) {
		if (set == null) return;
		IfcMappingSupport.objectResources(set, ifc, "hasProperties_IfcPropertySet").forEach(property -> {
			String name = text(property, ifc, "name_IfcProperty");
			RDFNode wrapped = object(property, ifc.getNominalValue_IfcPropertySingleValue());
			RDFNode value = EtimPropertyAnnotationStage.literalValue(wrapped);
			if (name == null || value == null || !value.isLiteral()) return;
			RDFNode sourceType = wrapped != null && wrapped.isResource()
					? wrapped.asResource().getPropertyResourceValue(RDF.type) : null;
			result.add(new Observation(name, value, sourceType,
					resource(property, ifc.getUnit_IfcPropertySingleValue()),
					EtimPropertyAnnotationStage.explicitIdentifiers(model, ifc, property)));
		});
	}

	private static String observedDescription(IfcOWL ifc, Resource element) {
		return String.join(" ", nonBlank(text(element, ifc, "name_IfcRoot"),
				text(element, ifc, "description_IfcRoot"), text(element, ifc, "objectType_IfcObject")));
	}

	private static Set<String> tokenOverlap(String left, String right) {
		Set<String> leftTokens = tokens(left);
		Set<String> rightTokens = tokens(right);
		leftTokens.retainAll(rightTokens);
		return new java.util.TreeSet<>(leftTokens);
	}
	private static Set<String> tokens(String value) {
		Set<String> result = new HashSet<>();
		if (value == null) return result;
		for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
			if (token.length() >= 3 && !STOP_WORDS.contains(token)) result.add(token);
		return result;
	}
	private static List<String> nonBlank(String... values) {
		return java.util.Arrays.stream(values).filter(value -> !blank(value)).toList();
	}
	private static String propertyId(ClassificationDefinition.ClassProperty property) {
		if (!blank(property.uri())) return property.uri();
		if (property.property() != null && !blank(property.property().uri())) return property.property().uri();
		if (property.property() != null && !blank(property.property().code())) return property.property().code();
		return property.property() == null ? "unnamed" : safe(property.property().name());
	}
	private static String entityName(Resource element) {
		Resource type = element.getPropertyResourceValue(RDF.type);
		return type == null ? null : type.getLocalName();
	}
	private static RDFNode object(Resource subject, org.apache.jena.rdf.model.Property property) {
		var statement = subject.getProperty(property);
		return statement == null ? null : statement.getObject();
	}
	private static Resource resource(Resource subject, org.apache.jena.rdf.model.Property property) {
		RDFNode node = subject == null ? null : object(subject, property);
		return node != null && node.isResource() ? node.asResource() : null;
	}
	private static String text(Resource subject, IfcOWL ifc, String property) {
		RDFNode node = object(subject, ifc.getProperty(property));
		RDFNode value = EtimPropertyAnnotationStage.literalValue(node);
		return value != null && value.isLiteral() ? value.asLiteral().getString() : null;
	}
	private static boolean same(String left, String right) {
		return !blank(left) && !blank(right) && left.trim().equals(right.trim());
	}
	private static boolean sameName(String left, String right) {
		return !blank(left) && !blank(right) && left.trim().equalsIgnoreCase(right.trim());
	}
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static String safe(String value) { return value == null ? "" : value; }
	private static void addLiteral(Resource resource, Model model, String property, String value) {
		if (!blank(value)) resource.addLiteral(model.createProperty(NS + property), value);
	}
	private static String shortHash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	private enum Compatibility { COMPATIBLE, MISSING, CONTRADICTION }
	private enum MatchKind {
		NONE("none"), IDENTIFIER("explicit-identifier"), EXACT_NAME("exact-name");
		private final String label;
		MatchKind(String label) { this.label = label; }
	}
	private record Observation(String name, RDFNode value, RDFNode sourceType, Resource explicitUnit,
			Set<String> identifiers) {
		String lexicalValue() { return value.asLiteral().getLexicalForm(); }
	}
	private record RankedCandidate(ClassificationResolver.Candidate candidate, int score,
			int independentEvidence, List<String> evidence, List<String> contradictions, List<String> missing) { }
}
