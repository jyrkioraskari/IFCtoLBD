package examples;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.ConversionProperties;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example8 {
    
    // Search by a bounding box
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
                
                // Get the bounding box coordinates of the geometry
                double geom_min_x = converter.getGeometryMinX();
                double geom_min_y = converter.getGeometryMinY();
                double geom_min_z = converter.getGeometryMinZ();
                double geom_max_x = converter.getGeometryMaxX();
                double geom_max_y = converter.getGeometryMaxY();
                double geom_max_z = converter.getGeometryMaxZ();
                
                // Print the maximum X coordinate of the geometry
                System.out.println(converter.getGeometryMaxX());

                // Get elements within the specified bounding box
                List<Resource> matching_elements = converter.getElementByGeometry(geom_min_x, geom_min_y, geom_min_z, geom_max_x, geom_max_y, geom_max_z);
                // Print each matching element
                for(Resource element : matching_elements) {
                    System.out.println(element);
                }
            }
        } catch (Exception e) {
            // Print any errors that may occur
            e.printStackTrace();
        }
    }
}
