package org.linkedbuildingdata.ifc2lbd;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import examples.BimSensorIntegrationExample;

@Tag("integration")
class BimSensorIntegrationExampleTest {
	@Test
	void completeLocalExampleRunsWithoutNetworkOrCredentials() throws Exception {
		java.nio.file.Path example = java.nio.file.Path.of("examples", "bim-sensor-integration");
		if (!java.nio.file.Files.isDirectory(example)) example = java.nio.file.Path.of("..", "examples", "bim-sensor-integration");
		String directory = example.toString();
		try {
			BimSensorIntegrationExample.main(new String[] { directory });
		} catch (java.net.SocketException denied) {
			Assumptions.abort("Host sandbox does not permit the loopback fixture API: " + denied.getMessage());
		}
	}
}
