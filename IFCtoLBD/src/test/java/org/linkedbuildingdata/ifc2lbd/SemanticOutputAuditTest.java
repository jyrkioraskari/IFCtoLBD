package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

class SemanticOutputAuditTest {
	@Test
	void reportsPropertyKindMisuseAndConflictingDeclarations() {
		var model = ModelFactory.createDefaultModel();
		var property = model.createProperty(PROPS.ns + "height_property_simple");
		property.addProperty(RDF.type, OWL.DatatypeProperty).addProperty(RDF.type, OWL.ObjectProperty);
		model.createResource("urn:window").addProperty(property, model.createResource("urn:not-a-literal"));

		var result = SemanticOutputAudit.audit(model);

		assertEquals("errors", result.status());
		assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("DATATYPE_PROPERTY_RESOURCE")));
		assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("CONFLICTING_PROPERTY_DECLARATION")));
	}

	@Test
	void reportsUndeclaredPredicatesInvalidNumbersAndMultipleCurrentStates() {
		var model = ModelFactory.createDefaultModel();
		var subject = model.createResource("urn:element");
		var undeclared = model.createProperty(PROPS.ns + "width_property_simple");
		subject.addLiteral(undeclared, model.createTypedLiteral("not-a-number", XSDDatatype.XSDdecimal));
		var property = model.createResource("urn:property");
		var first = model.createResource("urn:state:1").addProperty(RDF.type, OPM.currentPropertyState);
		var second = model.createResource("urn:state:2").addProperty(RDF.type, OPM.currentPropertyState);
		property.addProperty(OPM.hasPropertyState, first).addProperty(OPM.hasPropertyState, second);

		var result = SemanticOutputAudit.audit(model);

		assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("UNDECLARED_GENERATED_PREDICATE")));
		assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("INVALID_NUMERIC_LITERAL")));
		assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("MULTIPLE_CURRENT_STATES")));
		assertTrue(result.report().contains(null, RDF.type, result.report().createResource(SemanticOutputAudit.NS + "Finding")));
	}

	@Test
	void acceptsADeclaredWellFormedSimpleProperty() {
		var model = ModelFactory.createDefaultModel();
		var property = model.createProperty(PROPS.ns + "height_property_simple");
		property.addProperty(RDF.type, OWL.DatatypeProperty);
		model.createResource("urn:window").addLiteral(property, model.createTypedLiteral("2.2", XSDDatatype.XSDdecimal));

		assertEquals("clean", SemanticOutputAudit.audit(model).status());
	}
}
