package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

class McpConversionBridgeJsonLdTest {
	@Test
	void compactDescriptionRoundTripsItsBoundedRdfSubgraphOffline() {
		Model source = ModelFactory.createDefaultModel();
		Resource element = source.createResource("https://example.org/elements/1");
		Resource related = source.createResource("https://example.org/elements/2");
		Resource blank = source.createResource();
		element.addLiteral(source.createProperty("https://example.org/vocabulary#count"),
				source.createTypedLiteral("2", XSDDatatype.XSDinteger));
		element.addLiteral(source.createProperty("https://example.org/vocabulary#label"),
				source.createLiteral("door", "en"));
		element.addProperty(source.createProperty("https://example.org/vocabulary#related"), related);
		element.addProperty(source.createProperty("https://example.org/vocabulary#details"), blank);
		blank.addLiteral(source.createProperty("https://example.org/vocabulary#note"), "kept through blank node");

		try (McpConversionBridge bridge = new McpConversionBridge("https://example.org/", true, 1, "", "")) {
			McpConversionBridge.JsonLdDescription description = bridge.describeElement(source, element.getURI(),
					new String[0], 0, 20);
			assertTrue(description.isFound());
			assertFalse(description.isTruncated());
			assertTrue(description.getJsonLd().contains("@context"));
			assertTrue(description.getJsonLd().contains("https://example.org/vocabulary#count"));

			Model roundTripped = ModelFactory.createDefaultModel();
			RDFDataMgr.read(roundTripped, new java.io.StringReader(description.getJsonLd()), null, Lang.JSONLD11);
			Model expected = ModelFactory.createDefaultModel();
			expected.add(source.listStatements(element, null, (org.apache.jena.rdf.model.RDFNode) null));
			expected.add(source.listStatements(blank, null, (org.apache.jena.rdf.model.RDFNode) null));
			assertTrue(expected.isIsomorphicWith(roundTripped));
		}
	}

	@Test
	void statementLimitIsExplicit() {
		Model source = ModelFactory.createDefaultModel();
		Resource element = source.createResource("https://example.org/elements/limited");
		element.addLiteral(source.createProperty("https://example.org/vocabulary#a"), "one");
		element.addLiteral(source.createProperty("https://example.org/vocabulary#b"), "two");
		try (McpConversionBridge bridge = new McpConversionBridge("https://example.org/", true, 1, "", "")) {
			McpConversionBridge.JsonLdDescription description = bridge.describeElement(source, element.getURI(),
					new String[0], 0, 1);
			assertTrue(description.isTruncated());
			assertTrue(description.getReturnedStatementCount() == 1);
		}
	}

	@Test
	void unknownElementIsDistinctFromAnEmptyDescription() {
		Model source = ModelFactory.createDefaultModel();
		try (McpConversionBridge bridge = new McpConversionBridge("https://example.org/", true, 1, "", "")) {
			McpConversionBridge.JsonLdDescription description = bridge.describeElement(source,
					"https://example.org/elements/missing", new String[0], 0, 10);
			assertFalse(description.isFound());
			assertFalse(description.isTruncated());
			assertTrue(description.getReturnedStatementCount() == 0);
		}
	}
}
