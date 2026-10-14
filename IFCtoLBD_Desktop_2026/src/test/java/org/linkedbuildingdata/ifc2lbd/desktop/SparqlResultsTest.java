package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

class SparqlResultsTest {
	@Test
	void selectRowsKeepResourceUrisAndEscapeCsv() {
		Model model = ModelFactory.createDefaultModel();
		try {
			model.createResource("urn:element:1").addLiteral(model.createProperty("urn:name"), "A, \"quoted\" wall");
			String query = "SELECT ?element ?name WHERE { ?element <urn:name> ?name }";
			try (QueryExecution execution = QueryExecutionFactory.create(QueryFactory.create(query), model)) {
				IFCtoLBDController.SelectResults results = IFCtoLBDController.collectSelectResults(execution.execSelect());
				assertEquals(List.of("element", "name"), results.variables());
				assertEquals(1, results.rows().size());
				assertFalse(results.limited());
				assertEquals("urn:element:1", results.rows().getFirst().cells().get("element").resourceUri());
				assertEquals("element,name\nurn:element:1,\"A, \"\"quoted\"\" wall\"\n",
						IFCtoLBDController.selectResultsAsCsv(results, results.rows()));
			}
		} finally {
			model.close();
		}
	}

	@Test
	void templatesParseAndMissingPropertiesFindsUnannotatedElement() {
		for (String name : List.of("Spaces by storey", "Elements missing properties", "Element inventory")) {
			assertTrue(QueryFactory.create(IFCtoLBDController.queryForTemplate(name)).isSelectType());
		}
		Model model = ModelFactory.createDefaultModel();
		try {
			var elementType = model.createResource("https://w3id.org/bot#Element");
			var a = model.createResource("urn:element:a");
			var b = model.createResource("urn:element:b");
			var c = model.createResource("urn:element:c");
			a.addProperty(org.apache.jena.vocabulary.RDF.type, elementType);
			b.addProperty(org.apache.jena.vocabulary.RDF.type, elementType)
					.addLiteral(model.createProperty("http://lbd.arch.rwth-aachen.de/props#fireRating"), "60 min");
			c.addProperty(org.apache.jena.vocabulary.RDF.type, elementType)
					.addLiteral(model.createProperty("https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/prop/LoadBearing"), true);
			try (QueryExecution execution = QueryExecutionFactory.create(
					QueryFactory.create(IFCtoLBDController.queryForTemplate("Elements missing properties")), model)) {
				IFCtoLBDController.SelectResults results = IFCtoLBDController.collectSelectResults(execution.execSelect());
				assertEquals(1, results.rows().size());
				assertEquals("urn:element:a", results.rows().getFirst().cells().get("element").resourceUri());
			}
			try (QueryExecution execution = QueryExecutionFactory.create(
					QueryFactory.create(IFCtoLBDController.queryForTemplate("Element inventory")), model)) {
				assertEquals(3, IFCtoLBDController.collectSelectResults(execution.execSelect()).rows().size());
			}
		} finally {
			model.close();
		}
	}

	@Test
	void reportsWhenSelectTableIsLimited() {
		Model model = ModelFactory.createDefaultModel();
		try {
			var predicate = model.createProperty("urn:included");
			for (int i = 0; i < 1_001; i++) {
				model.createResource("urn:element:" + i).addLiteral(predicate, true);
			}
			try (QueryExecution execution = QueryExecutionFactory.create(
					QueryFactory.create("SELECT ?element WHERE { ?element <urn:included> true }"), model)) {
				IFCtoLBDController.SelectResults results = IFCtoLBDController.collectSelectResults(execution.execSelect());
				assertEquals(1_000, results.rows().size());
				assertTrue(results.limited());
			}
		} finally {
			model.close();
		}
	}
}
