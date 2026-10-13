package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shacl.validation.Severity;
import org.junit.jupiter.api.Test;

class ValidationFindingsTest {
	@Test
	void countsAndFiltersFindingsBySeverityShapeAndObject() {
		var violation = finding("Wall shape", "urn:wall:1", ValidationFindings.Level.VIOLATION);
		var warning = finding("Wall shape", "urn:wall:2", ValidationFindings.Level.WARNING);
		var info = finding("Door shape", "urn:door:1", ValidationFindings.Level.INFO);
		List<ValidationFindings.Finding> findings = List.of(violation, warning, info);

		assertEquals("1 violation, 1 warning, 1 info", ValidationFindings.count(findings).summary());
		assertEquals(List.of(warning), ValidationFindings.filter(findings,
				ValidationFindings.Level.WARNING, "Wall shape (rules.ttl)", "WALL:2"));
		assertEquals(List.of(info), ValidationFindings.filter(findings, null, "Door shape (rules.ttl)", ""));
		assertTrue(ValidationFindings.filter(findings, ValidationFindings.Level.INFO,
				"Wall shape (rules.ttl)", "").isEmpty());
	}

	@Test
	void copiesSeverityAndOffendingValueIntoInspector() {
		assertEquals(ValidationFindings.Level.WARNING, ValidationFindings.levelOf(Severity.Warning));
		assertEquals(ValidationFindings.Level.INFO, ValidationFindings.levelOf(Severity.Info));
		assertEquals(ValidationFindings.Level.VIOLATION, ValidationFindings.levelOf(Severity.Violation));
		assertEquals("bad value", ValidationFindings.nodeText(NodeFactory.createLiteralString("bad value")));
		String inspection = ValidationFindings.inspection(finding("Wall shape", "urn:wall:1",
				ValidationFindings.Level.WARNING));
		assertTrue(inspection.contains("Focus node: urn:wall:1"));
		assertTrue(inspection.contains("Offending value: bad value"));
		assertTrue(inspection.contains("Property path: urn:property:rating"));
	}

	private static ValidationFindings.Finding finding(String shape, String object,
			ValidationFindings.Level level) {
		return new ValidationFindings.Finding(shape, "rules.ttl", object, object,
				"urn:property:rating", "bad value", "Invalid rating", level);
	}
}
