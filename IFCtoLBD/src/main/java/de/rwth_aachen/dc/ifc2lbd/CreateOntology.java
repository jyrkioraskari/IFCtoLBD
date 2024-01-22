package de.rwth_aachen.dc.ifc2lbd;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.core.utils.StringOperations;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.PropertySet;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

public class CreateOntology {

    public CreateOntology() {
        try {
            String propsns = "http://lbd.arch.rwth-aachen.de/props#";
            OntModel ontology_model = ModelFactory.createOntologyModel();
            File ifc_file = new File("c:\\ifc\\Duplex_A_20110505.ifc");

            IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 3);

            // final Set<Property> properties = new HashSet<>();
            @SuppressWarnings("unused")
            Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());

            final Map<String, RDFNode> mapBSDD = new HashMap<>();
            final Map<String, String> mapBSDD_description = new HashMap<>();
            
            @SuppressWarnings("unused")
            Collection<PropertySet> propertysets = c1nb.getPropertysets().values();
            Model converter_ontology = c1nb.getOntology_model();
            
            converter_ontology.listStatements().forEachRemaining(s0 -> {
                if (s0.getPredicate().equals(PROPS.namePset))
                {
                    String pname=StringOperations.toCamelCase(s0.getObject().asLiteral().getLexicalForm());
                    mapBSDD.put(pname, s0.getSubject());
                    s0.getSubject().listProperties(RDFS.comment).forEachRemaining(s1->mapBSDD_description.put(pname, s1.getObject().asLiteral().getLexicalForm()));
                }
            });

            ObjectProperty p_generalproperty = ontology_model.createObjectProperty(propsns + "property");

            for (String pname : mapBSDD.keySet()) {
                ObjectProperty ppredicate = ontology_model.createObjectProperty(propsns + pname);
                ObjectProperty ppredicate_simple = ontology_model.createObjectProperty(propsns + pname + "_simple");
                ppredicate.setSuperProperty(p_generalproperty);
                RDFNode bsdd = mapBSDD.get(pname);
                ppredicate.addIsDefinedBy(bsdd.asResource());
                ppredicate_simple.addIsDefinedBy(bsdd.asResource());
                String comment= mapBSDD_description.get(pname);
                if(comment!=null)
                {
                    ppredicate.addComment(comment,"EN");
                    ppredicate_simple.addComment(comment,"EN");
                }
                ppredicate.addDomain(BOT.element);
                ppredicate_simple.addDomain(BOT.element);
                
                ppredicate.addRange(OPM.property);
            }

            ontology_model.write(System.out, "TTL");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @SuppressWarnings("unused")
	public static void main(String[] args) {
        new CreateOntology();
    }
}
