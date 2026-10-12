package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** End-to-end benchmark smoke test; excluded from the default test selection. */
@Tag("slow")
class ConversionPerformanceBenchmarkTest {
	@ParameterizedTest(name = "conversion throughput for {0}")
	@ValueSource(strings = { "/Duplex_A.ifc", "/TWO WALLS.ifc" })
	void reportsConversionThroughput(String resourceName) throws Exception {
		File input = new File(getClass().getResource(resourceName).toURI());
		runBenchmark(input, resourceName, ConversionSession.Storage.MEMORY);
	}

	@Test
	@EnabledIfSystemProperty(named = "ifc.benchmark.file", matches = ".+")
	void reportsExternalModelConversionThroughput() throws Exception {
		File input = Path.of(System.getProperty("ifc.benchmark.file")).toFile();
		ConversionSession.Storage storage = ConversionSession.Storage.valueOf(
				System.getProperty("ifc.benchmark.storage", "MEMORY").toUpperCase());
		assertTrue(input.isFile(), "Benchmark IFC does not exist: " + input);
		runBenchmark(input, input.getName(), storage);
	}

	private void runBenchmark(File input, String label, ConversionSession.Storage storage) throws Exception {
		long started = System.nanoTime();
		long statements;
		ConversionTimings timings;
		try (ConversionSession session = new ConversionSession(java.time.Clock.systemUTC(), LegacyUriPolicy.INSTANCE,
				NoGeometryProvider.INSTANCE, NoGeometryArtifactStore.INSTANCE, storage);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(
						new ConversionRequest(input.getAbsolutePath(), ConversionProfiles.CORE))) {
			statements = result.getModel().size();
			timings = converter.getLastTimings().orElseThrow();
			String outputFile = System.getProperty("ifc.benchmark.output");
			if (outputFile != null && !outputFile.isBlank()) {
				try (OutputStream output = Files.newOutputStream(Path.of(outputFile))) {
					RDFDataMgr.write(output, result.getModel(), RDFFormat.TURTLE_BLOCKS);
				}
			}
		}
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
		System.out.printf("IFCtoLBD benchmark: input=%s storage=%s inputBytes=%d statements=%d elapsedMs=%d phases=%s%n",
				label, storage, input.length(), statements, elapsedMillis, timings);
		assertTrue(statements > 0);
	}
}
