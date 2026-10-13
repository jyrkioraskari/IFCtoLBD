package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

class CrossViewLinkingTest {
	@Test
	void resolvesPropertyViolationBackToOwningElement() throws Exception {
		Model model = ModelFactory.createDefaultModel();
		try {
			var element = model.createResource("urn:element:wall");
			var property = model.createResource("urn:property:fireRating");
			element.addProperty(org.apache.jena.vocabulary.RDF.type,
					model.createResource("https://w3id.org/bot#Element"));
			element.addProperty(model.createProperty("urn:test:property"), property);

			Method method = IFCtoLBDController.class.getDeclaredMethod("resolveFocusResourceUri", Model.class,
					org.apache.jena.graph.Node.class);
			method.setAccessible(true);
			assertEquals(element.getURI(), method.invoke(null, model, property.asNode()));
		} finally {
			model.close();
		}
	}
}
