package org.lbd.ifc2lbd;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConverterUnitTest {

	@DisplayName("Test the existence of the test data Duplex.ifc")
	@Test
	public void findTestData() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File file = new File(file_url.toURI());
			if(file==null || !file.exists())
				fail("Test data not found/available");
		} catch (Exception e) {
			fail("Test data not found/available: "+e.getMessage());
		}
	}

	@DisplayName("Test basic conversion")
	@Test
	public void testBasicConversion() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file=File.createTempFile("ifc2lbd", "test.ttl");
			new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#", temp_file.getAbsolutePath(),
					0, true, false, true, false, false, false);
		} catch (Exception e) {
			fail("Conversion has an error: "+e.getMessage());
		}
	}
	
	@DisplayName("Test conversion case 1")
	@Test
	public void testConversion1() {
		URL file_url = ClassLoader.getSystemResource("Duplex.ifc");
		try {
			File ifc_file = new File(file_url.toURI());
			File temp_file=File.createTempFile("ifc2lbd", "test.ttl");
			IFCtoLBDConverter c1nb=new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,1);
			c1nb.convert(ifc_file.getAbsolutePath(),temp_file.getAbsolutePath()); 
			Model m1nb=c1nb.convert(ifc_file.getAbsolutePath());

			IFCtoLBDConverter c1wb=new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true,1);
			c1wb.convert(ifc_file.getAbsolutePath(),temp_file.getAbsolutePath()); 
			Model m1wb=c1wb.convert(ifc_file.getAbsolutePath());

			
			IFCtoLBDConverter c2nb=new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,2);
			c2nb.convert(ifc_file.getAbsolutePath(),temp_file.getAbsolutePath()); 
			Model m2nb=c2nb.convert(ifc_file.getAbsolutePath());

			IFCtoLBDConverter c2wb=new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true,2);
			c2wb.convert(ifc_file.getAbsolutePath(),temp_file.getAbsolutePath()); 
			Model m2wb=c2wb.convert(ifc_file.getAbsolutePath());

			
			IFCtoLBDConverter c3nb=new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", false,3);
			c3nb.convert(ifc_file.getAbsolutePath(),temp_file.getAbsolutePath()); 
			Model m3nb=c3nb.convert(ifc_file.getAbsolutePath());


			IFCtoLBDConverter c3wb=new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset#", true,3);
			c3wb.convert(ifc_file.getAbsolutePath(),temp_file.getAbsolutePath()); 
			Model m3wb=c3wb.convert(ifc_file.getAbsolutePath());

			
		} catch (Exception e) {
			fail("Conversion has an error: "+e.getMessage());
		}
	}
}
