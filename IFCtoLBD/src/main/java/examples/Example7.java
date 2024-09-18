package examples;

import java.io.File;
import java.net.URL;

import org.linkedbuildingdata.ifc2lbd.ConversionProperties;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example7 {
	
    public static void main(String[] args) {
        // Get the URL of the IFC file from the resources folder
        URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            // Convert the URL to a File object
            File ifc_file = new File(ifc_file_url.toURI());
            
            // Create a ConversionProperties object to set conversion options
            ConversionProperties props = new ConversionProperties();
            props.setHasGeometry(true); // Enable geometry conversion
            
            // Use a try-with-resources statement to ensure the converter is closed after use
            try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://lbd.org/", false, 1);) {
                // Convert the IFC file to an RDF model with the specified properties
                converter.convert(ifc_file.getAbsolutePath(), props);            
                
                // Print the JSON representation of the converted model
                System.out.println(converter.getObjJSON());
            }
        } catch (Exception e) {
            // Print any errors that may occur
            e.printStackTrace();
        }
    }
}
