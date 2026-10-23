package examples;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.ConversionProfiles;
import org.linkedbuildingdata.ifc2lbd.ConversionRequest;
import org.linkedbuildingdata.ifc2lbd.ConversionResult;
import org.linkedbuildingdata.ifc2lbd.ConversionSession;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.NoGeometryProvider;
import org.linkedbuildingdata.ifc2lbd.RevisionComparator;

/** Offline, runnable BIM-to-sensor discovery and measurement example. */
public final class BimSensorIntegrationExample {
	private static final String PROJECT = "https://example.org/projects/campus-a";
	private static final String SENSOR = "https://example.org/sensors/temperature-101";
	private static final String SOSA = "https://www.w3.org/ns/sosa/";
	private static final String BRICK = "https://brickschema.org/schema/Brick#";
	private static final String EX = "https://example.org/integration#";

	public static void main(String[] args) throws Exception {
		Path directory = args.length == 0 ? Path.of("examples", "bim-sensor-integration") : Path.of(args[0]);
		try (ConversionSession firstSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				ConversionSession secondSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				ConversionSession legacySession = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter firstConverter = new IFCtoLBDConverter(firstSession, "https://legacy.example/");
				IFCtoLBDConverter secondConverter = new IFCtoLBDConverter(secondSession, "https://renamed.example/");
				IFCtoLBDConverter legacyConverter = new IFCtoLBDConverter(legacySession, "https://legacy.example/");
				ConversionResult first = firstConverter.convert(request(directory.resolve("revision-1.ifcjson"), directory, true));
				ConversionResult second = secondConverter.convert(request(directory.resolve("revision-2.ifcjson"), directory, true));
				ConversionResult legacy = legacyConverter.convert(request(directory.resolve("revision-2.ifcjson"), directory, false));
				var diff = new RevisionComparator().compare(first, second)) {
			try (FixtureApi api = new FixtureApi()) {
			Model discovery = ModelFactory.createUnion(second.getModel(), second.getExternalLinkModel());
			discovery.add(sensorCatalog(api.measurementUri()));
			String query = """
					PREFIX bot: <https://w3id.org/bot#>
					PREFIX sosa: <https://www.w3.org/ns/sosa/>
					PREFIX brick: <https://brickschema.org/schema/Brick#>
					PREFIX ex: <https://example.org/integration#>
					SELECT ?equipment ?sensor ?service WHERE {
					  ?equipment a bot:Element ; sosa:hosts ?sensor .
					  ?sensor sosa:observes brick:Temperature ; ex:measurementService ?service .
					}
					""";
			try (var execution = QueryExecutionFactory.create(query, discovery)) {
				var rows = execution.execSelect(); int discovered = 0;
				while (rows.hasNext()) {
					var row = rows.next(); discovered++;
					String reading = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
							URI.create(row.getResource("service").getURI())).GET().build(),
							HttpResponse.BodyHandlers.ofString()).body();
					System.out.println(row.getResource("equipment") + " -> " + row.getResource("sensor") + " -> " + reading);
				}
				if (discovered == 0) throw new IllegalStateException("No equipment/sensor/service path was discovered");
			}
			if (diff.getRetainedGlobalIdCount() != 1 || diff.getAddedGlobalIdCount() != 1
					|| legacy.getExternalLinkModel().size() != 1)
				throw new IllegalStateException("Identity or mapping expectations were not met");
			System.out.printf("Revision: %d retained, %d added, %d removed GlobalIds; %d orphaned links.%n",
					diff.getRetainedGlobalIdCount(), diff.getAddedGlobalIdCount(), diff.getRemovedGlobalIdCount(),
					diff.getOrphanedLinkCount());
			System.out.println("Existing identity mode also resolved link triples: " + legacy.getExternalLinkModel().size());
			}
		}
	}

	private static ConversionRequest request(Path model, Path directory, boolean stable) {
		ConversionRequest request = new ConversionRequest(model.toString(), ConversionProfiles.CORE)
				.withExternalLinkMappings(directory.resolve("links.json"));
		return stable ? request.withStableProjectIdentity(PROJECT) : request;
	}

	private static Model sensorCatalog(URI service) {
		Model model = ModelFactory.createDefaultModel();
		model.createResource(SENSOR).addProperty(RDF.type, model.createResource(SOSA + "Sensor"))
				.addProperty(model.createProperty(SOSA + "observes"), model.createResource(BRICK + "Temperature"))
				.addProperty(model.createProperty(EX + "measurementService"), model.createResource(service.toString()));
		return model;
	}

	private static final class FixtureApi implements AutoCloseable {
		private final HttpServer server;
		FixtureApi() throws Exception {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/measurements/latest", exchange -> {
				byte[] body = "{\"sensor\":\"temperature-101\",\"value\":21.4,\"unit\":\"Cel\",\"observedAt\":\"2026-10-23T12:00:00Z\"}"
						.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
			});
			server.start();
		}
		URI measurementUri() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/measurements/latest"); }
		@Override public void close() { server.stop(0); }
	}
	private BimSensorIntegrationExample() { }
}
