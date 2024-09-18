package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example3 {

    public static void main(String[] args) {
        // Get the URL of the IFC file from the resources folder
        URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            // Convert the URL to a File object
            File ifcFile = new File(ifcFileUrl.toURI());

            // Use a try-with-resources statement to ensure the converter is closed after use
            try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);) {
                // Convert the IFC file to an RDF model
                Model model = converter.convert(ifcFile.getAbsolutePath());

                // Create a SPARQL query to select all elements of type bot:Element
                Query query = QueryFactory.create("""
                        PREFIX bot: <https://w3id.org/bot#>
                        
                        SELECT ?element WHERE {
                          ?element a bot:Element .
                        }""");
                
                // Execute the SPARQL query on the RDF model
                try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model)) {
                    ResultSet resultSet = queryExecution.execSelect();
                    // Iterate over the results and print each element's local name
                    resultSet.forEachRemaining(qs -> {
                        System.out.println("BOT element: " + qs.get("element").asResource().getLocalName());
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
