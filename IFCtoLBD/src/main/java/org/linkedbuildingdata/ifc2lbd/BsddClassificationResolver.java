package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Exact-code resolver backed by the versioned buildingSMART bSDD v1 API. */
public final class BsddClassificationResolver implements ClassificationResolver {
	private static final URI DEFAULT_ENDPOINT = URI.create("https://api.bsdd.buildingsmart.org/");
	private final URI endpoint;
	private final Map<String, String> dictionaryUris;
	private final HttpClient client;
	private final RegistryTransport transport;
	private final ObjectMapper mapper = new ObjectMapper();

	public BsddClassificationResolver(Map<String, String> dictionaryUris) {
		this(DEFAULT_ENDPOINT, dictionaryUris, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
	}

	BsddClassificationResolver(URI endpoint, Map<String, String> dictionaryUris, HttpClient client) {
		this(endpoint, dictionaryUris, client, null);
	}

	BsddClassificationResolver(URI endpoint, Map<String, String> dictionaryUris, RegistryTransport transport) {
		this(endpoint, dictionaryUris, null, transport);
	}

	private BsddClassificationResolver(URI endpoint, Map<String, String> dictionaryUris, HttpClient client,
			RegistryTransport transport) {
		this.endpoint = endpoint;
		this.dictionaryUris = dictionaryUris.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				entry -> normalize(entry.getKey()), Map.Entry::getValue));
		this.client = client;
		this.transport = transport;
	}

	@Override
	public String id() { return "buildingSMART-bsdd"; }
	@Override public String version() { return "v1"; }
	@Override public String configurationId() {
		return id() + "@" + version() + "|" + endpoint + "|" + dictionaryUris.entrySet().stream()
				.sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "=" + e.getValue())
				.reduce("", (a, b) -> a + "|" + b);
	}

	@Override
	public Optional<Resolution> resolve(Request request) {
		if (request.system() == null || request.code() == null) return Optional.empty();
		String dictionaryUri = dictionaryUris.get(normalize(request.system()));
		if (dictionaryUri == null) return Optional.empty();
		String query = "api/Dictionary/v1/Classes?Uri=" + encode(dictionaryUri) + "&SearchText="
				+ encode(request.code()) + "&Limit=100";
		HttpRequest httpRequest = HttpRequest.newBuilder(endpoint.resolve(query)).timeout(Duration.ofSeconds(15))
				.header("Accept", "application/json").header("User-Agent", "IFCtoLBD/2.51.1").GET().build();
		try {
			RegistryResponse response = transport == null
					? fromHttp(client.send(httpRequest, HttpResponse.BodyHandlers.ofString()))
					: transport.get(httpRequest.uri());
			if (response.statusCode() != 200) return Optional.empty();
			JsonNode root = mapper.readTree(response.body());
			JsonNode classes = root.path("classes");
			if (!classes.isArray()) return Optional.empty();
			for (JsonNode candidate : classes) {
				String code = text(candidate, "code", "referenceCode");
				String uri = text(candidate, "uri");
				if (request.code().equalsIgnoreCase(code) && uri != null) {
					return Optional.of(new Resolution(uri, "buildingSMART-bSDD-v1", request.edition(), 1.0));
				}
			}
			return Optional.empty();
		} catch (IOException e) {
			throw new java.io.UncheckedIOException("bSDD lookup failed", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("bSDD lookup interrupted", e);
		}
	}

	private static RegistryResponse fromHttp(HttpResponse<String> response) {
		return new RegistryResponse(response.statusCode(), response.body());
	}

	@FunctionalInterface
	interface RegistryTransport { RegistryResponse get(URI uri) throws IOException, InterruptedException; }
	record RegistryResponse(int statusCode, String body) { }

	private static String text(JsonNode node, String... fields) {
		for (String field : fields) if (node.hasNonNull(field)) return node.get(field).asText();
		return null;
	}
	private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
	private static String normalize(String value) { return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT); }
}
