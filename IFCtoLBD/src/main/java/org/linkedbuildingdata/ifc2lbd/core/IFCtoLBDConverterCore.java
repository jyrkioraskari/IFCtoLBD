
package org.linkedbuildingdata.ifc2lbd.core;

import java.io.File;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemErrorEvent;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemExit;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.core.utils.ChangeableOptonal;
import org.linkedbuildingdata.ifc2lbd.core.utils.FileUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.LBD_RDF_Utils;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.AttributeSet;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.PropertySet;
import org.linkedbuildingdata.ifc2lbd.geo.IfcOWL_GeolocationUtil;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.GEO;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;
import org.linkedbuildingdata.ifc2lbd.namespace.LBD;
import org.linkedbuildingdata.ifc2lbd.namespace.OMG;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;
import org.linkedbuildingdata.ifc2lbd.namespace.Product;
import org.linkedbuildingdata.ifc2lbd.namespace.SMLS;
import org.linkedbuildingdata.ifc2lbd.namespace.UNIT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.openifctools.guidcompressor.GuidCompressor;

import de.rwth_aachen.dc.lbd.BoundingBox;
import de.rwth_aachen.dc.lbd.IFCGeometry;
import de.rwth_aachen.dc.lbd.ObjDescription;

/*
 *  Copyright (c) 2017,2018,2019.2020,2024 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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
	public final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();

	private Set<String> selected_types; // The element types that are included in the output

	public Model ifcowl_model;
	protected Model ontology_model = null;
	protected Map<String, List<Resource>> ifcowl_product_map = new HashMap<>();
	protected Optional<String> uriBase = Optional.empty();

	public Optional<String> ontURI = Optional.empty();
	public IfcOWL ifcOWL;

	// URI-property set
	protected Map<String, PropertySet> propertysets = new HashMap<>();

	protected int props_level;
	protected boolean hasPropertiesBlankNodes;

	protected Model lbd_general_output_model;
	protected Model lbd_product_output_model;
	protected Model lbd_property_output_model;

	protected IFCGeometry ifc_geometry = null;

	protected final Set<Resource> has_geometry = new HashSet<>();

	protected RTree<Resource, Geometry> rtree;
	protected RTree<Resource, Geometry> rtree_walls;
	protected final Map<Rectangle, Resource> rtree_map = new HashMap<>();

	private boolean exportIfcOWL_setting = false;
	protected boolean hasBoundingBoxWKT = false;

	private boolean hasHierarchicalNaming_setting = false;
	protected boolean hasSimplified_properties = false;

	protected boolean hasNonLBDElement = true;

	private Map<String, String> property_replace_map = new HashMap<>(); // allows users to replace default properties
																		// (for now foe the attributes)
	private Dataset lbd_dataset = null;

	public IFCtoLBDConverterCore() {
		this.eventBus.register(this);
	}

	private Set<Resource> included_elements = new HashSet<>(); // Resources of included elements

	protected void conversion(String target_file, boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel,
			boolean hasBuildingProperties, boolean hasSeparatePropertiesModel, boolean hasGeolocation,
			boolean hasGeometry, boolean exportIfcOWL, @SuppressWarnings("unused") boolean namedGraphs,
			boolean hasHierarchicalNaming, boolean hasInterfaces) {
		this.handledAttributes4resource.clear(); // less performant but more dynamic
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("The LBD conversion starts"));
		this.exportIfcOWL_setting = exportIfcOWL;
		this.hasHierarchicalNaming_setting = hasHierarchicalNaming;
		this.included_elements.clear();
		if (hasGeometry)
		{
			this.rtree = RTree.dimensions(3).create();
			this.rtree_walls = RTree.dimensions(3).create();
		}

		List<RDFNode> sites = IfcOWLUtils.listSites(this.ifcOWL, this.ifcowl_model);
		if (!sites.isEmpty()) {
			sites.stream().map(RDFNode::asResource).forEach(site -> {
				Resource lbd_site = LBD_RDF_Utils.createformattedURIRecource(site, this.lbd_general_output_model,
						"Site", this.ifcOWL, this.uriBase.get(), this.exportIfcOWL_setting);
				String guid_site = IfcOWLUtils.getGUID(site, this.ifcOWL);
				String uncompressed_guid_site = GuidCompressor.uncompressGuidString(guid_site);
				addAttrributes(this.lbd_property_output_model, site.asResource(), lbd_site);

				lbd_site.addProperty(RDF.type, BOT.site);
				addGeometry(lbd_site, guid_site);

				IfcOWLUtils.listPropertysets(site, this.ifcOWL).stream().map(RDFNode::asResource)
						.forEach(propertyset -> {
							PropertySet p_set = this.propertysets.get(propertyset.getURI());
							if (p_set != null) {
								p_set.connect(lbd_site, uncompressed_guid_site);
							}
						});

				IfcOWLUtils.listBuildings(site, this.ifcOWL).stream().map(RDFNode::asResource)
						.forEach(building -> handle_building(lbd_site, building));
			});
		} else {
			IfcOWLUtils.listBuildings(this.ifcOWL, this.ifcowl_model).stream().map(RDFNode::asResource)
					.forEach(this::handle_building);
		}

		this.ifcowl_model.listStatements().forEachRemaining(st -> {
			if (st.getPredicate().getLocalName().toLowerCase().contains("globalid_ifcroot")) {
				Resource ifcOWL_element = st.getSubject();
				if (isIfcElement(ifcOWL_element))
					if (!this.included_elements.contains(ifcOWL_element)) {
						addSingleElement(ifcOWL_element);
					}
			}

		});

		if (hasGeolocation) {
			this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Geo location is calculated."));
			try {
				this.ontURI.ifPresent(s -> IfcOWL_GeolocationUtil.addGeolocation2BOT(this.ifcowl_model, this.ifcOWL,
						this.lbd_general_output_model, this.uriBase.get(), s));
			} catch (Exception e) {
				e.printStackTrace();
				this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Info : No geolocation"));
			}
		}

		if (hasBuildingElements) {
			if (hasSeparateBuildingElementsModel) {
				if (target_file != null) {
					String out_products_filename = target_file.substring(0, target_file.lastIndexOf("."))
							+ "_building_elements.ttl";
					RDFUtils.writeModelRDFStream(this.ifcowl_model, out_products_filename, this.eventBus);
					this.eventBus.post(
							new IFCtoLBD_SystemStatusEvent("Building elements file is: " + out_products_filename));
				}
			} else
				this.lbd_general_output_model.add(this.lbd_product_output_model);
		}

		if (hasGeometry)
		{
		   finish_geometry(hasInterfaces);
		}
		
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Writing out the results."));

		if (hasBuildingProperties) {
			if (hasSeparatePropertiesModel) {
				if (target_file != null) {
					String out_properties_filename = target_file.substring(0, target_file.lastIndexOf("."))
							+ "_element_properties.ttl";
					RDFUtils.writeModelRDFStream(this.lbd_property_output_model, out_properties_filename,
							this.eventBus);
					this.eventBus.post(new IFCtoLBD_SystemStatusEvent(
							"Building elements properties file is: " + out_properties_filename));
				} else
					this.lbd_general_output_model.add(this.lbd_property_output_model);
			} else
				this.lbd_general_output_model.add(this.lbd_property_output_model);
		}
		if (target_file != null)
			RDFUtils.writeModelRDFStream(this.lbd_general_output_model, target_file, this.eventBus);

		if (target_file != null) {
			if (!hasSeparatePropertiesModel || !hasSeparateBuildingElementsModel) {
				String target_trig = target_file.replaceAll(".ttl", ".trig");
				if (this.uriBase.isPresent() && this.lbd_dataset != null) {
					this.lbd_dataset.getDefaultModel().add(this.lbd_general_output_model);
					this.lbd_dataset.addNamedModel(this.uriBase.get() + "product", this.lbd_product_output_model);
					this.lbd_dataset.addNamedModel(this.uriBase.get() + "property", this.lbd_property_output_model);
				}

				RDFUtils.writeDataset(this.lbd_dataset, target_trig, this.eventBus);
				this.eventBus.post(
						new IFCtoLBD_SystemStatusEvent("Done. Linked Building Data graphs file is: " + target_trig));
			}
			this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Done. Linked Building Data file is: " + target_file));
		}

	}

	private void handle_building(Resource ifcowl_building) {
		handle_building(null, ifcowl_building);
	}

	private void handle_building(Resource lbd_site, Resource building) {
		// String building_url_name = IfcOWLUtils.getURLEncodedName(building,
		// this.ifcOWL);

		if (!RDFUtils.getType(building.asResource()).get().getURI().endsWith("#IfcBuilding")) {
			System.err.println("Not an #IfcBuilding");
			return;
		}
		Resource lbd_building;
		if (this.hasHierarchicalNaming_setting)
			lbd_building = LBD_RDF_Utils.createformattedHierarchicalURIRecource(building, this.lbd_general_output_model,
					"Building", this.ifcOWL, this.uriBase.get(), this.exportIfcOWL_setting);
		else
			lbd_building = LBD_RDF_Utils.createformattedURIRecource(building, this.lbd_general_output_model, "Building",
					this.ifcOWL, this.uriBase.get(), this.exportIfcOWL_setting);

		String guid_building = IfcOWLUtils.getGUID(building, this.ifcOWL);
		String uncompressed_guid_building = GuidCompressor.uncompressGuidString(guid_building);
		addAttrributes(this.lbd_property_output_model, building, lbd_building);

		lbd_building.addProperty(RDF.type, BOT.building);
		addGeometry(lbd_building, guid_building);
		if (lbd_site != null)
			lbd_site.addProperty(BOT.hasBuilding, lbd_building);

		IfcOWLUtils.listPropertysets(building, this.ifcOWL).stream().map(RDFNode::asResource).forEach(propertyset -> {
			PropertySet p_set = this.propertysets.get(propertyset.getURI());
			if (p_set != null) {
				p_set.connect(lbd_building, uncompressed_guid_building);
			}
		});

		IfcOWLUtils.listStoreys(building, this.ifcOWL).stream().map(RDFNode::asResource).forEach(storey -> {
			this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Storey: " + storey.getLocalName()));

			if (!RDFUtils.getType(storey.asResource()).get().getURI().endsWith("#IfcBuildingStorey")) {
				System.err.println("No an #IfcBuildingStorey");
				return;
			}
			Resource lbd_storey;
			if (this.hasHierarchicalNaming_setting)
				lbd_storey = LBD_RDF_Utils.createformattedHierarchicalURIRecource(storey, this.lbd_general_output_model,
						"Storey", this.ifcOWL, lbd_building, this.exportIfcOWL_setting);
			else
				lbd_storey = LBD_RDF_Utils.createformattedURIRecource(storey, this.lbd_general_output_model, "Storey",
						this.ifcOWL, this.uriBase.get(), this.exportIfcOWL_setting);
			String guid_storey = IfcOWLUtils.getGUID(storey, this.ifcOWL);
			String uncompressed_guid_storey = GuidCompressor.uncompressGuidString(guid_storey);
			addAttrributes(this.lbd_property_output_model, storey, lbd_storey);

			lbd_building.addProperty(BOT.hasStorey, lbd_storey);
			addGeometry(lbd_storey, guid_storey);
			lbd_storey.addProperty(RDF.type, BOT.storey);

			IfcOWLUtils.listPropertysets(storey, this.ifcOWL).stream().map(RDFNode::asResource).forEach(propertyset -> {
				PropertySet p_set = this.propertysets.get(propertyset.getURI());
				if (p_set != null)
					p_set.connect(lbd_storey, uncompressed_guid_storey);
			});

			IfcOWLUtils.listContained_StoreyElements(storey, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(element -> {
						if (RDFUtils.getType(element.asResource()).get().getURI().endsWith("#IfcSpace"))
							return;
						connectIfcContaidedElement(lbd_storey, element);
					});

			IfcOWLUtils.listStoreySpaces(storey.asResource(), this.ifcOWL).stream().forEach(space -> {
				if (!RDFUtils.getType(space.asResource()).get().getURI().endsWith("#IfcSpace"))
					return;
				Resource spo;
				if (this.hasHierarchicalNaming_setting)
					spo = LBD_RDF_Utils.createformattedHierarchicalURIRecource(space.asResource(),
							this.lbd_general_output_model, "Space", this.ifcOWL, lbd_storey, this.exportIfcOWL_setting);
				else
					spo = LBD_RDF_Utils.createformattedURIRecource(space.asResource(), this.lbd_general_output_model,
							"Space", this.ifcOWL, this.uriBase.get(), this.exportIfcOWL_setting);
				String guid_space = IfcOWLUtils.getGUID(space.asResource(), this.ifcOWL);
				String uncompressed_guid_space = GuidCompressor.uncompressGuidString(guid_space);
				addAttrributes(this.lbd_property_output_model, space.asResource(), spo);

				lbd_storey.addProperty(BOT.hasSpace, spo);
				addGeometry(spo, guid_space);
				spo.addProperty(RDF.type, BOT.space);

				final ChangeableOptonal<Boolean> isExternal = new ChangeableOptonal<>();
				IfcOWLUtils.listPropertysets(space.asResource(), this.ifcOWL).stream().map(RDFNode::asResource)
						.forEach(propertyset -> {
							PropertySet p_set = this.propertysets.get(propertyset.getURI());
							if (p_set != null) {
								p_set.connect(spo, uncompressed_guid_space);
								if (!isExternal.isPresent())
									isExternal.overwriteIfPresent(p_set.isExternal());
							}
						});

				IfcOWLUtils.listContained_SpaceElements(space.asResource(), this.ifcOWL).stream()
						.map(RDFNode::asResource).forEach(element -> {
							Resource lbd_element = connectIfcContaidedElement(spo, element);
							if (lbd_element != null)
								storey.addProperty(BOT.containsElement, lbd_element);
						});

				IfcOWLUtils.listAdjacent_SpaceElements(space.asResource(), this.ifcOWL).stream()
						.map(RDFNode::asResource).forEach(element -> {
							Resource lbd_element = connectElement(spo, BOT.adjacentElement, element);
							if (isExternal.isPresent() && isExternal.get()) {
								if (lbd_element != null)
									storey.addProperty(BOT.adjacentElement, lbd_element);

							} else {
								if (lbd_element != null)
									storey.addProperty(BOT.containsElement, lbd_element);

							}
						});

			});
		});
	}

	private static boolean isIfcElement(Resource s) {
		Optional<Resource> type = RDFUtils.getType(s);
		if (type.isPresent()) {
			Resource sc = IfcOWLUtils.getSuperClass(type.get());
			while (sc != null) {
				if (sc.asResource().getLocalName().equals("IfcElement"))
					return true;
				sc = IfcOWLUtils.getSuperClass(sc);

			}

		}
		return false;
	}

	private Property fogasObj = null;

	private void addGeometry(Resource lbd_resource, String guid) {
		if (this.ifc_geometry == null)
			return;
		try {

			if (this.has_geometry.add(lbd_resource)) {
				BoundingBox bb = this.ifc_geometry.getBoundingBox(guid);
				ObjDescription obj = this.ifc_geometry.getOBJ(guid);

				Resource sp_geometry = this.lbd_general_output_model
						.createResource(lbd_resource.getURI() + "_geometry");
				
				sp_geometry.addProperty(RDF.type, GEO.Geometry);
				if (bb != null && obj != null)
					lbd_resource.addProperty(OMG.hasGeometry, sp_geometry);
				// lbd_resource.addProperty(GEO.hasGeometry, sp_geometry);
				else
					System.err.println("The elemenet has no geometry: " + lbd_resource.getURI());
				if (bb != null) {
					if (this.hasBoundingBoxWKT) {
						Literal wktLiteral=this.lbd_general_output_model.createTypedLiteral(bb.toString(),GEO.wktLiteral);
						sp_geometry.addLiteral(GEO.asWKT, wktLiteral);
					} else {
						Resource sp_bb = this.lbd_general_output_model
								.createResource(lbd_resource.getURI() + "_geometry_bb");
						sp_geometry.addProperty(LBD.hasBoundingBox, sp_bb);
						sp_bb.addLiteral(LBD.xmin, bb.getMin().x);
						sp_bb.addLiteral(LBD.xmax, bb.getMax().x);
						sp_bb.addLiteral(LBD.ymin, bb.getMin().y);
						sp_bb.addLiteral(LBD.ymax, bb.getMax().y);
						sp_bb.addLiteral(LBD.zmin, bb.getMin().z);
						sp_bb.addLiteral(LBD.zmax, bb.getMax().z);
					}
					Rectangle rectangle = Rectangle.create(bb.getMin().x, bb.getMin().y, bb.getMin().z, bb.getMax().x,
							bb.getMax().y, bb.getMax().z);

					// immutable
					this.rtree = this.rtree.add(lbd_resource, rectangle); // rtree is
					this.rtree_map.put(rectangle, lbd_resource);

					if (obj != null) {
						if (this.fogasObj == null)
							this.fogasObj = this.lbd_general_output_model
									.createProperty("https://w3id.org/fog#asObj_v3.0-obj");
						Literal base64 = this.lbd_general_output_model.createTypedLiteral(obj.toString(),
								"https://www.w3.org/2001/XMLSchema#base64Binary");
						sp_geometry.addLiteral(this.fogasObj, base64);
					}
					// TODO if not all is in place yet
					if(lbd_resource.toString().toLowerCase().contains("furniture"))
						return;
					if(lbd_resource.toString().toLowerCase().contains("slab"))
						return;
					if(lbd_resource.toString().toLowerCase().contains("railing"))
						return;				
					if(lbd_resource.toString().toLowerCase().contains("beam"))
						return;				
					
					if(lbd_resource.toString().toLowerCase().contains("stairflight"))
						return;				
					if(lbd_resource.toString().toLowerCase().contains("member"))
						return;

					Iterable<Entry<Resource, Geometry>> results = this.rtree.search(rectangle);
					for (Entry<Resource, Geometry> e : results) {
						if (e.value() != lbd_resource)
							e.value().addProperty(LBD.containsInBoundingBox, lbd_resource);
					}

					double x1 = bb.getMin().x, y1 = bb.getMin().y, z1 = bb.getMin().z;
					double x2 = bb.getMax().x, y2 = bb.getMax().y, z2 = bb.getMax().z;

					// Front Face
					Rectangle frontFace = Rectangle.create(x1, y1, z1, x2, y2, z1);

					// Back Face
					Rectangle backFace = Rectangle.create(x1, y1, z2, x2, y2, z2);

					// Left Face
					Rectangle leftFace = Rectangle.create(x1, y1, z1, x1, y2, z2);

					// Right Face
					Rectangle rightFace = Rectangle.create(x2, y1, z1, x2, y2, z2);

					// Top Face
					Rectangle topFace = Rectangle.create(x1, y2, z1, x2, y2, z2);

					// Bottom Face
					Rectangle bottomFace = Rectangle.create(x1, y1, z1, x2, y1, z2);

					
					this.rtree_walls = this.rtree_walls.add(lbd_resource, frontFace);
					this.rtree_walls = this.rtree_walls.add(lbd_resource, backFace);
					this.rtree_walls = this.rtree_walls.add(lbd_resource, leftFace);
					this.rtree_walls = this.rtree_walls.add(lbd_resource, rightFace);
					this.rtree_walls = this.rtree_walls.add(lbd_resource, topFace);
					this.rtree_walls = this.rtree_walls.add(lbd_resource, bottomFace);

				}

				
			}

		} catch (Exception e) { // Just in case IFCOpenShell does not function
								// under Tomcat
			e.printStackTrace();
		}

	}

	private void finish_geometry(boolean hasInterfaces) {
		for (java.util.Map.Entry<Rectangle, Resource> entry : rtree_map.entrySet()) {
			Resource lbd_resource = entry.getValue();
			Rectangle rect_geometry = entry.getKey();
			
			Iterable<Entry<Resource, Geometry>> results = this.rtree.search(rect_geometry);
			for (Entry<Resource, Geometry> e : results) {
				Resource e_uri=e.value();
				if (e_uri != lbd_resource)
					lbd_resource.addProperty(LBD.containsInBoundingBox, e.value());
			}

			if(!hasInterfaces)
				continue;
			if(lbd_resource.toString().toLowerCase().contains("furniture"))
				continue;
			if(lbd_resource.toString().toLowerCase().contains("slab"))
				continue;
			if(lbd_resource.toString().toLowerCase().contains("railing"))
				continue;				
			if(lbd_resource.toString().toLowerCase().contains("beam"))
				continue;				
			
			if(lbd_resource.toString().toLowerCase().contains("stairflight"))
				continue;				
			if(lbd_resource.toString().toLowerCase().contains("member"))
				continue;
			// Interfaces for the bounding boxes
			//TODO 0.05  use model scale
			Iterable<Entry<Resource, Geometry>> w_results = this.rtree_walls.search(rect_geometry,0.05);
			for (Entry<Resource, Geometry> e : w_results) {
				Resource e_uri=e.value();
				

				if (e_uri != lbd_resource)
				{
					Resource bot_interface = this.lbd_general_output_model
							.createResource(lbd_resource.getURI() + "_interface_"+e.geometry().hashCode());
					bot_interface.addProperty(RDF.type, BOT.bot_interface);
					bot_interface.addProperty(BOT.bot_interfaceOf, e_uri);
					bot_interface.addProperty(BOT.bot_interfaceOf, lbd_resource);  // Duplicates does not matter
					
				}
			}
		}
	}

	private final Map<String, String> unitmap = new HashMap<>();

	/**
	 * Collects the PropertySet data from the ifcOWL model and creates a separate
	 * Apache Jena Model that contains the converted representation of the property
	 * set content.
	 * 
	 * @param props_level             The levels described in
	 *                                https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
	 * @param hasPropertiesBlankNodes If the nameless nodes are used.
	 */
	protected void handleUnitsAndPropertySetData(int props_level, boolean hasPropertiesBlankNodes, boolean hasUnits) {
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Handle Property set data"));
		Resource ifcproject = IfcOWLUtils.getIfcProject(this.ifcOWL, this.ifcowl_model);

		if (hasUnits) {
			RDFStep[] project_units_path = { new RDFStep(this.ifcOWL.getUnitsInContext_IfcProject()),
					new RDFStep(this.ifcOWL.getUnits_IfcUnitAssignment()) };

			if (ifcproject != null) {
				List<RDFNode> units = RDFUtils.pathQuery(ifcproject, project_units_path);
				for (RDFNode ru : units) {
					RDFStep[] namedUnit_path = { new RDFStep(this.ifcOWL.getUnitType_IfcNamedUnit()) };
					List<RDFNode> r1 = RDFUtils.pathQuery(ru.asResource(), namedUnit_path);

					String named_unit = null;
					for (RDFNode l1 : r1)
						named_unit = l1.asResource().getLocalName().substring(0,
								l1.asResource().getLocalName().length() - 4);

					RDFStep[] siUnit_prefix_path = { new RDFStep(this.ifcOWL.getPrefix_IfcSIUnit()) };
					List<RDFNode> runit_pref = RDFUtils.pathQuery(ru.asResource(), siUnit_prefix_path);

					String si_prefix = null;
					for (RDFNode lpref : runit_pref)
						si_prefix = lpref.asResource().getLocalName();

					RDFStep[] siUnit_path = { new RDFStep(this.ifcOWL.getName_IfcSIUnit()) };
					List<RDFNode> runit_name = RDFUtils.pathQuery(ru.asResource(), siUnit_path);
					String si_unit = null;
					for (RDFNode lname : runit_name)
						si_unit = lname.asResource().getLocalName();

					if (si_prefix != null)
						si_unit = si_prefix + " " + si_unit;

					if (named_unit != null && si_unit != null) {
						// System.out.println("SI UNIT: " + named_unit + " - " + si_unit);
						this.unitmap.put(named_unit.toLowerCase(), si_unit);
					}
				}
			}
		}

		IfcOWLUtils.listPropertysets(this.ifcOWL, this.ifcowl_model).stream().map(RDFNode::asResource)
				.forEach(propertyset -> {
					RDFStep[] pname_path = { new RDFStep(this.ifcOWL.getName_IfcRoot()),
							new RDFStep(IfcOWL.Express.getHasString()) };

					final List<RDFNode> propertyset_name = new ArrayList<>(RDFUtils.pathQuery(propertyset, pname_path));

					RDFStep[] path = { new RDFStep(this.ifcOWL.getHasProperties_IfcPropertySet()) };
					RDFUtils.pathQuery(propertyset, path).forEach(propertySingleValue -> {

						RDFStep[] name_path = { new RDFStep(this.ifcOWL.getName_IfcProperty()),
								new RDFStep(IfcOWL.Express.getHasString()) };
						final List<RDFNode> property_name = new ArrayList<>(
								RDFUtils.pathQuery(propertySingleValue.asResource(), name_path));

						if (property_name.isEmpty())
							return; // = stream continue

						RDFStep[] unit_path = { new RDFStep(this.ifcOWL.getUnit_IfcPropertySingleValue()),
								new RDFStep(this.ifcOWL.getName_IfcSIUnit()) };
						final List<RDFNode> property_unit = new ArrayList<>(
								RDFUtils.pathQuery(propertySingleValue.asResource(), unit_path));
						// if this optional property exists, it has the priority

						RDFStep[] type_path = { new RDFStep(this.ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(RDF.type) };
						final List<RDFNode> property_type = new ArrayList<>(
								RDFUtils.pathQuery(propertySingleValue.asResource(), type_path));

						RDFStep[] value_pathS = { new RDFStep(this.ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(IfcOWL.Express.getHasString()) };
						final List<RDFNode> property_value = new ArrayList<>(
								RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathS));

						RDFStep[] value_pathD = { new RDFStep(this.ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(IfcOWL.Express.getHasDouble()) }; // xsd:decimal
						RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathD).forEach(value -> {
							// if (property_name.toString().equals("[Width]"))
							// System.out.println("Property value 1 for " + property_name + " was: " +
							// value);
							if (value.asLiteral().getDatatypeURI().equals(XSD.xdouble.getURI()))
								value = this.ifcowl_model.createTypedLiteral(
										BigDecimal.valueOf(value.asLiteral().getDouble()), XSD.decimal.getURI());
							// if (property_name.toString().equals("[Width]"))
							// System.out.println("Property value 2 for " + property_name + " was: " +
							// value);
							property_value.add(value);
						}

				);

						RDFStep[] value_pathI = { new RDFStep(this.ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(IfcOWL.Express.getHasInteger()) };
						property_value.addAll(RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathI));

						RDFStep[] value_pathB = { new RDFStep(this.ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(IfcOWL.Express.getHasBoolean()) };
						property_value.addAll(RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathB));

						RDFStep[] value_pathL = { new RDFStep(this.ifcOWL.getNominalValue_IfcPropertySingleValue()),
								new RDFStep(IfcOWL.Express.getHasLogical()) };
						property_value.addAll(RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathL));

						RDFNode pname = property_name.get(0);

						PropertySet ps = this.propertysets.get(propertyset.getURI());
						if (ps == null) {
							if (!propertyset_name.isEmpty())
								ps = new PropertySet(this.uriBase.get(), this.lbd_property_output_model,
										this.ontology_model, propertyset_name.get(0).toString(), props_level,
										hasPropertiesBlankNodes, this.unitmap, hasUnits);
							else
								ps = new PropertySet(this.uriBase.get(), this.lbd_property_output_model,
										this.ontology_model, "", props_level, hasPropertiesBlankNodes, this.unitmap,
										hasUnits);
							this.propertysets.put(propertyset.getURI(), ps);
						}
						if (!property_value.isEmpty()) {
							RDFNode pvalue = property_value.get(0);
							if (!pname.toString().equals(pvalue.toString())) {
								if (!pvalue.toString().trim().isEmpty()) {
									if (pvalue.isLiteral()) {
										String val = pvalue.asLiteral().getLexicalForm();
										if (val.equals("-1.#IND"))
											return;// pvalue =
													// ResourceFactory.createTypedLiteral(Double.NaN);
													// // in an extreme case can cause an
													// empty property set in L2 or L3:
													// fixed in PropertySet.connect
									}
									ps.putPnameValue(pname.toString(), pvalue);
									ps.putPsetPropertyRef(pname);
								}
							}
							// else: do nothing
						} else {
							ps.putPnameValue(pname.toString(), propertySingleValue);
							ps.putPsetPropertyRef(pname);
							RDFUtils.copyTriples(0, propertySingleValue, this.lbd_property_output_model);
						}
						if (!property_type.isEmpty()) {
							RDFNode ptype = property_type.get(0);
							ps.putPnameType(pname.toString(), ptype);
						}
						if (!property_unit.isEmpty()) {
							RDFNode punit = property_unit.get(0);
							ps.putPnameUnit(pname.toString(), punit);
						}

					});

				});
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("LBD properties read"));
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Handle quantity set data"));

		IfcOWLUtils.listQuantitySets(this.ifcOWL, this.ifcowl_model).stream().map(RDFNode::asResource)
				.forEach(quantityset -> {
					// System.out.println("Quantity set: " + quantityset);
					RDFStep[] qsname_path = { new RDFStep(this.ifcOWL.getName_IfcRoot()),
							new RDFStep(IfcOWL.Express.getHasString()) };

					final List<RDFNode> quantityset_name = new ArrayList<>(
							RDFUtils.pathQuery(quantityset, qsname_path)); // fine qset name

					PropertySet quantity_set = this.propertysets.get(quantityset.getURI());
					if (quantity_set == null) {
						if (!quantityset_name.isEmpty())
							quantity_set = new PropertySet(this.uriBase.get(), this.lbd_property_output_model,
									this.ontology_model, quantityset_name.get(0).toString(), props_level,
									hasPropertiesBlankNodes, this.unitmap, hasUnits);
						else
							quantity_set = new PropertySet(this.uriBase.get(), this.lbd_property_output_model,
									this.ontology_model, "", props_level, hasPropertiesBlankNodes, this.unitmap,
									hasUnits);
						this.propertysets.put(quantityset.getURI(), quantity_set);

						final PropertySet final_quantity_set = quantity_set;
						RDFStep[] path = { new RDFStep(this.ifcOWL.getQuantities_IfcElementQuantity()) };
						RDFUtils.pathQuery(quantityset, path).forEach(quantity -> {
							// System.out.println("quantity:" + quantity);
							final List<String> name = new ArrayList<>();
							quantity.asResource().listProperties().forEach(property_value -> {

								if (property_value.getPredicate().getLocalName().contains("name_")) {
									// System.out.println("name:" +
									// property_value.getObject().asResource().getLocalName());
									RDFStep[] qname_path = { new RDFStep(IfcOWL.Express.getHasString()) };
									List<RDFNode> names = RDFUtils.pathQuery(property_value.getObject().asResource(),
											qname_path);
									if (names.size() > 0)
										name.add(names.get(0).toString());
								}
							});

							quantity.asResource().listProperties().forEach(property_value -> {

								// System.out.println("qname is: "+name+" val?
								// :"+property_value.getPredicate().getLocalName());
								if (!name.isEmpty()
										&& property_value.getPredicate().getLocalName().contains("Value_")) {
									// System.out.println("value:" +
									// property_value.getObject().asResource().getLocalName());

									RDFStep[] value_pathS = { new RDFStep(IfcOWL.Express.getHasString()) };
									final List<RDFNode> q_value = new ArrayList<>(
											RDFUtils.pathQuery(property_value.getObject().asResource(), value_pathS));

									RDFStep[] value_pathD = { new RDFStep(IfcOWL.Express.getHasDouble()) }; // xsd:decimal

									RDFUtils.pathQuery(property_value.getObject().asResource(), value_pathD)
											.forEach(value -> {
												if (value.asLiteral().getDatatypeURI().equals(XSD.xdouble.getURI()))
													value = this.ifcowl_model.createTypedLiteral(
															BigDecimal.valueOf(value.asLiteral().getDouble()),
															XSD.decimal.getURI());
												// System.out.println("Double found for: "+property_value.getObject());
												q_value.add(value);
											}

											);

									RDFStep[] value_pathI = { new RDFStep(IfcOWL.Express.getHasInteger()) };
									q_value.addAll(
											RDFUtils.pathQuery(property_value.getObject().asResource(), value_pathI));

									RDFStep[] value_pathB = { new RDFStep(IfcOWL.Express.getHasBoolean()) };
									q_value.addAll(
											RDFUtils.pathQuery(property_value.getObject().asResource(), value_pathB));

									RDFStep[] value_pathL = { new RDFStep(IfcOWL.Express.getHasLogical()) };
									q_value.addAll(
											RDFUtils.pathQuery(property_value.getObject().asResource(), value_pathL));

									if (!q_value.isEmpty()) {
										RDFNode qvalue = q_value.get(0);
										// System.out.println("Add value: "+name+" val:"+qvalue);
										final_quantity_set.putPnameValue(name.get(0), qvalue);
									} else
										System.err.println(
												"qval empty " + q_value + " for: " + property_value.getObject());
								}

							});
						});
					}
					;
				});
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("LBD quantity sets read"));

	}

	/**
	 * Adds the used RDF namespaces for the Jena Models
	 * 
	 * @param uriBase               The URI base for all the elements
	 * @param props_level           The levels described in
	 *                              https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
	 * @param hasBuildingElements   The Building Elements will be created in the
	 *                              output
	 * @param hasBuildingProperties The properties will be added into the
	 */
	protected void addNamespaces(String uriBase, int props_level, boolean hasBuildingElements,
			boolean hasBuildingProperties) {
		SMLS.addNameSpace(this.lbd_general_output_model);
		UNIT.addNameSpace(this.lbd_general_output_model);
		GEO.addNameSpace(this.lbd_general_output_model);
		OMG.addNameSpace(this.lbd_general_output_model);

		LBD.addNameSpace(this.lbd_general_output_model);
		BOT.addNameSpace(this.lbd_general_output_model);
		if (hasBuildingElements)
			Product.addNameSpace(this.lbd_product_output_model);
		if (hasBuildingProperties) {
			PROPS.addNameSpace(this.lbd_property_output_model);
			PROPS.addNameSpace(this.lbd_general_output_model);

			if (props_level != 1)
				this.lbd_property_output_model.setNsPrefix("prov", OPM.prov_ns);

			if (props_level == 2)
				OPM.addNameSpacesL2(this.lbd_property_output_model);
			if (props_level == 3)
				OPM.addNameSpacesL3(this.lbd_property_output_model);
		}
		Model[] ms = { this.lbd_general_output_model, this.lbd_product_output_model, this.lbd_property_output_model };
		for (Model model : ms) {
			model.setNsPrefix("rdf", RDF.uri);
			model.setNsPrefix("rdfs", RDFS.uri);
			model.setNsPrefix("owl", OWL.getURI());
			model.setNsPrefix("xsd", "https://www.w3.org/2001/XMLSchema#");
			model.setNsPrefix("inst", uriBase);
			model.setNsPrefix("geo", "https://www.opengis.net/ont/geosparql#");
			model.setNsPrefix("props", "http://lbd.arch.rwth-aachen.de/props#");
			model.setNsPrefix("fog", "https://w3id.org/fog#");

			if (this.ontURI.isPresent()) {
				String uri = this.ontURI.get();
				if (!uri.endsWith("/"))
					uri += "#";
				model.setNsPrefix("ifc", uri);
			}
		}
	}

	private Resource connectIfcContaidedElement(Resource bot_resource, Resource ifcOWL_element) {
		Optional<String> predefined_type = IfcOWLUtils.getPredefinedData(ifcOWL_element);
		Optional<Resource> ifcowl_type = RDFUtils.getType(ifcOWL_element);
		Optional<Resource> bot_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			bot_type = getLBDProductType(ifcowl_type.get().getLocalName());
			if (this.selected_types != null && !this.selected_types.isEmpty() && bot_type.isPresent()) {

				if (!this.selected_types.contains(bot_type.get().getLocalName()))
					return null;
			} else {
				if (!this.hasNonLBDElement)
					return null;
			}

		}
		if (bot_type.isPresent()) {
			Resource lbd_element = LBD_RDF_Utils.createformattedURIRecource(ifcOWL_element,
					this.lbd_general_output_model, bot_type.get().getLocalName(), this.ifcOWL, this.uriBase.get(),
					this.exportIfcOWL_setting);
			String guid = IfcOWLUtils.getGUID(ifcOWL_element, this.ifcOWL);
			String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
			addGeometry(lbd_element, guid);
			Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_element.getURI());
			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(bot_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			}
			lbd_property_object.addProperty(RDF.type, bot_type.get());
			lbd_element.addProperty(RDF.type, BOT.element);

			bot_resource.addProperty(BOT.containsElement, lbd_element);

			IfcOWLUtils.listPropertysets(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(propertyset -> {
						PropertySet p_set = this.propertysets.get(propertyset.getURI());
						if (p_set != null)
							p_set.connect(lbd_element, uncompressed_guid);
					});
			addAttrributes(this.lbd_property_output_model, ifcOWL_element, lbd_element);

			IfcOWLUtils.listHosted_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));

			IfcOWLUtils.listAggregated_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));
			this.included_elements.add(ifcOWL_element);
			return lbd_element;
		}
		return null;
	}

	private void addSingleElement(Resource ifcOWL_element) {
		Optional<String> predefined_type = IfcOWLUtils.getPredefinedData(ifcOWL_element);
		Optional<Resource> ifcowl_type = RDFUtils.getType(ifcOWL_element);
		Optional<Resource> bot_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			bot_type = getLBDProductType(ifcowl_type.get().getLocalName());

			if (this.selected_types != null && !this.selected_types.isEmpty() && bot_type.isPresent()) {

				if (!this.selected_types.contains(bot_type.get().getLocalName()))
					return;

			} else {
				if (!this.hasNonLBDElement)
					return;
			}

		}

		if (bot_type.isPresent()) {
			Resource lbd_element = LBD_RDF_Utils.createformattedURIRecource(ifcOWL_element,
					this.lbd_general_output_model, bot_type.get().getLocalName(), this.ifcOWL, this.uriBase.get(),
					this.exportIfcOWL_setting);
			String guid = IfcOWLUtils.getGUID(ifcOWL_element, this.ifcOWL);
			String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
			addGeometry(lbd_element, guid);
			Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_element.getURI());
			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(bot_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			}
			lbd_property_object.addProperty(RDF.type, bot_type.get());
			lbd_element.addProperty(RDF.type, BOT.element);

			IfcOWLUtils.listPropertysets(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(propertyset -> {
						PropertySet p_set = this.propertysets.get(propertyset.getURI());
						if (p_set != null)
							p_set.connect(lbd_element, uncompressed_guid);
					});
			addAttrributes(this.lbd_property_output_model, ifcOWL_element, lbd_element);

			IfcOWLUtils.listHosted_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));

			IfcOWLUtils.listAggregated_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));
			this.included_elements.add(ifcOWL_element);
			return;
		}
		if (ifcowl_type.isPresent()) {
			Resource lbd_element = LBD_RDF_Utils.createformattedURIRecource(ifcOWL_element,
					this.lbd_general_output_model, "ifcOWL_" + ifcowl_type.get().getLocalName(), this.ifcOWL,
					this.uriBase.get(), this.exportIfcOWL_setting);
			String guid = IfcOWLUtils.getGUID(ifcOWL_element, this.ifcOWL);
			String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
			addGeometry(lbd_element, guid);
			Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_element.getURI());
			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(ifcowl_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			}
			lbd_property_object.addProperty(RDF.type, ifcowl_type.get());
			lbd_element.addProperty(RDF.type, BOT.element);

			IfcOWLUtils.listPropertysets(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(propertyset -> {
						PropertySet p_set = this.propertysets.get(propertyset.getURI());
						if (p_set != null)
							p_set.connect(lbd_element, uncompressed_guid);
					});
			addAttrributes(this.lbd_property_output_model, ifcOWL_element, lbd_element);

			IfcOWLUtils.listHosted_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));

			IfcOWLUtils.listAggregated_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));
			this.included_elements.add(ifcOWL_element);
		}
	}

	/**
	 * For a RDF LBD resource, creates the targeted object for the given property
	 * and adds a triple that connects them with the property. The literals of the
	 * elements and and the hosted elements are added as well.
	 * 
	 * @param bot_resource   The Jena Resource in the LBD output model in the Apache
	 *                       model
	 * @param bot_property   The LBD ontology property
	 * @param ifcOWL_element The corresponding ifcOWL element
	 * @return returns the created LBD resource
	 */
	private Resource connectElement(Resource bot_resource, Property bot_property, Resource ifcOWL_element) {
		Optional<String> predefined_type = IfcOWLUtils.getPredefinedData(ifcOWL_element);
		Optional<Resource> ifcowl_type = RDFUtils.getType(ifcOWL_element);
		Optional<Resource> lbd_product_type = Optional.empty();
		if (ifcowl_type.isPresent()) {
			lbd_product_type = getLBDProductType(ifcowl_type.get().getLocalName());

			if (this.selected_types != null && !this.selected_types.isEmpty() && lbd_product_type.isPresent()) {

				if (!this.selected_types.contains(lbd_product_type.get().getLocalName()))
					return null;
			} else {
				if (!this.hasNonLBDElement)
					return null;
			}
		}

		if (lbd_product_type.isPresent()) {
			Resource lbd_element = LBD_RDF_Utils.createformattedURIRecource(ifcOWL_element,
					this.lbd_general_output_model, lbd_product_type.get().getLocalName(), this.ifcOWL,
					this.uriBase.get(), this.exportIfcOWL_setting);
			Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_element.getURI());

			String guid = IfcOWLUtils.getGUID(ifcOWL_element, this.ifcOWL);
			addGeometry(lbd_element, guid);

			if (predefined_type.isPresent()) {
				Resource product = this.lbd_product_output_model
						.createResource(lbd_product_type.get().getURI() + "-" + predefined_type.get());
				lbd_property_object.addProperty(RDF.type, product);
			}

			lbd_property_object.addProperty(RDF.type, lbd_product_type.get());
			lbd_element.addProperty(RDF.type, BOT.element);

			addAttrributes(this.lbd_property_output_model, ifcOWL_element, lbd_element);
			bot_resource.addProperty(bot_property, lbd_element);
			IfcOWLUtils.listHosted_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));

			IfcOWLUtils.listAggregated_Elements(ifcOWL_element, this.ifcOWL).stream().map(RDFNode::asResource)
					.forEach(ifc_element2 -> connectElement(lbd_element, BOT.hasSubElement, ifc_element2));
			this.included_elements.add(ifcOWL_element);
			return lbd_element;
		}

		return null;
	}

	private final Set<Resource> handledAttributes4resource = new HashSet<>();

	/**
	 * Creates and adds the literal triples from the original ifcOWL resource under
	 * the new LBD resource.
	 * 
	 * @param output_model The Apache Jena model where the conversion output is
	 *                     written
	 * @param r            The original ifcOWL resource
	 * @param bot_r        The corresponding resource in the output model. The LBD
	 *                     resource.
	 */
	private void addAttrributes(Model output_model, Resource r, Resource bot_r) {
		if (!this.handledAttributes4resource.add(r)) // Tests if the attributes are
			// added already
			return;
		String guid = IfcOWLUtils.getGUID(r, this.ifcOWL);
		addGeometry(bot_r, guid);
		String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
		final AttributeSet connected_attributes = new AttributeSet(this.uriBase.get(), output_model, this.props_level,
				this.hasPropertiesBlankNodes, this.unitmap, this.hasSimplified_properties, this.property_replace_map);
		r.listProperties().forEachRemaining(s -> {
			String ps = s.getPredicate().getLocalName();
			Resource attr = s.getObject().asResource();
			Optional<Resource> atype = RDFUtils.getType(attr);
			if (ps.startsWith("tag_"))
				ps = "batid";
			final String property_string = ps; // Just to make variable final
												// (needed in the following
												// stream)
			if (atype.isPresent()) {
				if (atype.get().getLocalName().equals("IfcLabel")) {
					attr.listProperties(IfcOWL.Express.getHasString()).forEachRemaining(attr_s -> {
						if (attr_s.getObject().isLiteral()
								&& !attr_s.getObject().asLiteral().getLexicalForm().isEmpty()) {
							connected_attributes.putAnameValue(property_string, attr_s.getObject(), atype);
						}
					});

				} else if (atype.get().getLocalName().equals("IfcIdentifier")) {
					attr.listProperties(IfcOWL.Express.getHasString()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject(), atype));
				} else {
					attr.listProperties(IfcOWL.Express.getHasString()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject(), atype));
					attr.listProperties(IfcOWL.Express.getHasInteger()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject(), atype));
					attr.listProperties(IfcOWL.Express.getHasDouble()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject(), atype));
					attr.listProperties(IfcOWL.Express.getHasBoolean()).forEachRemaining(
							attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject(), atype));
				}

			}
		});
		connected_attributes.connect(bot_r, uncompressed_guid);
	}

	/**
	 * This used the ifcowl_product_map map and returns one mapped class in a Linked
	 * Building Data ontology, if specified.
	 * 
	 * @param ifcType The IFC entity class
	 * @return The corresponding class Resource in a LBD ontology
	 */
	private Optional<Resource> getLBDProductType(String ifcType) {
		List<Resource> ret = this.ifcowl_product_map.get(ifcType);
		if (ret == null) {
			return Optional.empty();
		} else if (ret.size() > 1) {
			// System.out.println("many " + ifcType);
			return Optional.empty();
		} else if (!ret.isEmpty())
			return Optional.of(ret.get(0));
		else
			return Optional.empty();
	}

	/**
	 * Fills in the ifcowl_product_map map using the see also ontology statemets at
	 * the Apache Jena RDF ontology model on the memory.
	 * 
	 * Uses also RDFS.subClassOf so that subclasses are included.
	 */
	protected void createIfcLBDProductMapping() {
		StmtIterator si = this.ontology_model.listStatements();
		while (si.hasNext()) {
			Statement product_BE_ontology_statement = si.next();
			if (product_BE_ontology_statement.getPredicate().toString().toLowerCase().contains("seealso")) {
				if (product_BE_ontology_statement.getObject().isLiteral())
					continue;
				if (!product_BE_ontology_statement.getObject().isResource())
					continue;
				Resource ifcowl_class = product_BE_ontology_statement.getObject().asResource();

				// This adds the seeAlso mapping directly: The base IRI is
				// removed so that the
				// mapping is independent of various IFC versions
				List<Resource> resource_list = this.ifcowl_product_map.getOrDefault(ifcowl_class.getLocalName(),
						new ArrayList<>());
				this.ifcowl_product_map.put(ifcowl_class.getLocalName(), resource_list);
				resource_list.add(product_BE_ontology_statement.getSubject());

			}
		}
		StmtIterator so = this.ontology_model.listStatements();
		while (so.hasNext()) {
			Statement product_BE_ontology_statement = so.next();
			if (product_BE_ontology_statement.getPredicate().toString().toLowerCase().contains("seealso")) {
				if (product_BE_ontology_statement.getObject().isLiteral())
					continue;
				if (!product_BE_ontology_statement.getObject().isResource())
					continue;
				Resource ifcowl_class = product_BE_ontology_statement.getObject().asResource();
				// System.err.println("ontURL: " + this.ontURI.get());
				Resource mapped_ifcowl_class = this.ontology_model
						.getResource(this.ontURI.get() + "#" + ifcowl_class.getLocalName());
				StmtIterator subclass_statement_iterator = this.ontology_model.listStatements(null, RDFS.subClassOf,
						mapped_ifcowl_class);
				while (subclass_statement_iterator.hasNext()) {
					Statement su = subclass_statement_iterator.next();
					Resource ifcowl_subclass = su.getSubject();
					if (this.ifcowl_product_map.get(ifcowl_subclass.getLocalName()) == null) {
						List<Resource> r_list = this.ifcowl_product_map.getOrDefault(ifcowl_subclass.getLocalName(),
								new ArrayList<>());
						this.ifcowl_product_map.put(ifcowl_subclass.getLocalName(), r_list);
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
	 * The generated temporary file is used to reduce the temporary memory need and
	 * make it possible to convert larger models.
	 * 
	 * Sets the this.ontURI class variable. That is used to create the right ifcOWL
	 * version based ontology base URI that is used to create the ifcOWL version
	 * based properties and class URIs-
	 * 
	 * @param ifc_file   the absolute path (For example: c:\ifcfiles\ifc_file.ifc)
	 *                   for the IFC file
	 * @param uriBase    the URL beginning for the elements in the ifcOWL TTL output
	 * @param isTmpFile  if the output is written to a temporary file.
	 * @param targetFile if not a temporary file, the absolute filename of the
	 *                   conversion result
	 * @return the Jena Model that contains the ifcOWL attribute value (Abox)
	 *         output.
	 */
	public Model readAndConvertIFC2ifcOWL(String ifc_file, String uriBase, boolean isTmpFile, String targetFile,
			boolean hasPerformanceBoost) {
		try {
			IFCtoRDF rj = new IFCtoRDF();
			File outputFile;

			if (!isTmpFile && targetFile == null) {
				String tmpdir = System.getProperty("java.io.tmpdir");
				String name = new File(ifc_file).getName();
				targetFile = tmpdir + name;
			}
			if (isTmpFile || targetFile == null) {
				outputFile = File.createTempFile("ifc", ".ttl");
				outputFile.deleteOnExit();
			} else {
				String ifcowlfilename;
				ifcowlfilename = targetFile.substring(0, targetFile.lastIndexOf(".")) + "_ifcOWL.ttl";
				outputFile = new File(ifcowlfilename);
				if (outputFile.exists() && outputFile.length() > 1000 && hasPerformanceBoost) { // Only when the
																								// performance boost
																								// selected
					System.out.println("Using existing ifcOWL file");
					this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Using existing ifcOWL file"));
					Model model = ModelFactory.createDefaultModel();

					// model.read(new FileInputStream(ifcowlfilename), null, "TTL");
					RDFDataMgr.read(model, ifcowlfilename);
					String inst_ns = model.getNsPrefixMap().get("inst");
					if (inst_ns != null && this.ontURI.isEmpty())
						this.uriBase = Optional.of(inst_ns);

					this.ontURI = rj.getOntologyURI(ifc_file);
					return model;
				}

			}
			Model m = ModelFactory.createDefaultModel();
			this.eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF conversion"));

			if (hasPerformanceBoost) {
				File pruned_file_ = IfcOWLUtils.filterIFC(new File(ifc_file));
				this.ontURI = rj.convert_into_rdf(pruned_file_.getAbsolutePath(), outputFile.getAbsolutePath(), uriBase,
						hasPerformanceBoost);
			} else {
				this.ontURI = rj.convert_into_rdf(ifc_file, outputFile.getAbsolutePath(), uriBase, hasPerformanceBoost);
			}

			this.eventBus.post(new IFCtoLBD_SystemStatusEvent("ifcOWL ready: reading in the model."));

			//TODO  This does not work wit Apache Jena 5.1 (org.apache.jena.riot.RiotException: Out of place: [DOT])
			//File t2 = IfcOWLUtils.characterCoding(outputFile); // UTF-8 characters
			File t2=null;
			System.out.println(Objects.requireNonNullElse(t2, outputFile).getAbsolutePath());
			RDFDataMgr.read(m, Objects.requireNonNullElse(t2, outputFile).getAbsolutePath(),Lang.TTL);
			return m;

		} catch (Exception e) {

			this.eventBus.post(new IFCtoLBD_SystemErrorEvent(this.getClass().getSimpleName(),
					"readAndConvertIFC: " + e.getMessage() + " line:" + e.getStackTrace()[0].getLineNumber()));
			e.printStackTrace();

		}
		System.err.println("IFC-RDF conversion not done");
		return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
	}

	/**
	 * This internal method reads in all the associated ontologies so that ontology
	 * inference can be used during the conversion.
	 * 
	 * @param ifc_file the absolute path (For example: c:\ifcfiles\ifc_file.ifc) for
	 *                 the IFC file
	 */
	protected void readInOntologies(String ifc_file) {
		try {
			IfcOWLUtils.readIfcOWLOntology(ifc_file, this.ontology_model);
			IfcOWLUtils.readIfcOWLOntology(ifc_file, this.ifcowl_model);
			RDFUtils.readInOntologyTTL(this.ontology_model, "prod.ttl", this.eventBus);
			RDFUtils.readInOntologyTTL(this.ontology_model, "prod_furnishing.ttl", this.eventBus);
			RDFUtils.readInOntologyTTL(this.ontology_model, "beo_ontology.ttl", this.eventBus);

			RDFUtils.readInOntologyTTL(this.ontology_model, "mep_ontology.ttl", this.eventBus);

			RDFUtils.readInOntologyTTL(this.ontology_model, "psetdef.ttl", this.eventBus);
			List<String> files = FileUtils.getListofFiles("pset", ".ttl");
			for (String file : files) {
				file = file.substring(file.indexOf("pset"));
				file = file.replaceAll("\\\\", "/");
				RDFUtils.readInOntologyTTL(this.ontology_model, file, this.eventBus);
				// System.out.println("read ontology file : " + file);
			}

		} catch (Exception e) {
			this.eventBus.post(new IFCtoLBD_SystemErrorEvent(this.getClass().getSimpleName(),
					"readInOntologies: " + e.getMessage() + " line:" + e.getStackTrace()[0].getLineNumber()));

		}
	}

	// int ios = 0;

	protected void initialise() {
		this.ontology_model = ModelFactory.createDefaultModel();
		this.lbd_general_output_model = ModelFactory.createDefaultModel();
		this.lbd_product_output_model = ModelFactory.createDefaultModel();
		this.lbd_property_output_model = ModelFactory.createDefaultModel();
		this.lbd_dataset = DatasetFactory.create();
		this.lbd_dataset.setDefaultModel(this.lbd_general_output_model);
	}

	public Map<String, PropertySet> getPropertysets() {
		return this.propertysets;
	}

	public Set<String> getPropertySetNames() {
		Set<String> pset_names = new HashSet<>();
		for (PropertySet ps : this.propertysets.values())
			pset_names.add(ps.getPropertyset_name());
		return pset_names;
	}

	public Model getOntology_model() {
		return this.ontology_model;
	}

	/**
	 * Lists the list of element types in the model so that they can be selected
	 * 
	 * @return the list
	 */
	public Set<Resource> getElementTypes() {
		Set<Resource> types = new HashSet<>();
		this.ifcowl_model.listStatements().forEachRemaining(st -> {
			if (st.getPredicate().getLocalName().toLowerCase().contains("globalid_ifcroot")) {
				Resource ifcOWL_element = st.getSubject();
				if (isIfcElement(ifcOWL_element)) {
					// Optional<String> predefined_type =
					// IfcOWLUtils.getPredefinedData(ifcOWL_element);
					Optional<Resource> ifcowl_type = RDFUtils.getType(ifcOWL_element);
					Optional<Resource> bot_type = Optional.empty();
					if (ifcowl_type.isPresent()) {
						bot_type = getLBDProductType(ifcowl_type.get().getLocalName());
						bot_type.ifPresent(types::add);
					}

				}
			}

		});
		return types;
	}

	/**
	 * Sets the element types that are included in the output
	 * 
	 * @param selected_types list of types
	 */
	public void setSelected_types(Set<String> selected_types) {
		System.out.println(selected_types);
		this.selected_types = selected_types;
	}

	public void setSelected_types(String selected_types_json) {
		try {
			Set<String> selected_psets = new ObjectMapper().readValue(selected_types_json, HashSet.class);

			this.selected_types = selected_types;
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets the element types that are included in the output
	 * 
	 * @param selected_types list of types
	 */
	public void setSelected_psets(Set<String> selected_psets) {
		for (PropertySet pset : this.propertysets.values()) {
			if (selected_psets.contains(pset.getPropertyset_name())) {
				pset.setActive(true);
			} else {
				pset.setActive(false);
			}
		}
	}

	public void setSelected_psets(String selected_psets_json) {
		try {
			Set<String> selected_psets = new ObjectMapper().readValue(selected_psets_json, HashSet.class);
			for (PropertySet pset : this.propertysets.values()) {
				if (selected_psets.contains(pset.getPropertyset_name())) {
					pset.setActive(true);
				} else {
					pset.setActive(false);
				}
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

	}

	public void resetModels() {
		this.lbd_general_output_model.removeAll();
		this.lbd_product_output_model.removeAll();
		this.lbd_property_output_model.removeAll();
	}

	@Subscribe
	public void handleEvent(final IFCtoLBD_SystemExit event) {
		System.out.println("Exit reason: " + event.getReason_message());
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Stopping services"));
		if (this.ifc_geometry != null)
			this.ifc_geometry.close();
		this.eventBus.post(new IFCtoLBD_SystemStatusEvent("Stopped"));
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// Just do it
		}
		closeGeometryEngine();
		System.exit(0);

	}

	// Should be done only when the app is closing
	public void closeGeometryEngine() {
		try {
			if (IFCGeometry.ifcOpenShellEngine_singlethon != null)
				IFCGeometry.ifcOpenShellEngine_singlethon.close();
		} catch (RenderEngineException e) {
			e.printStackTrace();
		}
	}

	public String getObjJSON() {
		if (!this.has_geometry.isEmpty()) {
			Query query = QueryFactory.create("""
					PREFIX bot: <https://w3id.org/bot#>\r
					PREFIX fog: <https://w3id.org/fog#>\r
					\r
					JSON { 'element' : ?element,'obj': ?obj } WHERE {\r
					  ?element a bot:Element .\r
					  ?element <https://w3id.org/omg#hasGeometry> ?g .\r
					  ?g fog:asObj_v3.0-obj ?obj \r
					}\s""", Syntax.syntaxARQ);

			try (QueryExecution queryExecution = QueryExecutionFactory.create(query, this.lbd_general_output_model)) {
				JsonArray jsonArray = queryExecution.execJson();
				return jsonArray.toString();
			}
		} else
			return "";
	}

	public String getObjJSON(String json_query) {

		Query query = QueryFactory.create(json_query, Syntax.syntaxARQ);

		try (QueryExecution queryExecution = QueryExecutionFactory.create(query, this.lbd_general_output_model)) {
			JsonArray jsonArray;
			try {
				jsonArray = queryExecution.execJson();
			} catch (Exception e) {
				return "";
			}
			return jsonArray.toString();
		}

	}

	public String getJSONLD() {
		StringWriter sw = new StringWriter();
		RDFDataMgr.write(sw, lbd_general_output_model, RDFFormat.JSONLD);
		return sw.toString();
	}

	public double getGeometryMinX() {
		if (this.rtree.mbr().isPresent())
			return this.rtree.mbr().get().min(0);
		else
			return 0d;
	}

	public double getGeometryMinY() {
		if (this.rtree.mbr().isPresent())
			return this.rtree.mbr().get().min(1);
		else
			return 0d;
	}

	public double getGeometryMinZ() {
		if (this.rtree.mbr().isPresent())
			return this.rtree.mbr().get().min(2);
		else
			return 0d;
	}

	public double getGeometryMaxX() {
		if (this.rtree.mbr().isPresent())
			return this.rtree.mbr().get().max(0);
		else
			return 0d;
	}

	public double getGeometryMaxY() {
		if (this.rtree.mbr().isPresent())
			return this.rtree.mbr().get().max(1);
		else
			return 0d;
	}

	public double getGeometryMaxZ() {
		if (this.rtree.mbr().isPresent())
			return this.rtree.mbr().get().max(2);
		else
			return 0d;
	}

	public List<Resource> getElementByGeometry(double x1, double y1, double z1, double x2, double y2, double z2) {
		List<Resource> result = new ArrayList<>();
		Rectangle rectangle = Rectangle.create(x1, y1, z1, x2, y2, z2);

		Iterable<Entry<Resource, Geometry>> results = this.rtree.search(rectangle);
		for (Entry<Resource, Geometry> e : results) {
			result.add(e.value());
		}
		return result;
	}

	// Affects property sets only after: convert_read_in_phase
	public void setHasSimplified_properties(boolean hasSimplified_properties) {
		System.out.println("set simple: " + hasSimplified_properties);
		this.hasSimplified_properties = hasSimplified_properties;

		for (PropertySet pset : this.propertysets.values())
			pset.setHasSimplified_properties(hasSimplified_properties);
	}

	public void setProperty_replace_map(Map<String, String> property_replace_map) {
		this.property_replace_map = property_replace_map;
	}

	public void setProperty_replace_map(String property_replace_map_json) {
		try {
			Map<String, String> mapping = new ObjectMapper().readValue(property_replace_map_json, HashMap.class);
			this.property_replace_map = mapping;

			for (PropertySet pset : this.propertysets.values()) {
				pset.setProperty_replace_map(mapping);
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

	}

	// Should be done only when the app is closing
	public void closeJava() {
		System.exit(0);
	}

	public void setHasNonLBDElement(boolean hasNonLBDElement) {
		this.hasNonLBDElement = hasNonLBDElement;
	}

}
