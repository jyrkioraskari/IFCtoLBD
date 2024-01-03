package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;
import java.util.Optional;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.core.IFCtoRDF;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;

import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;
import com.google.common.eventbus.EventBus;

public class ConverterUnitTests {

	

	@DisplayName("Test the ontolgy read")
	@Test
	public void test_getOntology() {
		
		try {
			URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
			Model ontology_model = ModelFactory.createDefaultModel();
			File file = new File(file_url.toURI());
			IfcOWLUtils.readIfcOWLOntology(file.getAbsolutePath(), ontology_model);
			EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
			RDFUtils.readInOntologyTTL(ontology_model, "prod.ttl", eventBus);
		} catch (Exception e) {
			fail("Test get ontologies failed: " + e.getMessage());
		}
	}
	
	@DisplayName("Test the existence of the test data Duplex.ifc")
	@Test
	public void findTestData() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File file = new File(file_url.toURI());
			if (file == null || !file.exists())
				fail("Test data not found/available");
		} catch (Exception e) {
			fail("Test data not found/available: " + e.getMessage());
		}
	}

	@DisplayName("Test ifcOWL")
    @Test
    public void test_ifcOWL_Conversion() {
        URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
        try {
            File ifc_file = new File(file_url.toURI());
            File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
            IFCtoRDF rj = new IFCtoRDF();
            Optional<String>  ontURI = rj.convert_into_rdf(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath(), "http://test.de/", false);
            
            if(ontURI.isEmpty())
                fail("Should have an Ontology URI");
            
        } catch (Exception e) {
            e.printStackTrace();
            fail("ifcOWL conversion had an error: " + e.getMessage());
        }
    }

	
	@DisplayName("Two walls geometry conversion")
	@Test
	public void testTwoWallsConversion() {
		URL file_url = ClassLoader.getSystemResource("TWO WALLS.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Conversion had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test basic conversion")
	@Test
	public void testBasicConversion() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Conversion had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test old IFC version conversion")
	@Test
	public void testOldIFCVersionConversion() {
		URL file_url = ClassLoader.getSystemResource("05111002_IFCR2_Geo_Columns_1.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
		} catch (Exception e) {
			fail("Conversion had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test conversion test case 1")
	@Test
	public void testConversion1() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 1);
			c1nb.convert(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath());
			@SuppressWarnings("unused")
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());

			IFCtoLBDConverter c1wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, 1);
			c1wb.convert(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath());
			@SuppressWarnings("unused")
			Model m1wb = c1wb.convert(ifc_file.getAbsolutePath());

			IFCtoLBDConverter c2nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 2);
			c2nb.convert(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath());
			@SuppressWarnings("unused")
			Model m2nb = c2nb.convert(ifc_file.getAbsolutePath());

			IFCtoLBDConverter c2wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, 2);
			c2wb.convert(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath());
			@SuppressWarnings("unused")
			Model m2wb = c2wb.convert(ifc_file.getAbsolutePath());

			IFCtoLBDConverter c3nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 3);
			c3nb.convert(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath());
			@SuppressWarnings("unused")
			Model m3nb = c3nb.convert(ifc_file.getAbsolutePath());

			IFCtoLBDConverter c3wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, 3);
			c3wb.convert(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath());
			@SuppressWarnings("unused")
			Model m3wb = c3wb.convert(ifc_file.getAbsolutePath());

		} catch (Exception e) {
			fail("Conversion set 1 had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test conversion test case Level 1")
	@Test
	public void testConversionLevel1() {
		System.out.println("Start");
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex.ifc");
		URL rule_file_url = ClassLoader.getSystemResource("SHACL_rulesetLevel1.ttl");
		try {
			File ifc_file = new File(ifc_file_url.toURI());
			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 1);
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());
			Graph graph_m1nb = m1nb.getGraph();

			File rule1_file = new File(rule_file_url.toURI());
			Graph shapesGraph = RDFDataMgr.loadGraph(rule1_file.getAbsolutePath());
			Shapes shapes = Shapes.parse(shapesGraph);

			ValidationReport report = ShaclValidator.get().validate(shapes, graph_m1nb);
			if (!report.conforms()) {

				ShLib.printReport(report);
				report.getModel().write(System.out);

				fail("Conversion output does not conform SHACL_rulesetLevel1");
			} else
				System.out.println("Actually ok");
		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
			fail("Conversion using set SHACL_rulesetLevel1 had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test conversion test case Level 3")
	@Test
	public void testConversionLevel3() {
		System.out.println("Start");
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex.ifc");
		URL rule_file_url = ClassLoader.getSystemResource("SHACL_rulesetLevel3.ttl");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 3);
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());
			Graph graph_m1nb = m1nb.getGraph();

			File rule1_file = new File(rule_file_url.toURI());
			Graph shapesGraph = RDFDataMgr.loadGraph(rule1_file.getAbsolutePath());
			Shapes shapes = Shapes.parse(shapesGraph);

			ValidationReport report = ShaclValidator.get().validate(shapes, graph_m1nb);
			if (!report.conforms()) {
				System.out.println("false");

				ShLib.printReport(report);
				RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
				fail("Conversion output does not conform SHACL_rulesetLevel3");
			} else
				System.out.println("Actually ok");
		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
			fail("Conversion using set SHACL_rulesetLevel3 had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test IfcRelAggregates not converting #16")
	@Test
	public void testConversionBugIfcRelAggregatesNotConverting() {
		System.out.println("Start");
		URL ifc_file_url = ClassLoader.getSystemResource("SampleHouse.ifc");
		URL rule_file_url = ClassLoader.getSystemResource("SHACL_rulesetLevel2.ttl");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 1);
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());
			// m1nb.removeAll();
			m1nb.write(System.out, "TTL");
			Graph graph_m1nb = m1nb.getGraph();

			File rule1_file = new File(rule_file_url.toURI());
			Graph shapesGraph = RDFDataMgr.loadGraph(rule1_file.getAbsolutePath());
			Shapes shapes = Shapes.parse(shapesGraph);

			ValidationReport report = ShaclValidator.get().validate(shapes, graph_m1nb);
			if (!report.conforms()) {
				System.out.println("false");

				ShLib.printReport(report);
				RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
				fail("Conversion output does not conform SHACL_rulesetLevel3");
			} else
				System.out.println("Actually ok");

		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
			fail("Conversion using set SHACL_rulesetLevel4 had an error: " + e.getMessage());
		}
	}

	@DisplayName("IFC Virtual Elements not found #4")
	@Test
	public void testConversionBugIfcVirtualElement() {
		System.out.println("Start");
		URL ifc_file_url = ClassLoader.getSystemResource("IFC_Schependomlaan.ifc");
		URL rule_file_url = ClassLoader.getSystemResource("SHACL_rulesetLevel2.ttl");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 1);
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());
			m1nb.write(System.out, "TTL");
			Graph graph_m1nb = m1nb.getGraph();

			File rule1_file = new File(rule_file_url.toURI());
			Graph shapesGraph = RDFDataMgr.loadGraph(rule1_file.getAbsolutePath());
			Shapes shapes = Shapes.parse(shapesGraph);

			ValidationReport report = ShaclValidator.get().validate(shapes, graph_m1nb);
			if (!report.conforms()) {
				System.out.println("false");

				ShLib.printReport(report);
				RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
				fail("Conversion output does not conform SHACL_rulesetLevel3");
			} else
				System.out.println("Actually ok");

		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
			fail("Conversion using set SHACL_rulesetLevel4 had an error: " + e.getMessage());
		}
	}

	@DisplayName("IFC3 Duplex_A from Japan")
	@Test
	public void testDuplexAJapanBug() {
		System.out.println("Start");
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_AJ.ifc");
		URL rule_file_url = ClassLoader.getSystemResource("SHACL_rulesetLevel2.ttl");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, 1);
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());
			m1nb.write(System.out, "TTL");
			Graph graph_m1nb = m1nb.getGraph();

			File rule1_file = new File(rule_file_url.toURI());
			Graph shapesGraph = RDFDataMgr.loadGraph(rule1_file.getAbsolutePath());
			Shapes shapes = Shapes.parse(shapesGraph);

			ValidationReport report = ShaclValidator.get().validate(shapes, graph_m1nb);
			if (!report.conforms()) {
				System.out.println("false");

				ShLib.printReport(report);
				RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
				fail("Conversion output does not conform SHACL_rulesetLevel3");
			} else
				System.out.println("Actually ok");

		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
			fail("Conversion using set SHACL_rulesetLevel4 had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test RTree")
	@Test
	public void testRTree() {
		try {
			RTree<Resource, Geometry> rtree = RTree.dimensions(3).create();

			Rectangle rectangle = Rectangle.create(-0.5, -0.5, -0.5, 0.1, 0.1, 0.1);
			Resource r1 = ResourceFactory.createResource("http://example.de/1");
			rtree = rtree.add(r1, rectangle); // rtree is immutable

			Rectangle rectangle2 = Rectangle.create(-0.1, -0.1, -0.1, 1, 1, 1);

			Iterable<Entry<Resource, Geometry>> results = rtree.search(rectangle2);
			boolean correct = false;
			for (@SuppressWarnings("unused")
			Entry<Resource, Geometry> e : results) {
				correct = true;
			}
			if (!correct)
				fail("RTree test failed");

		} catch (Exception e) {
			fail("RTree fails: " + e.getMessage());
		}
	}

	@DisplayName("ifcOWL test 1")
	@Test
	public void test_ifcOWL1() {
		System.out.println("Start");
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());
			File tmp_output = File.createTempFile("ifc", ".ttl");
			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://test.de/", false, 1);
			c1nb.convert(ifc_file.getAbsolutePath(), tmp_output.getAbsolutePath(), true, false, true, false, true, false,
					true, false);
			File ifcOwlFile = new File(tmp_output.getAbsolutePath().split("\\.ttl")[0] + "_ifcOWL.ttl");
			if (!ifcOwlFile.exists()) {
				System.out.println("No ifcOWL File created");
				System.out.println("Filename was: " + ifcOwlFile.getAbsolutePath());
				fail("No ifcOWL File created");
			}

		} catch (Exception e) {
			System.err.println("ERROR");
			e.printStackTrace();
			fail("ifcOWL test 1 had an error: " + e.getMessage());
		}
	}

	
}
