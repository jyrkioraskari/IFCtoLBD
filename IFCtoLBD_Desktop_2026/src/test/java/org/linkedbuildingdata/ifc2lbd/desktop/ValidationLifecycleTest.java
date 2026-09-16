package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Headless tests for output invalidation and late asynchronous SHACL results. */
class ValidationLifecycleTest {
	private static Field field(Class<?> type, String name) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Object nested(String name, Object... arguments) throws Exception {
		Class<?> type = Class.forName(IFCtoLBDController.class.getName() + "$" + name);
		Constructor<?> constructor = type.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		return constructor.newInstance(arguments);
	}

	private static Object invoke(String name, Object receiver, Class<?>[] parameters, Object... arguments)
			throws Exception {
		Method method = IFCtoLBDController.class.getDeclaredMethod(name, parameters);
		method.setAccessible(true);
		return method.invoke(receiver, arguments);
	}

	@Test
	void readingNewModelClearsPreviousOutputAndShapeValues() throws Exception {
		IFCtoLBDController controller = new IFCtoLBDController();
		try {
			Object request = nested("ConversionRequest", null, Set.of(), Set.of());
			field(IFCtoLBDController.class, "lastSuccessfulConversionRequest").set(controller, request);
			field(IFCtoLBDController.class, "pendingConversionRequest").set(controller, request);
			field(IFCtoLBDController.class, "queryDataAvailable").set(controller, true);
			Object item = nested("ShapeValidationItem", "rules.ttl", "Shape", NodeFactory.createURI("urn:test:shape"), Set.of());
			field(item.getClass(), "conforms").set(item, false);
			field(item.getClass(), "message").set(item, "Old failure");
			@SuppressWarnings("unchecked")
			List<Object> items = (List<Object>) field(IFCtoLBDController.class, "shapeValidationItems").get(controller);
			items.add(item);
			invoke("resetConversionOutput", controller, new Class<?>[0]);
			assertNull(field(IFCtoLBDController.class, "lastSuccessfulConversionRequest").get(controller));
			assertNull(field(IFCtoLBDController.class, "pendingConversionRequest").get(controller));
			assertFalse((boolean) field(IFCtoLBDController.class, "queryDataAvailable").get(controller));
			assertNull(field(item.getClass(), "conforms").get(item));
			assertNotEquals("Old failure", field(item.getClass(), "message").get(item));
			assertEquals(1, items.size(), "Keep loaded shapes, only clear their results");
		} finally {
			controller.shutdown();
		}
	}

	@Test
	void lateValidationCannotRestorePreviousModelResults() throws Exception {
		IFCtoLBDController controller = new IFCtoLBDController();
		try {
			Object item = nested("ShapeValidationItem", "rules.ttl", "Shape", NodeFactory.createURI("urn:test:shape"), Set.of());
			Object oldResult = nested("ShapeValidationResult", item, false, "Previous model failure");
			long oldRevision = field(IFCtoLBDController.class, "validationRevision").getLong(controller);
			invoke("resetConversionOutput", controller, new Class<?>[0]);
			invoke("publishValidationResults", controller, new Class<?>[] {long.class, List.class, String.class},
					oldRevision, List.of(oldResult), "old.ttl");
			assertNull(field(item.getClass(), "conforms").get(item));
			long currentRevision = field(IFCtoLBDController.class, "validationRevision").getLong(controller);
			Object freshResult = nested("ShapeValidationResult", item, true, "Current model passed");
			invoke("publishValidationResults", controller, new Class<?>[] {long.class, List.class, String.class},
					currentRevision, List.of(freshResult), "new.ttl");
			assertEquals(true, field(item.getClass(), "conforms").get(item));
			assertEquals("Current model passed", field(item.getClass(), "message").get(item));
		} finally {
			controller.shutdown();
		}
	}

	@Test
	void rereadsReplacedOutputEvenWhenFileTimestampIsUnchanged(@TempDir Path directory) throws Exception {
		Path file = directory.resolve("output.ttl");
		Files.writeString(file, "<urn:test:first> <urn:test:p> <urn:test:o> .");
		FileTime timestamp = Files.getLastModifiedTime(file);
		Model first = (Model) invoke("readOutputModel", null, new Class<?>[] {File.class}, file.toFile());
		try {
			Files.writeString(file, "<urn:test:second> <urn:test:p> <urn:test:o> .");
			Files.setLastModifiedTime(file, timestamp);
			Model second = (Model) invoke("readOutputModel", null, new Class<?>[] {File.class}, file.toFile());
			try {
				assertTrue(second.containsResource(second.createResource("urn:test:second")));
				assertFalse(second.containsResource(second.createResource("urn:test:first")));
			} finally { second.close(); }
		} finally { first.close(); }
	}
}
