package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Terms verified against Digital Construction BuildingMaterials 0.1.
 *
 * <p>The bundled ontology is a verification resource only. Conversion does not
 * import it or enable inference.</p>
 */
public final class BMO {
	public static final String NS = "https://w3id.org/digitalconstruction/BuildingMaterials#";
	public static final String SOURCE_REVISION = "b30a0a29e04c4f5d4a63e26a50987afce7d8492f";
	public static final String SOURCE_SHA256 = "b8ed0e82828ccc2700cc1c30252e0c1383bb2149821e63b43ed85ad925da7ca9";
	public static final String SOURCE_RESOURCE = "/ontology/bmo-0.1.ttl";

	public static final Resource Material = resource("Material");
	public static final Resource MaterialDefinition = resource("MaterialDefinition");
	public static final Resource Layer = resource("Layer");
	public static final Resource LayerSet = resource("LayerSet");
	public static final Resource MaterialType = resource("MaterialType");
	public static final Resource CompositeMaterial = resource("CompositeMaterial");
	public static final Resource MetallicMaterial = resource("MetallicMaterial");

	/** Material-type individuals; these are not OWL classes. */
	public static final Resource Concrete = resource("Concrete");
	public static final Resource Steel = resource("Steel");

	public static final Property hasMaterial = property("hasMaterial");
	public static final Property hasMaterialName = property("hasMaterialName");
	public static final Property hasLayers = property("hasLayers");
	public static final Property adjacentLayer = property("adjacentLayer");
	public static final Property hasThickness = property("hasThickness");
	public static final Property hasUnit = property("hasUnit");

	public static void addNamespace(Model model) {
		model.setNsPrefix("bmo", NS);
	}

	private static Resource resource(String localName) {
		return ResourceFactory.createResource(NS + localName);
	}

	private static Property property(String localName) {
		return ResourceFactory.createProperty(NS + localName);
	}

	private BMO() { }
}
