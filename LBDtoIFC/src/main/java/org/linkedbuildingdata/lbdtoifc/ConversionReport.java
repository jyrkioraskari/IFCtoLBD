package org.linkedbuildingdata.lbdtoifc;

import java.util.List;

public record ConversionReport(
        int sites,
        int buildings,
        int storeys,
        int spaces,
        int elements,
        int propertySets,
        int geometries,
        long geometryVertices,
        long geometryTriangles,
        List<String> warnings) {

    public ConversionReport {
        warnings = List.copyOf(warnings);
    }
}
