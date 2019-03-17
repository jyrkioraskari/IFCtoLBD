
package org.lbd.ifc2lbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.buildingsmart.tech.ifcowl.IfcSpfReader;
import org.lbd.ifc2lbd.messages.SystemStatusEvent;
import org.lbd.ifc2lbd.ns.IfcOWLNameSpace;
import org.lbd.ifc2lbd.ns.LBD_NS;
import org.lbd.ifc2lbd.ns.OPM;
import org.lbd.ifc2lbd.rdfpath.InvRDFStep;
import org.lbd.ifc2lbd.rdfpath.RDFStep;

import com.google.common.eventbus.EventBus;
import com.openifctools.guidcompressor.GuidCompressor;

import nl.tue.ddss.convert.Header;
import nl.tue.ddss.convert.HeaderParser;
import nl.tue.ddss.convert.IfcVersion;
import nl.tue.ddss.convert.IfcVersionException;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.lbd.ifc2lbd.geo.IFC_Geolocation;
import org.lbd.ifc2lbd.geo.WktLiteral;

/*
* The GNU Affero General Public License
* 
* Copyright (c) 2017, 2018 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

public class IFCtoLBDConverter {
	private final EventBus eventBus = EventBusService.getEventBus();
	private Model ifcowl_model;
	public static Model ontology_model = null;
	private Map<String, List<Resource>> ifcowl_product_map = new HashMap<>();
	private final String uriBase;

	private Optional<String> ontURI = Optional.empty();
	private IfcOWLNameSpace ifcOWL;

	// URI-property set
	private Map<String, PropertySet> propertysets = new HashMap<>();
	private final int props_level;
	private final boolean hasBuildingElements;
	private final boolean hasBuildingProperties;
	private final boolean hasPropertiesBlankNodes;
	private final boolean hasGeolocation;

	private final Model lbd_general_output_model;
	private final Model lbd_product_output_model;
	private final Model lbd_property_output_model;
	
	public IFCtoLBDConverter(String ifc_filename, String uriBase, String target_file, int props_level,
			boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,
			boolean hasSeparatePropertiesModel, boolean hasPropertiesBlankNodes, boolean hasGeolocation) {
		this.props_level = props_level;
		this.hasBuildingElements = hasBuildingElements;
		this.hasBuildingProperties = hasBuildingProperties;
		this.hasPropertiesBlankNodes = hasPropertiesBlankNodes;
		this.hasGeolocation = hasGeolocation;

		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = uriBase;
		ontology_model = ModelFactory.createDefaultModel();
		eventBus.post(new SystemStatusEvent("IFCtoRDF conversion"));
		ifcowl_model = readAndConvertIFC(ifc_filename, uriBase); // Before: readInOntologies(ifc_filename);

		eventBus.post(new SystemStatusEvent("Reading in ontologies"));

		readInOntologies(ifc_filename);
		createIfcBOTMapping();

		this.lbd_general_output_model = ModelFactory.createDefaultModel();
		this.lbd_product_output_model = ModelFactory.createDefaultModel();
		this.lbd_property_output_model = ModelFactory.createDefaultModel();

		LBD_NS.BOT.addNameSpace(lbd_general_output_model);
		if (hasBuildingElements)
			LBD_NS.Product.addNameSpace(lbd_product_output_model);
		if (hasBuildingProperties) {
			if (props_level == 1)
				LBD_NS.PROPS_NS.addNameSpace(lbd_property_output_model);
			else {
				LBD_NS.PROPS_NS.addNameSpace(lbd_property_output_model);
				lbd_property_output_model.setNsPrefix("prov", OPM.prov_ns);
			}
			if (props_level == 2)
				OPM.addNameSpacesL2(lbd_property_output_model);
			if (props_level == 3)
				OPM.addNameSpacesL3(lbd_property_output_model);
		}
		Model[] ms = { lbd_general_output_model, lbd_product_output_model, lbd_property_output_model };
		for (Model model : ms) {
			model.setNsPrefix("rdf", RDF.uri);
			model.setNsPrefix("rdfs", RDFS.uri);
			model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
			model.setNsPrefix("inst", uriBase);
			model.setNsPrefix("geo", "http://www.opengis.net/ont/geosparql#");
		}

		eventBus.post(new SystemStatusEvent("IFC->LBD"));
		if (this.ontURI.isPresent())
			ifcOWL = new IfcOWLNameSpace(this.ontURI.get());
		else {
			System.out.println("No ifcOWL ontology available.");
			eventBus.post(new SystemStatusEvent("No ifcOWL ontology available."));
			return;
		}

		if (hasBuildingProperties) {
			listPropertysets().stream().map(rn -> rn.asResource()).forEach(propertyset -> {
				
				RDFStep[] pname_path = { new RDFStep(ifcOWL.getName_IfcRoot()), new RDFStep(ifcOWL.getHasString()) };
				
				if(pathQuery(propertyset, pname_path).get(0).isLiteral() && pathQuery(propertyset, pname_path).get(0).asLiteral().getString().startsWith("Pset")){
					//System.out.println("included PSET : " + propertyset.asResource().getLocalName());	
					String psetName = pathQuery(propertyset, pname_path).get(0).asLiteral().getString();
					System.out.println("included PSET : " + pathQuery(propertyset, pname_path).get(0).asLiteral().getString());
					
//					RDFStep[] pname_path = { new RDFStep(ifcOWL.getName_IfcRoot()), new RDFStep(ifcOWL.getHasString()) };
					final List<RDFNode> propertyset_name = new ArrayList<>();
					pathQuery(propertyset, pname_path).forEach(name -> propertyset_name.add(name));	
										
					RDFStep[] path = { new RDFStep(ifcOWL.getHasProperties_IfcPropertySet()) };
					pathQuery(propertyset, path).forEach(propertySingleValue -> {
							
						RDFStep[] name_path = { new RDFStep(ifcOWL.getName_IfcProperty()),
								new RDFStep(ifcOWL.getHasString()) };
						final List<RDFNode> property_name = new ArrayList<>();
						pathQuery(propertySingleValue.asResource(), name_path).forEach(name -> property_name.add(name));
						
						//TODO: String propertyName = pathQuery(propertySingleValue.asResource(), name_path) -----

						final List<RDFNode> property_value = new ArrayList<>();

						RDFStep[] value_pathS = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(ifcOWL.getHasString()) };
						pathQuery(propertySingleValue.asResource(), value_pathS)
								.forEach(value -> property_value.add(value));

						RDFStep[] value_pathD = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(ifcOWL.getHasDouble()) };
						pathQuery(propertySingleValue.asResource(), value_pathD)
								.forEach(value -> property_value.add(value));

						RDFStep[] value_pathI = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(ifcOWL.getHasInteger()) };
						pathQuery(propertySingleValue.asResource(), value_pathI)
								.forEach(value -> property_value.add(value));

						RDFStep[] value_pathB = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(ifcOWL.getHasBoolean()) };
						pathQuery(propertySingleValue.asResource(), value_pathB)
								.forEach(value -> property_value.add(value));

						RDFStep[] value_pathL = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(ifcOWL.getHasLogical()) };
						pathQuery(propertySingleValue.asResource(), value_pathL)
								.forEach(value -> property_value.add(value));
						
						String guid = getGUID(propertyset);
						String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
						if (guid != null) {						
							if (property_name.size() > 0 && property_value.size() > 0) {
								RDFNode pname = property_name.get(0);
								RDFNode pvalue = property_value.get(0);
								if (!pname.toString().equals(pvalue.toString())) {
									PropertySet ps = this.propertysets.get(propertyset.getURI());
									if (ps == null) {
										if (!propertyset_name.isEmpty())
											ps = new PropertySet(this.uriBase,lbd_property_output_model,
													propertyset_name.get(0).toString(), props_level,hasPropertiesBlankNodes);
										else
											ps = new PropertySet(this.uriBase,lbd_property_output_model, "", 
													props_level,hasPropertiesBlankNodes);
										this.propertysets.put(propertyset.getURI(), ps);
									}
									if (pvalue.toString().trim().length() > 0) {
										ps.put(pname.toString(), pvalue);
										ps.putPropertyRef(pname);											
									}
								}
							} else {
								RDFNode pname = property_name.get(0);
								PropertySet ps = this.propertysets.get(propertyset.getURI());
								if (ps == null) {
									if (!propertyset_name.isEmpty())
										ps = new PropertySet(this.uriBase,lbd_property_output_model, propertyset_name.get(0).toString(),
												props_level,hasPropertiesBlankNodes);
									else
										ps = new PropertySet(this.uriBase,lbd_property_output_model, "", props_level,hasPropertiesBlankNodes);

									this.propertysets.put(propertyset.getURI(), ps);
								}
								ps.put(pname.toString(), propertySingleValue);
								ps.putPropertyRef(pname);
								copyTriples(0, propertySingleValue, lbd_property_output_model);
							}
						} else {
							RDFNode pname = property_name.get(0);
							PropertySet ps = this.propertysets.get(propertyset.getURI());
							if (ps == null) {
								if (!propertyset_name.isEmpty())
									ps = new PropertySet(this.uriBase, lbd_property_output_model,
											propertyset_name.get(0).toString(), props_level, hasPropertiesBlankNodes);
								else
									ps = new PropertySet(this.uriBase, lbd_property_output_model, "", props_level,
											hasPropertiesBlankNodes);
								this.propertysets.put(propertyset.getURI(), ps);
							}
							ps.put(pname.toString(), propertySingleValue);
							copyTriples(0, propertySingleValue, lbd_property_output_model);
						}
					}			
					);
					
				}
			});
			eventBus.post(new SystemStatusEvent("LBD properties read"));
		}

		listSites().stream().map(rn -> rn.asResource()).forEach(site -> {
			Resource sio = createformattedURI(site, lbd_general_output_model, "Site");
			String guid_site = getGUID(site);
			String uncompressed_guid_site = GuidCompressor.uncompressGuidString(guid_site);
			//TODO: PUT THEM BACK!! 
			addAttrributes(lbd_property_output_model, site.asResource(), sio);

			sio.addProperty(RDF.type, LBD_NS.BOT.site);

			listPropertysets(site).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
				PropertySet p_set = this.propertysets.get(propertyset.getURI());
				if (p_set != null) {
					p_set.connect(sio, uncompressed_guid_site);
				}
			});

			listBuildings(site).stream().map(rn -> rn.asResource()).forEach(building -> {
				if (!getType(building.asResource()).get().getURI().endsWith("#IfcBuilding"))
					return;
				Resource bo = createformattedURI(building, lbd_general_output_model, "Building");
				String guid_building = getGUID(building);
				String uncompressed_guid_building = GuidCompressor.uncompressGuidString(guid_building);
				//TODO: PUT THEM BACK!! 
				addAttrributes(lbd_property_output_model, building, bo);

				bo.addProperty(RDF.type, LBD_NS.BOT.building);
				sio.addProperty(LBD_NS.BOT.hasBuilding, bo);

				listPropertysets(building).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
					PropertySet p_set = this.propertysets.get(propertyset.getURI());
					if (p_set != null) {
						p_set.connect(bo, uncompressed_guid_building);
					}
				});

				listStoreys(building).stream().map(rn -> rn.asResource()).forEach(storey -> {
					eventBus.post(new SystemStatusEvent("Storey: " + storey.getLocalName()));

					if (!getType(storey.asResource()).get().getURI().endsWith("#IfcBuildingStorey"))
						return;

					Resource so = createformattedURI(storey, lbd_general_output_model, "Storey");
					String guid_storey = getGUID(storey);
					String uncompressed_guid_storey = GuidCompressor.uncompressGuidString(guid_storey);
					//TODO: PUT THEM BACK!! 
					addAttrributes(lbd_property_output_model, storey, so);

					bo.addProperty(LBD_NS.BOT.hasStorey, so);
					so.addProperty(RDF.type, LBD_NS.BOT.storey);

					listPropertysets(storey).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
						PropertySet p_set = this.propertysets.get(propertyset.getURI());
						if (p_set != null)
							p_set.connect(so, uncompressed_guid_storey);
					});

					listContained_StoreyElements(storey).stream().map(rn -> rn.asResource()).forEach(element -> {
						if (getType(element.asResource()).get().getURI().endsWith("#IfcSpace"))
							return;
						connectElement(so, element);
					});

					listStoreySpaces(storey.asResource()).stream().forEach(space -> {
						if (!getType(space.asResource()).get().getURI().endsWith("#IfcSpace"))
							return;
						Resource spo = createformattedURI(space.asResource(), lbd_general_output_model, "Space");
						String guid_space = getGUID(space.asResource());
						String uncompressed_guid_space = GuidCompressor.uncompressGuidString(guid_space);
						//TODO: PUT THEM BACK!! 
						addAttrributes(lbd_property_output_model, space.asResource(), spo);

						so.addProperty(LBD_NS.BOT.hasSpace, spo);
						spo.addProperty(RDF.type, LBD_NS.BOT.space);
						listContained_SpaceElements(space.asResource()).stream().map(rn -> rn.asResource())
								.forEach(element -> {
									connectElement(spo, element);
								});

						listAdjacent_SpaceElements(space.asResource()).stream().map(rn -> rn.asResource())
								.forEach(element -> {
									connectElement(spo, LBD_NS.BOT.adjacentElement, element);
								});

						listPropertysets(space.asResource()).stream().map(rn -> rn.asResource())
								.forEach(propertyset -> {
									PropertySet p_set = this.propertysets.get(propertyset.getURI());
									if (p_set != null) {
										p_set.connect(spo, uncompressed_guid_space);
									}
								});
					});
				});
			});
		});

		if (hasBuildingElements) {
			if (hasSeparateBuildingElementsModel) {
				String out_products_filename = target_file.substring(0, target_file.lastIndexOf("."))
						+ "_building_elements.ttl";
				writeModel(lbd_product_output_model, out_products_filename);
				eventBus.post(new SystemStatusEvent("Building elements file is: " + out_products_filename));
			} else
				lbd_general_output_model.add(lbd_product_output_model);
		}

		if (hasBuildingProperties) {
			if (hasSeparatePropertiesModel) {
				String out_properties_filename = target_file.substring(0, target_file.lastIndexOf("."))
						+ "_element_properties.ttl";
				writeModel(lbd_property_output_model, out_properties_filename);
				eventBus.post(
						new SystemStatusEvent("Building elements properties file is: " + out_properties_filename));
			} else
				lbd_general_output_model.add(lbd_property_output_model);
		}

		if (hasGeolocation) {
			try {
				addGeolocation2BOT();
			} catch (Exception e) {
				eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
			}
		}
		writeModel(lbd_general_output_model, target_file);

		eventBus.post(new SystemStatusEvent("Done. Linked Building Data File is: " + target_file));

	}

	private void writeModel(Model m, String target_file) {
		FileOutputStream fo = null;
		try {
			fo = new FileOutputStream(new File(target_file));
			m.write(fo, "TTL");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
		} finally {
			if (fo != null)
				try {
					fo.close();
				} catch (IOException e) {
				}
		}
	}

	private void copyTriples(int level, RDFNode r, Model output_model) {
		if (level > 4)
			return;
		if (!r.isResource())
			return;
		r.asResource().listProperties().forEachRemaining(s -> {
			// No ontology
			if (!s.getPredicate().asResource().getURI().startsWith("http://www.w3.org/2000/01/rdf-schema#")) {
				output_model.add(s);
				copyTriples(level + 1, s.getObject(), output_model);
			}
		});
	}

	private void connectElement(Resource bot_resource, Resource ifc_element) {
		Optional<String> predefined_type = getPredefinedData(ifc_element);
		Optional<Resource> ifcowl_type = getType(ifc_element);
		Optional<Resource> bot_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			bot_type = getLBDProductType(ifcowl_type.get().getLocalName());
		}

		if (bot_type.isPresent()) {
			Resource eo = createformattedURI(ifc_element, this.lbd_general_output_model, bot_type.get().getLocalName());
			String guid = getGUID(ifc_element);
			String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
			Resource lbd_property_object = this.lbd_product_output_model.createResource(eo.getURI());
			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(bot_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			} // else
			lbd_property_object.addProperty(RDF.type, bot_type.get());
			eo.addProperty(RDF.type, LBD_NS.BOT.element);
			bot_resource.addProperty(LBD_NS.BOT.containsElement, eo);

			listPropertysets(ifc_element).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
				PropertySet p_set = this.propertysets.get(propertyset.getURI());
				if (p_set != null)
					p_set.connect(eo, uncompressed_guid);
			});
			//TODO: put them back!!! 
			addAttrributes(this.lbd_property_output_model, ifc_element, eo);

			listHosted_Elements(ifc_element).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
				if (eo.getLocalName().toLowerCase().contains("space"))
					System.out.println("hosts: " + ifc_element + "--" + ifc_element2 + " bot:" + eo);
				connectElement(eo, LBD_NS.BOT.hostsElement, ifc_element2);
			});

			listAggregated_Elements(ifc_element).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
				connectElement(eo, LBD_NS.BOT.aggregates, ifc_element2);
			});
		} // else
			// System.out.println("no bot for: " + ifc_element.getLocalName());
	}

	private void connectElement(Resource bot_resource, Property bot_property, Resource ifc_element) {
		Optional<String> predefined_type = getPredefinedData(ifc_element);
		Optional<Resource> ifcowl_type = getType(ifc_element);
		Optional<Resource> lbd_product_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			lbd_product_type = getLBDProductType(ifcowl_type.get().getLocalName());
		}

		if (lbd_product_type.isPresent()) {
			Resource lbd_object = createformattedURI(ifc_element, this.lbd_general_output_model,
					lbd_product_type.get().getLocalName());
			Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_object.getURI());

			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(lbd_product_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			} // else

			lbd_property_object.addProperty(RDF.type, lbd_product_type.get());
			lbd_object.addProperty(RDF.type, LBD_NS.BOT.element);

//			 addLabel(ifc_element, bot_object);
//			 addDescription(ifc_element, bot_object);
			//TODO: put them back!!! 
			 addAttrributes(this.lbd_property_output_model, ifc_element, lbd_object);
			bot_resource.addProperty(bot_property, lbd_object);
			listHosted_Elements(ifc_element).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
				if (lbd_object.getLocalName().toLowerCase().contains("space"))
					System.out.println("hosts2: " + ifc_element + "-->" + ifc_element2 + " bot:" + lbd_object);
				connectElement(lbd_object, LBD_NS.BOT.hostsElement, ifc_element2);
			});

			listAggregated_Elements(ifc_element).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
				connectElement(lbd_object, LBD_NS.BOT.aggregates, ifc_element2);
			});
		} else {
			System.err.println("No type: " + ifc_element);
		}

	}

	Set<Resource> handledSttributes4resource = new HashSet<>();

	private void addAttrributes(Model output_model, Resource r, Resource bot_r) {
		if (!handledSttributes4resource.add(r))
			return;
		String guid = getGUID(r);
		String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
		final PropertySet local = new PropertySet(this.uriBase, output_model, "attributes", this.props_level,
				hasPropertiesBlankNodes, true, uncompressed_guid);
		// Literal l = bot_r.getModel().createLiteral(guid);
		// local.put("guid", l);
		r.listProperties().forEachRemaining(s -> {
			String ps = s.getPredicate().getLocalName();
			Resource attr = s.getObject().asResource();
			Optional<Resource> atype = getType(attr);
			if (ps.startsWith("tag_"))
				ps = "batid";
			String property_string = ps; // Just to make it effectively final
			if (atype.isPresent()) {
				if (atype.get().getLocalName().equals("IfcLabel")) {
					attr.listProperties(ifcOWL.getHasString()).forEachRemaining(attr_s -> {
						if (attr_s.getObject().isLiteral()
								&& attr_s.getObject().asLiteral().getLexicalForm().length() > 0)
//							 bot_r.addProperty(BOT.LocalProperty.getProperty(bot_r.getNameSpace(),property_string),
//							 attr_s.getObject());
							local.put(property_string, attr_s.getObject());
					});

				} else if (atype.get().getLocalName().equals("IfcIdentifier")) {
					// attr.listProperties(ifcOWL.getHasString()).forEachRemaining(attr_s -> bot_r
					// .addProperty(BOT.LocalProperty.getProperty(bot_r.getNameSpace(),property_string),
					// attr_s.getObject()));
					attr.listProperties(ifcOWL.getHasString())
							.forEachRemaining(attr_s -> local.put(property_string, attr_s.getObject()));
				} else {
					attr.listProperties(ifcOWL.getHasString())
							.forEachRemaining(attr_s -> local.put(property_string, attr_s.getObject()));
					attr.listProperties(ifcOWL.getHasInteger())
							.forEachRemaining(attr_s -> local.put(property_string, attr_s.getObject()));
					attr.listProperties(ifcOWL.getHasDouble())
							.forEachRemaining(attr_s -> local.put(property_string, attr_s.getObject()));
					attr.listProperties(ifcOWL.getHasBoolean())
							.forEachRemaining(attr_s -> local.put(property_string, attr_s.getObject()));
				}

			}
		});
		local.connect(bot_r, uncompressed_guid);
	}

	private Optional<Resource> getType(Resource r) {
		RDFStep[] path = { new RDFStep(RDF.type) };
		return pathQuery(r, path).stream().map(rn -> rn.asResource()).findAny();
	}

	private Optional<String> getPredefinedData(RDFNode rn) {
		if (!rn.isResource())
			return Optional.empty();
		;
		final StringBuilder sb = new StringBuilder();
		rn.asResource().listProperties().toList().stream()
				.filter(t -> t.getPredicate().getLocalName().startsWith("predefinedType_"))
				.map(t -> t.getObject().asResource().getLocalName()).forEach(o -> sb.append(o));
		if (sb.length() == 0)
			return Optional.empty();
		return Optional.of(sb.toString());
	}

	/*
	 * private void addDescription(Resource ifc_r, final Resource bot_r) {
	 * ifc_r.listProperties(ifcOWL.getDescription()).toList() .forEach(x ->
	 * x.getObject().asResource().listProperties(ifcOWL.getHasString())
	 * .forEachRemaining(y -> bot_r.addProperty(RDFS.comment, y.getObject()))); }
	 * 
	 * private void addLabel(Resource ifc_r, final Resource bot_r) {
	 * ifc_r.listProperties(ifcOWL.getName()).toList() .forEach(x ->
	 * x.getObject().asResource().listProperties(ifcOWL.getHasString())
	 * .forEachRemaining(y -> bot_r.addProperty(RDFS.label, y.getObject()))); }
	 * 
	 * private void addLongName(Resource ifc_r, final Resource bot_r) {
	 * ifc_r.listProperties(ifcOWL.getLongName()).toList() .forEach(x ->
	 * x.getObject().asResource().listProperties(ifcOWL.getHasString())
	 * .forEachRemaining(y -> bot_r.addProperty(RDFS.label, y.getObject()))); }
	 */

	private Resource createformattedURI(Resource r, Model m, String product_type) {
		String guid = getGUID(r);
		if (guid == null) {
			String localName = r.getLocalName();
			if (localName.startsWith("IfcPropertySingleValue")) {
				if (localName.lastIndexOf('_') > 0)
					localName = localName.substring(localName.lastIndexOf('_') + 1);
				Resource uri = m.createResource(this.uriBase + "propertySingleValue_" + localName);
				return uri;
			}
			if (localName.toLowerCase().startsWith("ifc"))
				localName = localName.substring(3);
			Resource uri = m.createResource(this.uriBase + product_type.toLowerCase() + "_" + localName);
			return uri;
		} else {
			Resource guid_uri = m.createResource(
					this.uriBase + product_type.toLowerCase() + "_" + GuidCompressor.uncompressGuidString(guid));
			// Literal l = m.createLiteral(guid);
			// guid_uri.addLiteral(BOT.LocalProperty.getProperty(this.uriBase,"guid"), l);
			return guid_uri;
		}
	}

	private Resource getformattedURI(Resource r, Model m, String product_type) {
		String guid = getGUID(r);
		if (guid == null) {
			Resource uri = m.getResource(this.uriBase + product_type + "/" + r.getLocalName());
			return uri;
		} else {
			Resource guid_uri = m
					.getResource(this.uriBase + product_type + "/" + GuidCompressor.uncompressGuidString(guid));
			return guid_uri;
		}
	}

	private String getGUID(Resource r) {
		StmtIterator i = r.listProperties(ifcOWL.getGuid());
		if (i.hasNext()) {
			Statement s = i.next();
			String guid = s.getObject().asResource().getProperty(ifcOWL.getHasString()).getObject().asLiteral()
					.getLexicalForm();
			return guid;
		}
		return null;
	}

	public Optional<Resource> getLBDProductType(String ifcType) {
		List<Resource> ret = ifcowl_product_map.get(ifcType);
		if (ret == null) {
			return Optional.empty();
		} else if (ret.size() > 1) {
			System.out.println("many " + ifcType);
			return Optional.empty();
		} else if (ret.size() > 0)
			return Optional.of(ret.get(0));
		else
			return Optional.empty();
	}

	private List<RDFNode> listSites() {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return pathQuery(ifcowl_model.getResource(ifcOWL.getIfcSite()), path);
	}

	private List<RDFNode> listBuildings(Resource site) {
		RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
				new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		return pathQuery(site, path);
	}

	private List<RDFNode> listStoreys(Resource building) {
		RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
				new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		return pathQuery(building, path);
	}

	private List<RDFNode> listStoreySpaces(Resource storey) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
				new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		ret = pathQuery(storey, path1);
		RDFStep[] path2 = { new RDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(pathQuery(storey, path2));

		return ret;
	}

	private List<RDFNode> listContained_StoreyElements(Resource storey) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingStructure_IfcRelContainedInSpatialStructure")),
				new RDFStep(ifcOWL.getProperty("relatedElements_IfcRelContainedInSpatialStructure")) };
		ret = pathQuery(storey, path1);
		RDFStep[] path2 = { new RDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(pathQuery(storey, path2));
		return ret;
	}

	private List<RDFNode> listContained_SpaceElements(Resource space) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingStructure_IfcRelContainedInSpatialStructure")),
				new RDFStep(ifcOWL.getProperty("relatedElements_IfcRelContainedInSpatialStructure")) };
		ret = pathQuery(space, path1);
		return ret;
	}

	private List<RDFNode> listAdjacent_SpaceElements(Resource space) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingSpace_IfcRelSpaceBoundary")),
				new RDFStep(ifcOWL.getProperty("relatedBuildingElement_IfcRelSpaceBoundary")) };
		ret = pathQuery(space, path1);
		return ret;
	}

	private List<RDFNode> listHosted_Elements(Resource element) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingBuildingElement_IfcRelVoidsElement")),
				new RDFStep(ifcOWL.getProperty("relatedOpeningElement_IfcRelVoidsElement")),
				new InvRDFStep(ifcOWL.getProperty("relatingOpeningElement_IfcRelFillsElement")),
				new RDFStep(ifcOWL.getProperty("relatedBuildingElement_IfcRelFillsElement")) };
		ret = pathQuery(element, path1);

		RDFStep[] path2 = { new InvRDFStep(ifcOWL.getProperty("relatingBuildingElement_IfcRelVoidsElement")),
				new RDFStep(ifcOWL.getProperty("relatedOpeningElement_IfcRelVoidsElement")),
				new RDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(pathQuery(element, path2));

		return ret;
	}

	private List<RDFNode> listAggregated_Elements(Resource element) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingObject_IfcRelDecomposes")),
				new RDFStep(ifcOWL.getProperty("relatedObjects_IfcRelDecomposes")) };
		ret = pathQuery(element, path1);
		return ret;
	}

	private List<Resource> listBuildingElements() {
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

	private Resource getSuperClass(Resource r) {
		System.out.println("type: " + r);
		StmtIterator subclass_statement_iterator = r.listProperties(RDFS.subClassOf);
		while (subclass_statement_iterator.hasNext()) {
			Statement su = subclass_statement_iterator.next();
			Resource ifcowl_superclass = su.getObject().asResource();
			if (!ifcowl_superclass.isAnon())
				return ifcowl_superclass;
		}
		return null;
	}

	private List<Resource> listElements() {
		final List<Resource> ret = new ArrayList<>();
		ifcowl_model.listStatements().filterKeep(t1 -> t1.getPredicate().equals(RDF.type)).filterKeep(t2 -> {
			Optional<Resource> product_type = getLBDProductType(t2.getObject().asResource().getLocalName());
			return product_type.isPresent();
		}).mapWith(t1 -> t1.getSubject()).forEachRemaining(s -> ret.add(s));
		;
		return ret;
	}

	private List<RDFNode> listPropertysets(Resource resource) {
		RDFStep[] path = { new InvRDFStep(ifcOWL.getRelatedObjects_IfcRelDefines()),
				new RDFStep(ifcOWL.getRelatingPropertyDefinition_IfcRelDefinesByProperties()) };
		return pathQuery(resource, path);
	}

	private List<RDFNode> listPropertysets() {
		RDFStep[] path = { new InvRDFStep(RDF.type) };
		return pathQuery(ifcowl_model.getResource(ifcOWL.getIfcPropertySet()), path);
	}

	private List<RDFNode> pathQuery(Resource r, RDFStep[] path) {
		List<RDFStep> path_list = Arrays.asList(path);
		if (r.getModel() == null)
			return new ArrayList<RDFNode>();
		Optional<RDFStep> step = path_list.stream().findFirst();
		if (step.isPresent()) {
			List<RDFNode> step_result = step.get().next(r);
			if (path.length > 1) {
				final List<RDFNode> result = new ArrayList<RDFNode>();
				step_result.stream().filter(rn1 -> rn1.isResource()).map(rn2 -> rn2.asResource()).forEach(r1 -> {
					List<RDFStep> tail = path_list.stream().skip(1).collect(Collectors.toList());
					result.addAll(pathQuery(r1, tail.toArray(new RDFStep[tail.size()])));
				});
				return result;
			} else
				return step_result;
		}
		return new ArrayList<RDFNode>();
	}

	public void createIfcBOTMapping() {
		StmtIterator si = ontology_model.listStatements();
		while (si.hasNext()) {
			Statement product_BE_ontology_statement = si.next();
			if (product_BE_ontology_statement.getPredicate().toString().toLowerCase().contains("seealso")) {
				if (product_BE_ontology_statement.getObject().isLiteral())
					continue;
				if (!product_BE_ontology_statement.getObject().isResource())
					continue;
				Resource ifcowl_class = product_BE_ontology_statement.getObject().asResource();

				// This adds the seeAlso mapping directly: The base IRI is removed so that the
				// mapping independen of
				// various IFC versions
				List<Resource> resource_list = ifcowl_product_map.getOrDefault(ifcowl_class.getLocalName(),
						new ArrayList<Resource>());
				ifcowl_product_map.put(ifcowl_class.getLocalName(), resource_list);
				resource_list.add(product_BE_ontology_statement.getSubject());
				System.out.println("added to resource_list : " + product_BE_ontology_statement.getSubject());
			}
		}
		StmtIterator so = ontology_model.listStatements();
		while (so.hasNext()) {
			Statement product_BE_ontology_statement = so.next();
			if (product_BE_ontology_statement.getPredicate().toString().toLowerCase().contains("seealso")) {
				if (product_BE_ontology_statement.getObject().isLiteral())
					continue;
				if (!product_BE_ontology_statement.getObject().isResource())
					continue;
				Resource ifcowl_class = product_BE_ontology_statement.getObject().asResource();
				Resource mapped_ifcowl_class = ontology_model
						.getResource(this.ontURI.get() + "#" + ifcowl_class.getLocalName());
				StmtIterator subclass_statement_iterator = ontology_model
						.listStatements(new SimpleSelector(null, RDFS.subClassOf, mapped_ifcowl_class));
				while (subclass_statement_iterator.hasNext()) {
					Statement su = subclass_statement_iterator.next();
					Resource ifcowl_subclass = su.getSubject();
					if (ifcowl_product_map.get(ifcowl_subclass.getLocalName()) == null) {
						List<Resource> r_list = ifcowl_product_map.getOrDefault(ifcowl_subclass.getLocalName(),
								new ArrayList<Resource>());
						ifcowl_product_map.put(ifcowl_subclass.getLocalName(), r_list);
						System.out.println(
								ifcowl_subclass.getLocalName() + " ->> " + product_BE_ontology_statement.getSubject());
						r_list.add(product_BE_ontology_statement.getSubject());
					}
				}

			}
		}

	}

	public Model readAndConvertIFC(String ifc_file, String uriBase) {
		try {
			IfcSpfReader rj = new IfcSpfReader();
			File tempFile = File.createTempFile("ifc", ".ttl");
			try {
				Model m = ModelFactory.createDefaultModel();
				// File tempFile = File.createTempFile("MyAppName-", ".tmp");
				this.ontURI = rj.convert(ifc_file, tempFile.getAbsolutePath(), uriBase);
				// m.read(stream, null, "TTL");
				RDFDataMgr.read(m, tempFile.getAbsolutePath());
				return m;
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				tempFile.deleteOnExit();
			}

		} catch (Exception e) {
			eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
			e.printStackTrace();

		}
		System.out.println("IFC-RDF conversion not done");
		return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
	}

	/*
	 * public Model readAndConvertIFC(String ifc_file, String uriBase) { try {
	 * IfcSpfReader rj = new IfcSpfReader(); try { Model m =
	 * ModelFactory.createDefaultModel(); ByteArrayOutputStream stringStream = new
	 * ByteArrayOutputStream(); this.ontURI = rj.convert(ifc_file, stringStream,
	 * uriBase); InputStream stream = new ByteArrayInputStream(
	 * stringStream.toString().getBytes(StandardCharsets.UTF_8.name()));
	 * m.read(stream, null, "TTL"); return m; } catch (IOException e) {
	 * e.printStackTrace(); }
	 * 
	 * } catch (Exception e) { eventBus.post(new SystemStatusEvent("Error: " +
	 * e.getMessage())); e.printStackTrace();
	 * 
	 * } System.out.println("IFC-RDF conversion not done"); return
	 * ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM); }
	 */
	

	private void readInOntologies(String ifc_file) {
		IfcVersion.initDefaultIfcNsMap();
		IfcVersion version = null;
		FileInputStream input;
		try {
			input = new FileInputStream(new File(ifc_file));
			Header header = HeaderParser.parseHeader(input);
			Object ifcVersion;
			version = IfcVersion.getIfcVersion(header);

			// TODO Fix this: Double reading
			readInOntologyTTL(ontology_model, version.getLabel() + ".ttl");
			readInOntologyTTL(ifcowl_model, version.getLabel() + ".ttl");
		} catch (FileNotFoundException e) {
			eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
			e.printStackTrace();
		} catch (IOException e) {
			eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
			e.printStackTrace();
		} catch (IfcVersionException e) {
			eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
			e.printStackTrace();
		}

		readInOntologyTTL(ontology_model, "prod.ttl");
		readInOntologyTTL(ontology_model, "prod_building_elements.ttl");
		readInOntologyTTL(ontology_model, "prod_furnishing.ttl");
		readInOntologyTTL(ontology_model, "prod_mep.ttl");
		// ontology_model.write(System.out, "TTL");		

		readInOntologyTTL(ontology_model, "psetdef.ttl");
		List<String> files = showFiles("pset", ".ttl");
		for(String s : files){
			s = s.substring(s.indexOf("pset"));
			s = s.replaceAll("\\\\", "/");
			readInOntologyTTL(ontology_model, s);		
			System.out.println("read ontology file : " + s);
		}		
	}

	private void readInOntologyTTL(Model model, String ontology_file) {

		InputStream in = null;
		try {
			in = IFCtoLBDConverter.class.getResourceAsStream("/" + ontology_file);
			if (in == null) {
				try {
					in = IFCtoLBDConverter.class.getResourceAsStream("/resources/" + ontology_file);
				} catch (Exception e) {
					eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
					e.printStackTrace();
					return;
				}
			}
			model.read(in, null, "TTL");
			in.close();

		} catch (Exception e) {
			eventBus.post(new SystemStatusEvent("Error: " + e.getMessage()));
			System.out.println("missing file: " + ontology_file);
			e.printStackTrace();
		}

	}

	private void addGeolocation2BOT() {

		IFC_Geolocation c = new IFC_Geolocation();
		String wkt_point = c.addGeolocation(ifcowl_model);

		listSites().stream().map(rn -> rn.asResource()).forEach(site -> {
			// Create a resource and add to bot model (resource, model, string)
			Resource sio = createformattedURI(site, lbd_general_output_model, "Site");

			// Create a resource geosparql:Feature;
			Resource geof = lbd_general_output_model.createResource("http://www.opengis.net/ont/geosparql#Feature");
			// Add geosparl:Feature as a type to site;
			sio.addProperty(RDF.type, geof);
			// Create a resource geosparql:hasGeometry;
			Property geo_hasGeometry = lbd_general_output_model
					.createProperty("http://www.opengis.net/ont/geosparql#hasGeometry");
			// For the moment we will use a seperate graph for geometries, to "encourage"
			// people to not link to geometries
			// This could also be done using blanknodes, although, hard to maintain
			// provenance if required in future versions.
			String wktLiteralID = "urn:bot:geom:pt:";
			String guid_site = getGUID(site);
			String uncompressed_guid_site = GuidCompressor.uncompressGuidString(guid_site);
			String uncompressed_wktLiteralID = wktLiteralID + uncompressed_guid_site;

			// Create a resource <urn:bot:geom:pt:guid>
			Resource rr = lbd_general_output_model.createResource(uncompressed_wktLiteralID);
			sio.addProperty(geo_hasGeometry, rr);

			// Create a property asWKT
			Property geo_asWKT = lbd_general_output_model.createProperty("http://www.opengis.net/ont/geosparql#asWKT");
			// add a data type
			RDFDatatype rtype = WktLiteral.wktLiteralType;
			TypeMapper.getInstance().registerDatatype(rtype);
			// add a typed wkt literal
			Literal l = lbd_general_output_model.createTypedLiteral(wkt_point, rtype);

			rr.addProperty(geo_asWKT, l);

		});

		eventBus.post(new SystemStatusEvent("LDB geom read"));

	}


	public static void main(String[] args) {

		if (args.length > 2) {
			new IFCtoLBDConverter(args[0], args[1], args[2], 2, true, false, true, false, false, true);	
		} 
		else if(args.length == 1){
			//directory upload
			final List<String> inputFiles;
            final List<String> outputFiles;

            inputFiles = showFiles(args[0]);
            outputFiles = null;

            for (int i = 0; i < inputFiles.size(); ++i) {
                final String inputFile = inputFiles.get(i);
                String outputFile;
                if (inputFile.endsWith(".ifc")) {
                    if (outputFiles == null) {
                        outputFile = inputFile.substring(0, inputFile.length() - 4) + ".ttl";
                    } else {
                        outputFile = outputFiles.get(i);
                    }                                   

                    outputFile = outputFile.replaceAll(args[0], args[0] + "\\___out\\");
                    String copyFile = inputFile.replaceAll(args[0], args[0] + "\\___done\\");
                    
                    //move file to output directory
//                    Path p = Paths.get(outputFile);
//                    String fileNameTTL = p.getFileName().toString();
//                    outputFile = args[0] + "\\___out\\" + fileNameTTL ;

//                    Path p1 = Paths.get(inputFile);
//                    String fileNameIFC = p1.getFileName().toString();                    
//                    String copyFile = args[0] + "\\___done\\" + fileNameIFC ;
                    
                    
                    
//                  new IFCtoLBDConverter(ifc_filename, uriBase, target_file,this.props_level,this.hasBuildingElements,this.hasSeparateBuildingElementsModel,
//        					this.hasBuildingProperties,this.hasSeparatePropertiesModel,this.hasPropertiesBlankNodes);
                    System.out.println("--------- converting: " + inputFile);
                    new IFCtoLBDConverter(inputFile, "https://dot.ugent.be/IFCtoLBDset#", outputFile, 0, true, false, true, false, false, false);	
                    
                    //move original file to output directory
                    File afile =new File(inputFile);            		
             	   	afile.renameTo(new File(copyFile));
                    System.out.println("--------- done ");
                }
            }
		}
		else
			System.out.println("Usage: IFCtoLBDConverter ifc_filename base_uri targer_file");
	}
	


    public static List<String> showFiles(String dir) {
        List<String> goodFiles = new ArrayList<String>();

        File folder = new File(dir);
        File[] listOfFiles = folder.listFiles();

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile())
                goodFiles.add(listOfFiles[i].getAbsolutePath());
            else if (listOfFiles[i].isDirectory())
                goodFiles.addAll(showFiles(listOfFiles[i].getAbsolutePath()));
        }
        return goodFiles;
    }
    
    public static List<String> showFiles(String dir, String extension) {
		List<String> goodFiles = new ArrayList<>();

		try {
			URI uri = IFCtoLBDConverter.class.getResource("/" + dir).toURI();
			Path myPath;
			myPath = Paths.get(uri);
			Stream<Path> walk = Files.walk(myPath).filter(p -> p.toString().endsWith(extension))
					.filter(p -> p.toString().contains("_")).distinct();

			for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
				Path p = it.next();
				if (p.toString().endsWith(extension)) {
					goodFiles.add(p.toString());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		return goodFiles;
	}
}
