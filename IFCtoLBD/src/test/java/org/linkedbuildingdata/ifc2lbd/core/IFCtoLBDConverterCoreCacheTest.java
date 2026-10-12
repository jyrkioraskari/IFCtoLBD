package org.linkedbuildingdata.ifc2lbd.core;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IFCtoLBDConverterCoreCacheTest {
	@Test
	void cacheKeyTracksContentEvenWhenSizeAndTimestampDoNotChange(@TempDir Path directory) throws Exception {
		Path source = directory.resolve("model.ifc");
		FileTime timestamp = FileTime.fromMillis(1_700_000_000_000L);
		Files.writeString(source, "AAAA");
		Files.setLastModifiedTime(source, timestamp);
		String first = IFCtoLBDConverterCore.ifcOwlCacheKey(source.toFile(), "https://example.com/", true);

		Files.writeString(source, "BBBB");
		Files.setLastModifiedTime(source, timestamp);
		String second = IFCtoLBDConverterCore.ifcOwlCacheKey(source.toFile(), "https://example.com/", true);

		assertNotEquals(first, second);
	}
}
