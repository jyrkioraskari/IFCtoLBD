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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.InvRDFStep;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWLNameSpace;

/*
 *  Copyright (c) 2020 Jyrki Oraskari (Jyrki.Oraskari@gmail.fi), Simon Steyskal, Pieter Pauwels 
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
	public static String getGUID(Resource r, IfcOWLNameSpace ifcOWL) {
		StmtIterator i = r.listProperties(ifcOWL.getGuid());
		if (i.hasNext()) {
			Statement s = i.next();
			String guid = s.getObject().asResource().getProperty(IfcOWLNameSpace.getHasString()).getObject().asLiteral()
					.getLexicalForm();
			return guid;
		}
		return null;
	}

	// Solution proposed by Simon Steyskal 2018
	private static RDFStep[] getNextLevelPath(IfcOWLNameSpace ifcOWL) {
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

	public static List<RDFNode> listSites(IfcOWLNameSpace ifcOWL, Model ifcowl_model) {
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
	public static List<RDFNode> listBuildings(Resource site, IfcOWLNameSpace ifcOWL) {
		System.out.println("Site: "+site.toString());
		List<RDFNode> buildings = RDFUtils.pathQuery(site, getNextLevelPath(ifcOWL));
		if (buildings == null || buildings.size() == 0)
			System.err.println("No Buildings!");
		return buildings;
	}

	/**
	 * 
	 * @param building Apache Jena Resource RDF node that refers to an #IfcBuilding
	 *                 ifcOWL element
	 * @param ifcOWL   The ifcOWL namespace element.
	 * @return The list of all #IfcBuildingStorey ifcOWL elements under the building
	 *         element
	 */

	public static List<RDFNode> listStoreys(Resource building, IfcOWLNameSpace ifcOWL) {
		return RDFUtils.pathQuery(building, getNextLevelPath(ifcOWL));
	}

	/**
	 * @param storey Apache Jena Resource RDF node that refers to an
	 *               #IfcBuildingStorey ifcOWL element
	 * @param ifcOWL The ifcOWL namespace element.
	 * @return The list of all corresponding space ifcOWL elements under the storey
	 *         element
	 */
	public static List<RDFNode> listStoreySpaces(Resource storey, IfcOWLNameSpace ifcOWL) {
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

	public static List<RDFNode> listContained_StoreyElements(Resource storey, IfcOWLNameSpace ifcOWL) {
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

	public static List<RDFNode> listContained_SpaceElements(Resource space, IfcOWLNameSpace ifcOWL) {
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
	public static List<RDFNode> listAdjacent_SpaceElements(Resource space, IfcOWLNameSpace ifcOWL) {
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
	public static List<RDFNode> listHosted_Elements(Resource element, IfcOWLNameSpace ifcOWL) {
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
	
	public static List<RDFNode> listAggregated_Elements(Resource element, IfcOWLNameSpace ifcOWL) {
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
	private static RDFStep[] getPropertySetPath(IfcOWLNameSpace ifcOWL) {
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

	   public static List<RDFNode> getProjectSIUnits(IfcOWLNameSpace ifcOWL, Model ifcowl_model) {
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
	public static List<RDFNode> listPropertysets(Resource resource, IfcOWLNameSpace ifcOWL) {
		return RDFUtils.pathQuery(resource, getPropertySetPath(ifcOWL));
	}

	/**
	 * Returns list of all RDF nodes that are of type ifcOWL Ontology base URI +
	 * IfcPropertySet on the RDF graph.
	 * 
	 * @param ifcOWL    name space
	 * @param ifcowl_model jena model
	 * @return  the list of the matching RDF nodes.
	 */
	public static List<RDFNode> listPropertysets(IfcOWLNameSpace ifcOWL, Model ifcowl_model) {
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
	public static void readIfcOWLOntology(String ifc_file, Model model) {
		String exp = IfcOWLUtils.getExpressSchema(ifc_file);
		InputStream in = null;
		try {
			in = IfcOWLUtils.class.getResourceAsStream("/" + exp + ".ttl");

			if (in == null)
				in = IfcOWLUtils.class.getResourceAsStream("/resources/" + exp + ".ttl");
			model.read(in, null, "TTL");
		} finally {
			try {
				in.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
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
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            try {
                String strLine;
                while ((strLine = br.readLine()) != null) {
                    if (strLine.length() > 0) {
                        if (strLine.startsWith("FILE_SCHEMA")) {
                            if (strLine.indexOf("IFC2X3") != -1)
                                return "IFC2X3_TC1";
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
                            if (strLine.indexOf("IFC4") != -1)
                                return "IFC4_ADD1";
                            else
                                return null;
                        }
                    }
                }
            } finally {
                br.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static File filterContent(File whole_content_file) {
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
                        line= new String(line.getBytes(), StandardCharsets.UTF_8);
                        
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

    @SuppressWarnings("deprecation")
    private static List<String> split(String s) {
        List<String> ret = new ArrayList<>();
        int state = 0;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (state) {
                case 2:
                if (c == '\"' || c == '\'')
                    state = 0;
                sb.append(c);
                    break;
                case 1:
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
                if (c == '\"' || c == '\'') {
                    sb.append(c);
                    state = 2;
                } else if (Character.isSpace(c))
                    state = 1;
                else
                    sb.append(c);
                    break;
            }
        }
        if (sb.length() > 0)
            ret.add(sb.toString());
        return ret;
    }

}
