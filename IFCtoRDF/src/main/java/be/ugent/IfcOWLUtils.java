package be.ugent;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.ni.rdf.Namespace;

/*
 * Copyright 2016, 2022, 2023 Pieter Pauwels, Ghent University; Jyrki Oraskari, Aalto University/ RWTH Aachen; Lewis John McGibbney, Apache
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License atf
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class IfcOWLUtils {
    private static final Logger LOG = LoggerFactory.getLogger(IfcOWLUtils.class);

    public static  String filterExtras(String txt) {
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < txt.length(); n++) {
            char ch = txt.charAt(n);
            switch (ch) {
                case '\'':
                    break;
                case '=':
                    break;
                default:
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static  String filterPoints(String txt) {
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < txt.length(); n++) {
            char ch = txt.charAt(n);
            switch (ch) {
                case '.':
                    break;
                default:
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static OntResource getListContentType(OntClass range,OntModel ontModel,String ontNS) {
        String resourceURI = range.asClass().getURI();
        
        if ((Namespace.EXPRESS + "STRING_List").equalsIgnoreCase(resourceURI) || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "STRING_List")))
            return ontModel.getOntResource(Namespace.EXPRESS + "STRING");
        else if ((Namespace.EXPRESS + "REAL_List").equalsIgnoreCase(resourceURI) || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "REAL_List")))
            return ontModel.getOntResource(Namespace.EXPRESS + "REAL");
        else if ((Namespace.EXPRESS + "INTEGER_List").equalsIgnoreCase(resourceURI) || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "INTEGER_List")))
            return ontModel.getOntResource(Namespace.EXPRESS + "INTEGER");
        else if ((Namespace.EXPRESS + "BINARY_List").equalsIgnoreCase(resourceURI) || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "BINARY_List")))
            return ontModel.getOntResource(Namespace.EXPRESS + "BINARY");
        else if ((Namespace.EXPRESS + "BOOLEAN_List").equalsIgnoreCase(resourceURI) || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "BOOLEAN_List")))
            return ontModel.getOntResource(Namespace.EXPRESS + "BOOLEAN");
        else if ((Namespace.EXPRESS + "LOGICAL_List").equalsIgnoreCase(resourceURI) || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "LOGICAL_List")))
            return ontModel.getOntResource(Namespace.EXPRESS + "LOGICAL");
        else if ((Namespace.EXPRESS + "NUMBER_List").equalsIgnoreCase(resourceURI) || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "NUMBER_List")))
            return ontModel.getOntResource(Namespace.EXPRESS + "NUMBER");
        else if (range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
            String listvaluepropURI = ontNS + range.getLocalName().substring(0, range.getLocalName().length() - 5);
            return ontModel.getOntResource(listvaluepropURI);
        } else {
            LOG.warn("*WARNING 29*: did not find listcontenttype for : {}", range.getLocalName());
            return null;
        }
    }

    public static String getXSDTypeFromRange(OntResource range,OntModel ontModel) {
        if (range.asClass().getURI().equalsIgnoreCase(Namespace.EXPRESS + "STRING") || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "STRING")))
            return "string";
        else if (range.asClass().getURI().equalsIgnoreCase(Namespace.EXPRESS + "REAL") || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "REAL")))
            return "double";
        else if (range.asClass().getURI().equalsIgnoreCase(Namespace.EXPRESS + "INTEGER") || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "INTEGER")))
            return "integer";
        else if (range.asClass().getURI().equalsIgnoreCase(Namespace.EXPRESS + "BINARY") || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "BINARY")))
            return "hexBinary";
        else if (range.asClass().getURI().equalsIgnoreCase(Namespace.EXPRESS + "BOOLEAN") || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "BOOLEAN")))
            return "boolean";
        else if (range.asClass().getURI().equalsIgnoreCase(Namespace.EXPRESS + "LOGICAL") || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "LOGICAL")))
            return "logical";
        else if (range.asClass().getURI().equalsIgnoreCase(Namespace.EXPRESS + "NUMBER") || range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "NUMBER")))
            return "double";
        else
            return null;
    }

    public static String getXSDTypeFromRangeExpensiveMethod(OntResource range,OntModel ontModel) {
        ExtendedIterator<OntClass> iter = range.asClass().listSuperClasses();
        while (iter.hasNext()) {
            OntClass super_class = iter.next();
            if (!super_class.isAnon()) {
                String type = getXSDTypeFromRange(super_class,ontModel);
                if (type != null)
                    return type;
            }
        }
        return null;
    }

    

}
