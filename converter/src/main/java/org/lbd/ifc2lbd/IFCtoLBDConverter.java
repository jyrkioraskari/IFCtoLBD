package org.lbd.ifc2lbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
import org.lbd.ifc2lbd.ns.BOT;
import org.lbd.ifc2lbd.ns.IfcOWLNameSpace;
import org.lbd.ifc2lbd.ns.OPM;
import org.lbd.ifc2lbd.rdfpath.InvRDFStep;
import org.lbd.ifc2lbd.rdfpath.RDFStep;

import com.google.common.eventbus.EventBus;
import com.openifctools.guidcompressor.GuidCompressor;

import nl.tue.ddss.convert.Header;
import nl.tue.ddss.convert.HeaderParser;
import nl.tue.ddss.convert.IfcVersion;
import nl.tue.ddss.convert.IfcVersionException;

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
	private Model ontology_model = null;
	private Map<String, List<Resource>> ifcowl_product_map = new HashMap<>();
	private final String uriBase;

	private Optional<String> ontURI = Optional.empty();
	private IfcOWLNameSpace ifcOWL;

	// URI-property set
	private Map<String, PropertySet> propertysets = new HashMap<>();
	private final boolean hasBuildingElements;
	private final boolean hasBuildingProperties;

	public IFCtoLBDConverter(String ifc_filename, String uriBase, String target_file, int props_level,
			boolean hasBuildingElements, boolean hasBuildingProperties) {
		this.hasBuildingElements=hasBuildingElements;
		this.hasBuildingProperties=hasBuildingProperties;
		
		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = uriBase;
		ontology_model = ModelFactory.createDefaultModel();
		eventBus.post(new SystemStatusEvent("IFCtoRDF conversion"));
		ifcowl_model = readAndConvertIFC(ifc_filename, uriBase); // Before: readInOntologies(ifc_filename);

		eventBus.post(new SystemStatusEvent("Reading in ontologies"));

		readInOntologies(ifc_filename);
		createIfcBOTMapping();

		Model output_model = ModelFactory.createDefaultModel();
		BOT.addNameSpaces(output_model);
		output_model.setNsPrefix("rdfs", RDFS.uri);
		output_model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
		output_model.setNsPrefix("inst", uriBase);
		output_model.setNsPrefix("props", OPM.props_ns);
		output_model.setNsPrefix("opm", OPM.opm_ns);
		output_model.setNsPrefix("prov", OPM.prov_ns);

		output_model.setNsPrefix("ifc", this.uriBase);

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

				RDFStep[] pname_path = { new RDFStep(ifcOWL.getName_IfcRoot()), new RDFStep(ifcOWL.getHasstring()) };
				final List<RDFNode> propertyset_name = new ArrayList<>();
				pathQuery(propertyset, pname_path).forEach(name -> propertyset_name.add(name));

				RDFStep[] path = { new RDFStep(ifcOWL.getHasProperties_IfcPropertySet()) };
				pathQuery(propertyset, path).forEach(propertySingleValue -> {

					RDFStep[] name_path = { new RDFStep(ifcOWL.getName_IfcProperty()),
							new RDFStep(ifcOWL.getHasstring()) };
					final List<RDFNode> property_name = new ArrayList<>();
					pathQuery(propertySingleValue.asResource(), name_path).forEach(name -> property_name.add(name));

					final List<RDFNode> property_value = new ArrayList<>();

					RDFStep[] value_pathS = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
							new RDFStep(ifcOWL.getHasstring()) };
					pathQuery(propertySingleValue.asResource(), value_pathS)
							.forEach(value -> property_value.add(value));

					RDFStep[] value_pathD = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
							new RDFStep(ifcOWL.getHasdouble()) };
					pathQuery(propertySingleValue.asResource(), value_pathD)
							.forEach(value -> property_value.add(value));

					RDFStep[] value_pathI = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
							new RDFStep(ifcOWL.getHasinteger()) };
					pathQuery(propertySingleValue.asResource(), value_pathI)
							.forEach(value -> property_value.add(value));

					RDFStep[] value_pathB = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
							new RDFStep(ifcOWL.getHasboolean()) };
					pathQuery(propertySingleValue.asResource(), value_pathB)
							.forEach(value -> property_value.add(value));

					RDFStep[] value_pathL = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()),
							new RDFStep(ifcOWL.getHaslogical()) };
					pathQuery(propertySingleValue.asResource(), value_pathL)
							.forEach(value -> property_value.add(value));

					String guid = getGUID(propertyset);
					if (guid != null) {
						Resource pset = output_model
								.createResource(OPM.props_ns + GuidCompressor.uncompressGuidString(guid));

						if (property_name.size() > 0 && property_value.size() > 0) {
							RDFNode pname = property_name.get(0);
							RDFNode pvalue = property_value.get(0);
							if (!pname.toString().equals(pvalue.toString())) {
								PropertySet ps = this.propertysets.get(propertyset.getURI());
								if (ps == null) {
									if (!propertyset_name.isEmpty())
										ps = new PropertySet(propertyset_name.get(0).toString(), pset, props_level);
									else
										ps = new PropertySet("", pset, props_level);
									this.propertysets.put(propertyset.getURI(), ps);
								}
								if (pvalue.toString().trim().length() > 0) {
									ps.put(pname.toString(), pvalue);
								}
							}
						} else {
							RDFNode pname = property_name.get(0);
							PropertySet ps = this.propertysets.get(propertyset.getURI());
							if (ps == null) {
								if (!propertyset_name.isEmpty())
									ps = new PropertySet(propertyset_name.get(0).toString(), pset, props_level);
								else
									ps = new PropertySet("", pset, props_level);
								this.propertysets.put(propertyset.getURI(), ps);
							}
							ps.put(pname.toString(), propertySingleValue);
							copyTriples(0, propertySingleValue, output_model);
						}
					}
				});
			});

			eventBus.post(new SystemStatusEvent("LDB properties read"));
		}

		listSites().stream().map(rn -> rn.asResource()).forEach(site -> {
			Resource sio = createformattedURI(site, output_model, "Site");
			addAttrributes(site.asResource(), sio);

			sio.addProperty(RDF.type, BOT.site);

			listPropertysets(site).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
				PropertySet p_set = this.propertysets.get(propertyset.getURI());
				if (p_set != null) {
					p_set.connect(sio);
				}
			});

			listBuildings(site).stream().map(rn -> rn.asResource()).forEach(building -> {
				if (!getType(building.asResource()).get().getURI()
						.equals("http://www.buildingsmart-tech.org/ifcOWL/IFC2X3_TC1#IfcBuilding"))
					return;
				Resource bo = createformattedURI(building, output_model, "Building");
				addAttrributes(building, bo);

				bo.addProperty(RDF.type, BOT.building);
				sio.addProperty(BOT.hasBuilding, bo);

				listPropertysets(building).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
					PropertySet p_set = this.propertysets.get(propertyset.getURI());
					if (p_set != null) {
						p_set.connect(bo);
					}
				});

				listStoreys(building).stream().map(rn -> rn.asResource()).forEach(storey -> {
					eventBus.post(new SystemStatusEvent("Storey: " + storey.getLocalName()));

					if (!getType(storey.asResource()).get().getURI()
							.equals("http://www.buildingsmart-tech.org/ifcOWL/IFC2X3_TC1#IfcBuildingStorey"))
						return;

					Resource so = createformattedURI(storey, output_model, "Storey");
					addAttrributes(storey, so);

					bo.addProperty(BOT.hasStorey, so);
					so.addProperty(RDF.type, BOT.storey);

					listPropertysets(storey).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
						PropertySet p_set = this.propertysets.get(propertyset.getURI());
						if (p_set != null)
							p_set.connect(so);
					});
					
					listContained_StoreyElements(storey).stream().map(rn -> rn.asResource()).forEach(element -> {
						connectElement(output_model, so, element);
					});

					listStoreySpaces(storey.asResource()).stream().forEach(space -> {
						if (!getType(space.asResource()).get().getURI()
								.equals("http://www.buildingsmart-tech.org/ifcOWL/IFC2X3_TC1#IfcSpace"))
							return;
						Resource spo = createformattedURI(space.asResource(), output_model, "Space");
						addAttrributes(space.asResource(), spo);

						so.addProperty(BOT.hasSpace, spo);
						spo.addProperty(RDF.type, BOT.space);
						listContained_SpaceElements(space.asResource()).stream().map(rn -> rn.asResource())
								.forEach(element -> {
									connectElement(output_model, spo, element);
								});

						listAdjacent_SpaceElements(space.asResource()).stream().map(rn -> rn.asResource())
								.forEach(element -> {
									connectElement(output_model, spo, BOT.adjacentElement, element);
								});

						listPropertysets(space.asResource()).stream().map(rn -> rn.asResource())
								.forEach(propertyset -> {
									PropertySet p_set = this.propertysets.get(propertyset.getURI());
									if (p_set != null) {
										p_set.connect(spo);
									}
								});
					});
				});
			});
		});

		// String out_filename = ifc_filename.split("\\.")[0] + "_BOT.ttl";
		FileOutputStream fo = null;
		try {
			fo = new FileOutputStream(new File(target_file));
			output_model.write(fo, "TTL");
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

		System.out.println("Conversion done. File is: " + target_file);
		eventBus.post(new SystemStatusEvent("Done. File is: " + target_file));

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

	private void connectElement(Model output_model, Resource bot_resource, Resource ifc_element) {
		Optional<String> predefined_type = getPredefinedData(ifc_element);
		Optional<Resource> ifcowl_type = getType(ifc_element);
		Optional<Resource> bot_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			bot_type = getBOTProductType(ifcowl_type.get().getLocalName());
		}

		if (bot_type.isPresent()) {
			Resource eo = createformattedURI(ifc_element, output_model, bot_type.get().getLocalName());
			if (predefined_type.isPresent()) {
				Resource product = output_model.createResource(bot_type.get().getURI() + "-" + predefined_type.get());
				eo.addProperty(RDF.type, product);
			} else
				eo.addProperty(RDF.type, bot_type.get());
			eo.addProperty(RDF.type, BOT.element);
			bot_resource.addProperty(BOT.containsElement, eo);

			listPropertysets(ifc_element).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
				PropertySet p_set = this.propertysets.get(propertyset.getURI());
				if (p_set != null)
					p_set.connect(eo);
			});
			addAttrributes(ifc_element, eo);

			listHosted_Elements(ifc_element).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
				connectElement(output_model, eo, BOT.hostsElement, ifc_element2);
			});

			listAggregated_Elements(ifc_element).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
				connectElement(output_model, eo, BOT.aggregates, ifc_element2);
			});
		} // else
			// System.out.println("no bot for: " + ifc_element.getLocalName());
	}

	private void connectElement(Model output_model, Resource bot_resource, Property bot_property,
			Resource ifc_element) {
		Optional<Resource> ifcowl_type = getType(ifc_element);
		Optional<Resource> bot_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			bot_type = getBOTProductType(ifcowl_type.get().getLocalName());
		}

		if (bot_type.isPresent()) {
			Resource bot_object = createformattedURI(ifc_element, output_model, bot_type.get().getLocalName());
			addLabel(ifc_element, bot_object);
			addDescription(ifc_element, bot_object);
			bot_resource.addProperty(bot_property, bot_object);
		}
	}

	private void addAttrributes(Resource r, Resource bot_r) {
		r.listProperties().forEachRemaining(s -> {
			String ps = s.getPredicate().getLocalName();
			Resource attr = s.getObject().asResource();
			Optional<Resource> atype = getType(attr);
			if (ps.startsWith("tag_"))
				ps = "batid";
			String property_string = ps; // Just to make it effectively final
			if (atype.isPresent())
				if (atype.get().getLocalName().equals("IfcLabel")) {
					attr.listProperties(ifcOWL.getHasstring()).forEachRemaining(attr_s -> {
						if (attr_s.getObject().isLiteral()
								&& attr_s.getObject().asLiteral().getLexicalForm().length() > 0)
							bot_r.addProperty(BOT.LocalProperty.getProperty(property_string), attr_s.getObject());
					});
				}

			if (atype.get().getLocalName().equals("IfcIdentifier")) {
				attr.listProperties(ifcOWL.getHasstring()).forEachRemaining(attr_s -> bot_r
						.addProperty(BOT.LocalProperty.getProperty(property_string), attr_s.getObject()));
			}
		});
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

	private void addDescription(Resource ifc_r, final Resource bot_r) {
		ifc_r.listProperties(ifcOWL.getDescription()).toList()
				.forEach(x -> x.getObject().asResource().listProperties(ifcOWL.getHasstring())
						.forEachRemaining(y -> bot_r.addProperty(RDFS.comment, y.getObject())));
	}

	private void addLabel(Resource ifc_r, final Resource bot_r) {
		ifc_r.listProperties(ifcOWL.getName()).toList()
				.forEach(x -> x.getObject().asResource().listProperties(ifcOWL.getHasstring())
						.forEachRemaining(y -> bot_r.addProperty(RDFS.label, y.getObject())));
	}

	private void addLongName(Resource ifc_r, final Resource bot_r) {
		ifc_r.listProperties(ifcOWL.getLongName()).toList()
				.forEach(x -> x.getObject().asResource().listProperties(ifcOWL.getHasstring())
						.forEachRemaining(y -> bot_r.addProperty(RDFS.label, y.getObject())));
	}

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
			Literal l = m.createLiteral(guid);
			guid_uri.addLiteral(BOT.LocalProperty.getProperty("guid"), l);
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
			String guid = s.getObject().asResource().getProperty(ifcOWL.getHasstring()).getObject().asLiteral()
					.getLexicalForm();
			return guid;
		}
		return null;
	}

	public Optional<Resource> getBOTProductType(String ifcType) {
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
		RDFStep[] path2 = { new RDFStep(ifcOWL.getProperty(" objectPlacement_IfcProduct")),
				new InvRDFStep(ifcOWL.getProperty("placementRelTo_IfcLocalPlacement")),
				new InvRDFStep(ifcOWL.getProperty("objectPlacement_IfcProduct")) };
		ret.addAll(pathQuery(storey, path2));
		return ret;
	}

	private List<RDFNode> listContained_SpaceElements(Resource storey) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingStructure_IfcRelContainedInSpatialStructure")),
				new RDFStep(ifcOWL.getProperty("relatedElements_IfcRelContainedInSpatialStructure")) };
		ret = pathQuery(storey, path1);
		return ret;
	}

	private List<RDFNode> listAdjacent_SpaceElements(Resource storey) {
		List<RDFNode> ret;

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatingSpace_IfcRelSpaceBoundary")),
				new RDFStep(ifcOWL.getProperty("relatedBuildingElement_IfcRelSpaceBoundary")) };
		ret = pathQuery(storey, path1);
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

		RDFStep[] path1 = { new InvRDFStep(ifcOWL.getProperty("relatedObjects_IfcRelDecomposes")),
				new RDFStep(ifcOWL.getProperty("relatingObject_IfcRelDecomposes")) };
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
			Optional<Resource> product_type = getBOTProductType(t2.getObject().asResource().getLocalName());
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

	/* @formatter:off */
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
	/* @formatter:on */

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

	public static void main(String[] args) {
		if (args.length > 2) {
			new IFCtoLBDConverter(args[0], args[1], args[2], 2, true, true);
		} else
			System.out.println("Usage: IFCtoLBDConverter ifc_filename base_uri targer_file");
	}

}
