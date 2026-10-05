package org.linkedbuildingdata.lbdtoifc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A validated triangle mesh read from the deliberately small OBJ subset used by IFCtoLBD. */
record ObjMesh(List<Vertex> vertices, List<Triangle> triangles, boolean closed) {
    record Vertex(double x, double y, double z) {}
    record Triangle(int a, int b, int c) {}
    private record Edge(int low, int high) {
        static Edge of(int first, int second) {
            return first < second ? new Edge(first, second) : new Edge(second, first);
        }
    }
    private static final class EdgeUse {
        int count;
        int orientation;
    }

    static ObjMesh parse(byte[] content, int maximumVertices, int maximumTriangles) throws IOException {
        String obj = new String(content, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(content, obj.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("OBJ is not valid UTF-8");
        }
        List<Vertex> vertices = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(obj))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] fields = trimmed.split("\\s+");
                if (fields[0].equals("v")) {
                    if (fields.length < 4) {
                        throw error(lineNumber, "vertex requires three coordinates");
                    }
                    double x = coordinate(fields[1], lineNumber);
                    double y = coordinate(fields[2], lineNumber);
                    double z = coordinate(fields[3], lineNumber);
                    vertices.add(new Vertex(x, y, z));
                    if (vertices.size() > maximumVertices) {
                        throw error(lineNumber, "vertex limit of " + maximumVertices + " exceeded");
                    }
                } else if (fields[0].equals("f")) {
                    if (fields.length < 4) {
                        throw error(lineNumber, "face requires at least three vertices");
                    }
                    int first = vertexIndex(fields[1], vertices.size(), lineNumber);
                    int previous = vertexIndex(fields[2], vertices.size(), lineNumber);
                    for (int index = 3; index < fields.length; index++) {
                        int current = vertexIndex(fields[index], vertices.size(), lineNumber);
                        if (first == previous || previous == current || current == first) {
                            throw error(lineNumber, "face contains a degenerate triangle");
                        }
                        triangles.add(new Triangle(first, previous, current));
                        if (triangles.size() > maximumTriangles) {
                            throw error(lineNumber, "triangle limit of " + maximumTriangles + " exceeded");
                        }
                        previous = current;
                    }
                }
            }
        }
        if (vertices.isEmpty() || triangles.isEmpty()) {
            throw new IOException("OBJ does not contain both vertices and faces");
        }
        return new ObjMesh(List.copyOf(vertices), List.copyOf(triangles), isClosed(triangles));
    }

    private static double coordinate(String lexical, int lineNumber) throws IOException {
        try {
            double value = Double.parseDouble(lexical);
            if (!Double.isFinite(value)) {
                throw error(lineNumber, "coordinate is not finite");
            }
            return value;
        } catch (NumberFormatException invalid) {
            throw error(lineNumber, "invalid coordinate " + lexical);
        }
    }

    private static int vertexIndex(String token, int vertexCount, int lineNumber) throws IOException {
        String lexical = token.split("/", -1)[0];
        try {
            int objIndex = Integer.parseInt(lexical);
            int oneBased = objIndex > 0 ? objIndex : vertexCount + objIndex + 1;
            if (objIndex == 0 || oneBased < 1 || oneBased > vertexCount) {
                throw error(lineNumber, "vertex index " + objIndex + " is out of range");
            }
            return oneBased;
        } catch (NumberFormatException invalid) {
            throw error(lineNumber, "invalid face index " + lexical);
        }
    }

    private static boolean isClosed(List<Triangle> triangles) {
        Map<Edge, EdgeUse> edges = new HashMap<>();
        for (Triangle triangle : triangles) {
            addEdge(edges, triangle.a(), triangle.b());
            addEdge(edges, triangle.b(), triangle.c());
            addEdge(edges, triangle.c(), triangle.a());
        }
        return edges.values().stream().allMatch(use -> use.count == 2 && use.orientation == 0);
    }

    private static void addEdge(Map<Edge, EdgeUse> edges, int first, int second) {
        Edge edge = Edge.of(first, second);
        EdgeUse use = edges.computeIfAbsent(edge, ignored -> new EdgeUse());
        use.count++;
        use.orientation += first < second ? 1 : -1;
    }

    private static IOException error(int lineNumber, String message) {
        return new IOException("OBJ line " + lineNumber + ": " + message);
    }
}
