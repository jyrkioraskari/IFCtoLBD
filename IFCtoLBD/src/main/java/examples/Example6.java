package examples;

import java.io.File;
import java.net.URL;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example6 {
    // Configuration constants for the IFC to LBD conversion
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
        // Get the URL of the IFC file from the resources folder
        URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            // Convert the URL to a File object
            File ifc_file = new File(ifc_file_url.toURI());

            // Use a try-with-resources statement to ensure the converter is closed after use
            try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes, props_level);) {
                // Perform the initial conversion phase
                converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
                        exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits);
                
                // List element types
                System.out.println("Element types:");
                System.out.println(converter.getElementTypes());

                // List property sets
                System.out.println("Property sets:");
                System.out.println(converter.getPropertySetNames());

            }
        } catch (Exception e) {
            // Print any errors that may occur
            e.printStackTrace();
        }
    }
}
