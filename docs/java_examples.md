
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


## List element types and property sets in the model
```
package examples;

import java.io.File;
import java.net.URL;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example6 {
	static final private int props_level = 1;
	static final private boolean hasBuildingElements = true; 
	static final private boolean hasBuildingProperties = true; 
	static final boolean hasPropertiesBlankNodes = false; 
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
						exportIfcOWL,hasBuildingElements,hasBuildingProperties,hasBoundingBoxWKT,hasUnits);
				
				// List types
				System.out.println("Element types:");
				System.out.println(converter.getElementTypes());

				// List property sets
				System.out.println("Property sets:");
				System.out.println(converter.getPropertySetNames());

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
## The 2D Line Graph Example

```
package examples;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.geometry.euclidean.threed.Plane;
import org.apache.commons.geometry.euclidean.threed.Planes;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.apache.commons.geometry.euclidean.threed.line.Line3D;
import org.apache.commons.geometry.euclidean.threed.line.Lines3D;
import org.apache.commons.geometry.euclidean.threed.mesh.TriangleMesh;
import org.apache.commons.geometry.io.euclidean.threed.obj.ObjTriangleMeshReader;
import org.apache.commons.numbers.core.Precision;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

/*
 *  Copyright (c) 2024 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Example how to create 2D floorplan linegraph of the building   */

public class Example14 {
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
	static final boolean hasBoundingBoxWKT = false;

	class Line {
		final Vector3D a;
		final Vector3D b;

		public Line(Vector3D a, Vector3D b) {
			super();
			this.a = a;
			this.b = b;
		}
		@Override
			public String toString() {				
				return a+" - "+b;
			}
	}

	List<Line> crossing_lines = new ArrayList<>();

	public Example14() {
		readAndProcessIFC();
		
		for(Line line:crossing_lines)
			System.out.println(line);
	}

	private void readAndProcessIFC() {

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
							PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>\r
							\r
							SELECT ?e ?obj WHERE {\r
							  ?e <https://w3id.org/omg#hasGeometry> ?g .\r
							  ?e a beo:Wall .\r
							  ?g fog:asObj_v3.0-obj ?obj \r
							}\s""");
					try (QueryExecution queryExecution = QueryExecutionFactory.create(query, m)) {
						ResultSet rs = queryExecution.execSelect();
						rs.forEachRemaining(qs -> {
							System.out.println("BOT element: " + qs.get("e").asResource().getLocalName());

							byte[] decodedBytes = Base64.getDecoder()
									.decode(qs.get("obj").asLiteral().getLexicalForm());
							String decodedString = new String(decodedBytes);

							extract_2dlines(decodedString, 4);

						});
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void extract_2dlines(String objfilecontent, double elevation) {
		Precision.DoubleEquivalence precision = Precision.doubleEquivalenceOfEpsilon(1e-6);

		final Plane plane = Planes.fromPointAndNormal(Vector3D.of(0, 0, elevation), Vector3D.Unit.PLUS_Z, precision);

		Reader input = new StringReader(objfilecontent);
		ObjTriangleMeshReader reader = new ObjTriangleMeshReader(input, precision);

		TriangleMesh mesh = reader.readTriangleMesh();

		for (TriangleMesh.Face t : mesh.getFaces()) {
			// get the vertices of the triangle
			Vector3D a = t.getPoint1();
			Vector3D b = t.getPoint2();
			Vector3D c = t.getPoint3();

			// check if the plane intersects any of the edges
			Vector3D ab = plane.intersection(Lines3D.fromPoints(a, b, precision));
			Vector3D bc = plane.intersection(Lines3D.fromPoints(b, c, precision));
			Vector3D ca = plane.intersection(Lines3D.fromPoints(c, a, precision));

			Line3D interplane = t.getPolygon().getPlane().intersection(plane);

			if (ab != null && bc != null && !ab.vectorTo(bc).isZero(precision)) {
				if (t.getPolygon().contains(ab) && t.getPolygon().contains(bc))
					if (interplane.contains(ab) && interplane.contains(bc)) {
						Line line3d = new Line(ab, bc);
						crossing_lines.add(line3d);
					}
			}

			if (bc != null && ca != null && !bc.vectorTo(ca).isZero(precision)) {
				if (t.getPolygon().contains(bc) && t.getPolygon().contains(ca))
					if (interplane.contains(bc) && interplane.contains(ca)) {
						// if (between(b, bc, c) && between(c, ca, a)) {
						Line line3d = new Line(bc, ca);
						crossing_lines.add(line3d);
					}
			}

			if (ca != null && ab != null && !ca.vectorTo(ab).isZero(precision)) {
				if (t.getPolygon().contains(ca) && t.getPolygon().contains(ab))
					if (interplane.contains(ca) && interplane.contains(ab)) {
						// if (between(c, ca, a) && between(a, ab, b)) {
						Line line3d = new Line(ca, ab);
						crossing_lines.add(line3d);
					}
			}

		}
	}

	public static void main(String[] args) {
		new Example14();
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
