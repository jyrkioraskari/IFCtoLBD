package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class DiskStagingIntegrationTest {
	@Test
	void convertsWithExplicitTdb2Staging() throws Exception {
		File input = new File(getClass().getResource("/TWO WALLS.ifc").toURI());
		try (ConversionSession session = new ConversionSession(ConversionSession.Storage.TDB2);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(
						new ConversionRequest(input.getAbsolutePath(), ConversionProfiles.CORE))) {
			assertFalse(result.getModel().isEmpty());
		}
	}
}
