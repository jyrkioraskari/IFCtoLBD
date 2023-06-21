
package org.linkedbuildingdata.ifc2lbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sys.JenaSystem;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemErrorEvent;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.core.IFCtoLBDConverterCore;
import org.linkedbuildingdata.ifc2lbd.core.utils.FileUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.PropertySet;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

import de.rwth_aachen.dc.lbd.IFCGeometry;

/*
 *  Copyright (c) 2017,2018,2019, 2020 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

public class IFCtoLBDConverter extends IFCtoLBDConverterCore {
	private int ios = 0;

	/**
	 * IFCtoLBD constructor The construction method for the converter process. This
	 * does the whole process.
	 * 
	 * @param ifc_filename                     The absolute path for the IFC file
	 *                                         that will be converted
	 * @param uriBase                          The URI base for all the elements
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
	 * @param hasBuildingProperties            The properties will be added into the
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
		super();
		this.hasPropertiesBlankNodes = hasPropertiesBlankNodes;
		this.props_level = props_level;
		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = Optional.of(uriBase);
		initialise();

		convert(ifc_filename, target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation, true, false, false);
	}

	/**
	 * IFCtoLBD constructor: Geometry selection version
	 * 
	 * @param ifc_filename                     The absolute path for the IFC file
	 *                                         that will be converted
	 * @param uriBase                          The URI base for all the elements
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
	 * @param hasBuildingProperties            The properties will be added into the
	 *                                         output
	 * @param hasSeparatePropertiesModel       The properties will be written in a
	 *                                         separate file
	 * @param hasPropertiesBlankNodes          Blank nodes are used
	 * @param hasGeolocation                   Geolocation, i.e., the latitude and
	 *                                         longitude are added.
	 * @param hasGeometry                      If bounding boxes are created for
	 *                                         elements.
	 */
	public IFCtoLBDConverter(String ifc_filename, String uriBase, String target_file, int props_level,
			boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,
			boolean hasSeparatePropertiesModel, boolean hasPropertiesBlankNodes, boolean hasGeolocation,
			boolean hasGeometry) {
		super();
		this.hasPropertiesBlankNodes = hasPropertiesBlankNodes;
		this.props_level = props_level;
		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = Optional.of(uriBase);
		System.out.println("Conversion starts");
		initialise();

		convert(ifc_filename, target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation, hasGeometry, false, false);
	}

	/**
	 * IFCtoLBD constructor
	 * 
	 * @param uriBase     The URI base for all the elements that will be created
	 * @param props_level The levels described in
	 *                    https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
	 * 
	 */
	public IFCtoLBDConverter(String uriBase, Integer... props_level) {
		super();
		if (props_level.length > 0)
			this.props_level = props_level[0];
		else
			this.props_level = 1;
		this.hasPropertiesBlankNodes = true;

		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = Optional.of(uriBase);
		System.out.println("Conversion starts");
		initialise();
	}

	/**
	 * IFCtoLBD constructor
	 * 
	 * @param uriBase                 The URI base for all the elements that will be
	 *                                created
	 * @param hasPropertiesBlankNodes Blank nodes are used
	 * 
	 * @param props_level             The levels described in
	 *                                https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
	 *                                longitude are added.
	 * 
	 */
	public IFCtoLBDConverter(String uriBase, boolean hasPropertiesBlankNodes, Integer... props_level) {
		super();
		if (props_level.length > 0)
			this.props_level = props_level[0];
		else
			this.props_level = 1;
		this.hasPropertiesBlankNodes = hasPropertiesBlankNodes;

		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = Optional.of(uriBase);
		initialise();
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
		boolean hasGeometry = false;
		boolean exportIfcOWL=false;
		boolean hasUnits=false;

		convert(ifc_filename, target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL, hasUnits);
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
		boolean hasGeolocation = false;
		boolean hasGeometry = false;
		boolean exportIfcOWL=true;
		boolean hasUnits=false;

		convert(ifc_filename, null, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
				hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL, hasUnits);
		return lbd_general_output_model;
	}

	private String unzip(String ifcZipFile) {
		ZipInputStream zis;
		try {
			byte[] buffer = new byte[1024];
			zis = new ZipInputStream(new FileInputStream(ifcZipFile));
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				System.out.println("entry: " + zipEntry);
				String name = zipEntry.getName().split("\\.")[0];
				File newFile = File.createTempFile("ifc", ".ifc");

				// write file content
				FileOutputStream fos = new FileOutputStream(newFile);
				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}
				fos.close();

				zipEntry = zis.getNextEntry();
				zis.close();
				newFile.deleteOnExit();
				return newFile.getAbsolutePath();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public CompletableFuture<IFCGeometry> getgeom(String ifc_filename) throws InterruptedException {
		CompletableFuture<IFCGeometry> completableFuture = new CompletableFuture<>();

		Executors.newCachedThreadPool().submit(() -> {
			IFCGeometry ifc_geometry = null;
			try {
				eventBus.post(new IFCtoLBD_SystemStatusEvent("ifcOpenShell for the geometry"));
				Timer timer = new Timer();
				this.ios = 0;
				// final long start = System.currentTimeMillis();
				timer.schedule(new TimerTask() {
					@Override
					public void run() {
						eventBus.post(new IFCtoLBD_SystemStatusEvent("ifcOpenShell running  " + ios++));
					}
				}, 1000, 1000);

				ifc_geometry = new IFCGeometry(new File(ifc_filename));
				timer.cancel();
			} catch (Exception e) {
				eventBus.post(new IFCtoLBD_SystemErrorEvent(this.getClass().getSimpleName(),
						"Geometry handling was not done. " + e.getMessage()));
				e.printStackTrace();
			}

			completableFuture.complete(ifc_geometry);
			return null;
		});

		return completableFuture;
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
	 * @param hasBuildingProperties            The properties will be added into the
	 *                                         output
	 * @param hasSeparatePropertiesModel       The properties will be written in a
	 *                                         separate file
	 * @param hasGeolocation                   Geolocation, i.e., the latitude and
	 *                                         longitude are added.
	 * @param hasGeometry                      If bounding boxes are created for
	 *                                         elements.
	 * @return The model as a Jena-model
	 */

	public Model convert(String ifc_filename, String target_file, boolean hasBuildingElements,
			boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties, boolean hasSeparatePropertiesModel,
			boolean hasGeolocation, boolean hasGeometry, boolean exportIfcOWL, boolean hasUnits) {
		boolean hasBoundingBoxWKT=false;

		return convert(ifc_filename, target_file, hasBuildingElements, hasSeparateBuildingElementsModel,
				hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL, hasUnits,
				(exportIfcOWL) ? false : true, hasBoundingBoxWKT);
	}

	public Model convert(String ifc_filename, String target_file, boolean hasBuildingElements,
			boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties, boolean hasSeparatePropertiesModel,
			boolean hasGeolocation, boolean hasGeometry, boolean exportIfcOWL, boolean hasUnits,
			boolean hasPerformanceBoost, boolean hasBoundingBoxWKT) {

		
		if(convert_read_in_phase(ifc_filename,target_file, hasGeometry,hasPerformanceBoost,exportIfcOWL))			
		{
		   return convert_LBD_phase(hasBuildingElements,
					hasSeparateBuildingElementsModel, hasBuildingProperties, hasSeparatePropertiesModel,
					hasGeolocation, hasGeometry, exportIfcOWL, hasUnits,	hasBoundingBoxWKT);	
		}
		
		return null;
	}

	private String target_file;
	
	public boolean convert_read_in_phase(String ifc_filename, String target_file, boolean hasGeometry,boolean hasPerformanceBoost,boolean exportIfcOWL) {

		this.target_file=target_file;

		if (ifc_filename.endsWith(".ifczip"))
			ifc_filename = unzip(ifc_filename);

		if (IfcOWLUtils.getExpressSchema(ifc_filename) == null) {
			eventBus.post(new IFCtoLBD_SystemStatusEvent("Not a valid IFC version."));
			return false;
		}

		CompletableFuture<IFCGeometry> future_ifc_geometry = null;
		if (hasGeometry)
			try {
				future_ifc_geometry = getgeom(ifc_filename);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

		this.ifcowl_model = readAndConvertIFC2ifcOWL(ifc_filename, uriBase.get(), !exportIfcOWL, target_file,
				hasPerformanceBoost); // Before:
		if(this.ifcowl_model ==null)
			return false;
		
		// readInOntologies(ifc_filename);
		System.out.println("Geometry?");

		if (future_ifc_geometry != null) {
			future_ifc_geometry.join();
			try {
				this.ifc_geometry = future_ifc_geometry.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Reading in ontologies");
		eventBus.post(new IFCtoLBD_SystemStatusEvent("Reading in ontologies"));
		readInOntologies(ifc_filename);
		createIfcLBDProductMapping();

		eventBus.post(new IFCtoLBD_SystemStatusEvent("Model ready in the memory."));

		return true;
	}


	public Model convert_LBD_phase(boolean hasBuildingElements,
			boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties, boolean hasSeparatePropertiesModel,
			boolean hasGeolocation, boolean hasGeometry, boolean exportIfcOWL, boolean hasUnits,
			boolean hasBoundingBoxWKT) {

		this.hasBoundingBoxWKT = hasBoundingBoxWKT;

		System.out.println("Product mapping");

		addNamespaces(uriBase.get(), props_level, hasBuildingElements, hasBuildingProperties);

		eventBus.post(new IFCtoLBD_SystemStatusEvent("IFC->LBD"));
		if (this.ontURI.isPresent())
			ifcOWL = new IfcOWL(this.ontURI.get());
		else {
			System.out.println("No ifcOWL ontology available.");
			eventBus.post(new IFCtoLBD_SystemStatusEvent("No ifcOWL ontology available."));
			return lbd_general_output_model;
		}

		System.out.println("Conversion phase 1");
		if (hasBuildingProperties) {
			handleUnitsAndPropertySetData(props_level, hasPropertiesBlankNodes, hasUnits);
		}

		System.out.println("Conversion phase 2");

		boolean namedGraphs=false; 
		try {
			conversion(this.target_file, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties,
					hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL, namedGraphs);
		} catch (Exception e) {
			eventBus.post(new IFCtoLBD_SystemErrorEvent(this.getClass().getSimpleName(),
					"Conversion: " + e.getMessage() + " line:" + e.getStackTrace()[0].getLineNumber()));

		}
		System.out.println("conversion done..");
		eventBus.post(new IFCtoLBD_SystemStatusEvent("Conversion done"));
		return lbd_general_output_model;

	}

	
	public static void main(String[] args) {
		JenaSystem.init();
		if (args.length > 3) {
			int level = 2;
			try {
				level = Integer.parseInt(args[3]);
			} catch (Exception e) {
				System.out.println("OPT level was not a number: Example: ");
				return;
			}
			System.out.println("Base URI: " + args[0]);
			System.out.println("Selected IFC File: " + args[1]);
			System.out.println("Targer TTL File: " + args[2]);
			System.out.println("OPM Level: " + level);
			new IFCtoLBDConverter(args[1], args[0], args[2], level, true, false, true, false, false, true);
		} else if (args.length > 2) {
			System.out.println("Base URI: " + args[0]);
			System.out.println("Selected IFC File: " + args[1]);
			System.out.println("Targer TTL File: " + args[2]);
			System.out.println("OPM Level: " + 2);
			new IFCtoLBDConverter(args[1], args[0], args[2], 2, true, false, true, false, false, true);
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
		} else {
			System.out.println("Usage:");
			System.out.println("IFCtoLBDConverter ifc_filename base_uri targer_file ");
			System.out.println("With an OPM level :");
			System.out.println("IFCtoLBDConverter ifc_filename base_uri targer_file level");
			System.out.println(
					"Example: java -jar IFCtoLBD_Java_15.jar  http://lbd.example.com/ c:\\IFC\\Duplex_A_20110505.ifc c:\\IFC\\Duplex_A_20110505.ttl");
		}
	}
	
	public void close()
	{
		if(this.lbd_general_output_model!=null)
		    lbd_general_output_model.close();
		if(this.lbd_product_output_model!=null)
			lbd_product_output_model.close();;
		if(this.lbd_property_output_model!=null)
			lbd_property_output_model.close();;
		if(this.ifcowl_model!=null)
			ifcowl_model.removeAll();
		this.ifcowl_product_map.clear();
		this.propertysets.clear();
	}

}
