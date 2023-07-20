
## IFCtoLBD Java Implementation

The example source code can be found in the IFCtoLBD/src/main/java/examples  subfolder


### Print out the Turtle formatted LBD of an IFC file

```
package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example1 {

	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", false, 1);
			Model m = c.convert(ifc_file.getAbsolutePath());
			m.write(System.out, "TTL");

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

```

### Print RDF subjects of the LBD of the Duplex model. 
```
package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example2 {

	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", false, 1);
			Model m = c.convert(ifc_file.getAbsolutePath());
			
			m.listSubjects().forEach(s->System.out.println(s));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

```


### SPARQL query to query BOT elements of an LBD model
```
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
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", false, 1);
			Model m = c.convert(ifc_file.getAbsolutePath());
			
			Query query = QueryFactory.create("PREFIX bot: <https://w3id.org/bot#>\r\n"
					+ "\r\n"
					+ "SELECT ?e WHERE {\r\n"
					+ "  ?e a bot:Element .\r\n"
					+ "} ");
			QueryExecution queryExecution = QueryExecutionFactory.create(query, m);
			ResultSet rs = queryExecution.execSelect();
			rs.forEachRemaining(qs -> {
				System.out.println("BOT element: "+qs.get("e").asResource().getLocalName());
				
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

```


### Print out the element geometries
```
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
	static final private int props_level=1;
	static final private boolean hasBuildingElements=true;
	static final private boolean hasBuildingProperties=true;
	
	static final boolean hasSeparateBuildingElementsModel=false; 
	static final boolean hasPropertiesBlankNodes=false;
	static final boolean hasSeparatePropertiesModel=false;
	
	static final boolean hasGeolocation=false;
	
	static final boolean hasGeometry=true;
	static final boolean exportIfcOWL=false;
	static final boolean hasUnits=false;
    
	static final boolean hasPerformanceBoost=false;
	static final boolean hasBoundingBoxWKT=true;

	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			
			IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes, props_level);
			Model m=c.convert(ifc_file.getAbsoluteFile().toString(), null, hasBuildingElements, hasSeparateBuildingElementsModel, hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry,exportIfcOWL,hasUnits,hasPerformanceBoost,hasBoundingBoxWKT);
			if(m!=null)
			{
				Query query = QueryFactory.create("PREFIX fog: <https://w3id.org/fog#>\r\n"
						+ "\r\n"
						+ "SELECT ?e ?wkt ?obj WHERE {\r\n"
						+ "  ?e <http://www.opengis.net/ont/geosparql#hasGeometry> ?g .\r\n"
						+ "  ?g <http://www.opengis.net/ont/geosparql#asWKT> ?wkt .\r\n"
						+ "  ?g fog:asObj_v3.0-obj ?obj \r\n"
						+ "} ");
				QueryExecution queryExecution = QueryExecutionFactory.create(query, m);
				ResultSet rs = queryExecution.execSelect();
				rs.forEachRemaining(qs -> {
					System.out.println("BOT element: "+qs.get("e").asResource().getLocalName());
					System.out.println("BOT element WKT: "+qs.get("wkt"));
					System.out.println("BOT element OBJ: "+qs.get("obj"));
					
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
```
