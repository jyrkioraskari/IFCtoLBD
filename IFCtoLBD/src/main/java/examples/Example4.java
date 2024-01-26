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
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			try (IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				Model m = c.convert(ifc_file.getAbsoluteFile().toString(), null, hasBuildingElements,
						hasSeparateBuildingElementsModel, hasBuildingProperties, hasSeparatePropertiesModel,
						hasGeolocation, hasGeometry, exportIfcOWL, hasUnits, hasPerformanceBoost, hasBoundingBoxWKT);
				if (m != null) {
					Query query = QueryFactory.create("""
							PREFIX fog: <https://w3id.org/fog#>\r
							\r
							SELECT ?e ?wkt ?obj WHERE {\r
							  ?e <https://w3id.org/omg#hasGeometry> ?g .\r
							  ?g <https://www.opengis.net/ont/geosparql#asWKT> ?wkt .\r
							  ?g fog:asObj_v3.0-obj ?obj \r
							}\s""");
					try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
						ResultSet rs = queryExecution.execSelect();
						rs.forEachRemaining(qs -> {
							System.out.println("BOT element: " + qs.get("e").asResource().getLocalName());
							System.out.println("BOT element WKT: " + qs.get("wkt"));
							System.out.println("BOT element OBJ: " + qs.get("obj"));
							

						});
						// m.write(System.out, "TTL");
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}