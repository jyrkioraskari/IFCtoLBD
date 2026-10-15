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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
	@Override public boolean allowsNetworkAccess() { return true; }
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
		String dictionaryEdition = dictionaryEdition(dictionaryUri);
		EditionCompatibility editionCompatibility = editionCompatibility(request.edition(), dictionaryEdition);
		String query = "api/Dictionary/v1/Classes?Uri=" + encode(dictionaryUri) + "&SearchText="
				+ encode(request.code()) + "&Limit=100";
		HttpRequest httpRequest = HttpRequest.newBuilder(endpoint.resolve(query)).timeout(Duration.ofSeconds(30))
				.header("Accept", "application/json").header("User-Agent", "IFCtoLBD/2.55.0").GET().build();
		try {
			RegistryResponse response = send(httpRequest);
			if (response.statusCode() != 200) return Optional.empty();
			JsonNode root = mapper.readTree(response.body());
			JsonNode classes = root.path("classes");
			if (!classes.isArray()) return Optional.empty();
			for (JsonNode candidate : classes) {
				String code = text(candidate, "code", "referenceCode");
				String uri = text(candidate, "uri");
				if (request.code().equalsIgnoreCase(code) && uri != null) {
					return Optional.of(new Resolution(uri, "buildingSMART-bSDD-v1", dictionaryEdition, 1.0,
							IdentifierVerification.REGISTRY_VERIFIED, request.edition(), dictionaryEdition,
							editionCompatibility));
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

	@Override
	public Optional<ClassificationDefinition> definition(Resolution resolution) {
		if (resolution == null || resolution.conceptUri() == null) return Optional.empty();
		String query = "api/Class/v1?Uri=" + encode(resolution.conceptUri())
				+ "&IncludeClassProperties=true&IncludeClassRelations=true";
		return get(query).map(this::definition);
	}

	@Override
	public Optional<DictionaryMetadata> dictionary(String system) {
		if (system == null) return Optional.empty();
		String dictionaryUri = dictionaryUris.get(normalize(system));
		if (dictionaryUri == null) return Optional.empty();
		String query = "api/Dictionary/v1?Uri=" + encode(dictionaryUri);
		return get(query).flatMap(root -> {
			JsonNode dictionaries = array(root, "dictionaries");
			if (dictionaries.isEmpty()) return Optional.empty();
			JsonNode value = dictionaries.get(0);
			String uri = text(value, "uri");
			if (uri == null) return Optional.empty();
			return Optional.of(new DictionaryMetadata(uri, text(value, "code"), text(value, "name"),
					text(value, "version"), text(value, "status"), text(value, "organizationCodeOwner"),
					text(value, "organizationNameOwner"), text(value, "license"), text(value, "licenseUrl")));
		});
	}

	@Override
	public List<Candidate> candidates(CandidateRequest request) {
		if (request == null || request.system() == null || request.ifcEntity() == null) return List.of();
		String dictionaryUri = dictionaryUris.get(normalize(request.system()));
		if (dictionaryUri == null) return List.of();
		Map<String, Candidate> candidates = new LinkedHashMap<>();
		for (String classUri : classUris(dictionaryUri, request.ifcEntity()))
			candidate(classUri, "published-ifc-relationship", null).ifPresent(value ->
					candidates.putIfAbsent(value.resolution().conceptUri(), value));

		String modellingDictionaryUri = dictionaryUris.get("etimmc");
		if (modellingDictionaryUri != null) {
			for (String modellingClassUri : classUris(modellingDictionaryUri, request.ifcEntity())) {
				for (String productClassUri : relatedProductClasses(modellingClassUri, dictionaryUri))
					candidate(productClassUri, "etim-modelling-class-link", modellingClassUri).ifPresent(value ->
							candidates.merge(value.resolution().conceptUri(), value, (direct, linked) -> new Candidate(
									direct.resolution(), direct.definition(),
									"published-ifc-relationship+etim-modelling-class-link", modellingClassUri)));
			}
		}
		return candidates.values().stream().sorted(java.util.Comparator.comparing(
				value -> value.resolution().conceptUri())).toList();
	}

	private List<String> classUris(String dictionaryUri, String ifcEntity) {
		String query = "api/Dictionary/v1/Classes?Uri=" + encode(dictionaryUri) + "&RelatedIfcEntity="
				+ encode(ifcEntity) + "&Limit=1000";
		return get(query).map(root -> {
			List<String> result = new ArrayList<>();
			for (JsonNode node : array(root, "classes")) {
				String uri = text(node, "uri");
				if (uri != null) result.add(uri);
			}
			return result.stream().distinct().sorted().toList();
		}).orElseGet(List::of);
	}

	private Optional<Candidate> candidate(String classUri, String method, String supportingClassUri) {
		String dictionaryEdition = dictionaryEditionForClass(classUri);
		Resolution resolution = new Resolution(classUri, "buildingSMART-bSDD-v1", dictionaryEdition, 1.0,
				IdentifierVerification.REGISTRY_VERIFIED, null, dictionaryEdition,
				EditionCompatibility.SOURCE_UNSPECIFIED);
		return definition(resolution).map(value -> new Candidate(resolution, value, method, supportingClassUri));
	}

	private List<String> relatedProductClasses(String modellingClassUri, String productDictionaryUri) {
		java.util.Set<String> result = new java.util.TreeSet<>();
		String modellingEdition = dictionaryEditionForClass(modellingClassUri);
		definition(new Resolution(modellingClassUri, "buildingSMART-bSDD-v1", modellingEdition, 1.0,
				IdentifierVerification.REGISTRY_VERIFIED, null, modellingEdition,
				EditionCompatibility.SOURCE_UNSPECIFIED))
				.ifPresent(value -> value.relationships().stream().map(ClassificationDefinition.Relationship::relatedClassUri)
						.filter(uri -> belongsTo(uri, productDictionaryUri)).forEach(result::add));
		String reverseQuery = "api/Class/Relations/v1?ClassUri=" + encode(modellingClassUri)
				+ "&GetReverseRelations=true&Limit=1000";
		get(reverseQuery).ifPresent(root -> {
			for (JsonNode relation : array(root, "classRelations")) {
				String uri = text(relation, "classUri", "relatedClassUri");
				if (belongsTo(uri, productDictionaryUri)) result.add(uri);
			}
		});
		return List.copyOf(result);
	}

	private static boolean belongsTo(String classUri, String dictionaryUri) {
		if (classUri == null || dictionaryUri == null) return false;
		String base = dictionaryUri.endsWith("/") ? dictionaryUri.substring(0, dictionaryUri.length() - 1)
				: dictionaryUri;
		return classUri.startsWith(base + "/class/");
	}

	private Optional<JsonNode> get(String query) {
		HttpRequest request = HttpRequest.newBuilder(endpoint.resolve(query)).timeout(Duration.ofSeconds(30))
				.header("Accept", "application/json").header("User-Agent", "IFCtoLBD/2.55.0").GET().build();
		try {
			RegistryResponse response = send(request);
			return response.statusCode() == 200 ? Optional.of(mapper.readTree(response.body())) : Optional.empty();
		} catch (IOException e) {
			throw new java.io.UncheckedIOException("bSDD lookup failed", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("bSDD lookup interrupted", e);
		}
	}

	private RegistryResponse send(HttpRequest request) throws IOException, InterruptedException {
		RegistryResponse response = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			response = transport == null
					? fromHttp(client.send(request, HttpResponse.BodyHandlers.ofString()))
					: transport.get(request.uri());
			if (response.statusCode() != 429 && response.statusCode() != 503) return response;
			if (attempt < 2) Thread.sleep((attempt + 1L) * 1000L);
		}
		return response;
	}

	private ClassificationDefinition definition(JsonNode root) {
		List<ClassificationDefinition.ClassProperty> classProperties = new ArrayList<>();
		for (JsonNode node : array(root, "classProperties")) classProperties.add(classProperty(node));
		List<ClassificationDefinition.Relationship> relationships = new ArrayList<>();
		for (JsonNode node : array(root, "classRelations")) relationships.add(new ClassificationDefinition.Relationship(
				text(node, "uri"), text(node, "relationType"), text(node, "relatedClassUri"),
				text(node, "relatedClassName")));
		List<String> ifcEntities = new ArrayList<>();
		for (JsonNode node : array(root, "relatedIfcEntityNames")) if (node.isTextual()) ifcEntities.add(node.asText());
		return new ClassificationDefinition(text(root, "uri"), text(root, "dictionaryUri"),
				text(root, "code", "referenceCode"), text(root, "name"), text(root, "description", "definition"),
				ifcEntities, classProperties, relationships);
	}

	private ClassificationDefinition.ClassProperty classProperty(JsonNode node) {
		String propertyUri = text(node, "propertyUri");
		ClassificationDefinition.Property property = new ClassificationDefinition.Property(propertyUri,
				text(node, "propertyCode"), text(node, "name"), text(node, "description", "definition"));
		List<ClassificationDefinition.AllowedValue> values = new ArrayList<>();
		for (JsonNode value : array(node, "allowedValues")) values.add(new ClassificationDefinition.AllowedValue(
				text(value, "uri"), text(value, "code"), text(value, "value"), text(value, "description")));
		List<ClassificationDefinition.Unit> units = new ArrayList<>();
		JsonNode unitCodes = array(node, "units");
		JsonNode qudtCodes = array(node, "qudtCodes");
		int count = Math.max(unitCodes.size(), qudtCodes.size());
		for (int i = 0; i < count; i++) units.add(new ClassificationDefinition.Unit(
				i < unitCodes.size() ? unitCodes.get(i).asText() : null,
				i < qudtCodes.size() ? qudtCodes.get(i).asText() : null));
		return new ClassificationDefinition.ClassProperty(text(node, "uri"), property,
				text(node, "propertySet"), text(node, "dataType"), text(node, "description", "definition"),
				text(node, "predefinedValue"), bool(node, "isRequired"), bool(node, "isWritable"), values, units);
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
	private static JsonNode array(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isArray() ? value : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
	}
	private static Boolean bool(JsonNode node, String field) {
		return node.hasNonNull(field) ? node.get(field).asBoolean() : null;
	}
	private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
	private static String normalize(String value) { return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT); }

	private String dictionaryEditionForClass(String classUri) {
		return dictionaryUris.values().stream().filter(uri -> belongsTo(classUri, uri))
				.map(BsddClassificationResolver::dictionaryEdition).findFirst().orElse(null);
	}

	static EditionCompatibility editionCompatibility(String sourceEdition, String dictionaryEdition) {
		String source = versionToken(sourceEdition);
		String configured = versionToken(dictionaryEdition);
		if (source == null) return EditionCompatibility.SOURCE_UNSPECIFIED;
		if (configured == null) return EditionCompatibility.CONFIGURED_UNSPECIFIED;
		return source.equals(configured) ? EditionCompatibility.MATCH : EditionCompatibility.CONFLICT;
	}

	static String dictionaryEdition(String dictionaryUri) {
		if (dictionaryUri == null) return null;
		String path = URI.create(dictionaryUri).getPath();
		if (path == null) return null;
		String[] segments = path.split("/");
		for (int i = segments.length - 1; i >= 0; i--) {
			String token = versionToken(segments[i]);
			if (token != null) return segments[i];
		}
		return null;
	}

	private static String versionToken(String value) {
		if (value == null || value.isBlank()) return null;
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)(?:^|[^0-9])v?(\\d+(?:\\.\\d+)*)(?:$|[^0-9])")
				.matcher(value);
		String token = null;
		while (matcher.find()) token = matcher.group(1);
		if (token == null) return null;
		String[] parts = token.split("\\.");
		int length = parts.length;
		while (length > 1 && Integer.parseInt(parts[length - 1]) == 0) length--;
		return java.util.stream.IntStream.range(0, length).mapToObj(i -> Integer.toString(Integer.parseInt(parts[i])))
				.collect(java.util.stream.Collectors.joining("."));
	}
}
