package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IfcOpenShellStructuredGeometryTest {
	private static Path pixelTextureJson() {
		Path archive = Path.of("ISO Spec archive");
		if (!Files.isDirectory(archive)) archive = Path.of("..", "ISO Spec archive");
		assumeTrue(Files.isDirectory(archive), "ISO Spec archive is not available");
		return archive.resolve("tessellation-with-pixel-texture.json");
	}

	@Test
	void extractsPixelTextureJsonTessellation() {
		try (GeometryResult result = IfcOpenShellGeometryProvider.INSTANCE.load(pixelTextureJson())) {
			String guid = "3K_lgFFuT4$xmawQPom25e";
			assertNotNull(result.getBoundingBox(guid));
			var obj = result.getOBJ(guid);
			assertNotNull(obj);
			assertTrue(obj.toString().length() > 100);
		}
	}

	@Test
	void geometryFullProfileIncludesJsonGeometry(@TempDir Path temporaryDirectory) throws Exception {
		Path input = Files.createTempFile(temporaryDirectory, "pixel-texture-", ".json");
		Files.copy(pixelTextureJson(), input, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(
						new ConversionRequest(input.toString(), ConversionProfiles.GEOMETRY_FULL))) {
			var model = result.getModel();
			assertTrue(model.contains(null, model.createProperty("https://w3id.org/omg#hasGeometry")));
		}
	}
}
