package org.lbd.ifc2lbd.utils;

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
import org.lbd.ifc2lbd.ns.IfcOWLNameSpace;
import org.lbd.ifc2lbd.utils.rdfpath.InvRDFStep;
import org.lbd.ifc2lbd.utils.rdfpath.RDFStep;

/*
* The GNU Affero General Public License
* 
* Copyright (c) 2017, 2018, 2019 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
* 
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
* 
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

public class IfcOWLUtils {
	public static String getGUID(Resource r,IfcOWLNameSpace ifcOWL) {
		StmtIterator i = r.listProperties(ifcOWL.getGuid());
		if (i.hasNext()) {
			Statement s = i.next();
			String guid = s.getObject().asResource().getProperty(ifcOWL.getHasString()).getObject().asLiteral()
					.getLexicalForm();
			return guid;
		}
		return null;
	}

	public static List<RDFNode> listSites(IfcOWLNameSpace ifcOWL,Model ifcowl_model) {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return RDFUtils.pathQuery(ifcowl_model.getResource(ifcOWL.getIfcSite()), path);
	}

	
	/**
	 * 
	 * <font color="green">
	 * <b>Site – building (bot:hasBuilding)</b>
     * (inst:IfcBuilding_xx)&lt;-[ifcowl:relatedObjects_IfcRelDecomposes]-(inst:IfcRelAggregates_xx)-[ifcowl:relatingObject_IfcRelDecomposes]-&gt;(inst:IfcSite_xx)
	 * </font>
	 * @param site     Apache Jena Resource RDF node that refers to an #IfcSite ifcOWL element 
	 * @param ifcOWL   The ifcOWL namespace element. 
	 * @return The list of all #IfcBuilding ifcOWL elements under the site element
	 */
	public static  List<RDFNode> listBuildings(Resource site, IfcOWLNameSpace ifcOWL) {
		RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
				new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		return RDFUtils.pathQuery(site, path);
	}
	
	/**
	 * 
	 * <font color="green">
	 * <b>Building – storeys (bot:hasStorey)</b>
	 * (inst:IfcBuildingStorey_xx)&lt;-[ifcowl:relatedObjects_IfcRelDecomposes]-(inst:IfcRelAggregates_xx)-[ifcowl:relatingObjects_IfcRelDecomposes]-&gt;(inst:IfcBuilding_xx)
	 * </font>
	 * @param building     Apache Jena Resource RDF node that refers to an #IfcBuilding ifcOWL element 
	 * @param ifcOWL   The ifcOWL namespace element. 
	 * @return The list of all #IfcBuildingStorey ifcOWL elements under the building element
	 */

	public static  List<RDFNode> listStoreys(Resource building, IfcOWLNameSpace ifcOWL) {
		RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
				new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		return RDFUtils.pathQuery(building, path);
	}

	
	/**
	 * 
	 * <font color="green">
	 * <b>Storeys – spaces (bot:hasSpace)</b>
	 * (inst:IfcSpace_xx)&lt;-[ifcowl:relatedObjects_IfcRelDecomposes]-(inst:IfcRelAggregates_xx)-[ifcowl:relatingObjects_IfcRelDecomposes]-&gt;(inst:IfcBuildingStorey_xx)
	 * <P>
	 * <b>OR</b>
	 * <P>
	 * (inst:IfcSpace_xx)-[ifcowl:objectPlacement_IfcProduct]-&gt;(inst:IfcLocalPlacement_xx)-[ifcowl:placementRelTo_IfcLocalPlacement]-&gt;(inst:IfcLocalPlacement_yy)&lt;-[ifcowl: objectPlacement_IfcProduct]-(inst:IfcBuildingStorey_xx)
	 * 
	 * </font>
	 * @param storey     Apache Jena Resource RDF node that refers to an #IfcBuildingStorey ifcOWL element 
	 * @param ifcOWL   The ifcOWL namespace element. 
	 * @return The list of all corresponding space ifcOWL elements under the storey element
	 */
	public static  List<RDFNode> listStoreySpaces(Resource storey,IfcOWLNameSpace ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
				new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		ret = RDFUtils.pathQuery(storey, path1);
		RDFStep[] path2 = { new RDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(RDFUtils.pathQuery(storey, path2));

		return ret;
	}
	
	/**
	 * 
	 * <font color="green">
	 * <b>Storeys – elements (bot:containsElement)</b>
	 * (inst:IfcDoor_xx)&lt;-[ifcowl:relatedElements_IfcRelContainedInSpatialStructure]-(inst:IfcRelContainedInSpatialStructure_xx)-[ifcowl:relatingStructure_IfcRelContainedInSpatialStructure]-&gt;(inst:IfcBuildingStorey_xx)
	 *
	 * <P>
	 * <b>OR</b>
	 * <P>
	 * 
	 * (inst:IfcDoor_xx)-[ifcowl:objectPlacement_IfcProduct]-&gt;(inst:IfcLocalPlacement_xx)-[ifcowl:placementRelTo_IfcLocalPlacement]-&gt;(inst:IfcLocalPlacement_yy)&lt;-[ifcowl: objectPlacement_IfcProduct]-(inst:IfcBuildingStorey_xx)
	 * 
	 * </font>
	 * @param storey     Apache Jena Resource RDF node that refers to an #IfcBuildingStorey ifcOWL element 
	 * @param ifcOWL   The ifcOWL namespace element. 
	 * @return The list of all containded elements under the storey element
	 */

	public static  List<RDFNode> listContained_StoreyElements(Resource storey,IfcOWLNameSpace ifcOWL) {
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

	public static  List<RDFNode> listContained_SpaceElements(Resource space,IfcOWLNameSpace ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingStructure_IfcRelContainedInSpatialStructure")),
				new RDFStep(ifcOWL.getProperty("relatedElements_IfcRelContainedInSpatialStructure")) };
		ret = RDFUtils.pathQuery(space, path1);
		return ret;
	}

	public static  List<RDFNode> listAdjacent_SpaceElements(Resource space,IfcOWLNameSpace ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingSpace_IfcRelSpaceBoundary")),
				new RDFStep(ifcOWL.getProperty("relatedBuildingElement_IfcRelSpaceBoundary")) };
		ret = RDFUtils.pathQuery(space, path1);
		return ret;
	}

	/**
	 * 
	 * <font color="green">
	 * <b>Element – element (bot:hostsElement)</b>
	 * (inst:IfcDoor_xx)&lt;-[ifcowl:relatedBuildingElement_IfcRelFillsElement]-(inst:IfcRelFillsElement_xx)-[ifcowl:relatingOpeningElement_IfcRelFillsElement]-&gt;(inst:IfcOpeningElement_xx)&lt;-[ifcowl:relatedOpeningElement_IfcRelVoidsElement]-(inst:IfcRelVoidsElement_xx)-[ifcowl:relatingBuildingElement_IfcRelVoidsElement]-&gt;(inst:IfcWallStandardCase_xx)
	 * 
	 * <P>
	 * <b>OR</b>
	 * <P>
	 * 
	 * (inst:IfcDoor_xx)-[ifcowl:objectPlacement_IfcProduct]-&gt;(inst:IfcLocalPlacement_xx)-[ifcowl:placementRelTo_IfcLocalPlacement]-&gt;(inst:IfcLocalPlacement_yy)&lt;-[ifcowl:objectPlacement_IfcProduct]-(inst:IfcOpeningElement_xx)&lt;-[ifcowl:relatedOpeningElement_IfcRelVoidsElement]-(inst:IfcRelVoidsElement_xx)-[ifcowl:relatingBuildingElement_IfcRelVoidsElement]-&gt;(inst:IfcWallStandardCase_xx)
	 * 
	 * </font>
	 * @param element     Apache Jena Resource RDF node that refers to an ifcOWL element 
	 * @param ifcOWL   The ifcOWL namespace element. 
	 * @return The list of all hosted elements under the element
	 */
	public static  List<RDFNode> listHosted_Elements(Resource element,IfcOWLNameSpace ifcOWL) {
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
	 * INVERSE(relatingObject_IfcRelDecomposes)->
	 *    relatedObjects_IfcRelDecomposes
	 *  
	 * @param element  the starting RDF node
	 * @return		   The list of the matching elements
	 */
	public static  List<RDFNode> listAggregated_Elements(Resource element,IfcOWLNameSpace ifcOWL) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingObject_IfcRelDecomposes")),
				new RDFStep(ifcOWL.getProperty("relatedObjects_IfcRelDecomposes")) };
		ret = RDFUtils.pathQuery(element, path1);
		return ret;
	}

	/**
	 * Returns list of all RDF nodes that are of subclasses of IfcBuildingElement
	 *  on the RDF graph.
	 *  
	 * @return list of the elements
	 */
	public static  List<Resource> listBuildingElements(Model ifcowl_model) {
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
	 *  Returns a super class of the RDF resource, if any
	 *  
	 * @param r  The Apache Jena node resource
	 * @return   The RDF redource node of the super class, null if none found.
	 */
	public static  Resource getSuperClass(Resource r) {
		StmtIterator subclass_statement_iterator = r.listProperties(RDFS.subClassOf);
		while (subclass_statement_iterator.hasNext()) {
			Statement su = subclass_statement_iterator.next();
			Resource ifcowl_superclass = su.getObject().asResource();
			if (!ifcowl_superclass.isAnon())
				return ifcowl_superclass;
		}
		return null;
	}

	
	

	/**
	 * Returns list of all RDF nodes that match the RDF graoh pattern:
	 * 
     *
	 * INVERSE (RelatedObjects_IfcRelDefines) ->
	 *   RelatingPropertyDefinition_IfcRelDefinesByProperties
	 *  
	 *  on the RDF graph.
	 *  
	 * @param resource the starting RDF node 
	 * @return the list of the matching RDF nodes.
	 */
	public static  List<RDFNode> listPropertysets(Resource resource,IfcOWLNameSpace ifcOWL) {
		RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatedObjects_IfcRelDefines()),
				new RDFStep(ifcOWL.getRelatingPropertyDefinition_IfcRelDefinesByProperties()) };
		return RDFUtils.pathQuery(resource, path);
	}

	
	
	/**
	 * Returns list of all RDF nodes that are of type 
	 * ifcOWL Ontology base URI + IfcPropertySet on the RDF graph.
	 * @return the list of the matching RDF nodes.
	 */
	public static  List<RDFNode> listPropertysets(IfcOWLNameSpace ifcOWL,Model ifcowl_model) {
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


}
