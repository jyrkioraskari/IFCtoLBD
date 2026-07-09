package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DuplexLbdPatternShaclTest {
	private static final String BASE_URI = "https://dot.dc.rwth-aachen.de/IFCtoLBDset#";
	private static final String CORE_SHAPES = "SHACL_duplex_lbd_patterns_core.ttl";
	private static final String IFCOWL_LINK_SHAPES = "SHACL_duplex_lbd_patterns_ifcowl_links.ttl";
	private static final String INTERFACE_SHAPES = "SHACL_duplex_lbd_patterns_interfaces.ttl";
	private static final String GEOMETRY_SHAPES = "SHACL_duplex_lbd_patterns_geometry.ttl";

	@DisplayName("Duplex_LBD reference output conforms to the documented LBD pattern shapes")
	@Test
	public void duplexReferenceOutputConformsToPatternShapes() {
		Model reference = loadResourceModel("Duplex_LBD.ttl");
		assertTrue(reference.size() > 0, "Duplex_LBD.ttl should contain triples.");

		assertConforms(reference, CORE_SHAPES);
		assertConforms(reference, IFCOWL_LINK_SHAPES);
		assertConforms(reference, INTERFACE_SHAPES);
		assertConforms(reference, GEOMETRY_SHAPES);
	}

	@DisplayName("Converter output conforms to the core Duplex LBD pattern shapes")
	@Test
	public void converterOutputConformsToCorePatternShapes() {
		try {
			Path outputDir = Path.of("target", "duplex-pattern-tests");
			Files.createDirectories(outputDir);
			File targetFile = Files.createTempFile(outputDir, "duplex-pattern", ".ttl").toFile();

			IFCtoLBDConverter converter = new IFCtoLBDConverter(BASE_URI, false, Integer.valueOf(3));
			boolean readOk = converter.convert_read_in_phase(resourceFile("Duplex.ifc").getAbsolutePath(),
					targetFile.getAbsolutePath(), false, false, false, true, true, false, true);
			assertTrue(readOk, "Read-in phase should succeed.");

			Model converted = converter.convert_LBD_phase(true, false, true, false, false, false, false, true, false, false,
					false, false, false);
			assertNotNull(converted, "Converted model should not be null.");
			assertTrue(converted.size() > 0, "Converted model should contain triples.");
			assertTrue(targetFile.length() > 0, "Converted Turtle output should be written.");

			Model written = loadModel(targetFile);
			assertTrue(written.size() > 0, "Converted Turtle output should contain triples.");
			assertConforms(written, CORE_SHAPES);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Duplex LBD SHACL pattern test had an error: " + e.getMessage());
		}
	}

	private Model loadResourceModel(String resourceName) {
		return loadModel(resourceFile(resourceName));
	}

	private Model loadModel(File file) {
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model, file.getAbsolutePath(), Lang.TTL);
		return model;
	}

	private File resourceFile(String resourceName) {
		try {
			URL resourceUrl = ClassLoader.getSystemResource(resourceName);
			assertNotNull(resourceUrl, "Test resource not found: " + resourceName);
			return new File(resourceUrl.toURI());
		} catch (Exception e) {
			throw new IllegalStateException("Cannot resolve test resource: " + resourceName, e);
		}
	}

	private void assertConforms(Model dataModel, String shapeResourceName) {
		Graph shapesGraph = RDFDataMgr.loadGraph(resourceFile(shapeResourceName).getAbsolutePath());
		Shapes shapes = Shapes.parse(shapesGraph);
		ValidationReport report = ShaclValidator.get().validate(shapes, dataModel.getGraph());

		if (!report.conforms()) {
			StringWriter reportWriter = new StringWriter();
			RDFDataMgr.write(reportWriter, report.getModel(), RDFFormat.TURTLE_PRETTY);
			fail("Data does not conform to " + shapeResourceName + System.lineSeparator() + reportWriter);
		}
	}
}
