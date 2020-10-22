
package org.linkedbuildingdata.ifc2lbd.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
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
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;
import org.linkedbuildingdata.ifc2lbd.namespace.Product;
import org.linkedbuildingdata.ifc2lbd.namespace.SMLS;
import org.linkedbuildingdata.ifc2lbd.namespace.UNIT;

import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.openifctools.guidcompressor.GuidCompressor;

import de.rwth_aachen.dc.lbd.BoundingBox;
import de.rwth_aachen.dc.lbd.IFCBoundingBoxes;
import javafx.application.Platform;

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
    private Model ontology_model = null;
    private Map<String, List<Resource>> ifcowl_product_map = new HashMap<>();
    protected String uriBase;

    protected Optional<String> ontURI = Optional.empty();
    protected IfcOWL ifcOWL;

    // URI-property set
    private Map<String, PropertySet> propertysets = new HashMap<>();
 
    protected int props_level;
    protected boolean hasPropertiesBlankNodes;

    protected Model lbd_general_output_model;
    protected Model lbd_product_output_model;
    protected Model lbd_property_output_model;

    protected IFCBoundingBoxes bounding_boxes = null;

    private Set<Resource> has_geometry = new HashSet<>();
    
    private  RTree<Resource,Geometry> rtree;
    private  Map<Geometry,Resource>   rtree_map = new HashMap<>(); 

    public IFCtoLBDConverterCore()
    {
        eventBus.register(this);
    }
    
    protected void conversion(String target_file, boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties, boolean hasSeparatePropertiesModel,
                    boolean hasGeolocation,boolean hasGeometry) {
        eventBus.post(new IFCtoLBD_SystemStatusEvent("The LBD conversion starts"));

        rtree= RTree.dimensions(3).create();

        List<RDFNode> sites =IfcOWLUtils.listSites(ifcOWL, ifcowl_model);
        if (!sites.isEmpty()) {
            sites.stream().map(rn -> rn.asResource()).forEach(site -> {
                Resource lbd_site = LBD_RDF_Utils.createformattedURIRecource(site, lbd_general_output_model, "Site", this.ifcOWL, this.uriBase);
                String guid_site = IfcOWLUtils.getGUID(site, this.ifcOWL);
                String uncompressed_guid_site = GuidCompressor.uncompressGuidString(guid_site);
                addAttrributes(lbd_property_output_model, site.asResource(), lbd_site);

                lbd_site.addProperty(RDF.type, BOT.site);
                addBoundingBox(lbd_site, guid_site);

                IfcOWLUtils.listPropertysets(site, ifcOWL).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
                    PropertySet p_set = this.propertysets.get(propertyset.getURI());
                    if (p_set != null) {
                        p_set.connect(lbd_site, uncompressed_guid_site);
                    }
                });

                IfcOWLUtils.listBuildings(site, ifcOWL).stream().map(rn -> rn.asResource()).forEach(building -> {
                    handle_building(lbd_site, building);
                });
            });
        }
        else
        {
            IfcOWLUtils.listBuildings(ifcOWL, ifcowl_model).stream().map(rn -> rn.asResource()).forEach(building -> {
                handle_building(building);
            });
        }
        
        IfcOWLUtils.listSites(ifcOWL, ifcowl_model).stream().map(rn -> rn.asResource()).forEach(site -> {
            Resource lbd_site = LBD_RDF_Utils.createformattedURIRecource(site, lbd_general_output_model, "Site", this.ifcOWL, this.uriBase);
            String guid_site = IfcOWLUtils.getGUID(site, this.ifcOWL);
            String uncompressed_guid_site = GuidCompressor.uncompressGuidString(guid_site);
            addAttrributes(lbd_property_output_model, site.asResource(), lbd_site);

            lbd_site.addProperty(RDF.type, BOT.site);
            addBoundingBox(lbd_site, guid_site);

            IfcOWLUtils.listPropertysets(site, ifcOWL).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
                PropertySet p_set = this.propertysets.get(propertyset.getURI());
                if (p_set != null) {
                    p_set.connect(lbd_site, uncompressed_guid_site);
                }
            });

            IfcOWLUtils.listBuildings(site, ifcOWL).stream().map(rn -> rn.asResource()).forEach(building -> {
                handle_building(lbd_site, building);
            });
        });

        if (hasGeolocation) {
            try {
                IfcOWL_GeolocationUtil.addGeolocation2BOT(ifcowl_model,this.ifcOWL,lbd_general_output_model,this.uriBase); //TODO Check that this is getting the needed data
            } catch (Exception e) {
                eventBus.post(new IFCtoLBD_SystemStatusEvent("Info : No geolocation"));
            }
        }

        if (target_file != null) {
            if (hasBuildingElements) {
                if (hasSeparateBuildingElementsModel) {
                    String out_products_filename = target_file.substring(0, target_file.lastIndexOf(".")) + "_building_elements.ttl";
                    RDFUtils.writeModel(lbd_product_output_model, out_products_filename, this.eventBus);
                    eventBus.post(new IFCtoLBD_SystemStatusEvent("Building elements file is: " + out_products_filename));
                } else
                    lbd_general_output_model.add(lbd_product_output_model);
            }

            if (hasBuildingProperties) {
                if (hasSeparatePropertiesModel) {
                    String out_properties_filename = target_file.substring(0, target_file.lastIndexOf(".")) + "_element_properties.ttl";
                    RDFUtils.writeModel(lbd_property_output_model, out_properties_filename, this.eventBus);
                    eventBus.post(new IFCtoLBD_SystemStatusEvent("Building elements properties file is: " + out_properties_filename));
                } else
                    lbd_general_output_model.add(lbd_property_output_model);
            }
            RDFUtils.writeModel(lbd_general_output_model, target_file, this.eventBus);
            eventBus.post(new IFCtoLBD_SystemStatusEvent("Done. Linked Building Data File is: " + target_file));
        }
    }
    
    private void handle_building(Resource ifcowl_building) {
        handle_building(null, ifcowl_building);
    }

    private void handle_building(Resource lbd_site, Resource building) {
        if (!RDFUtils.getType(building.asResource()).get().getURI().endsWith("#IfcBuilding")) {
            System.err.println("Not an #IfcBuilding");
            return;
        }
        Resource lbd_building = LBD_RDF_Utils.createformattedURIRecource(building, lbd_general_output_model, "Building", this.ifcOWL, this.uriBase);
        String guid_building = IfcOWLUtils.getGUID(building, this.ifcOWL);
        String uncompressed_guid_building = GuidCompressor.uncompressGuidString(guid_building);
        addAttrributes(lbd_property_output_model, building, lbd_building);

        lbd_building.addProperty(RDF.type, BOT.building);
        addBoundingBox(lbd_building, guid_building);
        if (lbd_site != null)
           lbd_site.addProperty(BOT.hasBuilding, lbd_building);

        IfcOWLUtils.listPropertysets(building, ifcOWL).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
            PropertySet p_set = this.propertysets.get(propertyset.getURI());
            if (p_set != null) {
                p_set.connect(lbd_building, uncompressed_guid_building);
            }
        });

        IfcOWLUtils.listStoreys(building, ifcOWL).stream().map(rn -> rn.asResource()).forEach(storey -> {
            eventBus.post(new IFCtoLBD_SystemStatusEvent("Storey: " + storey.getLocalName()));

            if (!RDFUtils.getType(storey.asResource()).get().getURI().endsWith("#IfcBuildingStorey")) {
                System.err.println("No an #IfcBuildingStorey");
                return;
            }

            Resource lbd_storey = LBD_RDF_Utils.createformattedURIRecource(storey, lbd_general_output_model, "Storey", this.ifcOWL, this.uriBase);
            String guid_storey = IfcOWLUtils.getGUID(storey, this.ifcOWL);
            String uncompressed_guid_storey = GuidCompressor.uncompressGuidString(guid_storey);
            addAttrributes(lbd_property_output_model, storey, lbd_storey);

            lbd_building.addProperty(BOT.hasStorey, lbd_storey);
            addBoundingBox(lbd_storey, guid_storey);
            lbd_storey.addProperty(RDF.type, BOT.storey);

            IfcOWLUtils.listPropertysets(storey, ifcOWL).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
                PropertySet p_set = this.propertysets.get(propertyset.getURI());
                if (p_set != null)
                    p_set.connect(lbd_storey, uncompressed_guid_storey);
            });

            IfcOWLUtils.listContained_StoreyElements(storey, ifcOWL).stream().map(rn -> rn.asResource()).forEach(element -> {
                if (RDFUtils.getType(element.asResource()).get().getURI().endsWith("#IfcSpace"))
                    return;
                connectIfcContaidedElement(lbd_storey, element);
            });

            IfcOWLUtils.listStoreySpaces(storey.asResource(), ifcOWL).stream().forEach(space -> {
                if (!RDFUtils.getType(space.asResource()).get().getURI().endsWith("#IfcSpace"))
                    return;
                Resource spo = LBD_RDF_Utils.createformattedURIRecource(space.asResource(), lbd_general_output_model, "Space", this.ifcOWL, this.uriBase);
                String guid_space = IfcOWLUtils.getGUID(space.asResource(), this.ifcOWL);
                String uncompressed_guid_space = GuidCompressor.uncompressGuidString(guid_space);
                addAttrributes(lbd_property_output_model, space.asResource(), spo);

                lbd_storey.addProperty(BOT.hasSpace, spo);
                addBoundingBox(spo, guid_space);
                spo.addProperty(RDF.type, BOT.space);
                
                final ChangeableOptonal<Boolean> isExternal=new ChangeableOptonal<Boolean>();
                IfcOWLUtils.listPropertysets(space.asResource(), ifcOWL).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
                    PropertySet p_set = this.propertysets.get(propertyset.getURI());
                    if (p_set != null) {
                        p_set.connect(spo, uncompressed_guid_space);
                        if(!isExternal.isPresent())
                          isExternal.overwriteIfPresent(p_set.isExternal());
                    }
                });
                
                IfcOWLUtils.listContained_SpaceElements(space.asResource(), ifcOWL).stream().map(rn -> rn.asResource()).forEach(element -> {
                    Resource lbd_element=connectIfcContaidedElement(spo, element);
                    if(lbd_element!=null)
                       storey.addProperty(BOT.containsElement, lbd_element);
                });

                IfcOWLUtils.listAdjacent_SpaceElements(space.asResource(), ifcOWL).stream().map(rn -> rn.asResource()).forEach(element -> {
                    Resource lbd_element=connectElement(spo, BOT.adjacentElement, element);
                    if(isExternal.isPresent()&&isExternal.get()==true)
                    {
                        if(lbd_element!=null)
                            storey.addProperty(BOT.adjacentElement, lbd_element);
                        
                    }
                    else
                    {
                        if(lbd_element!=null)
                            storey.addProperty(BOT.containsElement, lbd_element);
                        
                    }
                });


            });
        });
    }

    
    private void addBoundingBox(Resource lbd_resource, String guid) {

        if(this.bounding_boxes==null)
            return;
        try {
            BoundingBox bb = this.bounding_boxes.getBoundingBox(guid);
            if (bb != null && has_geometry.add(lbd_resource)) {
                System.out.println("Bounding box for: "+lbd_resource);
                Resource sp_blank = this.lbd_general_output_model.createResource();
                lbd_resource.addProperty(GEO.hasGeometry, sp_blank);
                sp_blank.addLiteral(GEO.asWKT, bb.toString());
                Rectangle rectangle = Rectangle.create(bb.getMin().x, bb.getMin().y, bb.getMin().z, bb.getMax().x, bb.getMax().y, bb.getMax().z);
                rtree=rtree.add(lbd_resource,rectangle); // rtree is immutable
                rtree_map.put(rectangle, lbd_resource);
                
                Iterable<Entry<Resource, Geometry>> results =
                                rtree.search(rectangle);
                for(Entry<Resource, Geometry> e: results)
                {
                    if(e.value()!=lbd_resource)
                    e.value().addProperty(LBD.containsInBoundingBox, lbd_resource);
                }
                
            }
        } catch (Exception e) { // Just in case IFCOpenShell does not function
                                // under Tomcat
            e.printStackTrace();
        }

    }

    private final Map<String, String> unitmap = new HashMap<>();

    /**
     * Collects the PropertySet data from the ifcOWL model and creates a
     * separate Apache Jena Model that contains the converted representation of
     * the property set content.
     * 
     * @param props_level
     *            The levels described in
     *            https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
     * @param hasPropertiesBlankNodes
     *            If the nameless nodes are used.
     */
    protected void handlePropertySetData(int props_level, boolean hasPropertiesBlankNodes) {
        System.out.println("Property sets");
        eventBus.post(new IFCtoLBD_SystemStatusEvent("Handle Property set data"));

        List<RDFNode> units = IfcOWLUtils.getProjectSIUnits(ifcOWL, ifcowl_model);
        for (RDFNode ru : units) {
            RDFStep[] namedUnit_path = { new RDFStep(ifcOWL.getUnitType_IfcNamedUnit()) };
            List<RDFNode> r1 = RDFUtils.pathQuery(ru.asResource(), namedUnit_path);

            String named_unit = null;
            for (RDFNode l1 : r1)
                named_unit = l1.asResource().getLocalName().substring(0, l1.asResource().getLocalName().length() - 4);

            RDFStep[] siUnit_path = { new RDFStep(ifcOWL.getName_IfcSIUnit()) };
            List<RDFNode> r2 = RDFUtils.pathQuery(ru.asResource(), siUnit_path);
            String si_unit = null;
            for (RDFNode l2 : r2)
                si_unit = l2.asResource().getLocalName();
            if (named_unit != null && si_unit != null)
                unitmap.put(named_unit.toLowerCase(), si_unit);
        }

        IfcOWLUtils.listPropertysets(ifcOWL, ifcowl_model).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
            RDFStep[] pname_path = { new RDFStep(ifcOWL.getName_IfcRoot()), new RDFStep(IfcOWL.Express.getHasString()) };

            final List<RDFNode> propertyset_name = new ArrayList<>();
            RDFUtils.pathQuery(propertyset, pname_path).forEach(name -> propertyset_name.add(name));

            RDFStep[] path = { new RDFStep(ifcOWL.getHasProperties_IfcPropertySet()) };
            RDFUtils.pathQuery(propertyset, path).forEach(propertySingleValue -> {

                RDFStep[] name_path = { new RDFStep(ifcOWL.getName_IfcProperty()), new RDFStep(IfcOWL.Express.getHasString()) };
                final List<RDFNode> property_name = new ArrayList<>();
                RDFUtils.pathQuery(propertySingleValue.asResource(), name_path).forEach(name -> property_name.add(name));

                if (property_name.size() == 0)
                    return; // = stream continue

                final List<RDFNode> property_unit = new ArrayList<>();
                final List<RDFNode> property_type = new ArrayList<>();
                final List<RDFNode> property_value = new ArrayList<>();
                
                RDFStep[] unit_path = { new RDFStep(ifcOWL.getUnit_IfcPropertySingleValue()), new RDFStep(ifcOWL.getName_IfcSIUnit()) };
                RDFUtils.pathQuery(propertySingleValue.asResource(), unit_path).forEach(unit -> property_unit.add(unit));      // if this optional property exists, it has the priority

                RDFStep[] type_path = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()), new RDFStep(RDF.type) };
                RDFUtils.pathQuery(propertySingleValue.asResource(), type_path).forEach(type -> property_type.add(type));

                RDFStep[] value_pathS = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()), new RDFStep(IfcOWL.Express.getHasString()) };
                RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathS).forEach(value -> property_value.add(value));

                RDFStep[] value_pathD = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()), new RDFStep(IfcOWL.Express.getHasDouble()) };
                RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathD).forEach(value -> property_value.add(value));

                RDFStep[] value_pathI = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()), new RDFStep(IfcOWL.Express.getHasInteger()) };
                RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathI).forEach(value -> property_value.add(value));

                RDFStep[] value_pathB = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()), new RDFStep(IfcOWL.Express.getHasBoolean()) };
                RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathB).forEach(value -> property_value.add(value));

                RDFStep[] value_pathL = { new RDFStep(ifcOWL.getNominalValue_IfcPropertySingleValue()), new RDFStep(IfcOWL.Express.getHasLogical()) };
                RDFUtils.pathQuery(propertySingleValue.asResource(), value_pathL).forEach(value -> property_value.add(value));

                RDFNode pname = property_name.get(0);

                PropertySet ps = this.propertysets.get(propertyset.getURI());
                if (ps == null) {
                    if (!propertyset_name.isEmpty())
                        ps = new PropertySet(this.uriBase, lbd_property_output_model, this.ontology_model, propertyset_name.get(0).toString(), props_level, hasPropertiesBlankNodes, unitmap);
                    else
                        ps = new PropertySet(this.uriBase, lbd_property_output_model, this.ontology_model, "", props_level, hasPropertiesBlankNodes, unitmap);
                    this.propertysets.put(propertyset.getURI(), ps);
                }
                if (property_value.size() > 0) {
                    RDFNode pvalue = property_value.get(0);
                    if (!pname.toString().equals(pvalue.toString())) {
                        if (pvalue.toString().trim().length() > 0) {
                            if (pvalue.isLiteral()) {
                                String val = pvalue.asLiteral().getLexicalForm();
                                if (val.equals("-1.#IND"))
                                    return;//pvalue = ResourceFactory.createTypedLiteral(Double.NaN);  // in an extreme case can cause an empty property set in L2 or L3: fixed in PropertySet.connect
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
                if (property_type.size() > 0) {
                    RDFNode ptype = property_type.get(0);
                    ps.putPnameType(pname.toString(), ptype);
                }
                if (property_unit.size() > 0) {
                    RDFNode punit = property_unit.get(0);
                    ps.putPnameUnit(pname.toString(), punit);
                }

            });

        });
        eventBus.post(new IFCtoLBD_SystemStatusEvent("LBD properties read"));
    }

    /**
     * Adds the used RDF namespaces for the Jena Models
     * @param uriBase                          The URI base for all the elemenents
     * @param props_level                      The levels described in
     *                                         https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
     * @param hasBuildingElements              The Building Elements will be created
     *                                         in the output
     * @param hasBuildingProperties            The properties will ne added into the
     */
    protected void addNamespaces(String uriBase, int props_level, boolean hasBuildingElements, boolean hasBuildingProperties) {
        SMLS.addNameSpace(lbd_general_output_model);
        UNIT.addNameSpace(lbd_general_output_model);
        GEO.addNameSpace(lbd_general_output_model);
        
        
        LBD.addNameSpace(lbd_general_output_model);
        BOT.addNameSpace(lbd_general_output_model);
        if (hasBuildingElements)
            Product.addNameSpace(lbd_product_output_model);
        if (hasBuildingProperties) {
            PROPS.addNameSpace(lbd_property_output_model);
            PROPS.addNameSpace(lbd_general_output_model);
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
            model.setNsPrefix("owl", OWL.getURI());
            model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
            model.setNsPrefix("inst", uriBase);
            model.setNsPrefix("geo", "http://www.opengis.net/ont/geosparql#");
        }
    }

    
    private Resource connectIfcContaidedElement(Resource bot_resource,Resource ifc_element) {
        Optional<String> predefined_type = IfcOWLUtils.getPredefinedData(ifc_element);
        Optional<Resource> ifcowl_type = RDFUtils.getType(ifc_element);
        Optional<Resource> bot_type = Optional.empty();
        if (ifcowl_type.isPresent()) {
            bot_type = getLBDProductType(ifcowl_type.get().getLocalName());
        }
        System.out.println("Connect element: " + ifc_element);
        if (bot_type.isPresent()) {
            Resource lbd_element = LBD_RDF_Utils.createformattedURIRecource(ifc_element, this.lbd_general_output_model, bot_type.get().getLocalName(), this.ifcOWL, this.uriBase);
            String guid = IfcOWLUtils.getGUID(ifc_element, this.ifcOWL);
            String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
            addBoundingBox(lbd_element, guid);
            Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_element.getURI());
            if (predefined_type.isPresent()) {
                Resource product = this.lbd_product_output_model.createResource(bot_type.get().getURI() + "-" + predefined_type.get());
                lbd_property_object.addProperty(RDF.type, product);
            }
            lbd_property_object.addProperty(RDF.type, bot_type.get());
            lbd_element.addProperty(RDF.type, BOT.element);
            
            bot_resource.addProperty(BOT.containsElement, lbd_element);
            
            IfcOWLUtils.listPropertysets(ifc_element, ifcOWL).stream().map(rn -> rn.asResource()).forEach(propertyset -> {
                PropertySet p_set = this.propertysets.get(propertyset.getURI());
                if (p_set != null)
                    p_set.connect(lbd_element, uncompressed_guid);
            });
            addAttrributes(this.lbd_property_output_model, ifc_element, lbd_element);

            IfcOWLUtils.listHosted_Elements(ifc_element, ifcOWL).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
                // if (eo.getLocalName().toLowerCase().contains("space"))
                // System.out.println("hosts: " + ifc_element + "--" +
                // ifc_element2 + " bot:" +
                // eo);
                connectElement(lbd_element, BOT.hasSubElement, ifc_element2);
            });

            IfcOWLUtils.listAggregated_Elements(ifc_element, ifcOWL).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
                connectElement(lbd_element, BOT.hasSubElement, ifc_element2);
            });
            return lbd_element;
        }
        return null;
    }
    

    /**
     * For a RDF LBD resource, creates the targetted object for the given
     * property and adds a triple that connects them with the property. The
     * literals of the elements and and the hosted elements are added as well.
     * 
     * @param bot_resource
     *            The Jena Resource in the LBD output model in the Apacje model
     * @param bot_property
     *            The LBD ontology property
     * @param ifcowl_element
     *            The corresponding ifcOWL elemeny
     * @return returns the created LBD resource
     */
    private Resource connectElement(Resource bot_resource, Property bot_property, Resource ifcowl_element) {
        Optional<String> predefined_type = IfcOWLUtils.getPredefinedData(ifcowl_element);
        Optional<Resource> ifcowl_type = RDFUtils.getType(ifcowl_element);
        Optional<Resource> lbd_product_type = Optional.empty();
        if (ifcowl_type.isPresent()) {
            lbd_product_type = getLBDProductType(ifcowl_type.get().getLocalName());
        }

        if (lbd_product_type.isPresent()) {
            Resource lbd_element = LBD_RDF_Utils.createformattedURIRecource(ifcowl_element, this.lbd_general_output_model, lbd_product_type.get().getLocalName(), this.ifcOWL, this.uriBase);
            Resource lbd_property_object = this.lbd_product_output_model.createResource(lbd_element.getURI());
            
            String guid = IfcOWLUtils.getGUID(ifcowl_element, this.ifcOWL);
            addBoundingBox(lbd_element, guid);

            if (predefined_type.isPresent()) {
                Resource product = this.lbd_product_output_model.createResource(lbd_product_type.get().getURI() + "-" + predefined_type.get());
                lbd_property_object.addProperty(RDF.type, product);
            }

            lbd_property_object.addProperty(RDF.type, lbd_product_type.get());
            lbd_element.addProperty(RDF.type, BOT.element);

            addAttrributes(this.lbd_property_output_model, ifcowl_element, lbd_element);
            bot_resource.addProperty(bot_property, lbd_element);
            IfcOWLUtils.listHosted_Elements(ifcowl_element, ifcOWL).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
                connectElement(lbd_element, BOT.hasSubElement, ifc_element2);
            });

            IfcOWLUtils.listAggregated_Elements(ifcowl_element, ifcOWL).stream().map(rn -> rn.asResource()).forEach(ifc_element2 -> {
                connectElement(lbd_element, BOT.hasSubElement, ifc_element2);
            });
            return lbd_element;
        } 

        return null;
    }

    private Set<Resource> handledAttributes4resource = new HashSet<>();

    /**
     * Creates and adds the literal triples from the original ifcOWL resource
     * under the new LBD resource.
     * 
     * @param output_model
     *            The Apache Jena model where the conversion output is written
     * @param r
     *            The oroginal ifcOWL resource
     * @param bot_r
     *            The correspoinding resource in the output model. The LBD
     *            resource.
     */
    private void addAttrributes(Model output_model, Resource r, Resource bot_r) {
        if (!handledAttributes4resource.add(r)) // Tests if the attributes are
                                                // added already
            return;
        String guid = IfcOWLUtils.getGUID(r, this.ifcOWL);
        addBoundingBox(bot_r, guid);
        String uncompressed_guid = GuidCompressor.uncompressGuidString(guid);
        final AttributeSet connected_attributes = new AttributeSet(this.uriBase, output_model, this.props_level, hasPropertiesBlankNodes);
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
                        if (attr_s.getObject().isLiteral() && attr_s.getObject().asLiteral().getLexicalForm().length() > 0) {
                            connected_attributes.putAnameValue(property_string, attr_s.getObject());
                        }
                    });

                } else if (atype.get().getLocalName().equals("IfcIdentifier")) {
                    attr.listProperties(IfcOWL.Express.getHasString()).forEachRemaining(attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
                } else {
                    attr.listProperties(IfcOWL.Express.getHasString()).forEachRemaining(attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
                    attr.listProperties(IfcOWL.Express.getHasInteger()).forEachRemaining(attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
                    attr.listProperties(IfcOWL.Express.getHasDouble()).forEachRemaining(attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
                    attr.listProperties(IfcOWL.Express.getHasBoolean()).forEachRemaining(attr_s -> connected_attributes.putAnameValue(property_string, attr_s.getObject()));
                }

            }
        });
        connected_attributes.connect(bot_r, uncompressed_guid);
    }

 

    /**
     * This used the ifcowl_product_map map and returns one mapped class in a
     * Linked Building Data ontology, if specified.
     * 
     * @param ifcType
     *            The IFC entity class
     * @return The corresponding class Resource in a LBD ontology
     */
    private Optional<Resource> getLBDProductType(String ifcType) {
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
     * Fills in the ifcowl_product_map map using the seealso ontology statemets
     * at the Apache Jena RDF ontology model on the memory.
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

                // This adds the seeAlso mapping directly: The base IRI is
                // removed so that the
                // mapping is independent of various IFC versions
                List<Resource> resource_list = ifcowl_product_map.getOrDefault(ifcowl_class.getLocalName(), new ArrayList<Resource>());
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
                Resource mapped_ifcowl_class = ontology_model.getResource(this.ontURI.get() + "#" + ifcowl_class.getLocalName());
                StmtIterator subclass_statement_iterator = ontology_model.listStatements(new SimpleSelector(null, RDFS.subClassOf, mapped_ifcowl_class));
                while (subclass_statement_iterator.hasNext()) {
                    Statement su = subclass_statement_iterator.next();
                    Resource ifcowl_subclass = su.getSubject();
                    if (ifcowl_product_map.get(ifcowl_subclass.getLocalName()) == null) {
                        List<Resource> r_list = ifcowl_product_map.getOrDefault(ifcowl_subclass.getLocalName(), new ArrayList<Resource>());
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
     * The method converts an IFC STEP formatted file and returns an Apache Jena
     * RDF memory storage model that contains the generated RDF triples.
     * 
     * Apache Jena: https://jena.apache.org/index.html
     * 
     * The generated temporsary file is used to reduce the temporary memory need
     * and make it possible to convert larger models.
     * 
     * Sets the this.ontURI class variable. That is used to create the right
     * ifcOWL version based ontology base URI that is used to create the ifcOWL
     * version based peroperties and class URIs-
     * 
     * @param ifc_file
     *            the absolute path (For example: c:\ifcfiles\ifc_file.ifc) for
     *            the IFC file
     * @param uriBase
     *            the URL beginning for the elements in the ifcOWL TTL output                 
     * @param isTmpFile if the output is written to a temporary file.                  
     * @param targetFile if not a temporary file, the absolute filename of the conversion result                  
     * @return the Jena Model that contains the ifcOWL attribute value (Abox)
     *         output.
     */
    protected Model readAndConvertIFC(String ifc_file, String uriBase, boolean isTmpFile,String targetFile) {
        try {
            IFCtoRDF rj = new IFCtoRDF();
            File outputFile;
            if(isTmpFile)
                outputFile = File.createTempFile("ifc", ".ttl");
            else
            {
                String ifcowlfilename;
                if(targetFile!=null)
                {
                   ifcowlfilename = targetFile.substring(0, targetFile.lastIndexOf(".")) + "_ifcOWL.ttl";
                   outputFile = new File(ifcowlfilename);
                }
                else
                    outputFile = File.createTempFile("ifc", ".ttl");
                
            }
            try {
                Model m = ModelFactory.createDefaultModel();
                this.ontURI = rj.convert_into_rdf(ifc_file, outputFile.getAbsolutePath(), uriBase);
                File t2 = IfcOWLUtils.filterContent(outputFile);
                RDFDataMgr.read(m, t2.getAbsolutePath());
                return m;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                outputFile.deleteOnExit();
            }

        } catch (Exception e) {
            eventBus.post(new IFCtoLBD_SystemErrorEvent(this.getClass().getSimpleName(), "readAndConvertIFC: "+e.getMessage() + " line:" + e.getStackTrace()[0].getLineNumber()));
            e.printStackTrace();

        }
        System.err.println("IFC-RDF conversion not done");
        return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
    }


    /**
     * This internal method reads in all the associated ontologies so that
     * ontology inference can ne used during the conversion.
     * 
     * @param ifc_file
     *            the absolute path (For example: c:\ifcfiles\ifc_file.ifc) for
     *            the IFC file
     */
    protected void readInOntologies(String ifc_file) {
        try {
            IfcOWLUtils.readIfcOWLOntology(ifc_file, ontology_model);
            IfcOWLUtils.readIfcOWLOntology(ifc_file, ifcowl_model);

            RDFUtils.readInOntologyTTL(ontology_model, "prod.ttl", this.eventBus);
            RDFUtils.readInOntologyTTL(ontology_model, "beo_ontology.ttl", this.eventBus);
            
            RDFUtils.readInOntologyTTL(ontology_model, "mep_ontology.ttl", this.eventBus);

            RDFUtils.readInOntologyTTL(ontology_model, "psetdef.ttl", this.eventBus);
            List<String> files = FileUtils.getListofFiles("pset", ".ttl");
            for (String file : files) {
                file = file.substring(file.indexOf("pset"));
                file = file.replaceAll("\\\\", "/");
                RDFUtils.readInOntologyTTL(ontology_model, file, this.eventBus);
                System.out.println("read ontology file : " + file);
            }
            
        } catch (Exception e) {
            eventBus.post(new IFCtoLBD_SystemErrorEvent(this.getClass().getSimpleName(), "readInOntologies: "+e.getMessage() + " line:" + e.getStackTrace()[0].getLineNumber()));

        }
    }


    protected void initialise_JenaModels() {
        ontology_model = ModelFactory.createDefaultModel();

        this.lbd_general_output_model = ModelFactory.createDefaultModel();
        this.lbd_product_output_model = ModelFactory.createDefaultModel();
        this.lbd_property_output_model = ModelFactory.createDefaultModel();
    }

  
    
    public Map<String, PropertySet> getPropertysets() {
        return propertysets;
    }
    
    public Model getOntology_model() {
        return ontology_model;
    }

    
    @Subscribe
    public void handleEvent(final IFCtoLBD_SystemExit event) {
        System.out.println("Exit reason: " + event.getReason_message());
        eventBus.post(new IFCtoLBD_SystemStatusEvent("Stopping services"));
        if(this.bounding_boxes!=null)
            this.bounding_boxes.close();
        eventBus.post(new IFCtoLBD_SystemStatusEvent("Stopped"));
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Just do it
        }
        System.exit(0);  // Force IfcOpenShell to exit
    }
}
