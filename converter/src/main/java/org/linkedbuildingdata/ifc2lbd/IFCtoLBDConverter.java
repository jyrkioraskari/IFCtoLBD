
package org.linkedbuildingdata.ifc2lbd;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.core.IFCtoLBDConverterCore;
import org.linkedbuildingdata.ifc2lbd.core.utils.FileUtils;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWLNameSpace;

import de.rwth_aachen.dc.lbd.IFCBoundingBoxes;

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

// The Class diagram source code:
/*
 * @startuml doc-graphs/IFCtoLBDConverter_class_diagram.png class
 * IFCtoLBDConverter { Model ifcowl_model String uriBase IfcOWLNameSpace ifcOWL
 * Map<String, List<Resource>> ifcowl_product_map
 * 
 * Model readAndConvertIFC(String ifc_file, String uriBase) void
 * readInOntologies(String ifc_file) void createIfcLBDProductMapping() void
 * addNamespaces(String uriBase, int props_level, boolean hasBuildingElements,
 * boolean hasBuildingProperties) void handlePropertySetData(int props_level,
 * boolean hasPropertiesBlankNodes) void conversion(String target_file, boolean
 * hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean
 * hasBuildingProperties, boolean hasSeparatePropertiesModel, boolean
 * hasGeolocation) } IFCtoLBDConverter - IfcOWLUtils: use > IFCtoLBDConverter -
 * RDFUtils: use > IFCtoLBDConverter - FileUtils: use > IFCtoLBDConverter o--
 * PropertySet IfcOWLUtils .. RDFStep IfcOWLUtils .. InvRDFStep
 * 
 * @enduml
 */

public class IFCtoLBDConverter extends IFCtoLBDConverterCore{
	/**
	 * The construction method for the converter process. This does the whole
	 * process.
	 * 
	 * @param ifc_filename                     The absolute path for the IFC file
	 *                                         that will be converted
	 * @param uriBase                          The URI base for all the elemenents
	 *                                         that will be created
	 * @param target_file                      The main file name for the output. If
	 *                                         there are many, they will be sharing
	 *                                         the same beginning
	 * @param props_level                      The levels described in
	 *                                         https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
	 * @param hasBuildingElements              The Building Elements will be created
	 *                                         in the output
	 * @param hasSeparateBuildingElementsModel The Building elements will have a
	 *                                         separate file
	 * @param hasBuildingProperties            The properties will ne added into the
	 *                                         output
	 * @param hasSeparatePropertiesModel       The properties will be written in a
	 *                                         separate file
	 * @param hasPropertiesBlankNodes          Blank nodes are used
	 * @param hasGeolocation                   Geolocation, i.e., the latitude and
	 *                                         longitude are added.
	 */
    
    
    public IFCtoLBDConverter(String ifc_filename, String uriBase, String target_file, int props_level,
                    boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,
                    boolean hasSeparatePropertiesModel, boolean hasPropertiesBlankNodes, boolean hasGeolocation) {
                this.props_level = props_level;
                this.hasPropertiesBlankNodes = hasPropertiesBlankNodes;
                
                try {
                    System.out.println("Set the bounding box generator");
                    this.bounding_boxes = new IFCBoundingBoxes(new File(ifc_filename));
                } catch (RenderEngineException | DeserializeException | IOException e) {
                    e.printStackTrace();
                }

                
                if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
                    uriBase += "#";
                this.uriBase = uriBase;

                initialise_JenaModels();
                
                eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF conversion"));
                ifcowl_model = readAndConvertIFC(ifc_filename, uriBase); // Before: readInOntologies(ifc_filename);

                eventBus.post(new IFCtoLBD_SystemStatusEvent("Reading in ontologies"));

                readInOntologies(ifc_filename);
                
                eventBus.post(new IFCtoLBD_SystemStatusEvent("Create ifc to LBD mapping"));

                createIfcLBDProductMapping();

                addNamespaces(uriBase, props_level, hasBuildingElements, hasBuildingProperties);

                eventBus.post(new IFCtoLBD_SystemStatusEvent("IFC->LBD"));
                if (this.ontURI.isPresent())
                    ifcOWL = new IfcOWLNameSpace(this.ontURI.get());
                else {
                    System.out.println("No ifcOWL ontology available.");
                    eventBus.post(new IFCtoLBD_SystemStatusEvent("No ifcOWL ontology available."));
                    return;
                }

                if (hasBuildingProperties) {
                    handlePropertySetData(props_level, hasPropertiesBlankNodes);
                }

                conversion(target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
                        hasSeparatePropertiesModel, hasGeolocation, true);

                
    }
	public IFCtoLBDConverter(String ifc_filename, String uriBase, String target_file, int props_level,
			boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,
			boolean hasSeparatePropertiesModel, boolean hasPropertiesBlankNodes, boolean hasGeolocation, boolean hasGeometry) {
		this.props_level = props_level;
		this.hasPropertiesBlankNodes = hasPropertiesBlankNodes;

	    if(hasGeometry)
		try {
            System.out.println("Set the bounding box generator");
            this.bounding_boxes = new IFCBoundingBoxes(new File(ifc_filename));
        } catch (RenderEngineException | DeserializeException | IOException e) {
            e.printStackTrace();
        }

		
		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = uriBase;

		initialise_JenaModels();
		eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF conversion"));
		ifcowl_model = readAndConvertIFC(ifc_filename, uriBase); // Before: readInOntologies(ifc_filename);

		eventBus.post(new IFCtoLBD_SystemStatusEvent("Reading in ontologies"));

		readInOntologies(ifc_filename);
		
	    eventBus.post(new IFCtoLBD_SystemStatusEvent("Create ifc to LBD mapping"));

		createIfcLBDProductMapping();

		addNamespaces(uriBase, props_level, hasBuildingElements, hasBuildingProperties);

		eventBus.post(new IFCtoLBD_SystemStatusEvent("IFC->LBD"));
		if (this.ontURI.isPresent())
			ifcOWL = new IfcOWLNameSpace(this.ontURI.get());
		else {
			System.out.println("No ifcOWL ontology available.");
			eventBus.post(new IFCtoLBD_SystemStatusEvent("No ifcOWL ontology available."));
			return;
		}

		if (hasBuildingProperties) {
			handlePropertySetData(props_level, hasPropertiesBlankNodes);
		}

		conversion(target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation, hasGeometry);

	}

	public IFCtoLBDConverter(String uriBase, Integer... props_level) {
		if (props_level.length > 0)
			this.props_level = props_level[0];
		else
			this.props_level = 1;
		this.hasPropertiesBlankNodes = true;

		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = uriBase;
		System.out.println("Conversion starts");
		initialise_JenaModels();
	}
	
    private void initialise_JenaModels() {
        ontology_model = ModelFactory.createDefaultModel();

		this.lbd_general_output_model = ModelFactory.createDefaultModel();
		this.lbd_product_output_model = ModelFactory.createDefaultModel();
		this.lbd_property_output_model = ModelFactory.createDefaultModel();
    }
	
	/**
	 * The construction method for the converter process. This does the whole
	 * process.
	 * 
	 * @param uriBase                 The URI base for all the elemenents that will
	 *                                be created
	 * @param hasPropertiesBlankNodes Blank nodes are used
	 * 
	 * @param props_level             The levels described in
	 *                                https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
	 *                                longitude are added.
	 * 
	 */
	public IFCtoLBDConverter(String uriBase, boolean hasPropertiesBlankNodes, Integer... props_level) {
		if (props_level.length > 0)
			this.props_level = props_level[0];
		else
			this.props_level = 1;
		this.hasPropertiesBlankNodes = hasPropertiesBlankNodes;

		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = uriBase;
		initialise_JenaModels();
	}

	/**
	 * Convert an IFC STEP file into LBD
	 * 
	 * @param ifc_filename The absolute path for the IFC file that will be converted
	 * @param target_file  The main file name for the output. If there are many,
	 *                     they will be sharing
	 * @return The model as a Jena-model
	 */
	public Model convert(String ifc_filename, String target_file) {

		boolean hasBuildingElements = true;
		boolean hasSeparateBuildingElementsModel = false;
		boolean hasBuildingProperties = true;
		boolean hasSeparatePropertiesModel = false;
		boolean hasGeolocation = true;
		boolean hasGeometry = true;

		convert(ifc_filename, target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation,hasGeometry);
		return lbd_general_output_model;
	}

	/**
	 * Convert an IFC STEP file into LBD
	 * 
	 * @param ifc_filename The absolute path for the IFC file that will be converted
	 * @return The model as a Jena-model
	 */
	public Model convert(String ifc_filename) {

		boolean hasBuildingElements = true;
		boolean hasSeparateBuildingElementsModel = false;
		boolean hasBuildingProperties = true;
		boolean hasSeparatePropertiesModel = false;
		boolean hasGeolocation = true;
		boolean hasGeometry = true;

		convert(ifc_filename, null, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation, hasGeometry);
		return lbd_general_output_model;
	}

	/**
	 * Convert an IFC STEP file into LBD
	 * 
	 * @param ifc_filename                     The absolute path for the IFC file
	 *                                         that will be converted
	 * @param target_file                      The main file name for the output. If
	 *                                         there are many, they will be sharing
	 *                                         the same beginning
	 * @param hasBuildingElements              The Building Elements will be created
	 *                                         in the output
	 * @param hasSeparateBuildingElementsModel The Building elements will have a
	 *                                         separate file
	 * @param hasBuildingProperties            The properties will ne added into the
	 *                                         output
	 * @param hasSeparatePropertiesModel       The properties will be written in a
	 *                                         separate file
	 * @param hasGeolocation                   Geolocation, i.e., the latitude and
	 *                                         longitude are added.
	 * @return The model as a Jena-model
	 */
	public Model convert(String ifc_filename, String target_file, boolean hasBuildingElements,
			boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties, boolean hasSeparatePropertiesModel,
			boolean hasGeolocation, boolean hasGeometry) {
	    
	    
	    if(hasGeometry)
	    try {
	        eventBus.post(new IFCtoLBD_SystemStatusEvent("Geometry handling"));
            System.out.println("Set the bounding box generator");
            this.bounding_boxes = new IFCBoundingBoxes(new File(ifc_filename));
        } catch (RenderEngineException | DeserializeException | IOException e) {
            e.printStackTrace();
        }
        eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF conversion"));

	    
		ifcowl_model = readAndConvertIFC(ifc_filename, uriBase); // Before: readInOntologies(ifc_filename);

		eventBus.post(new IFCtoLBD_SystemStatusEvent("Reading in ontologies"));
		readInOntologies(ifc_filename);
		createIfcLBDProductMapping();

		addNamespaces(uriBase, props_level, hasBuildingElements, hasBuildingProperties);

		eventBus.post(new IFCtoLBD_SystemStatusEvent("IFC->LBD"));
		if (this.ontURI.isPresent())
			ifcOWL = new IfcOWLNameSpace(this.ontURI.get());
		else {
			System.out.println("No ifcOWL ontology available.");
			eventBus.post(new IFCtoLBD_SystemStatusEvent("No ifcOWL ontology available."));
			return lbd_general_output_model;
		}

		if (hasBuildingProperties) {
			handlePropertySetData(props_level, hasPropertiesBlankNodes);
		}

		conversion(target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation, hasGeometry);
		return lbd_general_output_model;

	}



	public static void main(String[] args) {

		if (args.length > 2) {
			new IFCtoLBDConverter(args[0], args[1], args[2], 2, true, false, true, false, false, true);
		} else if (args.length == 1) {
			// directory upload
			final List<String> inputFiles;
			final List<String> outputFiles;
			inputFiles = FileUtils.listFiles(args[0]);
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

					// move file to output directory

					System.out.println("--------- converting: " + inputFile);
					new IFCtoLBDConverter(inputFile, "https://dot.ugent.be/IFCtoLBDset#", outputFile, 0, true, false,
							true, false, false, false);

					// move original file to output directory
					File afile = new File(inputFile);
					afile.renameTo(new File(copyFile));
					System.out.println("--------- done ");
				}
			}
		} else
			System.out.println("Usage: IFCtoLBDConverter ifc_filename base_uri targer_file");
	}

}
