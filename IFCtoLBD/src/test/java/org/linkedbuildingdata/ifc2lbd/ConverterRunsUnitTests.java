package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

import org.apache.jena.graph.Graph;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.core.IFCtoRDF;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.PropertySet;

import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;

public class ConverterRunsUnitTests {
	public final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();

	@DisplayName("Test the existence of the test data Duplex.ifc")
	@Test
	public void findTestData() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File file = new File(file_url.toURI());
			if (!file.exists())
				fail("Test data not found/available");
		} catch (Exception e) {
			fail("Test data not found/available: " + e.getMessage());
		}
	}

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
			fail("Test ontology failed: " + e.getMessage());
		}
	}

	@DisplayName("Test ifcOWL 1")
	@Test
	public void test_ifcOWL_Conversion1() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			IFCtoRDF rj = new IFCtoRDF();
			Optional<String> ontURI1 = rj.convert_into_rdf(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath(),
					"http://test.de/", false);

			if (ontURI1.isEmpty())
				fail("Should have an Ontology URI");

			Optional<String> ontURI2 = rj.convert_into_rdf(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath(),
					"http://test.de/", true);

			if (ontURI2.isEmpty())
				fail("Should have an Ontology URI");

		} catch (Exception e) {
			e.printStackTrace();
			fail("ifcOWL conversion had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test ifcOWL 2")
	@Test
	public void test_ifcOWL_Conversion2() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			IFCtoLBDConverter c1wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true,
					Integer.valueOf(1));

			Model m3wb = c1wb.readAndConvertIFC2ifcOWL(ifc_file.getAbsolutePath(),
					"https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, temp_file.getAbsolutePath(), false);

			ImmutableList<Resource> subjectList1 = ImmutableList.copyOf(m3wb.listSubjects());
			if (subjectList1.size() != 94539) {
				System.out.println("Converted subject count  should  be 94539. Was: " + subjectList1.size());
				fail("Converted subject count  should  be 94539. Was: " + subjectList1.size());
			}

		} catch (Exception e) {
			e.printStackTrace();
			fail("ifcOWL conversion had an error: " + e.getMessage());
		}
	}

	
	@SuppressWarnings("unused")
	@DisplayName("Two walls geometry conversion")
	@Test
	public void testTwoWallsConversionFull() {
		URL file_url = ClassLoader.getSystemResource("TWO WALLS.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			String ifcFilePath = ifc_file.getAbsolutePath();
			String baseURI = "https://dot.dc.rwth-aachen.de/IFCtoLBDset#";
			String tempFilePath = temp_file.getAbsolutePath();

			boolean[] boolValues = { true, false };
			for (int level = 0; level < 3; level++)
				for (boolean param1 : boolValues) {
					for (boolean param2 : boolValues) {
						for (boolean param3 : boolValues) {
							for (boolean param4 : boolValues) {
								for (boolean param5 : boolValues) {
									for (boolean param6 : boolValues) {
										for (boolean param7 : boolValues) {
											 try (IFCtoLBDConverter c =  new IFCtoLBDConverter(ifcFilePath, baseURI, tempFilePath, level, param1,
													param2, param3, param4, param5, param6, param7)){
													}
													
										}
									}
								}
							}
						}
					}
				}
		} catch (Exception e) {
			e.printStackTrace();
			fail("Full Conversion had an error: " + e.getMessage());
		}
	}

	@SuppressWarnings("unused")
	@DisplayName("Test basic conversion")
	@Test
	public void testBasicConversion() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, true);
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false, true);
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false, false);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Conversion had an error: " + e.getMessage());
		}
	}

	@SuppressWarnings("unused")
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
		URL example_url = ClassLoader.getSystemResource("testConversion1_list.txt");
		List<String> subject_samples = new ArrayList<>();
		Scanner scanner;
		try {
			scanner = new Scanner(new File(example_url.toURI()));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine().trim();
				if (line.length() > 0)
					subject_samples.add(line);
			}
		} catch (FileNotFoundException | URISyntaxException e1) {
			e1.printStackTrace();
		}

		try {
			File ifc_file = new File(file_url.toURI());
			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(1));

			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());
			ImmutableList<Resource> subjectList1 = ImmutableList.copyOf(m1nb.listSubjects());
			if (subjectList1.size() != 549) {
				System.out.println("Converted subject count  should not be 549. Was: " + subjectList1.size());
				fail("Converted subject count  should not be 549. Was: " + subjectList1.size());
			}

			if (m1nb.size() == 0) {
				System.out.println("Conversion size should not be zero.");
				fail("Conversion size should not be zero.");
			}

			IFCtoLBDConverter c1wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true,
					Integer.valueOf(1));
			Model m1wb = c1wb.convert(ifc_file.getAbsolutePath());

			ImmutableList<Resource> subjectList2 = ImmutableList.copyOf(m1wb.listSubjects());

			if (subjectList2.size() != 549) {
				System.out.println("Converted subject count should not be 549. Was: " + subjectList2.size());
				fail("Converted subject count  should not be 549. Was: " + subjectList2.size());
			}

			IFCtoLBDConverter c2nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(2));
			Model m2nb = c2nb.convert(ifc_file.getAbsolutePath());

			ImmutableList<Resource> subjectList3 = ImmutableList.copyOf(m2nb.listSubjects());

			if (subjectList3.size() != 7068) {
				System.out.println("Converted subject count should not be 7068. Was: " + subjectList3.size());
				fail("Converted subject count  should not be 7068. Was: " + subjectList3.size());
			}

			IFCtoLBDConverter c2wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true,
					Integer.valueOf(2));

			Model m2wb = c2wb.convert(ifc_file.getAbsolutePath());

			ImmutableList<Resource> subjectList4 = ImmutableList.copyOf(m2wb.listSubjects());

			if (subjectList4.size() != 7075) {
				System.out.println("Converted subject count should  be 7075. Was: " + subjectList4.size());
				fail("Converted subject count  should  be 7075. Was: " + subjectList4.size());
			}

			IFCtoLBDConverter c3nb1 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(3));
			Model m3nb = c3nb1.convert(ifc_file.getAbsolutePath());

			ImmutableList<Resource> subjectList51 = ImmutableList.copyOf(m3nb.listSubjects());
			/*
			 * for (Resource r : subjectList51) {
			 * if(r.getURI().startsWith("http://lbd.arch.rwth-aachen.de/props#")) continue;
			 * if(r.getURI().startsWith("https://linkedbuildingdata.org/LBD#")) continue;
			 * 
			 * if (!subject_samples.contains(r.getURI().split("_a")[0].split("_p")[0])) {
			 * 
			 * System.out.println("" + r);
			 * 
			 * System.out.println("splitted was:" +
			 * r.getURI().split("_a")[0].split("_p")[0]);
			 * fail("Converted subjects: extras: ");
			 * 
			 * } }
			 */

			if (subjectList51.size() != 13593) {
				System.out.println("Converted subject count should  be 13593. Was: " + subjectList51.size());
				fail("Converted subject count  should be 13593. Was: " + subjectList51.size());
			}

			IFCtoLBDConverter c3nb2 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(3));
			Model m3nb2 = c3nb2.convert(ifc_file.getAbsolutePath());

			ImmutableList<Resource> subjectList52 = ImmutableList.copyOf(m3nb2.listSubjects());

			List<Resource> comparison = new ArrayList<>();
			comparison.addAll(subjectList52);
			comparison.removeAll(subjectList52);
			if (comparison.size() != 0) {
				System.out.println("Two comparison and different results. Was: " + comparison);
				fail("Two comparison and different results. Was: ");
			}

			if (subjectList52.size() != 13593) {
				System.out.println("Converted subject count should  be 13593. Was: " + subjectList52.size());
				fail("Converted subject count  should  be 13593. Was: " + subjectList52.size());
			}

			IFCtoLBDConverter c3wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true,
					Integer.valueOf(3));

			Model m3wb = c3wb.convert(ifc_file.getAbsolutePath());

			ImmutableList<Resource> subjectList6 = ImmutableList.copyOf(m3wb.listSubjects());

			if (subjectList6.size() != 13600) {
				System.out.println("Converted subject count should not be 13600. Was: " + subjectList6.size());
				fail("Converted subject count  should not be 13600. Was: " + subjectList6.size());
			}

		} catch (Exception e) {
			fail("Conversion set 1 had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test conversion test case 2: class variable text")
	@Test
	public void testConversion2() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			IFCtoLBDConverter c3nb1 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(3));

			Model m3nb1 = c3nb1.convert(ifc_file.getAbsolutePath());

			if (c3nb1.ontURI.isEmpty()) {
				System.out.println("Ontology UEI should not be nonexistent. ");
				fail("Ontology UEI should not be nonexistent. ");

			}
			ImmutableList<Resource> subjectList51 = ImmutableList.copyOf(m3nb1.listSubjects());

			if (subjectList51.size() != 13593) {
				System.out.println("Converted subject count should  be 13593. Was: " + subjectList51.size());
				fail("Converted subject count  should not be 13593. Was: " + subjectList51.size());
			}

			IFCtoLBDConverter c3nb2 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, 3);
			Model m3nb2 = c3nb2.convert(ifc_file.getAbsolutePath());
			m3nb2.write(System.out, "TTL");
			ImmutableList<Resource> subjectList52 = ImmutableList.copyOf(m3nb2.listSubjects());

			if (subjectList52.size() != 13600) {
				System.out.println("Converted subject count should not be 13600. Was: " + subjectList52.size());
				fail("Converted subject count  should not be 13600. Was: " + subjectList52.size());
			}

		} catch (Exception e) {
			fail("Conversion set 1 had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test conversion test case 3: repeated convert")
	@Test
	public void testConversion3() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			IFCtoLBDConverter c3nb1 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(3));
			Model m3nb1 = c3nb1.convert(ifc_file.getAbsolutePath());

			ImmutableList<Resource> subjectList51 = ImmutableList.copyOf(m3nb1.listSubjects());
			System.out.println("Converted subject count  5 was: " + subjectList51.size());

			IFCtoLBDConverter c3nb2 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, 3);
			Model m3nb21 = c3nb2.convert(ifc_file.getAbsolutePath());
			Model m3nb22 = c3nb2.convert(ifc_file.getAbsolutePath());

			Model out = m3nb21.remove(m3nb22);

			if (out.size() != 0) {
				System.out.println("Converted result shouls not change when doining it repeteadly.");
				fail("Converted result shouls not change when doining it repeteadly.");
			}

		} catch (Exception e) {
			fail("Conversion set 1 had an error: " + e.getMessage());
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

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(3));
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

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(1));
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

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(1));
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
				fail("Conversion output does not conform SHACL_rulesetLevel2");
			} else
				System.out.println("Actually ok");

		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
			fail("Conversion using set SHACL_rulesetLevel2 had an error: " + e.getMessage());
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

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(1));
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
			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://test.de/", false, Integer.valueOf(1));
			c1nb.convert(ifc_file.getAbsolutePath(), tmp_output.getAbsolutePath(), true, false, true, false, true,
					false, true, false);
			File ifcOwlFile = new File(tmp_output.getAbsolutePath().split("\\.ttl")[0] + "_ifcOWL.ttl");
			if (!ifcOwlFile.exists()) {
				System.out.println("No ifcOWL File created");
				System.out.println("Filename was: " + ifcOwlFile.getAbsolutePath());
				fail("No ifcOWL File created");
			}

			long bytes = ifcOwlFile.length();
			// Apache Jena 5.1
			if (bytes != 20185157) { // Old Jena had: 20289155
				System.out.println(
						"Wrong file size for ifcOWL result. (can be Jena version dependent) size was: " + bytes);
				System.out.println("Filename was: " + ifcOwlFile.getAbsolutePath());
				fail("Wrong file size for ifcOWL result. (can be Jena version dependent)");
			}
		} catch (Exception e) {
			System.err.println("ERROR");
			e.printStackTrace();
			fail("ifcOWL test 1 had an error: " + e.getMessage());
		}
	}

	@DisplayName("Test geometry 1")
	@Test
	public void testConversionGeometry() {
		System.out.println("Start");
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		URL rule_file_url = ClassLoader.getSystemResource("SHACL_rulesetLevel1_geometry.ttl");
		try {
			File ifc_file = new File(file_url.toURI());

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,
					Integer.valueOf(1));
			ConversionProperties props = new ConversionProperties();
			props.setHasGeometry(true);
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath(), props);
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
				fail("Conversion output does not conform SHACL_rulesetLevel1_geometry");
			} else
				System.out.println("Actually ok");

		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
			fail("Conversion using set SHACL_rulesetLevel1_geometry had an error: " + e.getMessage());
		}
	}

	int count = 0;

	@DisplayName("Test Example 3")
	@Test
	public void testConversionExample3() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifcFile = new File(file_url.toURI());

			IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);
			Model model = converter.convert(ifcFile.getAbsolutePath());

			Query query = QueryFactory.create("PREFIX bot: <https://w3id.org/bot#>\r\n" + "\r\n"
					+ "SELECT ?element WHERE {\r\n" + "  ?element a bot:Element .\r\n" + "} ");

			try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model)) {
				ResultSet resultSet = queryExecution.execSelect();
				resultSet.forEachRemaining(qs -> {
					this.count++;
				});
			}
			if (this.count != 268) {
				System.out.println("Converted BOT element count should be 268. Was: " + this.count);
				fail("Converted BOT element count should be 268. Was:" + this.count);
			}
		} catch (Exception e) {
			System.err.println("Example 3 error: " + e.getMessage());
			fail("Conversion Example 3 error: " + e.getMessage());
		}
	}

	static final private int props_level = 1;
	static final private boolean hasBuildingElements = true;
	static final private boolean hasBuildingProperties = true;

	static final boolean hasSeparateBuildingElementsModel = false;
	static final boolean hasPropertiesBlankNodes = false;
	static final boolean hasSeparatePropertiesModel = false;

	static final boolean hasGeolocation = false;

	static final boolean hasGeometry = true;
	static final boolean exportIfcOWL = false;
	static final boolean hasUnits = false;

	static final boolean hasPerformanceBoost = false;
	static final boolean hasBoundingBoxWKT = true;
	static final boolean hasInterfaces = false;

	@DisplayName("Test Example 4")
	@Test
	public void testConversionExample4() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes, props_level);
			Model m = c.convert(ifc_file.getAbsoluteFile().toString(), null, hasBuildingElements,
					hasSeparateBuildingElementsModel, hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation,
					hasGeometry, exportIfcOWL, hasUnits, hasPerformanceBoost, hasBoundingBoxWKT);
			if (m != null) {
				Query query = QueryFactory.create("PREFIX fog: <https://w3id.org/fog#>\r\n" + "\r\n"
						+ "SELECT ?e ?wkt ?obj WHERE {\r\n" + "  ?e <https://w3id.org/omg#hasGeometry> ?g .\r\n"
						+ "  ?g <https://www.opengis.net/ont/geosparql#asWKT> ?wkt .\r\n"
						+ "  ?g fog:asObj_v3.0-obj ?obj \r\n" + "} ");
				try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
					ResultSet rs = queryExecution.execSelect();
					rs.forEachRemaining(qs -> {
						this.count++;
					});
				}
				if (this.count != 286) {
					System.out.println("Converted geom element count should be 286. Was: " + this.count);
					fail("Converted geom element count should be 286. Was:" + this.count);
				}
			}
		} catch (Exception e) {
			System.err.println("Example 4 error: " + e.getMessage());
			fail("Conversion Example 4 error: " + e.getMessage());
		}

	}

	@DisplayName("Test two phases conversion")
	@Test
	public void testTwoPhases() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);
				Model m3nb1 = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
						hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
						hasUnits, hasBoundingBoxWKT, true, hasInterfaces);

				ImmutableList<Resource> subjectList51 = ImmutableList.copyOf(m3nb1.listSubjects());

				if (subjectList51.size() != 844) {
					System.out.println("Converted subject count should  be 844. Was: " + subjectList51.size());
					fail("Converted subject count  should  be 844. Was: " + subjectList51.size());
				}
			}

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);

				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);
				Model m3nb1 = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
						hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
						hasUnits, hasBoundingBoxWKT, true, hasInterfaces);

				ImmutableList<Resource> subjectList51 = ImmutableList.copyOf(m3nb1.listSubjects());

				if (subjectList51.size() != 844) {
					System.out.println("Converted subject count should  be 844. Was: " + subjectList51.size());
					fail("Converted subject count  should  be 844. Was: " + subjectList51.size());
				}
			}

		} catch (

		Exception e) {
			System.err.println("Example two phases error: " + e.getMessage());
			fail("Conversion Example two phaes error: " + e.getMessage());
		}

	}

	@DisplayName("Test two phases types list")
	@Test
	public void testTwoPhasesTypes() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);
				Set<Resource> element_types = converter.getElementTypes();
				if (element_types.size() != 13) {
					System.out.println("Element type count should be 13. Was: " + element_types.size());
					fail("Element type count should be 13. Was:" + element_types.size());
				}
			}

		} catch (

		Exception e) {
			System.err.println("Example two phases types error: " + e.getMessage());
			fail("Conversion Example two phases types error: " + e.getMessage());
		}

	}

	@DisplayName("Test two phases property sets list")
	@Test
	public void testTwoPhasesPsets() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);

				Map<String, PropertySet> psets = converter.getPropertysets();
				if (psets.size() != 1480) {
					System.out.println("Pset count should be 1480. Was: " + psets.size());
					fail("Pset count should be 1480. Was:" + psets.size());
				}

				Set<String> pset_names = converter.getPropertySetNames();
				if (pset_names.size() != 35) {
					System.out.println("Pset names count should be 35. Was: " + pset_names.size());
					fail("Pset names count should be 35. Was:" + pset_names.size());
				}

			}

		} catch (

		Exception e) {
			System.err.println("Example two phases psets error: " + e.getMessage());
			fail("Conversion Example two phases psets error: " + e.getMessage());
		}

	}

	@DisplayName("Test ontologocal validity 1")
	@Test
	public void testOntologicalValidity1() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);
				Model m3nb1 = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
						hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
						hasUnits, hasBoundingBoxWKT, true, hasInterfaces);

				OntModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF);
				infModel.add(converter.getOntology_model());
				infModel.add(m3nb1);
				ValidityReport report = infModel.validate();

				if (!report.isValid()) {
					System.out.println("The model is not consistent.");
					fail("The model is not consistent.");
				}

			}

		} catch (

		Exception e) {
			System.err.println("OntologicalValidity1 error: " + e.getMessage());
			fail("Conversion OntologicalValidity1 error: " + e.getMessage());
		}

	}

	@DisplayName("Test ontologocal name space validity ")
	@Test
	public void testOntologicalNSValidity() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);

				Model m = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
						hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
						hasUnits, hasBoundingBoxWKT, true, hasInterfaces);

				Set<Resource> subs = m.listSubjects().toSet();
				Set<String> nss = m.listNameSpaces().toSet();
				for (Resource s : subs) {
					Optional<Resource> type = RDFUtils.getType(s);
					if (type.isPresent()) {
						if (!nss.contains(type.get().getNameSpace())) {
							System.out.println(type.get().getNameSpace());
							System.out.println("Ontological name space was not defined error.");
							fail("Ontological name space was not defined error.");
						}
					} else {
						// TODO
						// System.err.println(s+" type is empty");
					}
				}

			}

		} catch (

		Exception e) {
			System.err.println("Ontological name space was not defined error: " + e.getMessage());
			fail("Ontological name space was not defined error: " + e.getMessage());
		}

	}

	@DisplayName("Test type selection ")
	@Test
	public void testTypeSelection() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);

				Set<String> types = new HashSet<>();
				types.add("Wall");
				converter.setSelected_types(types);

				Model m = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
						hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
						hasUnits, hasBoundingBoxWKT, true, hasInterfaces);

				ImmutableList<Resource> subjectList1 = ImmutableList.copyOf(m.listSubjects());
				if (subjectList1.size() == 581) {
					// Because of the filtering should be less
					System.out.println("Converted subject count  should not be 581. Was: " + subjectList1.size());
					fail("Converted subject count  should not be 581. Was: " + subjectList1.size());
				}
				if (subjectList1.size() != 373) {
					System.out.println("Converted subject count  should  be 373. Was: " + subjectList1.size());
					fail("Converted subject count  should  be 373. Was: " + subjectList1.size());
				}
			}

		} catch (

		Exception e) {
			System.err.println("type selection  error: " + e.getMessage());
			fail("type selection  error: " + e.getMessage());
		}

	}

	@DisplayName("Test geo selection ")
	@Test
	public void testGeoSelectionTest() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			ConversionProperties props = new ConversionProperties();
			props.setHasGeometry(true);
			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://lbd.org/", false, 1);) {
				converter.convert(ifc_file.getAbsolutePath(), props);
				double geom_min_x = converter.getGeometryMinX();
				double geom_min_y = converter.getGeometryMinY();
				double geom_min_z = converter.getGeometryMinZ();
				double geom_max_x = converter.getGeometryMaxX();
				double geom_max_y = converter.getGeometryMaxY();
				double geom_max_z = converter.getGeometryMaxZ();

				double geom_dx = (geom_max_x - geom_min_x) / 2;
				double geom_dy = (geom_max_y - geom_min_y) / 2;
				double geom_dz = (geom_max_z - geom_min_z) / 2;

				List<Resource> matching_elements1 = converter.getElementByGeometry(geom_min_x, geom_min_y, geom_min_z,
						geom_max_x, geom_max_y, geom_max_z);
				List<Resource> matching_elements2 = converter.getElementByGeometry(geom_min_x + geom_dx,
						geom_min_y + geom_dy, geom_min_z + geom_dz, geom_max_x, geom_max_y, geom_max_z);

				if (matching_elements1.size() != 286) {
					System.out.println("Converted selected geom element number 1 should be 286. Was: "
							+ matching_elements1.size());
					fail("Converted selected geom element number 1 should be 286. Was: " + matching_elements1.size());
				}

				if (matching_elements2.size() != 50) {
					System.out.println(
							"Converted selected geom element number 2 should be 50. Was: " + matching_elements2.size());
					fail("Converted selected geom element number 2 should be 50. Was: " + matching_elements2.size());
				}

			}

		} catch (

		Exception e) {
			System.err.println("Example two phases psets error: " + e.getMessage());
			fail("Conversion Example two phases psets error: " + e.getMessage());
		}

	}

	@DisplayName("Test performance boost ")
	@Test
	public void testPerformanceBoost() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			ConversionProperties props = new ConversionProperties();
			props.setHasPerformanceBoost(false);
			try (IFCtoLBDConverter converter1 = new IFCtoLBDConverter("https://lbd.org/", false, 1);) {
				Model m1nb = converter1.convert(ifc_file.getAbsolutePath(), props);
				ImmutableList<Resource> subjectList1 = ImmutableList.copyOf(m1nb.listSubjects());
				if (subjectList1.size() != 549) {
					System.out.println("Converted subject count  should not be 549. Was: " + subjectList1.size());
					fail("Converted subject count  should not be 549. Was: " + subjectList1.size());
				}

			}

			props.setHasPerformanceBoost(true);
			props.setExportIfcOWL(false);
			try (IFCtoLBDConverter converter2 = new IFCtoLBDConverter("https://lbd.org/", false, 1);) {
				Model m1nb1 = converter2.convert(ifc_file.getAbsolutePath(), props);
				ImmutableList<Resource> subjectList2 = ImmutableList.copyOf(m1nb1.listSubjects());
				if (subjectList2.size() != 549) {
					System.out.println("Converted subject count  should not be 549. Was: " + subjectList2.size());
					fail("Converted subject count  should not be 549. Was: " + subjectList2.size());
				}

			}

		} catch (

		Exception e) {
			System.err.println("Performance boost error: " + e.getMessage());
			fail("Performance boost error: " + e.getMessage());
		}

	}

	@DisplayName("Test element types and psets")
	@Test
	public void testTypesPsets() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);

				if (converter.getElementTypes().size() != 13) {
					System.out.println(
							"Converted element types count should be 13. Was: " + converter.getElementTypes().size());
					fail("Converted element types count should be 13. Was: " + converter.getElementTypes().size());
				}

				if (converter.getPropertySetNames().size() != 35) {
					System.out.println(
							"Converted pset count should be 35. Was: " + converter.getPropertySetNames().size());
					fail("Converted pset count should be 35. Was: " + converter.getPropertySetNames().size());
				}

			}
		} catch (

		Exception e) {
			System.err.println("Types and psets error: " + e.getMessage());
			fail("Types and psets error: " + e.getMessage());
		}

	}

	@DisplayName("Test ontologocal name space validity 2")
	@Test
	public void testOntologyNSValidity2() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,
						hasInterfaces);

				Model m = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
						hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
						hasUnits, hasBoundingBoxWKT, true, hasInterfaces);

				Set<String> nss = m.listNameSpaces().toSet();
				for (String ns : nss) {
					try {

						// redirect does not work
						if (ns.equals("https://standards.buildingsmart.org/IFC/DEV/IFC2x3/TC1/OWL#"))
							ns = "https://standards.buildingsmart.org/IFC/DEV/IFC2x3/TC1/OWL/IFC2X3_TC1.ttl";

						// redirect does not work
						if (ns.equals("https://pi.pauwel.be/voc/buildingelement#"))
							ns = "https://pi.pauwel.be/voc/buildingelement/ontology.ttl";

						// known issue
						// TODO
						if (ns.equals("http://pi.pauwel.be/voc/furniture#"))
							continue;

						// redirect does not work
						if (ns.equals("http://lbd.arch.rwth-aachen.de/props#"))
							continue; // may have server issues

						// redirect does not work
						if (ns.equals("https://linkedbuildingdata.org/LBD#"))
							continue; // may have server issues

						// Content negotiation should work: http://lbd.arch.rwth-aachen.de/props#

						Model model1 = ModelFactory.createDefaultModel();
						RDFDataMgr.read(model1, ns, Lang.TURTLE);

					} catch (Exception e) {
						System.err.println("Ontological name space was not defined error: " + ns);
						e.printStackTrace();
						fail("Ontological name space was not defined error: " + ns);

					}
				}

			}

		} catch (

		Exception e) {
			System.err.println("Ontological name space was not defined error: " + e.getMessage());
			fail("Ontological name space was not defined error: " + e.getMessage());
		}

	}

	@DisplayName("Test simplified attributes")
	@Test
	public void testSimplifiedAttributes() {
		this.count = 0;
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		URL ontology_url = ClassLoader.getSystemResource("simple_attributes_ontology_lbd.ttl");
		File ontology_file;
		try {
			ontology_file = new File(ontology_url.toURI());

			FileInputStream file_is = new FileInputStream(ontology_file.getAbsoluteFile());

			BufferedInputStream ontology_in = new BufferedInputStream(file_is);
			try {
				File ifc_file = new File(file_url.toURI());
				Model ontology_model = ModelFactory.createDefaultModel();
				ontology_model.read(ontology_in, null, "TTL");
				if (ontology_model.size() != 1296) {
					System.out.println("Ontology model size should be 1296.  Was: " + ontology_model.size());
					fail("Ontology model size should be 1296.  Was: " + ontology_model.size());
				}

				ConversionProperties props = new ConversionProperties();
				props.setHasPerformanceBoost(false);
				boolean local_hasGeometry = true;
				try (IFCtoLBDConverter converter1 = new IFCtoLBDConverter("https://lbd.org/", false, 1);) {

					converter1.convert_read_in_phase(ifc_file.getAbsolutePath(), null, local_hasGeometry,
							hasPerformanceBoost, exportIfcOWL, hasBuildingElements, hasBuildingProperties,
							local_hasGeometry, hasUnits, hasInterfaces);
					converter1.setHasSimplified_properties(true);

					Model model_level1 = converter1.convert_LBD_phase(hasBuildingElements,
							hasSeparateBuildingElementsModel, hasBuildingProperties, hasSeparatePropertiesModel,
							hasGeolocation, local_hasGeometry, exportIfcOWL, hasUnits, local_hasGeometry, true,
							hasInterfaces);

					ImmutableList<Resource> subjectList1 = ImmutableList.copyOf(model_level1.listSubjects());
					if (subjectList1.size() != 842) {
						System.out.println("Converted subject count  should not be 842. Was: " + subjectList1.size());
						fail("Converted subject count  should not be 842. Was: " + subjectList1.size());
					}

					final Set<String> properties = new HashSet<>();
					model_level1.listStatements().forEach(s -> properties.add(s.getPredicate().getURI()));
					for (String p : properties) {
						if (p.contains("simple")) {
							System.err.println("testSimplifiedAttributes: _simple found.");
							fail("testSimplifiedAttributes: _simple found.");
						}
						if (p.contains("Ifc")) {
							System.err.println("testSimplifiedAttributes: Ifc found.");
							fail("testSimplifiedAttributes: Ifc found.");
						}

						// For attribute properties
						if (p.toLowerCase().startsWith("https://linkebuildingdata.org/lbd#")) {
							Resource pr = ontology_model.getResource(p);
							if (!ontology_model.contains(pr, RDF.type, RDF.Property)) {
								if (p.equals("https://linkebuildingdata.org/LBD#batid"))
									continue; // TODO
								if (p.equals("https://linkebuildingdata.org/LBD#containsInBoundingBox"))
									continue; // TODO
								System.err.println("testSimplifiedAttributes: property missing" + p);
								fail("testSimplifiedAttributes: property missing" + p);
							} else
								System.out.println("In the ontology:" + p);
						}
					}
				}
			} catch (

			Exception e) {
				System.err.println("testSimplifiedAttributes: " + e.getMessage());
				fail("testSimplifiedAttributes: " + e.getMessage());
			}
		} catch (URISyntaxException e) {
			System.err.println("testSimplifiedAttributes: " + e.getMessage());
			fail("testSimplifiedAttributes: " + e.getMessage());
		} catch (FileNotFoundException e1) {
			System.err.println("testSimplifiedAttributes: " + e1.getMessage());
			fail("testSimplifiedAttributes: " + e1.getMessage());
		}
	}
}
