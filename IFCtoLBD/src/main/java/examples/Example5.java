package examples;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example5 {
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
            try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes, props_level);) {
                // Perform the initial conversion phase
                converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
                        exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits,false);
                
                // Filter only wall elements
                Set<String> types = new HashSet<>();
                types.add("Wall");
                converter.setSelected_types(types);
                
                // Perform the LBD conversion phase with specified options
                Model m = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
                        hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
                        hasUnits, hasBoundingBoxWKT, true,false);

                if (m != null) {
                    // Create a SPARQL query to select all elements of type bot:Element
                    Query query = QueryFactory.create("""
                            PREFIX bot: <https://w3id.org/bot#>
                            
                            SELECT ?element WHERE {
                              ?element a bot:Element .
                            }""");
                    
                    // Execute the SPARQL query on the RDF model
                    try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
                        ResultSet rs = queryExecution.execSelect();
                        // Iterate over the results and print each element's local name
                        rs.forEachRemaining(qs -> {
                            System.out.println("BOT element: " + qs.get("element").asResource().getLocalName());
                        });
                    }
                }
            }
        } catch (Exception e) {
            // Print any errors that may occur
            e.printStackTrace();
        }
    }
}
