package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example2 {

    public static void main(String[] args) {
        // Get the URL of the IFC file from the resources folder
        URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            // Convert the URL to a File object
            File ifcFile = new File(ifcFileUrl.toURI());

            // Use a try-with-resources statement to ensure the converter is closed after use
            try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);) {
                // Convert the IFC file to an RDF model
                Model model = converter.convert(ifcFile.getAbsolutePath());
                // List all subjects in the RDF model and print them to the standard output
                model.listSubjects().forEachRemaining(System.out::println);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
