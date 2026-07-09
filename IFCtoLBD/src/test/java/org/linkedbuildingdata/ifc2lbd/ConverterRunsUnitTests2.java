package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConverterRunsUnitTests2 {
	private static final String BASE_URI = "https://dot.dc.rwth-aachen.de/IFCtoLBDset#";

	private File duplexIfcFile() throws Exception {
		URL fileUrl = ClassLoader.getSystemResource("Duplex.ifc");
		if (fileUrl == null)
			fail("Test data Duplex.ifc not found/available");
		return new File(fileUrl.toURI());
	}

	private IFCtoLBDConverter readInDuplex(File targetFile) throws Exception {
		IFCtoLBDConverter converter = new IFCtoLBDConverter(BASE_URI, false, Integer.valueOf(1));
		boolean readOk = converter.convert_read_in_phase(duplexIfcFile().getAbsolutePath(), targetFile.getAbsolutePath(),
				false, false, false, true, true, false, false, false);
		assertTrue(readOk, "Read-in phase should succeed.");
		return converter;
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
}
