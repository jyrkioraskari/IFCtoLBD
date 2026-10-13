package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import org.junit.jupiter.api.Test;

class RememberedShapesTest {
	@Test
	void remembersOnlyCurrentlySelectedShapeEntries() {
		MemoryPreferences preferences = new MemoryPreferences(null, "");
		List<RememberedShapes.Entry> first = List.of(
				new RememberedShapes.Entry("/models/checks.ttl", "https://example.org/Shape1"),
				new RememberedShapes.Entry("/models/checks.ttl", "https://example.org/Shape2"));
		RememberedShapes.write(preferences, first);
		assertEquals(first, RememberedShapes.read(preferences));
		RememberedShapes.write(preferences, first.subList(1, 2));
		assertEquals(first.subList(1, 2), RememberedShapes.read(preferences));
		RememberedShapes.write(preferences, List.of());
		assertEquals(List.of(), RememberedShapes.read(preferences));
	}

	private static final class MemoryPreferences extends AbstractPreferences {
		private final Map<String, String> values = new HashMap<>();
		private final Map<String, MemoryPreferences> children = new HashMap<>();

		MemoryPreferences(AbstractPreferences parent, String name) {
			super(parent, name);
		}

		@Override protected void putSpi(String key, String value) { values.put(key, value); }
		@Override protected String getSpi(String key) { return values.get(key); }
		@Override protected void removeSpi(String key) { values.remove(key); }
		@Override protected void removeNodeSpi() { values.clear(); children.clear(); }
		@Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
		@Override protected String[] childrenNamesSpi() { return children.keySet().toArray(String[]::new); }
		@Override protected AbstractPreferences childSpi(String name) {
			return children.computeIfAbsent(name, child -> new MemoryPreferences(this, child));
		}
		@Override protected void syncSpi() throws BackingStoreException { }
		@Override protected void flushSpi() throws BackingStoreException { }
	}
}
