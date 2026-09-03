package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

class StableGuidUriPolicyTest {

	@Test
	void identityDoesNotDependOnProductType() {
		var sourceModel = ModelFactory.createDefaultModel();
		var ifcOWL = new IfcOWL("https://standards.buildingsmart.org/IFC/DEV/IFC4/ADD2/OWL#");
		var source = sourceModel.createResource("urn:ifc:object");
		var guidValue = sourceModel.createResource()
				.addLiteral(IfcOWL.Express.getHasString(), "2O2Fr$t4X7Zf8NOew3FNr2");
		source.addProperty(ifcOWL.getGuid(), guidValue);

		var policy = new StableGuidUriPolicy("model-a");
		String wallUri = policy.createResource(source, ModelFactory.createDefaultModel(), "Wall", ifcOWL,
				"https://example.com/#", false).getURI();
		String doorUri = policy.createResource(source, ModelFactory.createDefaultModel(), "Door", ifcOWL,
				"https://example.com/#", false).getURI();

		assertEquals(wallUri, doorUri);
		assertEquals("https://example.com/model/model-a/element/9808fd7f-dc48-478e-9217-628e833d7d42", wallUri);
	}
}
