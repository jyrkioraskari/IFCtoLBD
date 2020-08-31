
package org.lbd.ifc2lbd.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.lbd.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.lbd.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.lbd.ifc2lbd.core.utils.FileUtils;
import org.lbd.ifc2lbd.core.utils.IfcOWLUtils;
import org.lbd.ifc2lbd.core.utils.RDFUtils;
import org.lbd.ifc2lbd.core.utils.rdfpath.RDFStep;
import org.lbd.ifc2lbd.core.valuesets.AttributeSet;
import org.lbd.ifc2lbd.core.valuesets.PropertySet;
import org.lbd.ifc2lbd.geo.IFC_Geolocation;
import org.lbd.ifc2lbd.geo.WktLiteral;
import org.lbd.ifc2lbd.namespace.IfcOWLNameSpace;
import org.lbd.ifc2lbd.namespace.LBD_NS;
import org.lbd.ifc2lbd.namespace.OPM;

import com.google.common.eventbus.EventBus;
import com.openifctools.guidcompressor.GuidCompressor;

import be.ugent.IfcSpfReader;

/*
 *  Copyright (c) 2017,2018,2019.2020 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

public abstract class IFCtoLBDConverterCore {
	protected final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
	protected Model ifcowl_model;
	protected Model ontology_model = null;
	protected Map<String, List<Resource>> ifcowl_product_map = new HashMap<>();
	protected String uriBase;

	protected Optional<String> ontURI = Optional.empty();
	protected IfcOWLNameSpace ifcOWL;

	// URI-property set
	protected Map<String, PropertySet> propertysets = new HashMap<>();
	protected int props_level;
	protected boolean hasPropertiesBlankNodes;

	protected Model lbd_general_output_model;
	protected Model lbd_product_output_model;
	protected Model lbd_property_output_model;

	protected void conversion(String target_file, boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel,
			boolean hasBuildingProperties, boolean hasSeparatePropertiesModel, boolean hasGeolocation) {
		IfcOWLUtils.listSites(ifcOWL, ifcowl_model).stream().map(rn -> rn.asResource()).forEach(site -> {
			Resource sio = createformattedURI(site, lbd_general_output_model, "Site");
			String guid_site = IfcOWLUtils.getGUID(site, this.ifcOWL);
			String uncompressed_guid_site = GuidCompressor.uncompressGuidString(guid_site);
			addAttrributes(lbd_property_output_model, site.asResource(), sio);

			sio.addProperty(RDF.type, LBD_NS.BOT.site);

			IfcOWLUtils.listPropertysets(site, ifcOWL).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
				PropertySet p_set = this.propertysets.get(propertyset.getURI());
				if (p_set != null) {
					p_set.connect(sio, uncompressed_guid_site);
				}
			});

			IfcOWLUtils.listBuildings(site, ifcOWL).stream().map(rn -> rn.asResource()).forEach(building -> {
				if (!RDFUtils.getType(building.asResource()).get().getURI().endsWith("#IfcBuilding")) {
					System.err.println("Not an #IfcBuilding");
					return;
				}
				Resource bo = createformattedURI(building, lbd_general_output_model, "Building");
				String guid_building = IfcOWLUtils.getGUID(building, this.ifcOWL);
				String uncompressed_guid_building = GuidCompressor.uncompressGuidString(guid_building);
				addAttrributes(lbd_property_output_model, building, bo);

				bo.addProperty(RDF.type, LBD_NS.BOT.building);
				sio.addProperty(LBD_NS.BOT.hasBuilding, bo);

				IfcOWLUtils.listPropertysets(building, ifcOWL).stream().map(rn -> rn.asResource())
						.forEach(propertyset -> {
							PropertySet p_set = this.propertysets.get(propertyset.getURI());
							if (p_set != null) {
								p_set.connect(bo, uncompressed_guid_building);
							}
						});

				IfcOWLUtils.listStoreys(building, ifcOWL).stream().map(rn -> rn.asResource()).forEach(storey -> {
					eventBus.post(new IFCtoLBD_SystemStatusEvent("Storey: " + storey.getLocalName()));

					if (!RDFUtils.getType(storey.asResource()).get().getURI().endsWith("#IfcBuildingStorey")) {
						System.err.println("No an #IfcBuildingStorey");
						return;
					}

					Resource so = createformattedURI(storey, lbd_general_output_model, "Storey");
					String guid_storey = IfcOWLUtils.getGUID(storey, this.ifcOWL);
					String uncompressed_guid_storey = GuidCompressor.uncompressGuidString(guid_storey);
					addAttrributes(lbd_property_output_model, storey, so);

					bo.addProperty(LBD_NS.BOT.hasStorey, so);
					so.addProperty(RDF.type, LBD_NS.BOT.storey);

					IfcOWLUtils.listPropertysets(storey, ifcOWL).stream().map(rn -> rn.asResource())
							.forEach(propertyset -> {
								PropertySet p_set = this.propertysets.get(propertyset.getURI());
								if (p_set != null)
									p_set.connect(so, uncompressed_guid_storey);
							});

					IfcOWLUtils.listContained_StoreyElements(storey, ifcOWL).stream().map(rn -> rn.asResource())
							.forEach(element -> {
								if (RDFUtils.getType(element.asResource()).get().getURI().endsWith("#IfcSpace"))
									return;
								connectElement(so, element);
							});

					IfcOWLUtils.listStoreySpaces(storey.asResource(), ifcOWL).stream().forEach(space -> {
						if (!RDFUtils.getType(space.asResource()).get().getURI().endsWith("#IfcSpace"))
							return;
						Resource spo = createformattedURI(space.asResource(), lbd_general_output_model, "Space");
						String guid_space = IfcOWLUtils.getGUID(space.asResource(), this.ifcOWL);
						String uncompressed_guid_space = GuidCompressor.uncompressGuidString(guid_space);
						addAttrributes(lbd_property_output_model, space.asResource(), spo);

						so.addProperty(LBD_NS.BOT.hasSpace, spo);
						spo.addProperty(RDF.type, LBD_NS.BOT.space);
						IfcOWLUtils.listContained_SpaceElements(space.asResource(), ifcOWL).stream()
								.map(rn -> rn.asResource()).forEach(element -> {
									connectElement(spo, element);
								});

						IfcOWLUtils.listAdjacent_SpaceElements(space.asResource(), ifcOWL).stream()
								.map(rn -> rn.asResource()).forEach(element -> {
									connectElement(spo, LBD_NS.BOT.adjacentElement, element);
								});

						IfcOWLUtils.listPropertysets(space.asResource(), ifcOWL).stream().map(rn -> rn.asResource())
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

		if (hasGeolocation) {
			try {
				addGeolocation2BOT();
			} catch (Exception e) {
				// e.printStackTrace();
				eventBus.post(new IFCtoLBD_SystemStatusEvent("Info : No geolocation"));
			}
		}

		if (target_file != null) {
			if (hasBuildingElements) {
				if (hasSeparateBuildingElementsModel) {
					String out_products_filename = target_file.substring(0, target_file.lastIndexOf("."))
							+ "_building_elements.ttl";
					RDFUtils.writeModel(lbd_product_output_model, out_products_filename, this.eventBus);
					eventBus.post(
							new IFCtoLBD_SystemStatusEvent("Building elements file is: " + out_products_filename));
				} else
					lbd_general_output_model.add(lbd_product_output_model);
			}

			if (hasBuildingProperties) {
				if (hasSeparatePropertiesModel) {
					String out_properties_filename = target_file.substring(0, target_file.lastIndexOf("."))
							+ "_element_properties.ttl";
					RDFUtils.writeModel(lbd_property_output_model, out_properties_filename, this.eventBus);
					eventBus.post(new IFCtoLBD_SystemStatusEvent(
							"Building elements properties file is: " + out_properties_filename));
				} else
					lbd_general_output_model.add(lbd_property_output_model);
			}
			RDFUtils.writeModel(lbd_general_output_model, target_file, this.eventBus);
			eventBus.post(new IFCtoLBD_SystemStatusEvent("Done. Linked Building Data File is: " + target_file));
		}
	}

	/**
	 * Collects the PropertySet data from the ifcOWL model and creates a separate
	 * Apache Jena Model that contains the converted representation of the property
	 * set content.
	 * 
	 * @param props_level             The levels described in
	 *                                https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
	 * @param hasPropertiesBlankNodes If the nameless nodes are used.
	 */
	protected void handlePropertySetData(int props_level, boolean hasPropertiesBlankNodes) {
		IfcOWLUtils.listPropertysets(ifcOWL, ifcowl_model).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
			if ("https://www.ugent.be/myAwesomeFirstBIMProject#IfcPropertySet_17765".equals(propertyset.getURI()))
				System.out.println("HERE!!");
			RDFStep[] pname_path = { new RDFStep(ifcOWL.getName_IfcRoot()),
					new RDFStep(IfcOWLNameSpace.getHasString()) };

			final List<RDFNode> propertyset_name = new ArrayList<>();
			RDFUtils.pathQuery(propertyset, pname_path).forEach(name -> propertyset_name.add(name));

			RDFStep[] path = { new RDFStep(ifcOWL.getHasProperties_IfcPropertySet()) };
			RDFUtils.pathQuery(propertyset, path).forEach(propertySingleValue -> {

				RDFStep[] name_path = { new RDFStep(ifcOWL.getName_IfcProperty()),
						new RDFStep(IfcOWLNameSpace.getHasString()) };
				final List<RDFNode> property_name = new ArrayList<>();
				RDFUtils.pathQuery(propertySingleValue.asResource(), name_path)
						.forEach(name -> property_name.add(name));

				if (property_name.size() == 0)
					return; // = stream continue

				final List<RDFNode> property_value = new ArrayList<>();

				RDFStep[] value_pathS = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
						new RDFStep(IfcOWLNameSpace.getHasString()) };
				RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathS)
						.forEach(value -> property_value.add(value));

				RDFStep[] value_pathD = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
						new RDFStep(ifcOWL.getHasDouble()) };
				RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathD)
						.forEach(value -> property_value.add(value));

				RDFStep[] value_pathI = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
						new RDFStep(ifcOWL.getHasInteger()) };
				RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathI)
						.forEach(value -> property_value.add(value));

				RDFStep[] value_pathB = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
						new RDFStep(ifcOWL.getHasBoolean()) };
				RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathB)
						.forEach(value -> property_value.add(value));

				RDFStep[] value_pathL = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
						new RDFStep(ifcOWL.getHasLogical()) };
				RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathL)
						.forEach(value -> property_value.add(value));

				RDFNode pname = property_name.get(0);

				PropertySet ps = this.propertysets.get(propertyset.getURI());
				if (ps == null) {
					if (!propertyset_name.isEmpty())
						ps = new PropertySet(this.uriBase, lbd_property_output_model, this.ontology_model,
								propertyset_name.get(0).toString(), props_level, hasPropertiesBlankNodes);
					else
						ps = new PropertySet(this.uriBase, lbd_property_output_model, this.ontology_model, "",
								props_level, hasPropertiesBlankNodes);
					this.propertysets.put(propertyset.getURI(), ps);
				}

				if (property_value.size() > 0) {
					RDFNode pvalue = property_value.get(0);
					if (!pname.toString().equals(pvalue.toString())) {
						if (pvalue.toString().trim().length() > 0) {
							if (pvalue.isLiteral()) {
								String val = pvalue.asLiteral().getLexicalForm();
								if (val.equals("-1.#IND"))
									pvalue = ResourceFactory.createTypedLiteral(Double.NaN);
							}
							ps.putPnameValue(pname.toString(), pvalue);
							ps.putPsetPropertyRef(pname);
						}
					}
					// else: do nothing
				} else {
					ps.putPnameValue(pname.toString(), propertySingleValue);
					ps.putPsetPropertyRef(pname);
					RDFUtils.copyTriples(0, propertySingleValue, lbd_property_output_model);
				}

			});

		});
		eventBus.post(new IFCtoLBD_SystemStatusEvent("LBD properties read"));
	}

	/**
	 * Adds the used RDF namespaces for the Jena Models
	 * 
	 * @param uriBase
	 * @param props_level
	 * @param hasBuildingElements
	 * @param hasBuildingProperties
	 */
	protected void addNamespaces(String uriBase, int props_level, boolean hasBuildingElements,
			boolean hasBuildingProperties) {
		LBD_NS.BOT.addNameSpace(lbd_general_output_model);
		if (hasBuildingElements)
			LBD_NS.Product.addNameSpace(lbd_product_output_model);
		if (hasBuildingProperties) {
			LBD_NS.PROPS_NS.addNameSpace(lbd_property_output_model);
			LBD_NS.PROPS_NS.addNameSpace(lbd_general_output_model);
			if (props_level != 1)
				lbd_property_output_model.setNsPrefix("prov", OPM.prov_ns);

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
	}

	protected void connectElement(Resource bot_resource, Resource ifc_element) {
		Optional<String> predefined_type = IfcOWLUtils.getPredefinedData(ifc_element);
		Optional<Resource> ifcowl_type = RDFUtils.getType(ifc_element);
		Optional<Resource> bot_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			bot_type = getLBDProductType(ifcowl_type.get().getLocalName());
		}
		System.out.println("Connect element: " + ifc_element);
		if (bot_type.isPresent()) {
			Resource eo = createformattedURI(ifc_element, this.lbd_general_output_model, bot_type.get().getLocalName());
			String guid = IfcOWLUtils.getGUID(ifc_element, this.ifcOWL);
			String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
			Resource lbd_property_object = this.lbd_product_output_model.createResource(eo.getURI());
			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(bot_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			}
			lbd_property_object.addProperty(RDF.type, bot_type.get());
			eo.addProperty(RDF.type, LBD_NS.BOT.element);
			bot_resource.addProperty(LBD_NS.BOT.containsElement, eo);

			IfcOWLUtils.listPropertysets(ifc_element, ifcOWL).stream().map(rn -> rn.asResource())
					.forEach(propertyset -> {
						PropertySet p_set = this.propertysets.get(propertyset.getURI());
						if (p_set != null)
							p_set.connect(eo, uncompressed_guid);
					});
			addAttrributes(this.lbd_property_output_model, ifc_element, eo);

			IfcOWLUtils.listHosted_Elements(ifc_element, ifcOWL).stream().map(rn -> rn.asResource())
					.forEach(ifc_element2 -> {
						// if (eo.getLocalName().toLowerCase().contains("space"))
						// System.out.println("hosts: " + ifc_element + "--" + ifc_element2 + " bot:" +
						// eo);
						connectElement(eo, LBD_NS.BOT.hasSubElement, ifc_element2);
					});

			IfcOWLUtils.listAggregated_Elements(ifc_element, ifcOWL).stream().map(rn -> rn.asResource())
					.forEach(ifc_element2 -> {
						connectElement(eo, LBD_NS.BOT.hasSubElement, ifc_element2);
					});
		}
	}

	/**
	 * For a RDF LBD resource, creates the targetted object for the given property
	 * and adds a triple that connects them with the property. The literals of the
	 * elements and and the hosted elements are added as well.
	 * 
	 * @param bot_resource   The Jena Resource in the LBD output model in the Apacje
	 *                       model
	 * @param bot_property   The LBD ontology property
	 * @param ifcowl_element The corresponding ifcOWL elemeny
	 */
	protected void connectElement(Resource bot_resource, Property bot_property, Resource ifcowl_element) {
		Optional<String> predefined_type = IfcOWLUtils.getPredefinedData(ifcowl_element);
		Optional<Resource> ifcowl_type = RDFUtils.getType(ifcowl_element);
		Optional<Resource> lbd_product_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			lbd_product_type = getLBDProductType(ifcowl_type.get().getLocalName());
		}

		if (lbd_product_type.isPresent()) {
			Resource lbd_object = createformattedURI(ifcowl_element, this.lbd_general_output_model,
					lbd_product_type.get().getLocalName());
			Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_object.getURI());

			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(lbd_product_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			}

			lbd_property_object.addProperty(RDF.type, lbd_product_type.get());
			lbd_object.addProperty(RDF.type, LBD_NS.BOT.element);

			addAttrributes(this.lbd_property_output_model, ifcowl_element, lbd_object);
			bot_resource.addProperty(bot_property, lbd_object);
			IfcOWLUtils.listHosted_Elements(ifcowl_element, ifcOWL).stream().map(rn -> rn.asResource())
					.forEach(ifc_element2 -> {
						// if (lbd_object.getLocalName().toLowerCase().contains("space"))
						// System.out
						// .println("hosts2: " + ifcowl_element + "-->" + ifc_element2 + " bot:" +
						// lbd_object);
						connectElement(lbd_object, LBD_NS.BOT.hasSubElement, ifc_element2);
					});

			IfcOWLUtils.listAggregated_Elements(ifcowl_element, ifcOWL).stream().map(rn -> rn.asResource())
					.forEach(ifc_element2 -> {
						connectElement(lbd_object, LBD_NS.BOT.hasSubElement, ifc_element2);
					});
		} else {
			System.err.println("No type: " + ifcowl_element);
		}

	}

	protected Set<Resource> handledSttributes4resource = new HashSet<>();

	/**
	 * Creates and adds the literal triples from the original ifcOWL resource under
	 * the new LBD resource.
	 * 
	 * @param output_model The Apache Jena model where the conversion output is
	 *                     written
	 * @param r            The oroginal ifcOWL resource
	 * @param bot_r        The correspoinding resource in the output model. The LBD
	 *                     resource.
	 */
	protected void addAttrributes(Model output_model, Resource r, Resource bot_r) {
		if (!handledSttributes4resource.add(r)) // Tests if the attributes are added already
			return;
		String guid = IfcOWLUtils.getGUID(r, this.ifcOWL);
		String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
		final AttributeSet connected_attributes = new AttributeSet(this.uriBase, output_model, this.props_level,
				hasPropertiesBlankNodes);
		r.listProperties().forEachRemaining(s -> {
			String ps = s.getPredicate().getLocalName();
			Resource attr = s.getObject().asResource();
			Optional<Resource> atype = RDFUtils.getType(attr);
			if (ps.startsWith("tag_"))
				ps = "batid";
			final String property_string = ps; // Just to make variable final (needed in the following stream)
			if (atype.isPresent()) {
				if (atype.get().getLocalName().equals("IfcLabel")) {
					attr.listProperties(IfcOWLNameSpace.getHasString()).forEachRemaining(attr_s -> {
						if (attr_s.getObject().isLiteral()
								&& attr_s.getObject().asLiteral().getLexicalForm().length() > 0) {
							connected_attributes.putAnameValue(property_string, attr_s.getObject());
						}
					});

				} else if (atype.get().getLocalName().equals("IfcIdentifier")) {
					attr.listProperties(IfcOWLNameSpace.getHasString()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
				} else {
					attr.listProperties(IfcOWLNameSpace.getHasString()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
					attr.listProperties(ifcOWL.getHasInteger()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
					attr.listProperties(ifcOWL.getHasDouble()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
					attr.listProperties(ifcOWL.getHasBoolean()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
				}

			}
		});
		connected_attributes.connect(bot_r, uncompressed_guid);
	}

	/**
	 * Creates URIs for the elements in the output graph. The IfcRoot elements (that
	 * have a GUID) are given URI that contais the guid in the standard uncompressed
	 * format.
	 * 
	 * The uncompressed GUID form is created using the implementation by Tulke & Co.
	 * (The OPEN IFC JAVA TOOLBOX)
	 * 
	 * @param r            A ifcOWL RDF node in a Apache Jena RDF store.
	 * @param m            The Apache Jena RDF Store for the output.
	 * @param product_type The LBD product type to be shown on the URI
	 * @return
	 */
	protected Resource createformattedURI(Resource r, Model m, String product_type) {
		String guid = IfcOWLUtils.getGUID(r, this.ifcOWL);
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
			return guid_uri;
		}
	}

	/**
	 * This used the ifcowl_product_map map and returns one mapped class in a Linked
	 * Building Data ontology, if specified.
	 * 
	 * @param ifcType The IFC entity class
	 * @return The corresponding class Resource in a LBD ontology
	 */
	protected Optional<Resource> getLBDProductType(String ifcType) {
		List<Resource> ret = ifcowl_product_map.get(ifcType);
		if (ret == null) {
			return Optional.empty();
		} else if (ret.size() > 1) {
			// System.out.println("many " + ifcType);
			return Optional.empty();
		} else if (ret.size() > 0)
			return Optional.of(ret.get(0));
		else
			return Optional.empty();
	}

	/**
	 * Fills in the ifcowl_product_map map using the seealso ontology statemets at
	 * the Apache Jena RDF ontology model on the memory.
	 * 
	 * Uses also RDFS.subClassOf so that subclasses are included.
	 */
	protected void createIfcLBDProductMapping() {
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
				// mapping is independent of various IFC versions
				List<Resource> resource_list = ifcowl_product_map.getOrDefault(ifcowl_class.getLocalName(),
						new ArrayList<Resource>());
				ifcowl_product_map.put(ifcowl_class.getLocalName(), resource_list);
				resource_list.add(product_BE_ontology_statement.getSubject());
				// System.out.println("added to resource_list : " +
				// product_BE_ontology_statement.getSubject());
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
						// System.out.println(
						// ifcowl_subclass.getLocalName() + " ->> " +
						// product_BE_ontology_statement.getSubject());
						r_list.add(product_BE_ontology_statement.getSubject());
					}
				}

			}
		}

	}

	/**
	 * 
	 * The method converts an IFC STEP formatted file and returns an Apache Jena RDF
	 * memory storage model that contains the generated RDF triples.
	 * 
	 * Apache Jena: https://jena.apache.org/index.html
	 * 
	 * The generated temporsary file is used to reduce the temporary memory need and
	 * make it possible to convert larger models.
	 * 
	 * Sets the this.ontURI class variable. That is used to create the right ifcOWL
	 * version based ontology base URI that is used to create the ifcOWL version
	 * based peroperties and class URIs-
	 * 
	 * @param ifc_file the absolute path (For example: c:\ifcfiles\ifc_file.ifc) for
	 *                 the IFC file
	 * @param uriBase  the URL beginning for the elements in the ifcOWL TTL output
	 * @return the Jena Model that contains the ifcOWL attribute value (Abox)
	 *         output.
	 */
	protected Model readAndConvertIFC(String ifc_file, String uriBase) {
		try {
			IfcSpfReader rj = new IfcSpfReader();
			File tempFile = File.createTempFile("ifc", ".ttl");
			try {
				Model m = ModelFactory.createDefaultModel();
				this.ontURI = rj.convert(ifc_file, tempFile.getAbsolutePath(), uriBase);
				File t2 = filterContent(tempFile);
				RDFDataMgr.read(m, t2.getAbsolutePath());
				return m;
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				tempFile.deleteOnExit();
			}

		} catch (Exception e) {
			eventBus.post(new IFCtoLBD_SystemStatusEvent(
					"Error : " + e.getMessage() + " line:" + e.getStackTrace()[0].getLineNumber()));
			e.printStackTrace();

		}
		System.err.println("IFC-RDF conversion not done");
		return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
	}

	private File filterContent(File whole_content_file) {
		File tempFile = null;
		try {
			tempFile = File.createTempFile("ifc", ".ttl");
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
				try (BufferedReader br = new BufferedReader(new FileReader(whole_content_file))) {
					String line;
					while ((line = br.readLine()) != null) {
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

	/**
	 * This internal method reads in all the associated ontologies so that ontology
	 * inference can ne used during the conversion.
	 * 
	 * @param ifc_file the absolute path (For example: c:\ifcfiles\ifc_file.ifc) for
	 *                 the IFC file
	 */
	protected void readInOntologies(String ifc_file) {
		IfcOWLUtils.readIfcOWLOntology(ifc_file, ontology_model);
		IfcOWLUtils.readIfcOWLOntology(ifc_file, ifcowl_model);

		RDFUtils.readInOntologyTTL(ontology_model, "prod.ttl", this.eventBus);
		// RDFUtils.readInOntologyTTL(ontology_model,
		// "prod_building_elements.ttl",this.eventBus);
		RDFUtils.readInOntologyTTL(ontology_model, "beo_ontology.ttl", this.eventBus);
		RDFUtils.readInOntologyTTL(ontology_model, "prod_furnishing.ttl", this.eventBus);
		// RDFUtils.readInOntologyTTL(ontology_model, "prod_mep.ttl",this.eventBus);
		RDFUtils.readInOntologyTTL(ontology_model, "mep_ontology.ttl", this.eventBus);

		RDFUtils.readInOntologyTTL(ontology_model, "psetdef.ttl", this.eventBus);
		List<String> files = FileUtils.getListofFiles("pset", ".ttl");
		for (String file : files) {
			file = file.substring(file.indexOf("pset"));
			file = file.replaceAll("\\\\", "/");
			RDFUtils.readInOntologyTTL(ontology_model, file, this.eventBus);
			System.out.println("read ontology file : " + file);
		}
	}

	/**
	 * 
	 * Adds Geolocation triples to the RDF model. Ontology: http://www.opengis.net
	 */
	protected void addGeolocation2BOT() {

		IFC_Geolocation c = new IFC_Geolocation();
		String wkt_point;
		try {
			wkt_point = c.addGeolocation(ifcowl_model);
		} catch (Exception e) {
			return; // no geolocation
		}

		IfcOWLUtils.listSites(ifcOWL, ifcowl_model).stream().map(rn -> rn.asResource()).forEach(site -> {
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
			String guid_site = IfcOWLUtils.getGUID(site, this.ifcOWL);
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

		eventBus.post(new IFCtoLBD_SystemStatusEvent("LDB geom read"));

	}
}
