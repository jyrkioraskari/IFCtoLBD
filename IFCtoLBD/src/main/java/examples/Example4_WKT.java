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

// JTS imports
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

public class Example4_WKT {
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
            try (IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
                    props_level);) {
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
                              ?g <http://www.opengis.net/ont/geosparql#asWKT> ?wkt .
                              ?g fog:asObj_v3.0-obj ?obj
                            }""");

                    // Execute the SPARQL query on the RDF model
                    try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
                        ResultSet rs = queryExecution.execSelect();

                        // Create a single WKTReader instance (thread-unsafe; reuse in single-threaded code)
                        WKTReader wktReader = new WKTReader();

                        // Iterate over the results and print each element's details
                        rs.forEachRemaining(qs -> {
                            String wktLiteralText = qs.getLiteral("wkt").getString();
                            String parseableWkt = removeCrsPrefix(wktLiteralText);

                            System.out.println("BOT element: " + qs.get("e").asResource().getLocalName());
                            System.out.println("BOT element WKT: " + parseableWkt);
                            System.out.println("BOT element WKT literal text: " + wktLiteralText);
                            System.out.println("BOT element OBJ: " + qs.get("obj"));

                            // Parse the WKT into a JTS Geometry
                            try {
                            	System.out.println(parseableWkt);
                                Geometry geom = wktReader.read(parseableWkt);
                                // Print some useful info about the geometry
                                System.out.println("  1: Geometry type: " + geom.getGeometryType());
                                System.out.println("  Number of coordinates: " + geom.getNumPoints());
                                System.out.println("  Envelope (minX,minY,maxX,maxY): "
                                        + geom.getEnvelopeInternal().getMinX() + ","
                                        + geom.getEnvelopeInternal().getMinY() + ","
                                        + geom.getEnvelopeInternal().getMaxX() + ","
                                        + geom.getEnvelopeInternal().getMaxY());
                                System.out.println("  WKT (normalized): " + geom.toText());
                            } catch (Exception ex) {
                            	ex.printStackTrace();
                                System.err.println("  Failed to parse WKT: " + ex.getMessage());
                            }
                            

                            System.out.println("--------------------------------------------------");
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

    private static String removeCrsPrefix(String wktLiteralText) {
        if (wktLiteralText == null) return null;
        if (wktLiteralText.startsWith("<")) {
            int crsEnd = wktLiteralText.indexOf("> ");
            if (crsEnd >= 0) {
                return wktLiteralText.substring(crsEnd + 2);
            }
        }
        return wktLiteralText;
    }
}
