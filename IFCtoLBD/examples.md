
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
        URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            File ifcFile = new File(ifcFileUrl.toURI());

            try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);){
                Model model = converter.convert(ifcFile.getAbsolutePath());
                model.write(System.out, "TTL");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
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
        URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            File ifcFile = new File(ifcFileUrl.toURI());

            try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);)
            {
              Model model = converter.convert(ifcFile.getAbsolutePath());      
              model.listSubjects().forEach(System.out::println);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
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
		URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifcFile = new File(ifcFileUrl.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);) {
				Model model = converter.convert(ifcFile.getAbsolutePath());

				Query query = QueryFactory.create("""
						PREFIX bot: <https://w3id.org/bot#>\r
						\r
						SELECT ?element WHERE {\r
						  ?element a bot:Element .\r
						}\s""");
				try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model)) {
					ResultSet resultSet = queryExecution.execSelect();
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
```

## Print out wall elements
```
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

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL);
				converter.convert_unit_properties_phase(hasBuildingElements, hasBuildingProperties,
						hasUnits, hasBoundingBoxWKT);
				
				Set<String> types = new HashSet<>();
				types.add("Wall");  // Filter only wall elements
				converter.setSelected_types(types);
				
				Model m =converter.convert_LBD_phase(hasBuildingElements, hasSeparateBuildingElementsModel,
						hasBuildingProperties, hasSeparatePropertiesModel, hasGeolocation, hasGeometry, exportIfcOWL,
						hasUnits, hasBoundingBoxWKT, true);

				if (m != null) {
					Query query = QueryFactory.create("""
							PREFIX bot: <https://w3id.org/bot#>\r
							\r
							SELECT ?element WHERE {\r
							  ?element a bot:Element .\r
							}\s""");
					try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
						ResultSet rs = queryExecution.execSelect();
						rs.forEachRemaining(qs -> {
							System.out.println("BOT element: " + qs.get("element").asResource().getLocalName());

						});
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
```

Notice that ifcOWL elements like ifcowl_ifcopeningelement are also listed


## GET a JSON array of elements and their OBJ presentation
```
package examples;

import java.io.File;
import java.net.URL;

import org.linkedbuildingdata.ifc2lbd.ConversionProperties;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example7 {
	
	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());
			ConversionProperties props = new ConversionProperties();
			props.setHasGeometry(true);
			try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://lbd.org/", false, 1);){
                  converter.convert(ifc_file.getAbsolutePath(),props);            
				System.out.println(converter.getObjJSON());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
```



## GET elements by geometry
```
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
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());
			ConversionProperties props = new ConversionProperties();
			props.setHasGeometry(true);
			try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://lbd.org/", false, 1);){
                converter.convert(ifc_file.getAbsolutePath(),props);            
				double geom_min_x= converter.getGeometryMinX();
				double geom_min_y= converter.getGeometryMinY();
				double geom_min_z= converter.getGeometryMinZ();
				double geom_max_x= converter.getGeometryMaxX();
				double geom_max_y= converter.getGeometryMaxY();
				double geom_max_z= converter.getGeometryMaxZ();
				
				System.out.println(converter.getGeometryMaxX());

				List<Resource> matching_elements = converter.getElementByGeometry(geom_min_x,geom_min_y, geom_min_z, geom_max_x, geom_max_y, geom_max_z);
				for(Resource element:matching_elements)
					System.out.println(element);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
```

## List ifcOWL Sites and connected buildings
```
package examples;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.jena.rdf.model.RDFNode;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.InvRDFStep;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

public class Example9 {
	static final private int props_level = 1;
	static final boolean hasPropertiesBlankNodes = false;
	static final boolean hasGeometry = false;
	static final boolean exportIfcOWL = false;
	static final boolean hasPerformanceBoost = false;

	static final boolean hasBuildingElements=true;
	static final boolean hasBuildingProperties=true;
	static final boolean hasBoundingBoxWKT=false;
	static final boolean hasUnits=false;
	
	
	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL,hasBuildingElements,hasBuildingProperties,hasBoundingBoxWKT,hasUnits);
				
				// List ifcOWL IFCSITEs
				List<RDFNode> sites = IfcOWLUtils.listSites(converter.ifcOWL, converter.ifcowl_model);
				System.out.println(sites);
				IfcOWL ifcOWL=converter.ifcOWL;
				 RDFStep[] steps_2x3 = new RDFStep[]{ new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
							new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		        
				for(RDFNode site:sites)
				{
				  List<RDFNode> buildings = RDFUtils.pathQuery(site.asResource(), steps_2x3);
				  System.out.println(buildings);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
```

## The old Maven implementation
The Maven library was published on the 29th of May, 2020.  

```
<dependency>
  <groupId>io.github.jyrkioraskari</groupId>
  <artifactId>IFCtoLBD</artifactId>
  <version>1.88</version>
  <classifier>jar-with-dependencies</classifier>
</dependency>
```
https://mvnrepository.com/artifact/io.github.jyrkioraskari/IFCtoLBD/1.88



How to use the code:
```
new IFCtoLBDConverter("c:\\in\model.ifc", "http://example.uri/", "c:\\out\\file.ttl",2, true, false, true, false, false, true);
```
[Javadoc](https://jyrkioraskari.github.io/IFCtoLBD/org/lbd/ifc2lbd/IFCtoLBDConverter.html)

[![Creative Commons License](https://i.creativecommons.org/l/by/4.0/88x31.png)](http://creativecommons.org/licenses/by/4.0/)  
This work is licensed under a [Creative Commons Attribution 4.0 International License](http://creativecommons.org/licenses/by/4.0/).
