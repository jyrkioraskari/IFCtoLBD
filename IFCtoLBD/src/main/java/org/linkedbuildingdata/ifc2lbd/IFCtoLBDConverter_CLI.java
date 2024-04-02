
package org.linkedbuildingdata.ifc2lbd;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.sys.JenaSystem;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/*
 * 
 *  IFCtoLBD Command Line interface
 *  
 *  Copyright (c) 2023, 2024 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

@Command(name = "IFCtoLBD_CLI", mixinStandardHelpOptions = true)
public class IFCtoLBDConverter_CLI implements Callable<Integer> {

	/*
	 * 
	 * @param uriBase
	 * 
	 * @param target_file
	 * 
	 * @param props_level The levels described in
	 * https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/
	 * presentation_LBDcall_20180312_final.pdf
	 */

	@Parameters(index = "0", description = "The absolute path for the IFC file that will be converted.")
	private String ifc_filename;

	@Option(names = { "-t",
			"--target_file" }, required = false, description = "he main file name for the output. If there are many, they will be sharing the same name beginning.")
	private Optional<String> target_file;

	@Option(names = { "-u",
			"--url" }, required = false, description = "The URI base for all the elemenents that will be created.")
	private Optional<String> uriBase;

	@Option(names = { "-l", "--level" }, required = false, description = "The OPM ontology complexity level")
	private Optional<Integer> props_level;

	@Option(names = { "-be",
			"--hasBuildingElements" }, description = "The Building Elements will be created in the output.")
	private Optional<Boolean> hasBuildingElements;

	@Option(names = {
			"--hasSeparateBuildingElementsModel" }, description = "The Building elements will have a separate file.")
	private Optional<Boolean> hasSeparateBuildingElementsModel;

	@Option(names = { "-p",
			"--hasBuildingElementProperties" }, description = "The properties will ne added into the output.")
	private Optional<Boolean> hasBuildingProperties;

	@Option(names = {
			"--hasSeparatePropertiesModel" }, description = "The properties will be written in a separate file.")
	private Optional<Boolean> hasSeparatePropertiesModel;

	@Option(names = { "-b", "--hasBlankNodes" }, description = "Blank nodes are used.")
	private Optional<Boolean> hasPropertiesBlankNodes;

	@Option(names = { "--hasGeolocation" }, description = "Geolocation, i.e., the latitude and longitude are added.")
	private Optional<Boolean> hasGeolocation;

	@Option(names = { "--hasGeometry" }, description = "The bounding boxes are generated for elements.")
	private Optional<Boolean> hasGeometry;

	@Option(names = { "--hasWKT" }, description = "The bounding boxes are generated as WKT.")
	private Optional<Boolean> hasBoundingBoxWKT;

	
	@Option(names = { "--ifcOWL" }, description = "An ifcOWL  model is created and linked.")
	private Optional<Boolean> exportIfcOWL;

	@Option(names = {
			"--hasTriG" }, description = "TriG is a serialization format for RDF (Resource Description Framework) graphs. It is a plain text format for serializing named graphs")
	private Optional<Boolean> namedGraphs;

	@Option(names = { "--hasUnits" }, description = "Data units are added.")
	private Optional<Boolean> hasUnits;

	@Option(names = { "--hasHierarchicalNaming" }, description = "HierarchicalNaming is used.")
	private Optional<Boolean> hasHierarchicalNaming;

	
	@Option(names = { "--hasPerformanceBoost" }, description = "PerformanceBoost is used.")
	private Optional<Boolean> hasPerformanceBoost;

	
	
	
	
	@Override
	public Integer call() throws Exception {
		String ifc_filename = this.ifc_filename;

		String uriBase = "https://lbd.example.com/";
		if (this.uriBase.isPresent())
			uriBase = this.uriBase.get();

		String target_file = ifc_filename.split("\\.ifc")[0] + ".ttl";
		if (this.target_file.isPresent())
			target_file = this.target_file.get();

		int props_level = 1;
		if (this.props_level.isPresent())
			props_level = this.props_level.get();

		boolean hasBuildingElements = false;
		if (this.hasBuildingElements.isPresent())
			hasBuildingElements = this.hasBuildingElements.get();

		boolean hasSeparateBuildingElementsModel = false;
		if (this.hasSeparateBuildingElementsModel.isPresent())
			hasSeparateBuildingElementsModel = this.hasSeparateBuildingElementsModel.get();

		boolean hasBuildingProperties = false;
		if (this.hasBuildingProperties.isPresent())
			hasBuildingProperties = this.hasBuildingProperties.get();

		boolean hasSeparatePropertiesModel = false;
		if (this.hasSeparatePropertiesModel.isPresent())
			hasSeparatePropertiesModel = this.hasSeparatePropertiesModel.get();

		boolean hasPropertiesBlankNodes = false;
		if (this.hasPropertiesBlankNodes.isPresent())
			hasPropertiesBlankNodes = this.hasPropertiesBlankNodes.get();

		boolean hasGeolocation = false;
		if (this.hasGeolocation.isPresent())
			hasGeolocation = this.hasGeolocation.get();

		System.out.println("Target is: " + target_file);

		boolean hasGeometry = false;
		if (this.hasGeometry.isPresent())
			hasGeometry = this.hasGeometry.get();

		boolean exportIfcOWL = false;
		if (this.exportIfcOWL.isPresent())
			exportIfcOWL = this.exportIfcOWL.get();

		
		boolean hasBoundingBoxWKT = false ;
		if (this.hasBoundingBoxWKT.isPresent())
			hasBoundingBoxWKT = this.hasBoundingBoxWKT.get();

		boolean hasHierarchicalNaming = false ;
		if (this.hasHierarchicalNaming.isPresent())
			hasHierarchicalNaming = this.hasHierarchicalNaming.get();

		boolean hasPerformanceBoost = false ;
		if (this.hasPerformanceBoost.isPresent())
			hasPerformanceBoost = this.hasPerformanceBoost.get();

		//boolean namedGraphs = false;
		//if (this.namedGraphs.isPresent())
		//	namedGraphs = this.namedGraphs.get();

		boolean hasUnits = false;
		if (this.hasUnits.isPresent())
			hasUnits = this.hasUnits.get();

		IFCtoLBDConverter c1nb = new IFCtoLBDConverter(uriBase, hasPropertiesBlankNodes, props_level);
		c1nb.convert(ifc_filename, target_file, hasBuildingElements, hasSeparateBuildingElementsModel,
				hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL, hasUnits);

		
		
		try (IFCtoLBDConverter converter = new IFCtoLBDConverter(uriBase, hasPropertiesBlankNodes,
				props_level);) {
			converter.convert_read_in_phase(ifc_filename, target_file, hasGeometry, hasPerformanceBoost,
					exportIfcOWL,hasBuildingElements,hasBuildingProperties,hasBoundingBoxWKT,hasUnits);
						
			Model m =converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
					hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
					hasUnits, hasBoundingBoxWKT, hasHierarchicalNaming);
		}
		return null;
	}

	public static void main(String[] args) {
		JenaSystem.init();
		IFCtoLBDConverter_CLI cli = new IFCtoLBDConverter_CLI();
		CommandLine commandLine = new CommandLine(cli);
		int exitCode = commandLine.execute(args);
		if (commandLine.isVersionHelpRequested()) {

			System.out.println("Program version is  2.43.5.");

		}
		System.exit(exitCode);
	}

}
