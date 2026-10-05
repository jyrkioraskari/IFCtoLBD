package org.linkedbuildingdata.lbdtoifc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ObjMeshTest {
    @Test
    void resolvesNegativeSlashIndicesAndTriangulatesPolygon() throws Exception {
        String obj = """
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                f -4/1/1 -3/2/1 -2/3/1 -1/4/1
                """;

        ObjMesh mesh = ObjMesh.parse(obj.getBytes(StandardCharsets.UTF_8), 10, 10);

        assertEquals(4, mesh.vertices().size());
        assertEquals(2, mesh.triangles().size());
        assertEquals(new ObjMesh.Triangle(1, 2, 3), mesh.triangles().getFirst());
        assertEquals(new ObjMesh.Triangle(1, 3, 4), mesh.triangles().getLast());
        assertFalse(mesh.closed());
    }
}
