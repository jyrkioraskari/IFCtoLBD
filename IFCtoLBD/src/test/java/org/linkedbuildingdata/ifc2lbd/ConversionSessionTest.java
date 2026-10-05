package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.apache.jena.rdf.model.ModelFactory;

class ConversionSessionTest {
	@Test
	void selectedTypesJsonSetterAssignsParsedTypes() throws Exception {
		try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/")) {
			converter.setSelected_types("[\"Wall\",\"Door\"]");
			var field = org.linkedbuildingdata.ifc2lbd.core.IFCtoLBDConverterCore.class
					.getDeclaredField("selected_types");
			field.setAccessible(true);
			assertEquals(Set.of("Wall", "Door"), field.get(converter));
		}
	}
	@Test
	void eventBusesAreSessionScopedAndExitEventsCannotTerminateTheHost() {
		try (ConversionSession first = new ConversionSession(); ConversionSession second = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(first, "https://example.com/")) {
			assertNotEquals(first.getEventBus(), second.getEventBus());
			assertDoesNotThrow(() -> first.getEventBus().post(
					new org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemExit("test")));
		}
	}

	@Test
	void sessionsOwnSeparateDatasetsAndDeleteTheirWorkingDirectories() {
		Path firstDirectory;
		Path secondDirectory;
		try (ConversionSession first = new ConversionSession(); ConversionSession second = new ConversionSession()) {
			firstDirectory = first.getWorkingDirectory();
			secondDirectory = second.getWorkingDirectory();
			assertNotEquals(firstDirectory, secondDirectory);
			assertTrue(Files.isDirectory(firstDirectory));
			assertTrue(Files.isDirectory(secondDirectory));
			assertNotEquals(first.getDataset(), second.getDataset());
		}
		assertFalse(Files.exists(firstDirectory));
		assertFalse(Files.exists(secondDirectory));
	}

	@Test
	void closedSessionRejectsDatasetAccess() {
		ConversionSession session = new ConversionSession();
		session.close();
		assertThrows(IllegalStateException.class, session::getDataset);
	}

	@Test
	void resultIsAZeroCopyViewWithNoMaterializedUnion() {
		var general = ModelFactory.createDefaultModel();
		var product = ModelFactory.createDefaultModel();
		var property = ModelFactory.createDefaultModel();
		general.createResource("urn:test:element").addProperty(general.createProperty("urn:test:name"), "Wall");

		String key = "0".repeat(64);
		var names = ConversionGraphNames.forConversion(key);
		try (ConversionResult result = ConversionResult.of(general, product, property,
				ModelFactory.createDefaultModel(), ModelFactory.createDefaultModel(), names)) {
			assertEquals(1, result.getModel().size());
			general.createResource("urn:test:second").addProperty(general.createProperty("urn:test:name"), "Door");
			assertEquals(2, result.getModel().size());
			assertEquals(0, result.getProductModel().size());
			assertEquals(0, result.getPropertyModel().size());
			assertEquals(names, result.getGraphNames());
		}
	}

	@Test
	void sessionClosesItsGeometryProvider() {
		AtomicBoolean closed = new AtomicBoolean();
		GeometryProvider provider = new GeometryProvider() {
			@Override public String id() { return "test"; }
			@Override public String version() { return "1"; }
			@Override public GeometryResult load(Path ifcFile) { return GeometryResult.unavailable(); }
			@Override public void close() { closed.set(true); }
		};
		try (ConversionSession ignored = new ConversionSession(provider)) {
			assertFalse(closed.get());
		}
		assertTrue(closed.get());
	}

	@Test
	void noGeometryProviderIsDeterministicallyUnavailable() {
		try (GeometryResult result = NoGeometryProvider.INSTANCE.load(Path.of("unused.ifc"))) {
			assertFalse(result.isAvailable());
		}
	}
}
