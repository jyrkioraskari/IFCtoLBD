package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.core.IFCtoLBDConverterCore.IfcModelStatistics;

class ModelSummaryTest {
	@Test
	void summarizesCountsAndModelWarnings() {
		IfcModelStatistics statistics = new IfcModelStatistics(
				Map.of("IfcBuildingStorey", 2, "IfcSpace", 7), 14, false);

		IFCtoLBDController.ModelSummary summary = IFCtoLBDController.createModelSummary("IFC4", 1_572_864,
				Map.of("Wall", 6, "Door", 3), statistics, 4, 11, true, true);

		assertEquals("IFC4", summary.schema());
		assertEquals("1.5 MiB", summary.fileSize());
		assertEquals(14, summary.elementCount());
		assertEquals(9, summary.mappedElementCount());
		assertEquals(2, summary.storeyCount());
		assertEquals(7, summary.spaceCount());
		assertEquals("Model Summary  Storeys 2  Spaces 7",
				IFCtoLBDController.collapsedModelSummaryTitle(summary));
		assertEquals("Wall 6  ·  Door 3", summary.majorTypes());
		assertTrue(summary.warnings().stream().anyMatch(warning -> warning.contains("5 IFC elements")));
		assertTrue(summary.warnings().stream().anyMatch(warning -> warning.contains("Geometry was requested")));
	}

	@Test
	void healthySummaryHasNoWarningsAndStatisticsAreImmutable() {
		Map<String, Integer> mutableCounts = new HashMap<>();
		mutableCounts.put("IfcBuildingStorey", 1);
		mutableCounts.put("IfcSpace", 1);
		IfcModelStatistics statistics = new IfcModelStatistics(mutableCounts, 2, true);
		mutableCounts.clear();

		IFCtoLBDController.ModelSummary summary = IFCtoLBDController.createModelSummary("IFC4X3", 512,
				Map.of("Wall", 2), statistics, 1, 2, true, true);

		assertEquals(1, statistics.rootTypeCounts().get("IfcBuildingStorey"));
		assertEquals("Available", summary.geometryStatus());
		assertTrue(summary.warnings().isEmpty());
	}

	@Test
	void formatsFileSizesForDashboardDisplay() {
		assertEquals("0 B", IFCtoLBDController.formatFileSize(0));
		assertEquals("1.0 KiB", IFCtoLBDController.formatFileSize(1024));
		assertEquals("12 MiB", IFCtoLBDController.formatFileSize(12L * 1024 * 1024));
	}
}
