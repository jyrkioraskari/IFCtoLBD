package org.linkedbuildingdata.ifc2lbd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.BSDD;
import org.linkedbuildingdata.ifc2lbd.namespace.LBD;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

/** Performs inexpensive semantic checks over completed conversion data graphs. */
public final class SemanticOutputAudit {
	public static final String NS = "https://w3id.org/ifctolbd/audit#";
	private static final String XSD = "http://www.w3.org/2001/XMLSchema#";
	private static final Set<String> OWNED_NAMESPACES = Set.of(PROPS.ns, LBD.ns, BSDD.meta_ns,
			"https://w3id.org/ifctolbd/geometry#", "https://w3id.org/ifctolbd/evidence#");
	private static final Set<String> NUMERIC_TYPES = Set.of(
			XSD + "decimal", XSD + "double", XSD + "float", XSD + "integer", XSD + "long", XSD + "int",
			XSD + "short", XSD + "byte", XSD + "nonNegativeInteger", XSD + "positiveInteger",
			XSD + "nonPositiveInteger", XSD + "negativeInteger", XSD + "unsignedLong", XSD + "unsignedInt",
			XSD + "unsignedShort", XSD + "unsignedByte");

	public record Finding(Severity severity, String code, String message, Resource subject,
			Property predicate, RDFNode object) { }
	public enum Severity { WARNING, ERROR }
	public record Result(String status, int warningCount, int errorCount, List<Finding> findings, Model report) { }

	private SemanticOutputAudit() { }

	public static Result audit(Model... models) {
		List<Statement> statements = new ArrayList<>();
		Set<String> objectProperties = new HashSet<>();
		Set<String> datatypeProperties = new HashSet<>();
		for (Model model : models) {
			model.listStatements().forEachRemaining(statement -> {
				statements.add(statement);
				if (statement.getPredicate().equals(RDF.type) && statement.getSubject().isURIResource()
						&& statement.getObject().isResource()) {
					String uri = statement.getSubject().getURI();
					if (statement.getResource().equals(OWL.ObjectProperty)) objectProperties.add(uri);
					if (statement.getResource().equals(OWL.DatatypeProperty)) datatypeProperties.add(uri);
				}
			});
		}

		List<Finding> findings = new ArrayList<>();
		Set<String> usedPredicates = new LinkedHashSet<>();
		Map<String, Set<String>> literalValues = new HashMap<>();
		for (Statement statement : statements) {
			String predicate = statement.getPredicate().getURI();
			usedPredicates.add(predicate);
			if (datatypeProperties.contains(predicate) && statement.getObject().isResource()) {
				findings.add(finding(Severity.ERROR, "DATATYPE_PROPERTY_RESOURCE",
						"Datatype property is used with a resource object", statement));
			}
			if (objectProperties.contains(predicate) && statement.getObject().isLiteral()) {
				findings.add(finding(Severity.ERROR, "OBJECT_PROPERTY_LITERAL",
						"Object property is used with a literal object", statement));
			}
			if (statement.getObject().isLiteral()) {
				checkNumericLiteral(statement, findings);
				if (isSingleValuePredicate(predicate)) {
					String key = statement.getSubject().toString() + "\u0000" + predicate;
					literalValues.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
							.add(statement.getLiteral().toString());
				}
			}
		}

		for (String predicate : usedPredicates) {
			if (isOwned(predicate) && !objectProperties.contains(predicate) && !datatypeProperties.contains(predicate)) {
				Statement use = statements.stream().filter(s -> s.getPredicate().getURI().equals(predicate)).findFirst().orElse(null);
				if (use != null) findings.add(finding(Severity.WARNING, "UNDECLARED_GENERATED_PREDICATE",
						"Converter-owned predicate has no object/datatype property declaration", use));
			}
		}
		Set<String> conflicting = new HashSet<>(objectProperties);
		conflicting.retainAll(datatypeProperties);
		for (String predicate : conflicting) {
			findings.add(new Finding(Severity.ERROR, "CONFLICTING_PROPERTY_DECLARATION",
					"Predicate is declared as both an object and datatype property", null,
					ResourceFactorySupport.property(predicate), null));
		}
		literalValues.forEach((key, values) -> {
			if (values.size() > 1) {
				int split = key.indexOf('\u0000');
				findings.add(new Finding(Severity.WARNING, "MULTIPLE_SIMPLE_VALUES",
						"A simple generated property has multiple distinct literal values", null,
						ResourceFactorySupport.property(key.substring(split + 1)), null));
			}
		});
		checkCurrentStates(statements, findings);

		Model report = report(findings);
		int warnings = (int) findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
		int errors = findings.size() - warnings;
		return new Result(errors > 0 ? "errors" : warnings > 0 ? "warnings" : "clean",
				warnings, errors, List.copyOf(findings), report);
	}

	private static void checkNumericLiteral(Statement statement, List<Finding> findings) {
		Literal literal = statement.getLiteral();
		String datatypeUri = literal.getDatatypeURI();
		if (!NUMERIC_TYPES.contains(datatypeUri)) return;
		RDFDatatype datatype = literal.getDatatype();
		if (datatype == null || !datatype.isValid(literal.getLexicalForm())) {
			findings.add(finding(Severity.ERROR, "INVALID_NUMERIC_LITERAL",
					"Numeric literal is not valid for datatype " + datatypeUri, statement));
			return;
		}
		if ((XSD.concat("double").equals(datatypeUri) || XSD.concat("float").equals(datatypeUri))
				&& Set.of("INF", "-INF", "NaN").contains(literal.getLexicalForm())) {
			findings.add(finding(Severity.WARNING, "NON_FINITE_NUMERIC_LITERAL",
					"Non-finite numeric value is unsuitable for a building measurement", statement));
		}
	}

	private static void checkCurrentStates(List<Statement> statements, List<Finding> findings) {
		Set<Resource> currentStates = new HashSet<>();
		for (Statement statement : statements) {
			if (statement.getPredicate().equals(RDF.type) && statement.getObject().equals(OPM.currentPropertyState))
				currentStates.add(statement.getSubject());
		}
		Map<Resource, Set<Resource>> activeByProperty = new HashMap<>();
		for (Statement statement : statements) {
			if (statement.getPredicate().equals(OPM.hasPropertyState) && statement.getObject().isResource()
					&& currentStates.contains(statement.getResource())) {
				activeByProperty.computeIfAbsent(statement.getSubject(), ignored -> new HashSet<>())
						.add(statement.getResource());
			}
		}
		activeByProperty.forEach((property, states) -> {
			if (states.size() > 1) findings.add(new Finding(Severity.WARNING, "MULTIPLE_CURRENT_STATES",
					"OPM property has more than one current state", property, OPM.hasPropertyState, null));
		});
	}

	private static boolean isSingleValuePredicate(String uri) {
		return uri.startsWith(PROPS.ns) && (uri.endsWith("_property_simple") || uri.endsWith("_attribute_simple"));
	}

	private static boolean isOwned(String uri) {
		return OWNED_NAMESPACES.stream().anyMatch(uri::startsWith);
	}

	private static Finding finding(Severity severity, String code, String message, Statement statement) {
		return new Finding(severity, code, message, statement.getSubject(), statement.getPredicate(), statement.getObject());
	}

	private static Model report(List<Finding> findings) {
		Model model = ModelFactory.createDefaultModel();
		model.setNsPrefix("audit", NS);
		Resource report = model.createResource().addProperty(RDF.type, model.createResource(NS + "Report"));
		for (Finding finding : findings) {
			Resource entry = model.createResource().addProperty(RDF.type, model.createResource(NS + "Finding"))
					.addLiteral(model.createProperty(NS + "severity"), finding.severity().name().toLowerCase())
					.addLiteral(model.createProperty(NS + "code"), finding.code())
					.addLiteral(model.createProperty(NS + "message"), finding.message());
			if (finding.subject() != null) entry.addProperty(model.createProperty(NS + "subject"), finding.subject());
			if (finding.predicate() != null) entry.addProperty(model.createProperty(NS + "predicate"), finding.predicate());
			if (finding.object() != null) entry.addProperty(model.createProperty(NS + "object"), finding.object());
			report.addProperty(model.createProperty(NS + "finding"), entry);
		}
		return model;
	}

	private static final class ResourceFactorySupport {
		private static Property property(String uri) {
			return org.apache.jena.rdf.model.ResourceFactory.createProperty(uri);
		}
	}
}
