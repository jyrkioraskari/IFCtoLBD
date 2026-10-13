package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplorationModelCacheTest {
	@Test
	void reusesSnapshotUntilInvalidatedAndKeepsActiveLeaseAlive(@TempDir Path directory) throws Exception {
		Path output = directory.resolve("building.ttl");
		Files.writeString(output, "");
		AtomicInteger loads = new AtomicInteger();
		try (ExplorationModelCache cache = new ExplorationModelCache(file -> {
			loads.incrementAndGet();
			return ModelFactory.createDefaultModel();
		})) {
			try (var first = cache.acquire(output.toFile(), 1);
					var second = cache.acquire(output.toFile(), 1)) {
				assertSame(first.model(), second.model());
				assertEquals(1, loads.get());
				cache.invalidate();
				assertEquals(0, first.model().size());
				try (var third = cache.acquire(output.toFile(), 1)) {
					assertNotSame(first.model(), third.model());
					assertEquals(2, loads.get());
				}
			}
			Path sidecar = directory.resolve("building_element_properties.ttl");
			Files.writeString(sidecar, "<urn:a> <urn:b> <urn:c> .");
			try (var fourth = cache.acquire(output.toFile(), 1)) {
				assertEquals(3, loads.get());
			}
		}
	}

	@Test
	void closedCacheRejectsNewLeases(@TempDir Path directory) throws Exception {
		Path output = directory.resolve("building.ttl");
		Files.writeString(output, "");
		ExplorationModelCache cache = new ExplorationModelCache(file -> ModelFactory.createDefaultModel());
		cache.close();
		assertThrows(IllegalStateException.class, () -> cache.acquire(output.toFile(), 1));
	}
}
