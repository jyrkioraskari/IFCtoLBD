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

public class Example4 {
    // Configuration constants for the IFC to LBD conversion
    static final private int props_level = 1;
    static final private boolean hasBuildingElements = true;
    static final private boolean hasBuildingProperties = true;

    static final boolean hasSeparateBuildingElementsModel = false;
    static final boolean hasPropertiesBlankNodes = false;
    static final boolean hasSeparatePropertiesModel = false;

    static final boolean hasGeolocation = false;

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
            try (IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes, props_level);) {
                // Convert the IFC file to an RDF model with specified options
                Model m = c.convert(ifc_file.getAbsoluteFile().toString(), null, hasBuildingElements,
                        hasSeparateBuildingElementsModel, hasBuildingProperties, hasSeparatePropertiesModel,
                        hasGeolocation, hasGeometry, exportIfcOWL, hasUnits, hasPerformanceBoost, hasBoundingBoxWKT);
                
                if (m != null) {
                    // Create a SPARQL query to select elements with geometry information
                    Query query = QueryFactory.create("""
                            PREFIX fog: <https://w3id.org/fog#>
                            
                            SELECT ?e ?wkt ?obj WHERE {
                              ?e <https://w3id.org/omg#hasGeometry> ?g .
                              ?g <https://www.opengis.net/ont/geosparql#asWKT> ?wkt .
                              ?g fog:asObj_v3.0-obj ?obj 
                            }""");
                    
                    // Execute the SPARQL query on the RDF model
                    try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
                        ResultSet rs = queryExecution.execSelect();
                        // Iterate over the results and print each element's details
                        rs.forEachRemaining(qs -> {
                            System.out.println("BOT element: " + qs.get("e").asResource().getLocalName());
                            System.out.println("BOT element WKT: " + qs.get("wkt"));
                            System.out.println("BOT element OBJ: " + qs.get("obj"));
                        });
                        // Optionally write the RDF model to the standard output in Turtle format
                        // m.write(System.out, "TTL");
                    }
                }
            }
        } catch (Exception e) {
            // Print any errors that may occur
            e.printStackTrace();
        }
    }
}
