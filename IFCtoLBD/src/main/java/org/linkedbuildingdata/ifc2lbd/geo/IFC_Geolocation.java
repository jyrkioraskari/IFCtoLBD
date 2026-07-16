package org.linkedbuildingdata.ifc2lbd.geo;


import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

/*
 * Copyright 2017 Kris McGlinn, Adapt Centre, Trinity College University, Dublin, Ireland 
 * This code builds upon code developed by Pieter Pauwels for deleting geoemtry from IFC models, 
 * called SimpleBIM - https://github.com/pipauwel/IFCtoSimpleBIM/blob/master/src/main/java/be/ugent/Cleaner.java
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

public class IFC_Geolocation {	
	
    String inputfile = "";
    String outputfile = "";


    private final Resource ifcSiteResource;
    private final List<String> latitude = new ArrayList<>();
    private final List<String> longitude = new ArrayList<>();
    
    private final String ns1;
    private final String ns3;

    

    public IFC_Geolocation(String ns1) {	
        
        this.ns1=normalizeNamespace(ns1);
        this.ns3 = "https://w3id.org/list#";
        this.ifcSiteResource = ResourceFactory.createResource(this.ns1 + "IfcSite");

        
    }

    public String addGeolocation(Model model)
    {
        latitude.clear();
        longitude.clear();
        returnLongLat(model);
        String s = addWKTGeometryToModel();  // JO 2024
        return s;
    }

    //Author Kris McGlinn - This function takes the Model and a resources, and adds it to that resourse in the model
    //For wkt literal, a seperate class WktLiteral java is required, to add the literal datatype to the Model
    private String addWKTGeometryToModel()
    {
        if (latitude.isEmpty() || longitude.isEmpty())
            throw new IllegalStateException("IFC site geolocation latitude or longitude is missing.");

        String s1 = compoundPlaneAngleToDecimalDegrees(latitude);
        String s2 = compoundPlaneAngleToDecimalDegrees(longitude);
        //Have to switch long and lat for WKT
        String wkt_point = "POINT ("+s2+" "+s1+")";

        return wkt_point;
    }
    
    
    private static String normalizeNamespace(String ns) {
        if (ns.endsWith("#") || ns.endsWith("/"))
            return ns;
        return ns + "#";
    }

    private static String compoundPlaneAngleToDecimalDegrees(List<String> components) {
        double degrees = 0.0;
        boolean negative = false;

        for (int i = 0; i < components.size(); i++) {
            double value = Double.parseDouble(components.get(i));
            if (value < 0)
                negative = true;

            double absoluteValue = Math.abs(value);
            if (i == 0)
                degrees += absoluteValue;
            else if (i == 1)
                degrees += absoluteValue / 60.0;
            else if (i == 2)
                degrees += absoluteValue / 3600.0;
            else if (i == 3)
                degrees += absoluteValue / 3600000000.0;
        }

        if (negative)
            degrees = -degrees;

        return Double.toString(degrees);
    }

    //Author Kris McGlinn - This method traverses the RDF express list and recursively adds latitude and longitude values to a Java list
    private Statement traverseList(Model original, Statement stmt, boolean lat)
    {
        
        Model m = ModelFactory.createDefaultModel().add(original);
        Property listHasContents = m.createProperty( ns3 + "hasContents" );
        Property listHasNext = m.createProperty( ns3 + "hasNext" );
        Resource listNode = stmt.getObject().asResource();

        StmtIterator contents = m.listStatements(listNode, listHasContents, (RDFNode) null);
        while (contents.hasNext()) {
            Statement content = contents.nextStatement();
            StmtIterator values = m.listStatements(content.getObject().asResource(), null, (RDFNode) null);
            while (values.hasNext()) {
                Statement value = values.nextStatement();
                if (value.getObject().isLiteral()) {
                    if (lat)
                        latitude.add(value.getLiteral().getLexicalForm());
                    else
                        longitude.add(value.getLiteral().getLexicalForm());
                }
            }
        }

        StmtIterator nextNodes = m.listStatements(listNode, listHasNext, (RDFNode) null);
        while (nextNodes.hasNext()) {
            traverseList(original, nextNodes.nextStatement(), lat);
        }
        
        return stmt;
    }
    
    //Author Kris McGlinn - This method returns the longitude and latitude from the ifc_owl model by making use of traverseList()
    private Model returnLongLat(Model original){
        
        Model m = ModelFactory.createDefaultModel().add(original);
        
                
        Property refLatitude_IfcSite = m.createProperty( ns1 + "refLatitude_IfcSite" );
        Property refLongitude_IfcSite = m.createProperty( ns1 + "refLongitude_IfcSite" );
        
        StmtIterator iter = m.listStatements( null, RDF.type, ifcSiteResource );

        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();
            StmtIterator iter2 = m.listStatements( stmt.getSubject(), refLatitude_IfcSite, (RDFNode) null );
            stmt.getSubject();
            while ( iter2.hasNext() ) 
            {
                
                stmt = iter2.nextStatement();    
                traverseList(m, stmt, true);
                
            }
        }

  
        iter = m.listStatements( null, RDF.type, ifcSiteResource );

        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();

            StmtIterator iter2 = m.listStatements( stmt.getSubject(), refLongitude_IfcSite, (RDFNode) null );
            while ( iter2.hasNext() ) 
            {
                
                stmt = iter2.nextStatement();    
                traverseList(m, stmt, false);
                
            }
        }

        return original;
    }
    

        

}
