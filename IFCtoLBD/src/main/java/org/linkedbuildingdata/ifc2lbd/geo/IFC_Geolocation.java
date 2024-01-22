package org.linkedbuildingdata.ifc2lbd.geo;


import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ext.com.google.common.collect.Lists;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
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


    private static Property ifcSiteProperty;
    private static List<String> latitude = new ArrayList<>();
    private static List<String> longitude = new ArrayList<>();
    
    private final String ns1;
    private final String ns3;

    

    public IFC_Geolocation(String ns1) {	
        
        this.ns1=ns1;
        this.ns3 = "https://w3id.org/list#";

        
    }

    public String addGeolocation(Model model)
    {
        returnLongLat(model);
        String s = addWKTGeometryToModel();  // JO 2024
        return s;
    }

    //Author Kris McGlinn - This function takes the Model and a resources, and adds it to that resourse in the model
    //For wkt literal, a seperate class WktLiteral java is required, to add the literal datatype to the Model
    private static String addWKTGeometryToModel()
    {
    
        latitude = longLatNegativeConvert(latitude);
        latitude.set(latitude.size()-1, (latitude.get(latitude.size()-1)+"."));
        String s1 = StringUtils.join(Lists.reverse(latitude), "");
        longitude = longLatNegativeConvert(longitude);
        longitude.set(longitude.size()-1, (longitude.get(longitude.size()-1)+"."));
        String s2 = StringUtils.join(Lists.reverse(longitude), "");
        //Have to switch long and lat for WKT
        String wkt_point = "POINT ("+s2+" "+s1+")";

        return wkt_point;
    }
    
    
    //Author Kris McGlinn - This method changes the sign of the longitude or latitude values in a List
    private static List<String> longLatNegativeConvert(List<String> l)
    {
        String s = l.get(l.size()-1); 
        int x = Integer.parseInt(s);
        if(x<0)
        {           
            for(int i = 0; i <l.size()-1; i++)
            {
                s = l.get(i);  
                l.set(i, s.substring(1));
                
            }
        }        
        return l;
    }
    
    //Author Kris McGlinn - This method traverses the RDF express list and recursively adds latitude and longitude values to a Java list
    private Statement traverseList(Model original, Statement stmt, boolean lat)
    {
        
        Model m = ModelFactory.createDefaultModel().add(original);
        Property listHasContents = m.createProperty( ns3 + "hasContents" );
        Property listHasNext = m.createProperty( ns3 + "hasNext" );
        boolean moreInList = false;
        String s[];

                  
        StmtIterator iter = m.listStatements( stmt.getObject().asResource(), null, (RDFNode) null );
        
        while ( iter.hasNext() ) 
        {
            Statement stmt1 = iter.nextStatement();

            if(stmt1.getPredicate().equals(listHasContents))
            {
                StmtIterator iter2 = m.listStatements( stmt1.getObject().asResource(), null, (RDFNode) null );
                while ( iter2.hasNext() ) 
                    {
                        Statement stmt2 = iter2.nextStatement();

                        if(stmt2.getObject().isLiteral())
                        {
                            if(lat)
                            {
                                s = stmt2.getObject().toString().split("\\^\\^http");                               
                                latitude.add(s[0]);
                                
                            }
                            else {
                                s = stmt2.getObject().toString().split("\\^\\^http");
                                longitude.add(s[0]);

                            }
                        }

                        
                    }
            }
            else if(stmt1.getPredicate().equals(listHasNext))
            {
                moreInList = true;
                traverseList(original, stmt1, lat);
            }

        }
        
        if(!moreInList)
        {
            stmt = null;
            return stmt;
        }
        
        return stmt;
    }
    
    //Author Kris McGlinn - This method returns the longitude and latitude from the ifc_owl model by making use of traverseList()
    private Model returnLongLat(Model original){
        
        Model m = ModelFactory.createDefaultModel().add(original);
        
                
        Property refLatitude_IfcSite = m.createProperty( ns1 + "#refLatitude_IfcSite" );
        Property refLongitude_IfcSite = m.createProperty( ns1 + "#refLongitude_IfcSite" );
        
        StmtIterator iter = m.listStatements( null, RDF.type, ifcSiteProperty );

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

  
        iter = m.listStatements( null, RDF.type, ifcSiteProperty );

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
