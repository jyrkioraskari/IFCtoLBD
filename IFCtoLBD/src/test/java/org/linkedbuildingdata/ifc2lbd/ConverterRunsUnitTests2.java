package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public class ConverterRunsUnitTests2 {
	private static final String BASE_URI = "https://dot.dc.rwth-aachen.de/IFCtoLBDset#";

	private File duplexIfcFile() throws Exception {
		URL fileUrl = ClassLoader.getSystemResource("Duplex.ifc");
		if (fileUrl == null)
			fail("Test data Duplex.ifc not found/available");
		return new File(fileUrl.toURI());
	}

	private IFCtoLBDConverter readInDuplex(File targetFile) throws Exception {
		return readInDuplex(targetFile, 1);
	}

	private IFCtoLBDConverter readInDuplex(File targetFile, int propertyLevel) throws Exception {
		IFCtoLBDConverter converter = new IFCtoLBDConverter(BASE_URI, false, Integer.valueOf(propertyLevel));
		boolean readOk = converter.convert_read_in_phase(duplexIfcFile().getAbsolutePath(), targetFile.getAbsolutePath(),
				false, false, false, true, true, false, false, false);
		assertTrue(readOk, "Read-in phase should succeed.");
		return converter;
	}

	@DisplayName("Legacy OPM properties and states occur only in the property file")
	@Test
	public void legacyOpmPropertiesStayInPropertiesFile() {
		try {
			File generalTarget = File.createTempFile("ifc2lbd-opm-partitioned", ".ttl");
			IFCtoLBDConverter converter = readInDuplex(generalTarget, 3);
			converter.convert_LBD_phase(true, true, true, true, false, false, false, false, false, false,
					false, false, false);

			String stem = generalTarget.getAbsolutePath().substring(0,
					generalTarget.getAbsolutePath().lastIndexOf("."));
			File productTarget = new File(stem + "_building_elements.ttl");
			File propertyTarget = new File(stem + "_element_properties.ttl");
			Model general = assertRdfFileHasTriples(generalTarget, Lang.TTL);
			Model products = assertRdfFileHasTriples(productTarget, Lang.TTL);
			Model properties = assertRdfFileHasTriples(propertyTarget, Lang.TTL);
			try {
				assertCompactOpmNamespaces(generalTarget, general);
				assertCompactOpmNamespaces(productTarget, products);
				assertCompactOpmNamespaces(propertyTarget, properties);
				Resource opmProperty = properties.createResource("https://w3id.org/opm#Property");
				var hasPropertyState = properties.createProperty("https://w3id.org/opm#hasPropertyState");
				var sourceKind = properties.createProperty("https://w3id.org/ifctolbd/evidence#sourceKind");
				var occurrences = properties.listResourcesWithProperty(RDF.type, opmProperty).toList();
				assertFalse(occurrences.isEmpty(), "Level 3 conversion should create OPM property occurrences");
				for (Resource occurrence : occurrences) {
					assertTrue(occurrence.hasProperty(sourceKind), "OPM property should retain source evidence");
					assertFalse(general.containsResource(occurrence));
					assertFalse(products.containsResource(occurrence));
					Resource state = occurrence.getPropertyResourceValue(hasPropertyState);
					assertTrue(state != null, "Level 3 OPM property should have a state");
					assertFalse(general.containsResource(state));
					assertFalse(products.containsResource(state));
				}
				assertDisjoint(general, products);
				assertDisjoint(general, properties);
				assertDisjoint(products, properties);

				File icddTarget = File.createTempFile("ifc2lbd-opm-partitioned", ".icdd");
				converter.exportExistingOutputAsIcdd(icddTarget.getAbsolutePath(), duplexIfcFile().getAbsolutePath());
				try (ZipFile zip = new ZipFile(icddTarget)) {
					Model icddGeneral = ModelFactory.createDefaultModel();
					Model icddProducts = ModelFactory.createDefaultModel();
					Model icddProperties = ModelFactory.createDefaultModel();
					try {
						RDFDataMgr.read(icddGeneral,
								zip.getInputStream(zip.getEntry("Payload documents/lbd/general.ttl")), Lang.TTL);
						RDFDataMgr.read(icddProducts,
								zip.getInputStream(zip.getEntry("Payload documents/lbd/building-elements.ttl")), Lang.TTL);
						RDFDataMgr.read(icddProperties,
								zip.getInputStream(zip.getEntry("Payload documents/lbd/properties.ttl")), Lang.TTL);
						assertCompactOpmNamespaces(readZipText(zip, "Payload documents/lbd/general.ttl"), icddGeneral);
						assertCompactOpmNamespaces(readZipText(zip, "Payload documents/lbd/building-elements.ttl"),
								icddProducts);
						assertCompactOpmNamespaces(readZipText(zip, "Payload documents/lbd/properties.ttl"),
								icddProperties);
						String label = "PSet_Revit_Type_Other:classificationCode";
						assertTrue(icddProperties.contains(null, RDFS.label, label));
						assertFalse(icddGeneral.contains(null, RDFS.label, label));
					} finally {
						icddGeneral.close();
						icddProducts.close();
						icddProperties.close();
					}
				}
			} finally {
				general.close();
				products.close();
				properties.close();
				converter.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("Legacy OPM partition test had an error: " + e.getMessage());
		}
	}

	private void assertCompactOpmNamespaces(File file, Model model) throws Exception {
		assertCompactOpmNamespaces(Files.readString(file.toPath()), model);
	}

	private void assertCompactOpmNamespaces(String turtle, Model model) {
		assertFalse(turtle.contains("<https://w3id.org/opm#CurrentPropertyState>"));
		assertFalse(turtle.contains("<http://schema.org/value>"));
		assertFalse(turtle.contains("<http://www.w3.org/ns/prov#generatedAtTime>"));
		assertFalse(turtle.contains("<https://w3id.org/ifctolbd/evidence#unitResolutionMethod>"));
		assertEquals(OPM.ns, model.getNsPrefixURI("opm"));
		assertEquals(OPM.schema_ns, model.getNsPrefixURI("schema"));
		assertEquals(OPM.prov_ns, model.getNsPrefixURI("prov"));
		assertEquals("https://w3id.org/ifctolbd/evidence#", model.getNsPrefixURI("evidence"));
	}

	private String readZipText(ZipFile zip, String entry) throws Exception {
		return new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(), StandardCharsets.UTF_8);
	}

	private Model convertDefault(IFCtoLBDConverter converter) {
		return converter.convert_LBD_phase(true, false, true, false, false, false, false, false, false, false, false,
				false, false);
	}

	private Model assertRdfFileHasTriples(File file, Lang lang) {
		assertTrue(file.exists(), "RDF output file should exist.");
		assertTrue(file.length() > 0, "RDF output file should not be byte-empty.");
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model, file.getAbsolutePath(), lang);
		assertTrue(model.size() > 0, "RDF output file should contain triples.");
		return model;
	}

	@DisplayName("Repeated conversion starts from clean output state")
	@Test
	public void repeatedConversionKeepsSameModelSize() {
		try {
			File targetFile = File.createTempFile("ifc2lbd-repeat", ".ttl");
			IFCtoLBDConverter converter = readInDuplex(targetFile);

			Model first = convertDefault(converter);
			long firstSize = first.size();
			assertTrue(firstSize > 0, "First conversion should create triples.");
			assertEquals(firstSize, assertRdfFileHasTriples(targetFile, Lang.TTL).size(),
					"Written Turtle output should contain the first conversion triples.");

			Model second = convertDefault(converter);
			assertEquals(firstSize, second.size(), "Repeated conversion should not lose or accumulate triples.");
			assertEquals(firstSize, assertRdfFileHasTriples(targetFile, Lang.TTL).size(),
					"Written Turtle output should contain the repeated conversion triples.");
		} catch (Exception e) {
			e.printStackTrace();
			fail("Repeated conversion test had an error: " + e.getMessage());
		}
	}

	@DisplayName("Existing converted model can be exported as JSON-LD without reconversion")
	@Test
	public void exportExistingModelAsJsonLd() {
		try {
			File ttlTarget = File.createTempFile("ifc2lbd-export", ".ttl");
			IFCtoLBDConverter converter = readInDuplex(ttlTarget);
			Model converted = convertDefault(converter);
			assertTrue(converted.size() > 0, "Conversion should create triples before export.");

			File jsonLdTarget = File.createTempFile("ifc2lbd-export", ".jsonld");
			converter.exportExistingOutput(jsonLdTarget.getAbsolutePath(), false, false, true);

			assertTrue(jsonLdTarget.exists(), "JSON-LD export file should exist.");
			assertTrue(jsonLdTarget.length() > 0, "JSON-LD export file should not be empty.");
			Model jsonLdModel = ModelFactory.createDefaultModel();
			RDFDataMgr.read(jsonLdModel, jsonLdTarget.getAbsolutePath(), Lang.JSONLD);
			assertEquals(converted.size(), jsonLdModel.size(), "JSON-LD export should preserve the triple count.");
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON-LD export test had an error: " + e.getMessage());
		}
	}

	@DisplayName("JSON-LD target creates .trig file, not .trigld")
	@Test
	public void jsonLdTrigExportUsesTrigExtension() {
		try {
			File ttlTarget = File.createTempFile("ifc2lbd-trig-source", ".ttl");
			IFCtoLBDConverter converter = readInDuplex(ttlTarget);
			convertDefault(converter);

			File jsonLdTarget = File.createTempFile("ifc2lbd-trig-target", ".jsonld");
			converter.exportExistingOutput(jsonLdTarget.getAbsolutePath(), false, true, true);
			File trigTarget = new File(jsonLdTarget.getAbsolutePath().substring(0,
					jsonLdTarget.getAbsolutePath().lastIndexOf(".")) + ".trig");
			File wrongTrigTarget = new File(jsonLdTarget.getAbsolutePath().substring(0,
					jsonLdTarget.getAbsolutePath().lastIndexOf(".")) + ".trigld");

			assertTrue(trigTarget.exists(), "TriG export should use the .trig extension.");
			assertTrue(trigTarget.length() > 0, "TriG export file should not be empty.");
			assertTrue(!wrongTrigTarget.exists(), "TriG export must not use the .trigld extension.");
			Dataset dataset = RDFDataMgr.loadDataset(trigTarget.getAbsolutePath());
			assertTrue(dataset.getDefaultModel().size() > 0, "TriG default graph should contain triples.");
		} catch (Exception e) {
			e.printStackTrace();
			fail("TriG export test had an error: " + e.getMessage());
		}
	}

	@DisplayName("Existing converted model can export separate property file")
	@Test
	public void exportExistingModelWithSeparateProperties() {
		try {
			File ttlTarget = File.createTempFile("ifc2lbd-separate-source", ".ttl");
			IFCtoLBDConverter converter = readInDuplex(ttlTarget);
			converter.convert_LBD_phase(true, false, true, true, false, false, false, false, false, false, false, false,
					false);

			File exportTarget = File.createTempFile("ifc2lbd-separate-target", ".ttl");
			converter.exportExistingOutput(exportTarget.getAbsolutePath(), true, false, false);
			File propertyTarget = new File(exportTarget.getAbsolutePath().substring(0,
					exportTarget.getAbsolutePath().lastIndexOf(".")) + "_element_properties.ttl");

			assertTrue(exportTarget.exists(), "Main Turtle export file should exist.");
			assertTrue(exportTarget.length() > 0, "Main Turtle export file should not be empty.");
			assertRdfFileHasTriples(exportTarget, Lang.TTL);
			assertTrue(propertyTarget.exists(), "Separate property export file should exist.");
			assertTrue(propertyTarget.length() > 0, "Separate property export file should not be empty.");
			assertRdfFileHasTriples(propertyTarget, Lang.TTL);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Separate properties export test had an error: " + e.getMessage());
		}
	}

	@DisplayName("Separate UI Turtle files use the same disjoint partition as ICDD")
	@Test
	public void separateUiFilesAreDisjointAndKeepPropertySetsInPropertiesFile() {
		try {
			File generalTarget = File.createTempFile("ifc2lbd-partitioned", ".ttl");
			IFCtoLBDConverter converter = readInDuplex(generalTarget);
			converter.setPropertiesAsPropertySets(true);
			converter.convert_LBD_phase(true, true, true, true, false, false, false, false, false, false,
					false, false, false);

			String stem = generalTarget.getAbsolutePath().substring(0,
					generalTarget.getAbsolutePath().lastIndexOf("."));
			File productTarget = new File(stem + "_building_elements.ttl");
			File propertyTarget = new File(stem + "_element_properties.ttl");
			Model general = assertRdfFileHasTriples(generalTarget, Lang.TTL);
			Model products = assertRdfFileHasTriples(productTarget, Lang.TTL);
			Model properties = assertRdfFileHasTriples(propertyTarget, Lang.TTL);
			try {
				String label = "PSet_Revit_Constraints:topExtensionDistance";
				assertTrue(properties.contains(null, RDFS.label, label));
				assertFalse(general.contains(null, RDFS.label, label));
				assertFalse(products.contains(null, RDFS.label, label));
				assertDisjoint(general, products);
				assertDisjoint(general, properties);
				assertDisjoint(products, properties);
			} finally {
				general.close();
				products.close();
				properties.close();
				converter.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("Separate file partition test had an error: " + e.getMessage());
		}
	}

	private static void assertDisjoint(Model first, Model second) {
		Model overlap = first.intersection(second);
		try {
			assertTrue(overlap.isEmpty(), "Separate RDF files must not contain duplicate triples");
		} finally {
			overlap.close();
		}
	}
}
