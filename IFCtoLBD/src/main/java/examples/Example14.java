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
		final String element_URI;

		public Line(Vector3D a, Vector3D b,String element_URI) {
			super();
			this.a = a;
			this.b = b;
			this.element_URI=element_URI;
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

							extract_2dlines(decodedString, qs.get("e").asResource().getURI(),4);

						});
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void extract_2dlines(String objfilecontent, String element_URI,double elevation) {
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
						Line line3d = new Line(ab, bc,element_URI);
						this.crossing_lines.add(line3d);
					}
			}

			if (bc != null && ca != null && !bc.vectorTo(ca).isZero(precision)) {
				if (t.getPolygon().contains(bc) && t.getPolygon().contains(ca))
					if (interplane.contains(bc) && interplane.contains(ca)) {
						// if (between(b, bc, c) && between(c, ca, a)) {
						Line line3d = new Line(bc, ca,element_URI);
						this.crossing_lines.add(line3d);
					}
			}

			if (ca != null && ab != null && !ca.vectorTo(ab).isZero(precision)) {
				if (t.getPolygon().contains(ca) && t.getPolygon().contains(ab))
					if (interplane.contains(ca) && interplane.contains(ab)) {
						// if (between(c, ca, a) && between(a, ab, b)) {
						Line line3d = new Line(ca, ab,element_URI);
						this.crossing_lines.add(line3d);
					}
			}

		}
	}

	public static void main(String[] args) {
		new Example14();
	}

}