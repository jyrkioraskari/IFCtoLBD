package org.linkedbuildingdata.ifc2lbd.core.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.InvRDFStep;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/*
 *  Copyright (c) 2020, 2021 Jyrki Oraskari (Jyrki.Oraskari@gmail.fi), Simon Steyskal, Pieter Pauwels 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public abstract class IfcOWLUtils {
	
	public static String getGUID(Resource r, IfcOWL ifcOWL) {
		StmtIterator i = r.listProperties(ifcOWL.getGuid());
		if (i.hasNext()) {
			Statement s = i.next();
			String guid = s.getObject().asResource().getProperty(IfcOWL.Express.getHasString()).getObject().asLiteral()
					.getLexicalForm();
			return guid;
		}
		return null;
	}
	
	public static String getURLEncodedName(Resource r, IfcOWL ifcOWL) {
		String name=getName(r,  ifcOWL);
		if(name==null)
			return name;
		name=name.replaceAll(" ", "_"); // Just readability
		return URLEncoder.encode(name, StandardCharsets.UTF_8);
	}
	
	public static String getName(Resource r, IfcOWL ifcOWL) {
		StmtIterator i = r.listProperties(ifcOWL.getName());
		if (i.hasNext()) {
			Statement s = i.next();
			String guid = s.getObject().asResource().getProperty(IfcOWL.Express.getHasString()).getObject().asLiteral()
					.getLexicalForm();
			return guid;
		}
		return null;
	}

	// Solution proposed by Simon Steyskal 2018
	private static RDFStep[] getNextLevelPath(IfcOWL ifcOWL) {
		if (ifcOWL.getIfcURI().toUpperCase().indexOf("IFC2X3") != -1) {  // fixed by JO 2020
			RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
					new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
			return path;
		} else {
			RDFStep[] path = { new InvRDFStep(ifcOWL.getProperty("relatingObject_IfcRelAggregates")),
					new RDFStep(ifcOWL.getProperty("relatedObjects_IfcRelAggregates")) };
			return path;
		}
	}
	

    public static Resource getIfcProject(IfcOWL ifcOWL, Model ifcowl_model) {
        RDFStep[] path = { new InvRDFStep(RDF.type) };
        List<RDFNode> list= RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcProject()), path);
        if(!list.isEmpty())
            return list.get(0).asResource();
        else
            return null;
    }


	public static List<RDFNode> listSites(IfcOWL ifcOWL, Model ifcowl_model) {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcSite()), path);
	}

	/**
	 * 
	 * @param site   Apache Jena Resource RDF node that refers to an #IfcSite ifcOWL
	 *               element
	 * @param ifcOWL The ifcOWL namespace element.
	 * @return The list of all #IfcBuilding ifcOWL elements under the site element
	 */
	public static List<RDFNode> listBuildings(Resource site, IfcOWL ifcOWL) {
		List<RDFNode> buildings = RDFUtils.pathQuery(site, getNextLevelPath(ifcOWL));
		if (buildings == null || buildings.size() == 0)
			System.err.println("No Buildings!");
		return buildings;
	}

	public static List<RDFNode>        listBuildings(IfcOWL ifcOWL, Model ifcowl_model) {
        RDFStep[] path = { new InvRDFStep(RDF.type) };
        return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcBuilding()), path);
    }
	
	
	/**
	 * 
	 * @param building Apache Jena Resource RDF node that refers to an #IfcBuilding
	 *                 ifcOWL element
	 * @param ifcOWL   The ifcOWL namespace element.
	 * @return The list of all #IfcBuildingStorey ifcOWL elements under the building
	 *         element
	 */

	public static List<RDFNode> listStoreys(Resource building, IfcOWL ifcOWL) {
		return RDFUtils.pathQuery(building, getNextLevelPath(ifcOWL));
	}

	/**
	 * @param storey Apache Jena Resource RDF node that refers to an
	 *               #IfcBuildingStorey ifcOWL element
	 * @param ifcOWL The ifcOWL namespace element.
	 * @return The list of all corresponding space ifcOWL elements under the storey
	 *         element
	 */
	public static List<RDFNode> listStoreySpaces(Resource storey, IfcOWL ifcOWL) {
		List<RDFNode> ret;

		ret = RDFUtils.pathQuery(storey, getNextLevelPath(ifcOWL));
		RDFStep[] path2 = { new RDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(RDFUtils.pathQuery(storey, path2));

		return ret;
	}

	/**
	 * 
	 * @param storey Apache Jena Resource RDF node that refers to an
	 *               #IfcBuildingStorey ifcOWL element
	 * @param ifcOWL The ifcOWL namespace element.
	 * @return The list of all containded elements under the storey element
	 */

	public static List<RDFNode> listContained_StoreyElements(Resource storey, IfcOWL ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingStructure_IfcRelContainedInSpatialStructure")),
				new RDFStep(ifcOWL.getProperty("relatedElements_IfcRelContainedInSpatialStructure")) };
		ret = RDFUtils.pathQuery(storey, path1);
		RDFStep[] path2 = { new RDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(RDFUtils.pathQuery(storey, path2));
		return ret;
	}

	/**
	 * 
	 * 
	 * @param space  Apache Jena Resource RDF node that refers to an ifcOWL space
	 *               element
	 * @param ifcOWL The ifcOWL namespace element.
	 * @return The list of all containded elements under the space
	 */

	public static List<RDFNode> listContained_SpaceElements(Resource space, IfcOWL ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingStructure_IfcRelContainedInSpatialStructure")),
				new RDFStep(ifcOWL.getProperty("relatedElements_IfcRelContainedInSpatialStructure")) };
		ret = RDFUtils.pathQuery(space, path1);
		return ret;
	}

	/**
	 * 
	 * 
	 * @param space  Apache Jena Resource RDF node that refers to an ifcOWL space
	 * @param ifcOWL The ifcOWL namespace element.
	 * @return The list of all containded elements under the space
	 */
	public static List<RDFNode> listAdjacent_SpaceElements(Resource space, IfcOWL ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingSpace_IfcRelSpaceBoundary")),
				new RDFStep(ifcOWL.getProperty("relatedBuildingElement_IfcRelSpaceBoundary")) };
		ret = RDFUtils.pathQuery(space, path1);
		return ret;
	}

	/**
	 * @param element Apache Jena Resource RDF node that refers to an ifcOWL element
	 * @param ifcOWL  The ifcOWL namespace element.
	 * @return The list of all hosted elements under the element
	 */
	public static List<RDFNode> listHosted_Elements(Resource element, IfcOWL ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingBuildingElement_IfcRelVoidsElement")),
				new RDFStep(ifcOWL.getProperty("relatedOpeningElement_IfcRelVoidsElement")),
				new InvRDFStep(ifcOWL.getProperty("relatingOpeningElement_IfcRelFillsElement")),
				new RDFStep(ifcOWL.getProperty("relatedBuildingElement_IfcRelFillsElement")) };
		ret = RDFUtils.pathQuery(element, path1);

		RDFStep[] path2 = { new InvRDFStep(ifcOWL.getProperty("relatingBuildingElement_IfcRelVoidsElement")),
				new RDFStep(ifcOWL.getProperty("relatedOpeningElement_IfcRelVoidsElement")),
				new RDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(RDFUtils.pathQuery(element, path2));

		return ret;
	}

	/**
	 * Returns list of all RDF nodes that match the RDF graoh pattern:
	 * 
	 * INVERSE(relatingObject_IfcRelDecomposes) -  relatedObjects_IfcRelDecomposes
	 * 
	 * @param element the starting RDF node
	 * @param ifcOWL  namespace
	 * @return The list of the matching elements
	 */
	
	public static List<RDFNode> listAggregated_Elements(Resource element, IfcOWL ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingObject_IfcRelDecomposes")),
				new RDFStep(ifcOWL.getProperty("relatedObjects_IfcRelDecomposes")) };
		ret = RDFUtils.pathQuery(element, path1);
		return ret;
	}

	/**
	 * Returns list of all RDF nodes that are of subclasses of IfcBuildingElement on
	 * the RDF graph.
	 * 
	 * @param ifcowl_model  jena model
	 * @return list of the elements
	 */
	
	public static List<Resource> listBuildingElements(Model ifcowl_model) {
		final List<Resource> ret = new ArrayList<>();
		ifcowl_model.listStatements().filterKeep(t1 -> t1.getPredicate().equals(RDF.type)).filterKeep(t2 -> {
			if (!t2.getObject().isResource())
				return false;
			Resource rsuper = getSuperClass(t2.getObject().asResource());
			if (rsuper == null)
				return false;
			return rsuper.getLocalName().equals("IfcBuildingElement");
		}).mapWith(t1 -> t1.getSubject()).forEachRemaining(s -> ret.add(s));
		;
		return ret;
	}

	/**
	 * Returns a super class of the RDF resource, if any
	 * 
	 * @param r The Apache Jena node resource
	 * @return The RDF redource node of the super class, null if none found.
	 */
	public static Resource getSuperClass(Resource r) {
		StmtIterator subclass_statement_iterator = r.listProperties(RDFS.subClassOf);
		while (subclass_statement_iterator.hasNext()) {
			Statement su = subclass_statement_iterator.next();
			Resource ifcowl_superclass = su.getObject().asResource();
			if (!ifcowl_superclass.isAnon())
				return ifcowl_superclass;
		}
		return null;
	}

	// Solution proposed by Simon Steyskal 2018
	private static RDFStep[] getPropertySetPath(IfcOWL ifcOWL) {
		if (ifcOWL.getIfcURI().toUpperCase().indexOf("IFC2X3") != -1) {  // fixed by JO 2020
			RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatedObjects_IfcRelDefines()),
					new RDFStep(ifcOWL.getRelatingPropertyDefinition_IfcRelDefinesByProperties()) };
			return path;
		} else {
			RDFStep[] path = { new InvRDFStep(ifcOWL.getProperty("relatedObjects_IfcRelDefinesByProperties")),
					new RDFStep(ifcOWL.getProperty("relatingPropertyDefinition_IfcRelDefinesByProperties")) };
			return path;
		}
	}

		private static RDFStep[] getIfcTypeObjectPropertySetPath(IfcOWL ifcOWL) {
				RDFStep[] path = { new RDFStep(ifcOWL.getProperty("ifc:relatingType_IfcRelDefinesByType")),
						new RDFStep(ifcOWL.getProperty("ifc:hasPropertySets_IfcTypeObject")) };
				return path;
		}

	
	   public static List<RDFNode> getProjectSIUnits(IfcOWL ifcOWL, Model ifcowl_model) {
	        RDFStep[] path = { new InvRDFStep(RDF.type) };
	        return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcSIUnit()), path);
	    }

	
	/**
	 * Returns list of all RDF nodes that match the RDF graoh pattern:
	 * 
	 *
	 * INVERSE (RelatedObjects_IfcRelDefines) -
	 * RelatingPropertyDefinition_IfcRelDefinesByProperties
	 * 
	 * @param resource the starting RDF node
	 * @param ifcOWL   namespace
	 * @return the list of the matching RDF nodes.
	 */
	   
	//2023 JO added the IFC object type definition
	public static List<RDFNode> listPropertysets(Resource resource, IfcOWL ifcOWL) {
		List<RDFNode> ret = new ArrayList<>();
		ret.addAll(RDFUtils.pathQuery(resource, getPropertySetPath(ifcOWL)));
		ret.addAll(RDFUtils.pathQuery(resource, getIfcTypeObjectPropertySetPath(ifcOWL)));
		return ret;
	}

	/**
	 * Returns list of all RDF nodes that are of type ifcOWL Ontology base URI +
	 * IfcPropertySet on the RDF graph.
	 * 
	 * @param ifcOWL    name space
	 * @param ifcowl_model jena model
	 * @return  the list of the matching RDF nodes.
	 */
	public static List<RDFNode> listPropertysets(IfcOWL ifcOWL, Model ifcowl_model) {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcPropertySet()), path);
	}

	public static Optional<String> getPredefinedData(RDFNode rn) {
		if (!rn.isResource())
			return Optional.empty();

		final StringBuilder sb = new StringBuilder();
		rn.asResource().listProperties().toList().stream()
				.filter(t -> t.getPredicate().getLocalName().startsWith("predefinedType_"))
				.map(t -> t.getObject().asResource().getLocalName()).forEach(o -> sb.append(o));
		if (sb.length() == 0)
			return Optional.empty();
		return Optional.of(sb.toString());
	}

	/**
	 * Reads in a Turtle formatted ontology file into a Jena RDF store
	 * 
	 * @param ifc_file The absolute path of the Turtle formatted ontology file
	 * @param model    Am Apache Jene model where the ontology triples are added.
	 */
	public static String readIfcOWLOntology(String ifc_file, Model model) {
		String exp = IfcOWLUtils.getExpressSchema(ifc_file); //TODO clean
		if(exp==null)
		    return null;
		InputStream in = null;
		try {
			in = IfcOWLUtils.class.getResourceAsStream("/" + exp + ".ttl");

			if (in == null)
				in = IfcOWLUtils.class.getResourceAsStream("/resources/" + exp + ".ttl");
			if (in == null)
				in = ClassLoader.getSystemResources("ifcOWL/"+exp + ".ttl").nextElement().openStream(); // the module (Java 9 ) version 
			if(in==null)
			{
			    return null; 
			}
			model.read(in, null, "TTL");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {  // https://openjdk.org/jeps/421
			try {
				in.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
		return exp;
	}

	/**
	 * 
	 * This is a direct copy from the IFCtoRDF https://github.com/pipauwel/IFCtoRDF
	 * 
	 * The idea is to make sure that we are using exactly the same ontology files
	 * that the IFCtoRDF is using for the associated Abox output.
	 * 
	 * @param ifcFile the absolute path (For example: c:\ifcfiles\ifc_file.ifc) for
	 *                 the IFC file
	 * @return the IFC Express chema of the IFC file.
	 */
	public static String getExpressSchema(String ifcFile) {
        try (FileInputStream fstream = new FileInputStream(ifcFile)) {
        	// Fix by JO 2024: finally is deprecated
            try (DataInputStream in = new DataInputStream(fstream);
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));){
                String strLine;
                while ((strLine = br.readLine()) != null) {
                    if (strLine.length() > 0) {
                        if (strLine.startsWith("FILE_SCHEMA")) {
                            if (strLine.indexOf("IFC2X3") != -1)
                                return "IFC2X3_TC1";
                            if (strLine.indexOf("IFC4x2") != -1)
                                return "IFC4x3_RC1";
                            if (strLine.indexOf("IFC4X2") != -1)
                                return "IFC4x3_RC1";
                            if (strLine.indexOf("IFC4x3") != -1)
                                return "IFC4x3_RC1";
                            if (strLine.indexOf("IFC4X3") != -1)
                                return "IFC4x3_RC1";
                            if (strLine.indexOf("IFC4x3_RC1") != -1)
                                return "IFC4x3_RC1";
                            if (strLine.indexOf("IFC4X3_RC1") != -1)
                                return "IFC4x3_RC1";
                            if (strLine.indexOf("IFC4X1") != -1)
                                return "IFC4x1";
                            if (strLine.indexOf("IFC4x1") != -1)   
                                return "IFC4x1";
                            if (strLine.indexOf("IFC4") != -1)     // Should do also IFC4X2
                                return "IFC4_ADD2";                //JO 2020  to enable IFCPOLYGONALFACESET that was found in an IFC4 model
                            else
                                return null;
                        }
                    }
                }
            } 
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
	
	
	
    static public File characterCoding(File whole_content_file) {
        File tempFile = null;
        int state = 0;
        try {
            tempFile = File.createTempFile("ifc", ".ttl");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                try (BufferedReader br = new BufferedReader(new FileReader(whole_content_file))) {
                    String line;
                    String[] triple = new String[3];
                    for (int i = 0; i < 3; i++)
                        triple[i] = "";
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!line.contains("@prefix") && !trimmed.startsWith("#")) {
                            int len = trimmed.length();
                            if (len > 0) {
                                List<String> t;
                                if (trimmed.endsWith(".") || trimmed.endsWith(";"))
                                    t = split(trimmed.substring(0, trimmed.length() - 1));
                                else
                                    t = split(trimmed.substring(0, trimmed.length()));
                                if (state == 0) {
                                    for (int i = 0; i < t.size(); i++)
                                        triple[i] = t.get(i);

                                    if (trimmed.endsWith("."))
                                        state = 0;
                                    else
                                        state = 1;
                                    if (t.size() == 3) {
                                        StringBuffer sb = new StringBuffer();
                                        sb.append(t.get(0));
                                        sb.append(" ");
                                        sb.append(t.get(1));
                                        sb.append(" ");
                                        sb.append(t.get(2));
                                        sb.append(" .");
                                        line = sb.toString();
                                    } else
                                        continue;
                                } else {
                                    for (int i = 0; i < t.size(); i++)
                                        triple[2 - i] = t.get(t.size() - 1 - i);

                                    StringBuffer sb = new StringBuffer();
                                    sb.append(triple[0]);
                                    sb.append(" ");
                                    sb.append(triple[1]);
                                    sb.append(" ");
                                    sb.append(triple[2]);
                                    sb.append(" .");
                                    line = sb.toString();

                                    if (trimmed.endsWith("."))
                                        state = 0;
                                }
                            }
                        }
                        line = new String(line.getBytes(), StandardCharsets.UTF_8);
                        line = line.replace("\\\\", "\\");

                        // UTF-8 fix for French double encoding
                        line = line.replace("\\X\\0D", "");
                        line = line.replace("\\X\\0A", "");

                        line = line.replace("\\X2\\00A0\\X0\\", "");
                        line = line.replace("\\X2\\00B0\\X0\\", "°");
                        // LATIN letters
                        line = line.replace("\\X2\\00C0\\X0\\", "À");
                        line = line.replace("\\X2\\00C1\\X0\\", "Á");
                        line = line.replace("\\X2\\00C2\\X0\\", "Â");
                        line = line.replace("\\X2\\00C3\\X0\\", "Ã");
                        line = line.replace("\\X2\\00C4\\X0\\", "Ä");
                        line = line.replace("\\X2\\00C5\\X0\\", "Å");
                        line = line.replace("\\X2\\00C6\\X0\\", "Æ");
                        line = line.replace("\\X2\\00C7\\X0\\", "Ç");
                        line = line.replace("\\X2\\00C8\\X0\\", "È");
                        line = line.replace("\\X2\\00C9\\X0\\", "É");
                        line = line.replace("\\X2\\00CA\\X0\\", "Ê");
                        line = line.replace("\\X2\\00CB\\X0\\", "Ë");
                        line = line.replace("\\X2\\00CC\\X0\\", "Ì");
                        line = line.replace("\\X2\\00CD\\X0\\", "Í");
                        line = line.replace("\\X2\\00CE\\X0\\", "Î");
                        line = line.replace("\\X2\\00CF\\X0\\", "Ï");

                        line = line.replace("\\X2\\00D0\\X0\\", "Ð");
                        line = line.replace("\\X2\\00D1\\X0\\", "Ñ");
                        line = line.replace("\\X2\\00D2\\X0\\", "Ò");
                        line = line.replace("\\X2\\00D3\\X0\\", "Ó");
                        line = line.replace("\\X2\\00D4\\X0\\", "Ô");
                        line = line.replace("\\X2\\00D5\\X0\\", "Õ");
                        line = line.replace("\\X2\\00D6\\X0\\", "Ö");
                        line = line.replace("\\X2\\00D7\\X0\\", "×");
                        line = line.replace("\\X2\\00D8\\X0\\", "Ø");
                        line = line.replace("\\X2\\00D9\\X0\\", "Ù");
                        line = line.replace("\\X2\\00DA\\X0\\", "Ú");
                        line = line.replace("\\X2\\00DB\\X0\\", "Û");
                        line = line.replace("\\X2\\00DC\\X0\\", "Ü");
                        line = line.replace("\\X2\\00DD\\X0\\", "Ý");
                        line = line.replace("\\X2\\00DE\\X0\\", "Þ");
                        line = line.replace("\\X2\\00DF\\X0\\", "ß");

                        line = line.replace("\\X2\\00E0\\X0\\", "à");
                        line = line.replace("\\X2\\00E1\\X0\\", "á");
                        line = line.replace("\\X2\\00E2\\X0\\", "â");
                        line = line.replace("\\X2\\00E3\\X0\\", "ã");
                        line = line.replace("\\X2\\00E4\\X0\\", "ä");
                        line = line.replace("\\X2\\00E5\\X0\\", "å");
                        line = line.replace("\\X2\\00E6\\X0\\", "æ");
                        line = line.replace("\\X2\\00E7\\X0\\", "ç");
                        line = line.replace("\\X2\\00E8\\X0\\", "è");
                        line = line.replace("\\X2\\00E9\\X0\\", "é");
                        line = line.replace("\\X2\\00EA\\X0\\", "ê");
                        line = line.replace("\\X2\\00EB\\X0\\", "ê");
                        line = line.replace("\\X2\\00EC\\X0\\", "ì");
                        line = line.replace("\\X2\\00ED\\X0\\", "í");
                        line = line.replace("\\X2\\00EE\\X0\\", "î");
                        line = line.replace("\\X2\\00EF\\X0\\", "ï");

                        line = line.replace("\\X2\\00F0\\X0\\", "ð");
                        line = line.replace("\\X2\\00F1\\X0\\", "ñ");
                        line = line.replace("\\X2\\00F2\\X0\\", "ò");
                        line = line.replace("\\X2\\00F3\\X0\\", "ó");
                        line = line.replace("\\X2\\00F4\\X0\\", "ô");
                        line = line.replace("\\X2\\00F5\\X0\\", "õ");
                        line = line.replace("\\X2\\00F6\\X0\\", "ö");
                        line = line.replace("\\X2\\00F7\\X0\\", "÷");
                        line = line.replace("\\X2\\00F8\\X0\\", "ø");
                        line = line.replace("\\X2\\00F9\\X0\\", "ù");
                        line = line.replace("\\X2\\00FA\\X0\\", "ú");
                        line = line.replace("\\X2\\00FB\\X0\\", "û");
                        line = line.replace("\\X2\\00FC\\X0\\", "ü");
                        line = line.replace("\\X2\\00FD\\X0\\", "ý");
                        line = line.replace("\\X2\\00FE\\X0\\", "þ");
                        line = line.replace("\\X2\\00FF\\X0\\", "ÿ");
                        
                        line=unIFCUnicode(line);  // multi-character decode
                        
                        writer.write(line.trim());
                        writer.newLine();
                    }
                    writer.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e2) {
            e2.printStackTrace();
        }
        return tempFile;
    }

    static public File filterIFC(File ifc_file) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("ifc", ".ifc");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                try (BufferedReader br = new BufferedReader(new FileReader(ifc_file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        
                        if (line.contains("= IFCCARTESIANPOINT("))
                            continue;

                        if (line.contains("= IFCPOLYLINE("))
                            continue;

                        if (line.contains("= IFCEDGECURVE("))
                            continue;

                        if (line.contains("= IFCAXIS2PLACEMENT3D("))
                            continue;

                        if (line.contains("= IFCPLANE("))
                            continue;

                        if (line.contains("= IFCFACEOUTERBOUND("))
                            continue;

                        if (line.contains("= IFCFACE("))
                            continue;

                        if (line.contains("= IFCORIENTEDEDGE("))
                            continue;

                        if (line.contains("= IFCCONNECTIONSURFACEGEOMETRY("))
                            continue;
                        
                        if (line.contains("= IFCSURFACEOFLINEAREXTRUSION("))
                            continue;
                        
                        if (line.contains("= IFCRELSPACEBOUNDARY("))
                            continue;
                        
                        if (line.contains("= IFCPOLYLOOP("))
                            continue;
                        
                        if (line.contains("= IFCLINE("))
                            continue;
                        
                        if (line.contains("= IFCTRIMMEDCURVE("))
                            continue;
                        
                        if (line.contains("= IFCVERTEXPOINT("))
                            continue;
                        
                        if (line.contains("= IFCEDGELOOP("))
                            continue;
                        
                        if (line.contains("= IFCADVANCEDFACE("))
                            continue;
                        
                        if (line.contains("= IFCSHAPEREPRESENTATION("))
                            continue;
                        
                        if (line.contains("= IFCEXTRUDEDAREASOLID("))
                            continue;
                        
                       
                        writer.write(line.trim());
                        writer.newLine();
                    }
                    writer.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e2) {
            e2.printStackTrace();
        }
        return tempFile;
    }
	
    static public File filterContent(File whole_content_file) {
        File tempFile = null;
        int state = 0;
        try {
            tempFile = File.createTempFile("ifc", ".ttl");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                try (BufferedReader br = new BufferedReader(new FileReader(whole_content_file))) {
                    String line;
                    String[] triple = new String[3];
                    for (int i = 0; i < 3; i++)
                        triple[i] = "";
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!line.contains("@prefix") && !trimmed.startsWith("#")) {
                            int len = trimmed.length();
                            if (len > 0) {
                                List<String> t;
                                if (trimmed.endsWith(".") || trimmed.endsWith(";"))
                                    t = split(trimmed.substring(0, trimmed.length() - 1));
                                else
                                    t = split(trimmed.substring(0, trimmed.length()));
                                if (state == 0) {
                                    for (int i = 0; i < t.size(); i++)
                                        triple[i] = t.get(i);

                                    if (trimmed.endsWith("."))
                                        state = 0;
                                    else
                                        state = 1;
                                    if (t.size() == 3) {
                                        StringBuffer sb = new StringBuffer();
                                        sb.append(t.get(0));
                                        sb.append(" ");
                                        sb.append(t.get(1));
                                        sb.append(" ");
                                        sb.append(t.get(2));
                                        sb.append(" .");
                                        line = sb.toString();
                                    } else
                                        continue;
                                } else {
                                    for (int i = 0; i < t.size(); i++)
                                        triple[2 - i] = t.get(t.size() - 1 - i);

                                    StringBuffer sb = new StringBuffer();
                                    sb.append(triple[0]);
                                    sb.append(" ");
                                    sb.append(triple[1]);
                                    sb.append(" ");
                                    sb.append(triple[2]);
                                    sb.append(" .");
                                    line = sb.toString();

                                    if (trimmed.endsWith("."))
                                        state = 0;
                                }
                            }
                        }
                        line = new String(line.getBytes(), StandardCharsets.UTF_8);
                        line = line.replace("\\\\", "\\");

                        // UTF-8 fix for French double encoding
                        line = line.replace("\\X\\0D", " ");
                        line = line.replace("\\X\\0A", "");

                        line = line.replace("\\X2\\00A0\\X0\\", " ");
                        line = line.replace("\\X2\\00B0\\X0\\", "°");
                        // LATIN letters
                        line = line.replace("\\X2\\00C0\\X0\\", "À");
                        line = line.replace("\\X2\\00C1\\X0\\", "Á");
                        line = line.replace("\\X2\\00C2\\X0\\", "Â");
                        line = line.replace("\\X2\\00C3\\X0\\", "Ã");
                        line = line.replace("\\X2\\00C4\\X0\\", "Ä");
                        line = line.replace("\\X2\\00C5\\X0\\", "Å");
                        line = line.replace("\\X2\\00C6\\X0\\", "Æ");
                        line = line.replace("\\X2\\00C7\\X0\\", "Ç");
                        line = line.replace("\\X2\\00C8\\X0\\", "È");
                        line = line.replace("\\X2\\00C9\\X0\\", "É");
                        line = line.replace("\\X2\\00CA\\X0\\", "Ê");
                        line = line.replace("\\X2\\00CB\\X0\\", "Ë");
                        line = line.replace("\\X2\\00CC\\X0\\", "Ì");
                        line = line.replace("\\X2\\00CD\\X0\\", "Í");
                        line = line.replace("\\X2\\00CE\\X0\\", "Î");
                        line = line.replace("\\X2\\00CF\\X0\\", "Ï");

                        line = line.replace("\\X2\\00D0\\X0\\", "Ð");
                        line = line.replace("\\X2\\00D1\\X0\\", "Ñ");
                        line = line.replace("\\X2\\00D2\\X0\\", "Ò");
                        line = line.replace("\\X2\\00D3\\X0\\", "Ó");
                        line = line.replace("\\X2\\00D4\\X0\\", "Ô");
                        line = line.replace("\\X2\\00D5\\X0\\", "Õ");
                        line = line.replace("\\X2\\00D6\\X0\\", "Ö");
                        line = line.replace("\\X2\\00D7\\X0\\", "×");
                        line = line.replace("\\X2\\00D8\\X0\\", "Ø");
                        line = line.replace("\\X2\\00D9\\X0\\", "Ù");
                        line = line.replace("\\X2\\00DA\\X0\\", "Ú");
                        line = line.replace("\\X2\\00DB\\X0\\", "Û");
                        line = line.replace("\\X2\\00DC\\X0\\", "Ü");
                        line = line.replace("\\X2\\00DD\\X0\\", "Ý");
                        line = line.replace("\\X2\\00DE\\X0\\", "Þ");
                        line = line.replace("\\X2\\00DF\\X0\\", "ß");

                        line = line.replace("\\X2\\00E0\\X0\\", "à");
                        line = line.replace("\\X2\\00E1\\X0\\", "á");
                        line = line.replace("\\X2\\00E2\\X0\\", "â");
                        line = line.replace("\\X2\\00E3\\X0\\", "ã");
                        line = line.replace("\\X2\\00E4\\X0\\", "ä");
                        line = line.replace("\\X2\\00E5\\X0\\", "å");
                        line = line.replace("\\X2\\00E6\\X0\\", "æ");
                        line = line.replace("\\X2\\00E7\\X0\\", "ç");
                        line = line.replace("\\X2\\00E8\\X0\\", "è");
                        line = line.replace("\\X2\\00E9\\X0\\", "é");
                        line = line.replace("\\X2\\00EA\\X0\\", "ê");
                        line = line.replace("\\X2\\00EB\\X0\\", "ê");
                        line = line.replace("\\X2\\00EC\\X0\\", "ì");
                        line = line.replace("\\X2\\00ED\\X0\\", "í");
                        line = line.replace("\\X2\\00EE\\X0\\", "î");
                        line = line.replace("\\X2\\00EF\\X0\\", "ï");

                        line = line.replace("\\X2\\00F0\\X0\\", "ð");
                        line = line.replace("\\X2\\00F1\\X0\\", "ñ");
                        line = line.replace("\\X2\\00F2\\X0\\", "ò");
                        line = line.replace("\\X2\\00F3\\X0\\", "ó");
                        line = line.replace("\\X2\\00F4\\X0\\", "ô");
                        line = line.replace("\\X2\\00F5\\X0\\", "õ");
                        line = line.replace("\\X2\\00F6\\X0\\", "ö");
                        line = line.replace("\\X2\\00F7\\X0\\", "÷");
                        line = line.replace("\\X2\\00F8\\X0\\", "ø");
                        line = line.replace("\\X2\\00F9\\X0\\", "ù");
                        line = line.replace("\\X2\\00FA\\X0\\", "ú");
                        line = line.replace("\\X2\\00FB\\X0\\", "û");
                        line = line.replace("\\X2\\00FC\\X0\\", "ü");
                        line = line.replace("\\X2\\00FD\\X0\\", "ý");
                        line = line.replace("\\X2\\00FE\\X0\\", "þ");
                        line = line.replace("\\X2\\00FF\\X0\\", "ÿ");
                        
                        
                        line=unIFCUnicode(line);  // multi-character decode
                        line = line.replace("\\", "\\\\");
                        line = line.replace("\\\\\"", "\\\"");

                        if (line.contains("inst:IfcFace"))
                            continue;
                        if (line.contains("inst:IfcPolyLoop"))
                            continue;
                        if (line.contains("inst:IfcCartesianPoint"))
                            continue;
                        if (line.contains("inst:IfcOwnerHistory"))
                            continue;
                        if (line.contains("inst:IfcRelAssociatesMaterial"))
                            continue;

                        if (line.contains("inst:IfcExtrudedAreaSolid"))
                            continue;
                        if (line.contains("inst:IfcCompositeCurve"))
                            continue;
                        if (line.contains("inst:IfcSurfaceStyleRendering"))
                            continue;
                        if (line.contains("inst:IfcStyledItem"))
                            continue;
                        if (line.contains("inst:IfcShapeRepresentation"))
                            continue;

                        writer.write(line.trim());
                        writer.newLine();
                    }
                    writer.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e2) {
            e2.printStackTrace();
        }
        return tempFile;
    }

    
	static private String unIFCUnicode(String txt)
	{
		StringBuilder sb = new StringBuilder();
		StringBuilder su4 = new StringBuilder();
		int state=0;
		for(char ch:txt.toCharArray())
		{
			switch(state)
			{
			case	0:
				    if(ch=='\\' )
				    	state=1;
				    else
				    	sb.append(ch);
					break;
			case	1:
			    if(ch=='X' || ch=='x')
			    	state=2;
			    else 
			    	state=0;
				break;
			case	2:
			    if(ch=='2' || ch=='4')
			    	state=3;
			    else 
			    	state=0;
				break;
			case	3:
				if(ch=='\\')
			    	state=4;
			    else 
			    	state=0;
				break;
				
			case	4:
			    if(ch=='\\')
			    	state=5;
			    else
			    {
			    	su4.append(ch);
			    	if(su4.length()>3)
			    	{
			    		sb.append("\\u");
			    		sb.append(su4);
			    		su4.setLength(0);
			    	}
			    }
				break;
			case	5:
			    if(ch=='\'')
			    {
			    	sb.append("'");
			    	state=0;
			    }
			    if(ch=='/'|| ch=='\\')
			    	state=0;			    
				break;
			}
		}
		return StringEscapeUtils.unescapeJava(sb.toString());
	}
    
    @SuppressWarnings("deprecation")
    private static List<String> split(String s) {
        List<String> ret = new ArrayList<>();
        int state = 0;
        StringBuffer sb = new StringBuffer();
        boolean esc=false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (state) {
                case 2:
                if(!esc)
                if (c == '\"' || c == '\'')
                    state = 0;
                sb.append(c);
                    break;
                case 1:
                if(!esc)
                if (c == '\"' || c == '\'') {
                    ret.add(sb.toString());
                    sb = new StringBuffer();
                    sb.append(c);
                    state = 2;
                } else if (!Character.isSpace(c)) {
                    ret.add(sb.toString());
                    sb = new StringBuffer();
                    sb.append(c);
                    state = 0;
                }
                    break;
                case 0:
                if(!esc)
                if (c == '\"' || c == '\'') {
                    sb.append(c);
                    state = 2;
                } else if (Character.isSpace(c))
                    state = 1;
                else
                    sb.append(c);
                    break;
            }
            if(c=='\\')
                esc=true;
            else 
                esc=false;
        }
        if (sb.length() > 0)
            ret.add(sb.toString());
        return ret;
    }


}
