package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;
import java.util.Optional;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
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

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;

public class ConverterRunsUnitTests {


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
			fail("Test get test rilen and an ontology failed: " + e.getMessage());
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
            Optional<String>  ontURI1 = rj.convert_into_rdf(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath(), "http://test.de/", false);
            
            if(ontURI1.isEmpty())
                fail("Should have an Ontology URI");

            Optional<String>  ontURI2 = rj.convert_into_rdf(ifc_file.getAbsolutePath(), temp_file.getAbsolutePath(), "http://test.de/", true);
            
            if(ontURI2.isEmpty())
                fail("Should have an Ontology URI");

            
        } catch (Exception e) {
            e.printStackTrace();
            fail("ifcOWL conversion had an error: " + e.getMessage());
        }
    }


	@SuppressWarnings("unused")
	@DisplayName("Two walls geometry conversion")
	@Test
	public void testTwoWallsConversion() {
		URL file_url = ClassLoader.getSystemResource("TWO WALLS.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset",
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), null,
					temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Conversion had an error: " + e.getMessage());
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
		try {
			File ifc_file = new File(file_url.toURI());
			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, Integer.valueOf(1));
			
			Model m1nb = c1nb.convert(ifc_file.getAbsolutePath());
			ImmutableList<Resource> subjectList1 = ImmutableList.copyOf(m1nb.listSubjects());
			System.out.println("Converted subject count  1 was: "+subjectList1.size());
			if(subjectList1.size()!=295)
			{
				System.out.println("Converted subject count  should not be 295. Was: "+subjectList1.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList1.size());
			}
			
			if(m1nb.size()==0)
			{
				System.out.println("Conversion size should not be zero.");
				fail("Conversion size should not be zero.");
			}

			
			IFCtoLBDConverter c1wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, Integer.valueOf(1));
			Model m1wb = c1wb.convert(ifc_file.getAbsolutePath());
			
            ImmutableList<Resource> subjectList2 = ImmutableList.copyOf(m1wb.listSubjects());
			System.out.println("Converted subject count  2 was: "+subjectList2.size());
			
			if(subjectList2.size()!=295)
			{
				System.out.println("Converted subject count should not be 295. Was: "+subjectList2.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList2.size());
			}
			
			IFCtoLBDConverter c2nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, Integer.valueOf(2));
			Model m2nb = c2nb.convert(ifc_file.getAbsolutePath());
			
            ImmutableList<Resource> subjectList3 = ImmutableList.copyOf(m2nb.listSubjects());
			System.out.println("Converted subject count  3 was: "+subjectList3.size());

			if(subjectList3.size()!=6792)
			{
				System.out.println("Converted subject count should not be 295. Was: "+subjectList3.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList3.size());
			}

			IFCtoLBDConverter c2wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, Integer.valueOf(2));
			
			Model m2wb = c2wb.convert(ifc_file.getAbsolutePath()); 
			
            ImmutableList<Resource> subjectList4 = ImmutableList.copyOf(m2wb.listSubjects());
			System.out.println("Converted subject count  4 was: "+subjectList4.size());

			if(subjectList4.size()!=6792)
			{
				System.out.println("Converted subject count should not be 295. Was: "+subjectList4.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList4.size());
			}

			IFCtoLBDConverter c3nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, Integer.valueOf(3));
			Model m3nb = c3nb.convert(ifc_file.getAbsolutePath());  
			
            ImmutableList<Resource> subjectList5 = ImmutableList.copyOf(m3nb.listSubjects());
            for(Resource r: subjectList5)
            	System.out.println("s5: "+r);
			System.out.println("Converted subject count  5 was: "+subjectList5.size());

			if(subjectList5.size()!=295)
			{
				System.out.println("Converted subject count should not be 295. Was: "+subjectList5.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList5.size());
			}
			

			IFCtoLBDConverter c3wb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, Integer.valueOf(3));

			Model m3wb = c3wb.convert(ifc_file.getAbsolutePath());
			
            ImmutableList<Resource> subjectList6 = ImmutableList.copyOf(m3wb.listSubjects());
			
			System.out.println("Converted subject count  6 was: "+subjectList6.size());

			if(subjectList6.size()!=295)
			{
				System.out.println("Converted subject count should not be 295. Was: "+subjectList6.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList6.size());
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

			IFCtoLBDConverter c3nb1 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, Integer.valueOf(3));
			Model m3nb1 = c3nb1.convert(ifc_file.getAbsolutePath());
			
            ImmutableList<Resource> subjectList51 = ImmutableList.copyOf(m3nb1.listSubjects());         
			System.out.println("Converted subject count  5 was: "+subjectList51.size());

			if(subjectList51.size()!=13293)
			{
				System.out.println("Converted subject count should not be 295. Was: "+subjectList51.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList51.size());
			}
			
			IFCtoLBDConverter c3nb2 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, 3);
			Model m3nb2 = c3nb2.convert(ifc_file.getAbsolutePath());
			m3nb2.write(System.out,"TTL");
            ImmutableList<Resource> subjectList52 = ImmutableList.copyOf(m3nb2.listSubjects());
            
			System.out.println("Converted subject count  5 was: "+subjectList52.size());

			if(subjectList52.size()!=13293)
			{
				System.out.println("Converted subject count should not be 295. Was: "+subjectList52.size());
				fail("Converted subject count  should not be 295. Was: "+subjectList52.size());
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

			IFCtoLBDConverter c3nb1 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, Integer.valueOf(3));
			Model m3nb1 = c3nb1.convert(ifc_file.getAbsolutePath());
			
            ImmutableList<Resource> subjectList51 = ImmutableList.copyOf(m3nb1.listSubjects());         
			System.out.println("Converted subject count  5 was: "+subjectList51.size());

			
			IFCtoLBDConverter c3nb2 = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true, 3);
			Model m3nb21 = c3nb2.convert(ifc_file.getAbsolutePath());
			Model m3nb22 = c3nb2.convert(ifc_file.getAbsolutePath());
		    
			Model out =m3nb21.remove(m3nb22);
			
			if(out.size()!=0)
			{
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

			IFCtoLBDConverter c1nb = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false, Integer.valueOf(3));
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
}
