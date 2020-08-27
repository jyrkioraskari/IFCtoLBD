package org.lbd.ifc2lbd;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;

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
}
