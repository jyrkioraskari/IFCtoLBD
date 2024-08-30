package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example10 {
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
                        exportIfcOWL, hasBuildingElements, hasBuildingProperties, hasBoundingBoxWKT, hasUnits);
                
                // Perform the LBD conversion phase with specified options
                Model m = converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
                        hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
                        hasUnits, hasBoundingBoxWKT, true);

                if (m != null) {
                    // Create a SPARQL query to select all elements of type bot:Element and return them as JSON
                    Query query = QueryFactory.create("""
                            PREFIX bot: <https://w3id.org/bot#>
                            
                            JSON { 'element' : ?element } WHERE {
                              ?element a bot:Element .
                            }""", Syntax.syntaxARQ);
                    
                    // Execute the SPARQL query on the RDF model
                    try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
                        JsonArray jsonArray = queryExecution.execJson();
                        // Print the resulting JSON array
                        System.out.println("jsonArray:" + jsonArray);
                    }
                }
            }
        } catch (Exception e) {
            // Print any errors that may may occur
            e.printStackTrace();
        }
    }
}
