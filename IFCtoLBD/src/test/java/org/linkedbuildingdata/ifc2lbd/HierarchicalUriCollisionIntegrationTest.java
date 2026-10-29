package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

@Tag("integration")
class HierarchicalUriCollisionIntegrationTest {
	@Test
	void stairFlightAndSameNamedStringerRemainDistinct() throws Exception {
		var input = ClassLoader.getSystemResource("Duplex.ifc");
		assertNotNull(input);
		var properties = new ConversionProperties();
		properties.setHasHierarchicalNaming(true);
		properties.setHasGeometry(false);

		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.test/model#");
				ConversionResult result = converter.convert(new ConversionRequest(
						new File(input.toURI()).getAbsolutePath(), properties))) {
			Resource flight = subjectWithLiteral(result, "1oKjKg9PD3fP1iIwXLh3lK");
			Resource stringer = subjectWithLiteral(result, "1gtrSK5QnDuxDwygd0EDGO");
			assertNotEquals(flight, stringer);
			assertEquals("https://example.test/model#Stair%3AResidential_-_200mm_Max_Riser_250mm_Tread%3A151086%3A1",
					flight.getURI());
			assertTrue(stringer.getURI().startsWith(flight.getURI() + "_"));

			var turtle = new ByteArrayOutputStream();
			IFCtoLBDConverter_CLI.writeTurtle(turtle, result);
			String serialized = turtle.toString(StandardCharsets.UTF_8);
			assertTrue(serialized.contains("1oKjKg9PD3fP1iIwXLh3lK"));
			assertTrue(serialized.contains("1gtrSK5QnDuxDwygd0EDGO"));
		}
	}

	@Test
	void turtleCliIncludesProductGraph() throws Exception {
		var input = ClassLoader.getSystemResource("Duplex.ifc");
		assertNotNull(input);
		var output = Files.createTempFile("duplex-hierarchical-", ".ttl");
		try {
			int exit = new CommandLine(new IFCtoLBDConverter_CLI()).execute(
					new File(input.toURI()).getAbsolutePath(),
					"--target_file=" + output,
					"--hasBuildingElements=true",
					"--hasIfc_based_elements=false",
					"--hasHierarchicalNaming=true");
			assertEquals(0, exit);
			String turtle = Files.readString(output);
			assertTrue(turtle.contains("1oKjKg9PD3fP1iIwXLh3lK"),
					"Recognized stair flights must remain when IFC-only elements are disabled");
			assertTrue(turtle.contains("1gtrSK5QnDuxDwygd0EDGO"),
					"Recognized stringers must remain when IFC-only elements are disabled");
		} finally {
			Files.deleteIfExists(output);
		}
	}

	private static Resource subjectWithLiteral(ConversionResult result, String value) {
		var statements = result.getModel().listStatements(null, null, value).toList();
		assertEquals(1, statements.size(), "Expected one resource for IFC GlobalId " + value);
		return statements.getFirst().getSubject();
	}
}
