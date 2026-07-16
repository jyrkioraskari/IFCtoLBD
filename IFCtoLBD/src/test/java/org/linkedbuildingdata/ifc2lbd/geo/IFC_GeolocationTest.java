package org.linkedbuildingdata.ifc2lbd.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IFC_GeolocationTest {
	private static final String IFC_NS = "https://standards.buildingsmart.org/IFC/DEV/IFC2x3/TC1/OWL#";
	private static final String LIST_NS = "https://w3id.org/list#";

	@DisplayName("Returns WKT point from IfcSite RefLatitude and RefLongitude")
	@Test
	void addGeolocationReturnsWktPoint() {
		IFC_Geolocation geolocation = new IFC_Geolocation(IFC_NS);

		assertEquals("POINT (-87.63939999972222 41.874400000000004)", geolocation.addGeolocation(model(IFC_NS)));
		assertEquals("POINT (-87.63939999972222 41.874400000000004)", geolocation.addGeolocation(model(IFC_NS)));
	}

	@DisplayName("Accepts IFC namespace without trailing separator")
	@Test
	void addGeolocationNormalizesIfcNamespace() {
		IFC_Geolocation geolocation = new IFC_Geolocation(IFC_NS.substring(0, IFC_NS.length() - 1));

		assertEquals("POINT (-87.63939999972222 41.874400000000004)", geolocation.addGeolocation(model(IFC_NS)));
	}

	private Model model(String ifcNs) {
		Model model = ModelFactory.createDefaultModel();
		Resource site = model.createResource("urn:site");
		site.addProperty(RDF.type, model.createResource(ifcNs + "IfcSite"));
		site.addProperty(model.createProperty(ifcNs + "refLatitude_IfcSite"),
				list(model, "41", "52", "27", "840000"));
		site.addProperty(model.createProperty(ifcNs + "refLongitude_IfcSite"),
				list(model, "-87", "-38", "-21", "-839999"));
		return model;
	}

	private Resource list(Model model, String... values) {
		Property hasContents = model.createProperty(LIST_NS + "hasContents");
		Property hasNext = model.createProperty(LIST_NS + "hasNext");
		Property value = model.createProperty("urn:value");

		Resource first = model.createResource();
		Resource current = first;
		for (int i = 0; i < values.length; i++) {
			if (i < values.length - 1) {
				Resource next = model.createResource();
				current.addProperty(hasNext, next);
				Resource content = model.createResource();
				content.addLiteral(value, values[i]);
				current.addProperty(hasContents, content);
				current = next;
				continue;
			}
			Resource content = model.createResource();
			content.addLiteral(value, values[i]);
			current.addProperty(hasContents, content);
		}
		return first;
	}
}
