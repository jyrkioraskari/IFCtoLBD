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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 *  Copyright (c) 2020, 2021, 2025 Jyrki Oraskari (Jyrki.Oraskari@gmail.fi), Simon Steyskal, Pieter Pauwels 
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
			return s.getObject().asResource().getProperty(IfcOWL.Express.getHasString()).getObject().asLiteral()
					.getLexicalForm();
		}
		return null;
	}

	public static String getURLEncodedName(Resource r, IfcOWL ifcOWL) {
		String name = getName(r, ifcOWL);
		if (name == null)
			return name;
		name = name.replaceAll(" ", "_"); // Just readability
		return URLEncoder.encode(name, StandardCharsets.UTF_8);
	}

	public static String getName(Resource r, IfcOWL ifcOWL) {
		StmtIterator i = r.listProperties(ifcOWL.getName());
		if (i.hasNext()) {
			Statement s = i.next();
			return s.getObject().asResource().getProperty(IfcOWL.Express.getHasString()).getObject().asLiteral()
					.getLexicalForm();
		}
		return null;
	}

	// Solution proposed by Simon Steyskal 2018
	private static RDFStep[] getNextLevelPath(IfcOWL ifcOWL) {
		if (ifcOWL.getIfcURI().toUpperCase().contains("IFC2X3")) { // fixed by JO 2020
			return new RDFStep[] { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
					new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		}
		return new RDFStep[] { new InvRDFStep(ifcOWL.getProperty("relatingObject_IfcRelAggregates")),
				new RDFStep(ifcOWL.getProperty("relatedObjects_IfcRelAggregates")) };
	}

	public static Resource getIfcProject(IfcOWL ifcOWL, Model ifcowl_model) {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		List<RDFNode> list = RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcProject()), path);
		if (!list.isEmpty())
			return list.get(0).asResource();
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
		if (buildings == null || buildings.isEmpty())
			System.err.println("No Buildings!");
		return buildings;
	}

	public static List<RDFNode> listBuildings(IfcOWL ifcOWL, Model ifcowl_model) {
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
	 * INVERSE(relatingObject_IfcRelDecomposes) - relatedObjects_IfcRelDecomposes
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
	 * @param ifcowl_model jena model
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
		}).mapWith(Statement::getSubject).forEachRemaining(ret::add);
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
		if (ifcOWL.getIfcURI().toUpperCase().contains("IFC2X3")) { // fixed by JO 2020
			return new RDFStep[] { new InvRDFStep(ifcOWL.getRelatedObjects_IfcRelDefines()),
					new RDFStep(ifcOWL.getRelatingPropertyDefinition_IfcRelDefinesByProperties()) };
		}
		return new RDFStep[] { new InvRDFStep(ifcOWL.getProperty("relatedObjects_IfcRelDefinesByProperties")),
				new RDFStep(ifcOWL.getProperty("relatingPropertyDefinition_IfcRelDefinesByProperties")) };
	}

	private static RDFStep[] getIfcTypeObjectPropertySetPath(IfcOWL ifcOWL) {
		return new RDFStep[] { new RDFStep(ifcOWL.getProperty("ifc:relatingType_IfcRelDefinesByType")),
				new RDFStep(ifcOWL.getProperty("ifc:hasPropertySets_IfcTypeObject")) };
	}

	public static List<RDFNode> getProjectSIUnits(IfcOWL ifcOWL, Model ifcowl_model) {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcSIUnit()), path);
	}

	/**
	 * Returns list of all RDF nodes that match the RDF graoh pattern: INVERSE
	 * (RelatedObjects_IfcRelDefines) -
	 * RelatingPropertyDefinition_IfcRelDefinesByProperties
	 * 
	 * @param resource the starting RDF node
	 * @param ifcOWL   namespace
	 * @return the list of the matching RDF nodes.
	 */

	// 2023 JO added the IFC object type definition
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
	 * @param ifcOWL       name space
	 * @param ifcowl_model jena model
	 * @return the list of the matching RDF nodes.
	 */
	public static List<RDFNode> listPropertysets(IfcOWL ifcOWL, Model ifcowl_model) {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcPropertySet()), path);
	}

	public static List<RDFNode> listQuantitySets(IfcOWL ifcOWL, Model ifcowl_model) {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcElementQuantity()), path);
	}

	public static Optional<String> getPredefinedData(RDFNode rn) {
		if (!rn.isResource())
			return Optional.empty();

		final StringBuilder sb = new StringBuilder();
		rn.asResource().listProperties().toList().stream()
				.filter(t -> t.getPredicate().getLocalName().startsWith("predefinedType_"))
				.map(t -> t.getObject().asResource().getLocalName()).forEach(sb::append);
		if (sb.isEmpty())
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
		String exp = IfcOWLUtils.getExpressSchema(ifc_file); // TODO clean
		if (exp == null)
			return;
		InputStream in = null;
		try {
			in = IfcOWLUtils.class.getResourceAsStream("/" + exp + ".ttl");

			if (in == null)
				in = IfcOWLUtils.class.getResourceAsStream("/resources/" + exp + ".ttl");
			if (in == null)
				in = ClassLoader.getSystemResources("ifcOWL/" + exp + ".ttl").nextElement().openStream(); // the module
																											// (Java 9 )
																											// version
			if (in == null) {
				return;
			}
			model.read(in, null, "TTL");
		} catch (IOException e) {
			e.printStackTrace();
		} finally { // https://openjdk.org/jeps/421
			try {
				if (in != null) // JO 2024
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
	 *                the IFC file
	 * @return the IFC Express chema of the IFC file.
	 */
	
	
	public static String getExpressSchema(String ifcFile) {
	    Map<String, String> schemaMapping = Map.of(
	        "IFC2X3", "IFC2X3_TC1",
	        "IFC4x2", "IFC4x3_RC1",
	        "IFC4X2", "IFC4x3_RC1",
	        "IFC4x3", "IFC4x3_RC1",
	        "IFC4X3", "IFC4x3_RC1",
	        "IFC4x3_RC1", "IFC4x3_RC1",
	        "IFC4X3_RC1", "IFC4x3_RC1",
	        "IFC4X1", "IFC4x1",
	        "IFC4x1", "IFC4x1",
	        "IFC4", "IFC4_ADD2"    // Should do also IFC4X2, //JO 2020  to enable IFCPOLYGONALFACESET that was found in an IFC4 model
	    );

	    try (BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(ifcFile))))) {
	        String strLine;
	        while ((strLine = br.readLine()) != null) {
	            if (!strLine.isEmpty() && strLine.startsWith("FILE_SCHEMA")) {
	                for (Map.Entry<String, String> entry : schemaMapping.entrySet()) {
	                    if (strLine.contains(entry.getKey())) {
	                        return entry.getValue();
	                    }
	                }
	                return "";
	            }
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    return "";
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
									t = split(trimmed);
								if (state == 0) {
									for (int i = 0; i < t.size(); i++)
										triple[i] = t.get(i);

									if (trimmed.endsWith("."))
										state = 0;
									else
										state = 1;
									if (t.size() == 3) {
										line = t.get(0) + " " + t.get(1) + " " + t.get(2) + " .";
									} else
										continue;
								} else {
									for (int i = 0; i < t.size(); i++)
										triple[2 - i] = t.get(t.size() - 1 - i);

									line = triple[0] + " " + triple[1] + " " + triple[2] + " .";

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
						
						// For Scandinavian letters
						line = line.replace("\\X\\C5", "Å");
						line = line.replace("\\X\\C4", "Ä");
						line = line.replace("\\X\\D6", "Ö");
						line = line.replace("\\X\\E5", "å");
						line = line.replace("\\X\\E4", "ä");
						line = line.replace("\\X\\F6", "ö");

						// For Norwegian and Danish letters
						line = line.replace("\\X\\C6", "Æ");
						line = line.replace("\\X\\D8", "Ø");
						line = line.replace("\\X\\E6", "æ");
						line = line.replace("\\X\\F8", "ø");

						// For French letters
						line = line.replace("\\X\\C0", "À");
						line = line.replace("\\X\\C7", "Ç");
						line = line.replace("\\X\\C8", "È");
						line = line.replace("\\X\\C9", "É");
						line = line.replace("\\X\\CA", "Ê");
						line = line.replace("\\X\\CB", "Ë");
						line = line.replace("\\X\\CC", "Ì");
						line = line.replace("\\X\\CE", "Î");
						line = line.replace("\\X\\CF", "Ï");
						line = line.replace("\\X\\D4", "Ô");
						line = line.replace("\\X\\D9", "Ù");
						line = line.replace("\\X\\DB", "Û");
						line = line.replace("\\X\\E0", "à");
						line = line.replace("\\X\\E7", "ç");
						line = line.replace("\\X\\E8", "è");
						line = line.replace("\\X\\E9", "é");
						line = line.replace("\\X\\EA", "ê");
						line = line.replace("\\X\\EB", "ë");
						line = line.replace("\\X\\EC", "ì");
						line = line.replace("\\X\\EE", "î");
						line = line.replace("\\X\\EF", "ï");
						line = line.replace("\\X\\F4", "ô");
						line = line.replace("\\X\\F9", "ù");
						line = line.replace("\\X\\FB", "û");
						line = line.replace("\\X\\FC", "ü");

						
						
						line = line.replace("\\X2\\00A0\\X0\\", "");
						line = line.replace("\\X2\\00B0\\X0\\", "Â°");
						// LATIN letters
						line = line.replace("\\X2\\00C0\\X0\\", "Ã€");
						line = line.replace("\\X2\\00C1\\X0\\", "Ã�");
						line = line.replace("\\X2\\00C2\\X0\\", "Ã‚");
						line = line.replace("\\X2\\00C3\\X0\\", "Ãƒ");
						line = line.replace("\\X2\\00C4\\X0\\", "Ã„");
						line = line.replace("\\X2\\00C5\\X0\\", "Ã…");
						line = line.replace("\\X2\\00C6\\X0\\", "Ã†");
						line = line.replace("\\X2\\00C7\\X0\\", "Ã‡");
						line = line.replace("\\X2\\00C8\\X0\\", "Ãˆ");
						line = line.replace("\\X2\\00C9\\X0\\", "Ã‰");
						line = line.replace("\\X2\\00CA\\X0\\", "ÃŠ");
						line = line.replace("\\X2\\00CB\\X0\\", "Ã‹");
						line = line.replace("\\X2\\00CC\\X0\\", "ÃŒ");
						line = line.replace("\\X2\\00CD\\X0\\", "Ã�");
						line = line.replace("\\X2\\00CE\\X0\\", "ÃŽ");
						line = line.replace("\\X2\\00CF\\X0\\", "Ã�");

						line = line.replace("\\X2\\00D0\\X0\\", "Ã�");
						line = line.replace("\\X2\\00D1\\X0\\", "Ã‘");
						line = line.replace("\\X2\\00D2\\X0\\", "Ã’");
						line = line.replace("\\X2\\00D3\\X0\\", "Ã“");
						line = line.replace("\\X2\\00D4\\X0\\", "Ã”");
						line = line.replace("\\X2\\00D5\\X0\\", "Ã•");
						line = line.replace("\\X2\\00D6\\X0\\", "Ã–");
						line = line.replace("\\X2\\00D7\\X0\\", "Ã—");
						line = line.replace("\\X2\\00D8\\X0\\", "Ã˜");
						line = line.replace("\\X2\\00D9\\X0\\", "Ã™");
						line = line.replace("\\X2\\00DA\\X0\\", "Ãš");
						line = line.replace("\\X2\\00DB\\X0\\", "Ã›");
						line = line.replace("\\X2\\00DC\\X0\\", "Ãœ");
						line = line.replace("\\X2\\00DD\\X0\\", "Ã�");
						line = line.replace("\\X2\\00DE\\X0\\", "Ãž");
						line = line.replace("\\X2\\00DF\\X0\\", "ÃŸ");

						line = line.replace("\\X2\\00E0\\X0\\", "Ã ");
						line = line.replace("\\X2\\00E1\\X0\\", "Ã¡");
						line = line.replace("\\X2\\00E2\\X0\\", "Ã¢");
						line = line.replace("\\X2\\00E3\\X0\\", "Ã£");
						line = line.replace("\\X2\\00E4\\X0\\", "Ã¤");
						line = line.replace("\\X2\\00E5\\X0\\", "Ã¥");
						line = line.replace("\\X2\\00E6\\X0\\", "Ã¦");
						line = line.replace("\\X2\\00E7\\X0\\", "Ã§");
						line = line.replace("\\X2\\00E8\\X0\\", "Ã¨");
						line = line.replace("\\X2\\00E9\\X0\\", "Ã©");
						line = line.replace("\\X2\\00EA\\X0\\", "Ãª");
						line = line.replace("\\X2\\00EB\\X0\\", "Ãª");
						line = line.replace("\\X2\\00EC\\X0\\", "Ã¬");
						line = line.replace("\\X2\\00ED\\X0\\", "Ã­");
						line = line.replace("\\X2\\00EE\\X0\\", "Ã®");
						line = line.replace("\\X2\\00EF\\X0\\", "Ã¯");

						line = line.replace("\\X2\\00F0\\X0\\", "Ã°");
						line = line.replace("\\X2\\00F1\\X0\\", "Ã±");
						line = line.replace("\\X2\\00F2\\X0\\", "Ã²");
						line = line.replace("\\X2\\00F3\\X0\\", "Ã³");
						line = line.replace("\\X2\\00F4\\X0\\", "Ã´");
						line = line.replace("\\X2\\00F5\\X0\\", "Ãµ");
						line = line.replace("\\X2\\00F6\\X0\\", "Ã¶");
						line = line.replace("\\X2\\00F7\\X0\\", "Ã·");
						line = line.replace("\\X2\\00F8\\X0\\", "Ã¸");
						line = line.replace("\\X2\\00F9\\X0\\", "Ã¹");
						line = line.replace("\\X2\\00FA\\X0\\", "Ãº");
						line = line.replace("\\X2\\00FB\\X0\\", "Ã»");
						line = line.replace("\\X2\\00FC\\X0\\", "Ã¼");
						line = line.replace("\\X2\\00FD\\X0\\", "Ã½");
						line = line.replace("\\X2\\00FE\\X0\\", "Ã¾");
						line = line.replace("\\X2\\00FF\\X0\\", "Ã¿");

						line = StringOperations.unIFCUnicode(line); // multi-character decode
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
	    Set<String> keywords = new HashSet<>(Set.of(
	        "= IFCCARTESIANPOINT(", "= IFCPOLYLINE(", "= IFCEDGECURVE(", "= IFCAXIS2PLACEMENT3D(",
	        "= IFCPLANE(", "= IFCFACEOUTERBOUND(", "= IFCFACE(", "= IFCORIENTEDEDGE(",
	        "= IFCCONNECTIONSURFACEGEOMETRY(", "= IFCSURFACEOFLINEAREXTRUSION(", "= IFCRELSPACEBOUNDARY(",
	        "= IFCPOLYLOOP(", "= IFCLINE(", "= IFCTRIMMEDCURVE(", "= IFCVERTEXPOINT(",
	        "= IFCEDGELOOP(", "= IFCADVANCEDFACE(", "= IFCSHAPEREPRESENTATION(", "= IFCEXTRUDEDAREASOLID("
	    ));
	    
	    try {
	        tempFile = File.createTempFile("ifc", ".ifc");
	        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
	             BufferedReader br = new BufferedReader(new FileReader(ifc_file))) {
	            String line;
	            while ((line = br.readLine()) != null) {
	                boolean skip = keywords.stream().anyMatch(line::contains);
	                if (!skip) {
	                    writer.write(line.trim());
	                    writer.newLine();
	                }
	            }
	            writer.flush();
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
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
									t = split(trimmed);
								if (state == 0) {
									for (int i = 0; i < t.size(); i++)
										triple[i] = t.get(i);

									if (trimmed.endsWith("."))
										state = 0;
									else
										state = 1;
									if (t.size() == 3) {
										String sb = t.get(0) + " " + t.get(1) + " " + t.get(2) + " .";
										line = sb;
									} else
										continue;
								} else {
									for (int i = 0; i < t.size(); i++)
										triple[2 - i] = t.get(t.size() - 1 - i);

									StringBuilder sb = new StringBuilder();
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
						
						System.out.println("Character coding...........................................................");
						
						// For Scandinavian letters
						line = line.replace("\\X\\C5", "Å");
						line = line.replace("\\X\\C4", "Ä");
						line = line.replace("\\X\\D6", "Ö");
						line = line.replace("\\X\\E5", "å");
						line = line.replace("\\X\\E4", "ä");
						line = line.replace("\\X\\F6", "ö");

						// For Norwegian and Danish letters
						line = line.replace("\\X\\C6", "Æ");
						line = line.replace("\\X\\D8", "Ø");
						line = line.replace("\\X\\E6", "æ");
						line = line.replace("\\X\\F8", "ø");

						// For French letters
						line = line.replace("\\X\\C0", "À");
						line = line.replace("\\X\\C7", "Ç");
						line = line.replace("\\X\\C8", "È");
						line = line.replace("\\X\\C9", "É");
						line = line.replace("\\X\\CA", "Ê");
						line = line.replace("\\X\\CB", "Ë");
						line = line.replace("\\X\\CC", "Ì");
						line = line.replace("\\X\\CE", "Î");
						line = line.replace("\\X\\CF", "Ï");
						line = line.replace("\\X\\D4", "Ô");
						line = line.replace("\\X\\D9", "Ù");
						line = line.replace("\\X\\DB", "Û");
						line = line.replace("\\X\\E0", "à");
						line = line.replace("\\X\\E7", "ç");
						line = line.replace("\\X\\E8", "è");
						line = line.replace("\\X\\E9", "é");
						line = line.replace("\\X\\EA", "ê");
						line = line.replace("\\X\\EB", "ë");
						line = line.replace("\\X\\EC", "ì");
						line = line.replace("\\X\\EE", "î");
						line = line.replace("\\X\\EF", "ï");
						line = line.replace("\\X\\F4", "ô");
						line = line.replace("\\X\\F9", "ù");
						line = line.replace("\\X\\FB", "û");
						line = line.replace("\\X\\FC", "ü");


						line = line.replace("\\X2\\00A0\\X0\\", " ");
						line = line.replace("\\X2\\00B0\\X0\\", "Â°");
						// LATIN letters
						line = line.replace("\\X2\\00C0\\X0\\", "Ã€");
						line = line.replace("\\X2\\00C1\\X0\\", "Ã�");
						line = line.replace("\\X2\\00C2\\X0\\", "Ã‚");
						line = line.replace("\\X2\\00C3\\X0\\", "Ãƒ");
						line = line.replace("\\X2\\00C4\\X0\\", "Ã„");
						line = line.replace("\\X2\\00C5\\X0\\", "Ã…");
						line = line.replace("\\X2\\00C6\\X0\\", "Ã†");
						line = line.replace("\\X2\\00C7\\X0\\", "Ã‡");
						line = line.replace("\\X2\\00C8\\X0\\", "Ãˆ");
						line = line.replace("\\X2\\00C9\\X0\\", "Ã‰");
						line = line.replace("\\X2\\00CA\\X0\\", "ÃŠ");
						line = line.replace("\\X2\\00CB\\X0\\", "Ã‹");
						line = line.replace("\\X2\\00CC\\X0\\", "ÃŒ");
						line = line.replace("\\X2\\00CD\\X0\\", "Ã�");
						line = line.replace("\\X2\\00CE\\X0\\", "ÃŽ");
						line = line.replace("\\X2\\00CF\\X0\\", "Ã�");

						line = line.replace("\\X2\\00D0\\X0\\", "Ã�");
						line = line.replace("\\X2\\00D1\\X0\\", "Ã‘");
						line = line.replace("\\X2\\00D2\\X0\\", "Ã’");
						line = line.replace("\\X2\\00D3\\X0\\", "Ã“");
						line = line.replace("\\X2\\00D4\\X0\\", "Ã”");
						line = line.replace("\\X2\\00D5\\X0\\", "Ã•");
						line = line.replace("\\X2\\00D6\\X0\\", "Ã–");
						line = line.replace("\\X2\\00D7\\X0\\", "Ã—");
						line = line.replace("\\X2\\00D8\\X0\\", "Ã˜");
						line = line.replace("\\X2\\00D9\\X0\\", "Ã™");
						line = line.replace("\\X2\\00DA\\X0\\", "Ãš");
						line = line.replace("\\X2\\00DB\\X0\\", "Ã›");
						line = line.replace("\\X2\\00DC\\X0\\", "Ãœ");
						line = line.replace("\\X2\\00DD\\X0\\", "Ã�");
						line = line.replace("\\X2\\00DE\\X0\\", "Ãž");
						line = line.replace("\\X2\\00DF\\X0\\", "ÃŸ");

						line = line.replace("\\X2\\00E0\\X0\\", "Ã ");
						line = line.replace("\\X2\\00E1\\X0\\", "Ã¡");
						line = line.replace("\\X2\\00E2\\X0\\", "Ã¢");
						line = line.replace("\\X2\\00E3\\X0\\", "Ã£");
						line = line.replace("\\X2\\00E4\\X0\\", "Ã¤");
						line = line.replace("\\X2\\00E5\\X0\\", "Ã¥");
						line = line.replace("\\X2\\00E6\\X0\\", "Ã¦");
						line = line.replace("\\X2\\00E7\\X0\\", "Ã§");
						line = line.replace("\\X2\\00E8\\X0\\", "Ã¨");
						line = line.replace("\\X2\\00E9\\X0\\", "Ã©");
						line = line.replace("\\X2\\00EA\\X0\\", "Ãª");
						line = line.replace("\\X2\\00EB\\X0\\", "Ãª");
						line = line.replace("\\X2\\00EC\\X0\\", "Ã¬");
						line = line.replace("\\X2\\00ED\\X0\\", "Ã­");
						line = line.replace("\\X2\\00EE\\X0\\", "Ã®");
						line = line.replace("\\X2\\00EF\\X0\\", "Ã¯");

						line = line.replace("\\X2\\00F0\\X0\\", "Ã°");
						line = line.replace("\\X2\\00F1\\X0\\", "Ã±");
						line = line.replace("\\X2\\00F2\\X0\\", "Ã²");
						line = line.replace("\\X2\\00F3\\X0\\", "Ã³");
						line = line.replace("\\X2\\00F4\\X0\\", "Ã´");
						line = line.replace("\\X2\\00F5\\X0\\", "Ãµ");
						line = line.replace("\\X2\\00F6\\X0\\", "Ã¶");
						line = line.replace("\\X2\\00F7\\X0\\", "Ã·");
						line = line.replace("\\X2\\00F8\\X0\\", "Ã¸");
						line = line.replace("\\X2\\00F9\\X0\\", "Ã¹");
						line = line.replace("\\X2\\00FA\\X0\\", "Ãº");
						line = line.replace("\\X2\\00FB\\X0\\", "Ã»");
						line = line.replace("\\X2\\00FC\\X0\\", "Ã¼");
						line = line.replace("\\X2\\00FD\\X0\\", "Ã½");
						line = line.replace("\\X2\\00FE\\X0\\", "Ã¾");
						line = line.replace("\\X2\\00FF\\X0\\", "Ã¿");

						line = StringOperations.unIFCUnicode(line); // multi-character decode
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

	


	@SuppressWarnings("deprecation")
	private static List<String> split(String s) {
		List<String> ret = new ArrayList<>();
		int state = 0;
		StringBuilder sb = new StringBuilder();
		boolean esc = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (state) {
			default:
			case 2:
				if (!esc)
					if (c == '\"' || c == '\'')
						state = 0;
				sb.append(c);
				break;
			case 1:
				if (!esc)
					if (c == '\"' || c == '\'') {
						ret.add(sb.toString());
						sb = new StringBuilder();
						sb.append(c);
						state = 2;
					} else if (!Character.isSpace(c)) {
						ret.add(sb.toString());
						sb = new StringBuilder();
						sb.append(c);
						state = 0;
					}
				break;
			case 0:
				if (!esc)
					if (c == '\"' || c == '\'') {
						sb.append(c);
						state = 2;
					} else if (Character.isSpace(c))
						state = 1;
					else
						sb.append(c);
				break;
			}
			if (c == '\\')
				esc = true;
			else
				esc = false;
		}
		if (sb.length() > 0)
			ret.add(sb.toString());
		return ret;
	}

}
