package org.linkedbuildingdata.ifc2lbd.core.utils.ontology;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.linkedbuildingdata.ifc2lbd.core.utils.StringOperations;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

import be.ugent.IfcSpfReader;

public class CreatePsetAttributeOntology {
	OntModel m = ModelFactory.createOntologyModel();
	Set<String> attributes = new HashSet<>();
	Map<String, Boolean> ob_map = new HashMap<>();
	Set<String> simplified_attributes = new HashSet<String>();

	public CreatePsetAttributeOntology() {
		PROPS.addNameSpace(m);
		readOntology("IFC2X3_TC1");
		readOntology("IFC4x3_RC1");
		readOntology("IFC4x1");
		readOntology("IFC4_ADD2");

		for (String a : attributes) {
			if (!simplified_attributes.add(StringOperations.toCamelCase(a.split("_")[0]))) {
				// System.out.println(a);
			}
			// System.out.println(StringOperations.toCamelCase(a));

			// System.out.println(a+"_attribute_simple");
		}
		for (String a : simplified_attributes) {
			Boolean op = ob_map.get(a);
			if (op == null) {
				System.out.println("No value if an object property.");
			} else {
				if(op)
					m.createObjectProperty( PROPS.props_ns  + a );
				else
					m.createDatatypeProperty(PROPS.props_ns  + a );
				
				m.createDatatypeProperty(PROPS.props_ns  + a +"_attribute_simple");
			}
		}
		
		m.write(System.out,"TTL");
	}

	public void readOntology(String exp) {
		OntModel om = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
		InputStream in = IfcSpfReader.class.getResourceAsStream("/" + exp + ".ttl");
		if (in == null)
			in = IfcSpfReader.class.getResourceAsStream("/resources/" + exp + ".ttl");
		om.read(in, null, "TTL");

		in = IfcSpfReader.class.getResourceAsStream("/list.ttl");
		if (in == null)
			in = IfcSpfReader.class.getResourceAsStream("/resources/list.ttl");
		om.read(in, null, "TTL");

		om.listAllOntProperties().forEach(s -> {
			if (s.toString().contains("buildingsmart"))
				attributes.add(s.getLocalName());
			ob_map.put(StringOperations.toCamelCase(s.getLocalName().split("_")[0]), s.isObjectProperty());
		});
	}

	public static void main(String[] args) {
		new CreatePsetAttributeOntology();

	}

}
