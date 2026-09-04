
package org.linkedbuildingdata.ifc2lbd;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Callable;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sys.JenaSystem;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/*
 * 
 *  IFCtoLBD Command Line interface
 *  
 *  Copyright (c) 2023, 2024, 2025 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

@Command(name = "IFCtoLBD_CLI", mixinStandardHelpOptions = true,
		versionProvider = IFCtoLBDConverter_CLI.ManifestVersionProvider.class)
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
			"--hasBuildingElements" }, arity = "0..1", fallbackValue = "true", description = "The Building Elements will be created in the output.")
	private Optional<Boolean> hasBuildingElements;

	@Option(names = {
			"--hasSeparateBuildingElementsModel" }, arity = "0..1", fallbackValue = "true", description = "The Building elements will have a separate file.")
	private Optional<Boolean> hasSeparateBuildingElementsModel;

	@Option(names = { "-p",
			"--hasBuildingElementProperties" }, arity = "0..1", fallbackValue = "true", description = "The properties will ne added into the output.")
	private Optional<Boolean> hasBuildingProperties;

	@Option(names = {
			"--hasSeparatePropertiesModel" }, arity = "0..1", fallbackValue = "true", description = "The properties will be written in a separate file.")
	private Optional<Boolean> hasSeparatePropertiesModel;

	@Option(names = { "-b", "--hasBlankNodes" }, arity = "0..1", fallbackValue = "true", description = "Blank nodes are used.")
	private Optional<Boolean> hasPropertiesBlankNodes;

	@Option(names = { "--hasGeolocation" }, arity = "0..1", fallbackValue = "true", description = "Geolocation, i.e., the latitude and longitude are added.")
	private Optional<Boolean> hasGeolocation;

	@Option(names = { "--hasGeometry" }, arity = "0..1", fallbackValue = "true", description = "The bounding boxes are generated for elements.")
	private Optional<Boolean> hasGeometry;

	@Option(names = { "--hasWKT" }, arity = "0..1", fallbackValue = "true", description = "The bounding boxes are generated as WKT.")
	private Optional<Boolean> hasBoundingBoxWKT;

	@Option(names = { "-hasWireframe",
			"--hasWireframe" }, arity = "0..1", fallbackValue = "true", description = "Export simple mesh wireframes as lbd:hasWireframe WKT literals.")
	private Optional<Boolean> hasWireframe;
	
	@Option(names = { "--ifcOWL" }, arity = "0..1", fallbackValue = "true", description = "An ifcOWL  model is created and linked.")
	private Optional<Boolean> exportIfcOWL;

	
	@Option(names = { "--hasIfc_based_elements" }, arity = "0..1", fallbackValue = "true", description = "An IFC  based elements.")
	private Optional<Boolean> hasIfc_based_elements;
	
	
	@Option(names = {
			"--hasTriG" }, arity = "0..1", fallbackValue = "true", description = "TriG is a serialization format for RDF (Resource Description Framework) graphs. It is a plain text format for serializing named graphs")
	private Optional<Boolean> namedGraphs;

	@Option(names = { "--hasUnits" }, arity = "0..1", fallbackValue = "true", description = "Data units are added.")
	private Optional<Boolean> hasUnits;

	@Option(names = { "--hasHierarchicalNaming" }, arity = "0..1", fallbackValue = "true", description = "HierarchicalNaming is used.")
	private Optional<Boolean> hasHierarchicalNaming;

	@Option(names = "--naming-strategy", description = "IRI strategy: ${COMPLETION-CANDIDATES}")
	private Optional<ConversionProperties.NamingStrategy> namingStrategy;

	@Option(names = { "--hasSimpleProperties" }, arity = "0..1", fallbackValue = "true", description = "Simplified property predicates are used.")
	private Optional<Boolean> hasSimpleProperties;

	@Option(names = { "-asPropertySets", "--propertiesAsPropertySets" }, arity = "0..1", fallbackValue = "true", description = "Export properties as bSDD-typed property sets with OPM property states.")
	private Optional<Boolean> propertiesAsPropertySets;

	@Option(names = { "--selectedType" }, description = "Include the selected element type. May be repeated.")
	private Set<String> selectedTypes = new HashSet<>();

	@Option(names = { "--selectedPropertySet" }, description = "Include the selected property set. May be repeated.")
	private Set<String> selectedPropertySets = new HashSet<>();
	@Option(names = { "--property-mappings" }, description = "JSON array of property mapping rules.")
	private Optional<String> propertyMappings;

	
	@Option(names = { "--hasPerformanceBoost" }, arity = "0..1", fallbackValue = "true", description = "PerformanceBoost is used.")
	private Optional<Boolean> hasPerformanceBoost;

	@Option(names = { "--hasInterfaces" }, arity = "0..1", fallbackValue = "true", description = "Export BoundinBox style BOT interfaces.")
	private Optional<Boolean> hasInterfaces;


	
	@Option(names = { "--JSON" }, arity = "0..1", fallbackValue = "true", description = "Export as JSON-LD.")
	private Optional<Boolean> exportJSON;

	@Option(names = "--profile", description = "Named conversion profile (for example core, properties-opm, geometry-full).")
	private Optional<String> profile;

	@Option(names = "--validate", arity = "0..1", fallbackValue = "true", description = "Run all standard SHACL validation packs.")
	private Optional<Boolean> validate;

	@Option(names = "--validation", description = "SHACL validation pack. May be repeated. Values: ${COMPLETION-CANDIDATES}")
	private Set<ValidationShapePack> validationPacks = new HashSet<>();

	@Option(names = "--model-scope", description = "Stable model identity namespace required by revision-ready profiles.")
	private Optional<String> modelScope;

	
	
	@Override
	public Integer call() throws Exception {
		File ifcFile = new File(ifc_filename);
		if (!ifcFile.isFile()) {
			System.err.println("Cannot read IFC file: " + ifc_filename);
			System.err.println("Resolved path: " + ifcFile.getAbsolutePath());
			System.err.println("Current working directory: " + new File(".").getCanonicalPath());
			System.err.println("Use an absolute path or run the command from the folder that contains the IFC file.");
			return 1;
		}

		String outputFile = target_file.orElseGet(() -> ifc_filename.replaceFirst("(?i)\\.ifc(?:zip)?$", "")
				+ (namedGraphs.orElse(false) ? ".trig" : exportJSON.orElse(false) ? ".jsonld" : ".ttl"));
		ConversionRequest request;
		if (profile.isPresent()) {
			request = new ConversionRequest(ifcFile.getAbsolutePath(), ConversionProfiles.named(profile.get()));
		} else {
			ConversionProperties properties = legacyProperties();
			request = new ConversionRequest(ifcFile.getAbsolutePath(), properties);
		}
		request = request.withSelectedTypes(selectedTypes).withSelectedPropertySets(selectedPropertySets);
		if (modelScope.isPresent()) request = request.withModelScope(modelScope.get());
		if (validate.orElse(false)) request = request.withStandardValidation();
		else if (!validationPacks.isEmpty())
			request = request.withValidation(validationPacks.toArray(ValidationShapePack[]::new));

		System.out.println("Target is: " + outputFile);
		try (IFCtoLBDConverter converter = new IFCtoLBDConverter(uriBase.orElse("https://lbd.example.com/"),
				hasPropertiesBlankNodes.orElse(false), props_level.orElse(1));
				ConversionResult result = converter.convert(request);
				OutputStream output = Files.newOutputStream(Path.of(outputFile))) {
			if (namedGraphs.orElse(false)) RDFDataMgr.write(output, result.getDataset(), RDFFormat.TRIG_PRETTY);
			else if (exportJSON.orElse(false)) RDFDataMgr.write(output, result.getModel(), RDFFormat.JSONLD);
			else RDFDataMgr.write(output, result.getModel(), RDFFormat.TURTLE_PRETTY);
		}
		return 0;
	}

	private ConversionProperties legacyProperties() {
		ConversionProperties properties = new ConversionProperties();
		properties.setHasBuildingElements(hasBuildingElements.orElse(false));
		properties.setHasSeparateBuildingElementsModel(hasSeparateBuildingElementsModel.orElse(false));
		properties.setHasBuildingProperties(hasBuildingProperties.orElse(false));
		properties.setHasSeparatePropertiesModel(hasSeparatePropertiesModel.orElse(false));
		properties.setHasGeolocation(hasGeolocation.orElse(false));
		boolean wireframe = hasWireframe.orElse(false);
		properties.setHasGeometry(hasGeometry.orElse(false) || wireframe);
		properties.setExportIfcOWL(exportIfcOWL.orElse(false));
		properties.setHasUnits(hasUnits.orElse(false));
		properties.setHasBoundingBoxWKT(hasBoundingBoxWKT.orElse(false));
		properties.setHasWireframe(wireframe);
		properties.setHasHierarchicalNaming(hasHierarchicalNaming.orElse(false));
		if (namingStrategy.isPresent()) properties.setNamingStrategy(namingStrategy.get());
		properties.setHasPerformanceBoost(hasPerformanceBoost.orElse(false));
		properties.setHasNonLBDElement(hasIfc_based_elements.orElse(false));
		properties.setHasInterfaces(hasInterfaces.orElse(false));
		if (propertiesAsPropertySets.orElse(false)) properties.setPropertyMode(ConversionProperties.PropertyMode.OPM);
		else if (hasSimpleProperties.orElse(false)) properties.setPropertyMode(ConversionProperties.PropertyMode.SIMPLE);
		propertyMappings.ifPresent(json -> {
			try { properties.setPropertyMappings(new ObjectMapper().readValue(json, new TypeReference<List<PropertyMappingRule>>() {})); }
			catch (Exception e) { throw new IllegalArgumentException("Invalid --property-mappings JSON", e); }
		});
		return properties;
	}

	public static void main(String[] args) {
		JenaSystem.init();
		IFCtoLBDConverter_CLI cli = new IFCtoLBDConverter_CLI();
		int exitCode = new CommandLine(cli).execute(args);
		System.exit(exitCode);
	}

	public static final class ManifestVersionProvider implements CommandLine.IVersionProvider {
		@Override public String[] getVersion() {
			String version = IFCtoLBDConverter.class.getPackage().getImplementationVersion();
			return new String[] { version == null || version.isBlank() ? "development build" : version };
		}
	}

}
