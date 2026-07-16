package de.rwth_aachen.dc.lbd;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import javax.vecmath.Point3d;

public final class WireframeWKT {

	private WireframeWKT() {
	}

	public static String fromMesh(double[] vertices, int[] faces, boolean oneBasedFaces) {
		Set<Edge> edges = new LinkedHashSet<>();
		for (int i = 0; i + 2 < faces.length; i += 3) {
			int a = normalizeIndex(faces[i], oneBasedFaces);
			int b = normalizeIndex(faces[i + 1], oneBasedFaces);
			int c = normalizeIndex(faces[i + 2], oneBasedFaces);
			addEdge(edges, vertices, a, b);
			addEdge(edges, vertices, b, c);
			addEdge(edges, vertices, c, a);
		}
		return toMultiLineString(vertices, edges);
	}

	private static int normalizeIndex(int index, boolean oneBasedFaces) {
		return oneBasedFaces ? index - 1 : index;
	}

	private static void addEdge(Set<Edge> edges, double[] vertices, int from, int to) {
		if (from == to || !isValidVertex(vertices, from) || !isValidVertex(vertices, to)) {
			return;
		}
		edges.add(new Edge(Math.min(from, to), Math.max(from, to)));
	}

	private static boolean isValidVertex(double[] vertices, int index) {
		return index >= 0 && index * 3 + 2 < vertices.length;
	}

	private static String toMultiLineString(double[] vertices, Set<Edge> edges) {
		if (edges.isEmpty()) {
			return null;
		}
		StringBuilder wkt = new StringBuilder("MULTILINESTRING Z(");
		boolean first = true;
		for (Edge edge : edges) {
			if (!first) {
				wkt.append(", ");
			}
			first = false;
			wkt.append('(');
			appendPoint(wkt, vertices, edge.from);
			wkt.append(", ");
			appendPoint(wkt, vertices, edge.to);
			wkt.append(')');
		}
		wkt.append(')');
		return wkt.toString();
	}

	private static void appendPoint(StringBuilder wkt, double[] vertices, int index) {
		int offset = index * 3;
		wkt.append(format(vertices[offset])).append(' ')
				.append(format(vertices[offset + 1])).append(' ')
				.append(format(vertices[offset + 2]));
	}

	private static String format(double value) {
		if (value == -0.0d) {
			value = 0.0d;
		}
		return String.format(Locale.ROOT, "%.9f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
	}

	public static String fromObj(ObjDescription obj) {
		if (obj == null || obj.vertices.isEmpty() || obj.faces.isEmpty()) {
			return null;
		}
		double[] vertices = new double[obj.vertices.size() * 3];
		for (int i = 0; i < obj.vertices.size(); i++) {
			Point3d point = obj.vertices.get(i);
			vertices[i * 3] = point.x;
			vertices[i * 3 + 1] = point.y;
			vertices[i * 3 + 2] = point.z;
		}
		int[] faces = new int[obj.faces.size() * 3];
		for (int i = 0; i < obj.faces.size(); i++) {
			faces[i * 3] = obj.faces.get(i).left;
			faces[i * 3 + 1] = obj.faces.get(i).middle;
			faces[i * 3 + 2] = obj.faces.get(i).right;
		}
		return fromMesh(vertices, faces, true);
	}

	private record Edge(int from, int to) {
	}
}
