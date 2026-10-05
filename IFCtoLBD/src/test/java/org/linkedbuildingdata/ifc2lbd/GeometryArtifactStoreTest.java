package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeometryArtifactStoreTest {
	@TempDir Path directory;

	@Test
	void filesystemStoreDeduplicatesByContent() throws Exception {
		byte[] content = "v 0 0 0\n".getBytes(StandardCharsets.UTF_8);
		var store = new FileSystemGeometryArtifactStore(directory, URI.create("https://example.com/geometry/"));
		GeometryArtifact first = store.store(content, "model/obj", "obj", "detailed", "urn:test:crs").orElseThrow();
		GeometryArtifact second = store.store(content, "model/obj", ".obj", "detailed", "urn:test:crs").orElseThrow();

		assertEquals(first, second);
		assertTrue(first.uri().toString().endsWith(first.sha256() + ".obj"));
		try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
		assertArrayEquals(content, Files.readAllBytes(directory.resolve(first.sha256() + ".obj")));
	}

	@Test
	void externalArtifactsAreOptInAndNoOpStoreFallsBack() {
		assertFalse(ConversionProfiles.GEOMETRY_FULL.toConversionProperties().hasGeometryArtifacts());
		assertTrue(ConversionProfiles.GEOMETRY_EXTERNAL.toConversionProperties().hasGeometryArtifacts());
		assertTrue(NoGeometryArtifactStore.INSTANCE.store(new byte[0], "model/obj", "obj", "detailed", "crs")
				.isEmpty());
	}
}
