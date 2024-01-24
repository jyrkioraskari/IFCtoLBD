package examples;

import java.io.File;
import java.net.URL;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example6 {
	static final private int props_level = 1;
	static final private boolean hasBuildingElements = true; 
	static final private boolean hasBuildingProperties = true; 
	static final boolean hasPropertiesBlankNodes = false; 
	static final boolean hasGeometry = true; 
	static final boolean exportIfcOWL = false;
	static final boolean hasUnits = false; 
	static final boolean hasPerformanceBoost = false; 
	static final boolean hasBoundingBoxWKT = true; 

	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL,hasBuildingElements,hasBuildingProperties,hasBoundingBoxWKT,hasUnits);
				
				// List types
				System.out.println("Element types:");
				System.out.println(converter.getElementTypes());

				// List property sets
				System.out.println("Property sets:");
				System.out.println(converter.getPropertySetNames());

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}