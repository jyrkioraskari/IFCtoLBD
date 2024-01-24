package org.linkedbuildingdata.ifc2lbd.core.utils.ontology;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.core.utils.StringOperations;
import org.linkedbuildingdata.ifc2lbd.namespace.LBD;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

import be.ugent.IfcSpfReader;


// https://groups.google.com/g/brickschema/c/vNLjax1ZwFc
// chrome-extension://efaidnbmnnnibpcajpcglclefindmkaj/https://eva.fing.edu.uy/pluginfile.php/358551/mod_resource/content/1/SWTechnologies-Capitulo4.pdf

public class CreateIFCAttributeOntology {
	OntModel m = ModelFactory.createOntologyModel();
	Set<String> attributes = new HashSet<>();	
	Set<String> simplified_attributes = new HashSet<>();

	public CreateIFCAttributeOntology() {
		LBD.addNameSpace(m);
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
					//m.createObjectProperty( PROPS.props_ns  + a );
					//m.createDatatypeProperty(PROPS.props_ns  + a );
			        Property p = m.createProperty(LBD.ns  + a );
			        m.add(p, RDF.type, RDF.Property);
			
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
		});
	}

	public static void main(String[] args) {
		new CreateIFCAttributeOntology();

	}

}
