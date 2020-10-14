package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
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

import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;

public class ConverterUnitTest {

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

    @DisplayName("Test basic conversion")
    @Test
    public void testBasicConversion() {
        URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
        try {
            File ifc_file = new File(file_url.toURI());
            File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
            new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#", temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
        } catch (Exception e) {
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
            new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#", temp_file.getAbsolutePath(), 0, true, false, true, false, false, false);
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
                System.out.println("false");
                fail("Conversion output does not conform SHACL_rulesetLevel1");
                ShLib.printReport(report);
                report.getModel().write(System.out);
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
                fail("Conversion output does not conform SHACL_rulesetLevel3");
                ShLib.printReport(report);
                RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
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
            m1nb.write(System.out, "TTL");
            Graph graph_m1nb = m1nb.getGraph();

            File rule1_file = new File(rule_file_url.toURI());
            Graph shapesGraph = RDFDataMgr.loadGraph(rule1_file.getAbsolutePath());
            Shapes shapes = Shapes.parse(shapesGraph);

            ValidationReport report = ShaclValidator.get().validate(shapes, graph_m1nb);
            if (!report.conforms()) {
                System.out.println("false");
                fail("Conversion output does not conform SHACL_rulesetLevel3");
                ShLib.printReport(report);
                RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
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
                fail("Conversion output does not conform SHACL_rulesetLevel3");
                ShLib.printReport(report);
                RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
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
            boolean correct=false;
            for (Entry<Resource, Geometry> e : results) {
                correct=true;
            }
            if(!correct)
                fail("RTree test failed");
            
        } catch (Exception e) {
            fail("RTree fails: " + e.getMessage());
        }
    }
}
