package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Persistent, versioned cache decorator for authoritative registry lookups. */
public final class VersionedClassificationResolverCache implements ClassificationResolver {
	public enum AccessMode {
		CACHE_ONLY,
		/** @deprecated Refresh explicitly before conversion. */
		@Deprecated(forRemoval = true) CACHE_THEN_DELEGATE
	}
	private final Path cacheFile;
	private final String cacheVersion;
	private final ClassificationResolver delegate;
	private final Properties entries = new Properties();
	private final ObjectMapper mapper = new ObjectMapper();

	/** Opens an offline-only cache without constructing a registry client. */
	public VersionedClassificationResolverCache(Path cacheDirectory, String cacheVersion) {
		this(cacheDirectory, cacheVersion, ClassificationResolver.none(), AccessMode.CACHE_ONLY);
	}

	public VersionedClassificationResolverCache(Path cacheDirectory, String cacheVersion,
			ClassificationResolver delegate) {
		this(cacheDirectory, cacheVersion, delegate, AccessMode.CACHE_ONLY);
	}

	public VersionedClassificationResolverCache(Path cacheDirectory, String cacheVersion,
			ClassificationResolver delegate, AccessMode accessMode) {
		if (java.util.Objects.requireNonNull(accessMode, "accessMode") != AccessMode.CACHE_ONLY)
			throw new IllegalArgumentException("Network fallback during conversion is disabled; use refresh() or "
					+ "refreshCandidates() before conversion");
		this.cacheVersion = requireText(cacheVersion, "cacheVersion");
		this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
		this.cacheFile = java.util.Objects.requireNonNull(cacheDirectory, "cacheDirectory")
				.resolve("classification-resolutions-" + safeFilename(cacheVersion) + ".properties");
		load();
	}

	@Override
	public String id() { return "versioned-cache"; }
	@Override public String version() { return cacheVersion; }
	@Override public boolean allowsNetworkAccess() { return false; }
	@Override public String configurationId() {
		return id() + "@" + version() + "|CACHE_ONLY|" + delegate.configurationId();
	}

	@Override
	public synchronized Optional<Resolution> resolve(Request request) {
		// Keep the original key format so existing versioned caches remain usable.
		String key = key(request);
		String cached = entries.getProperty(key);
		if (cached == null) cached = entries.getProperty(resolutionKey(request));
		if (cached != null) return Optional.of(decode(cached));
		return Optional.empty();
	}

	@Override
	public synchronized Optional<ClassificationDefinition> definition(Resolution resolution) {
		String key = "definition." + digest(resolution.conceptUri());
		String cached = entries.getProperty(key);
		if (cached != null) return Optional.of(decodeDefinition(cached));
		return Optional.empty();
	}

	@Override
	public synchronized List<Candidate> candidates(CandidateRequest request) {
		String key = "candidates." + digest(safe(request.system()) + "\u0000" + safe(request.ifcEntity()));
		String cached = entries.getProperty(key);
		if (cached != null) return decodeCandidates(cached);
		return List.of();
	}

	@Override
	public synchronized Optional<DictionaryMetadata> dictionary(String system) {
		String cached = entries.getProperty("dictionary.system." + digest(normalize(system)));
		return cached == null ? Optional.empty() : Optional.of(decodeDictionary(cached));
	}

	@Override
	public synchronized List<DictionaryMetadata> dictionaries() {
		return entries.stringPropertyNames().stream().filter(key -> key.startsWith("dictionary.uri."))
				.sorted().map(entries::getProperty).map(this::decodeDictionary).toList();
	}

	/**
	 * Explicitly refreshes one resolution and its definition. Call this before
	 * constructing a conversion request; normal resolver methods never delegate.
	 */
	public synchronized Optional<Resolution> refresh(Request request) {
		ensureDictionaryEntry(request.system());
		Optional<Resolution> resolved = delegate.resolve(request);
		if (resolved.isEmpty()) { store(); return Optional.empty(); }
		Resolution value = resolved.orElseThrow();
		entries.setProperty(key(request), encode(value));
		entries.setProperty(resolutionKey(request), encode(value));
		delegate.definition(value).ifPresent(definition -> entries.setProperty(
				"definition." + digest(value.conceptUri()), encodeDefinition(definition)));
		store();
		return resolved;
	}

	/** Explicitly refreshes candidates and their embedded bSDD definitions. */
	public synchronized List<Candidate> refreshCandidates(CandidateRequest request) {
		ensureDictionaryEntry(request.system());
		List<Candidate> candidates = List.copyOf(delegate.candidates(request));
		entries.setProperty("candidates." + digest(safe(request.system()) + "\u0000" + safe(request.ifcEntity())),
				encodeJson(candidates));
		for (Candidate candidate : candidates) {
			ClassificationDefinition definition = candidate.definition();
			if (definition != null && definition.uri() != null)
				entries.setProperty("definition." + digest(definition.uri()), encodeDefinition(definition));
		}
		store();
		return candidates;
	}

	/** Explicitly loads and caches registry-supplied dictionary rights metadata. */
	public synchronized Optional<DictionaryMetadata> refreshDictionary(String system) {
		Optional<DictionaryMetadata> metadata = refreshDictionaryEntry(system);
		store();
		return metadata;
	}

	private Optional<DictionaryMetadata> refreshDictionaryEntry(String system) {
		Optional<DictionaryMetadata> metadata = delegate.dictionary(system);
		metadata.ifPresent(value -> {
			String encoded = encodeJson(value);
			entries.setProperty("dictionary.system." + digest(normalize(system)), encoded);
			entries.setProperty("dictionary.uri." + digest(value.uri()), encoded);
		});
		return metadata;
	}

	private Optional<DictionaryMetadata> ensureDictionaryEntry(String system) {
		Optional<DictionaryMetadata> cached = dictionary(system);
		return cached.isPresent() ? cached : refreshDictionaryEntry(system);
	}

	private void load() {
		if (!Files.exists(cacheFile)) return;
		try (InputStream input = Files.newInputStream(cacheFile)) {
			entries.load(input);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read resolver cache " + cacheFile, e);
		}
	}

	private void store() {
		try {
			Files.createDirectories(cacheFile.toAbsolutePath().getParent());
			Path temporary = Files.createTempFile(cacheFile.toAbsolutePath().getParent(), "resolver-cache-", ".tmp");
			try (OutputStream output = Files.newOutputStream(temporary)) {
				entries.store(output, "IFCtoLBD classification resolver cache " + cacheVersion);
			}
			try {
				Files.move(temporary, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write resolver cache " + cacheFile, e);
		}
	}

	private static String key(Request request) {
		String text = String.join("\u0000", safe(request.system()), safe(request.edition()), safe(request.code()),
				safe(request.label()), safe(request.sourceLocation()));
		return digest(text);
	}
	private static String resolutionKey(Request request) {
		return "resolution." + digest(String.join("\u0000", normalize(request.system()), safe(request.edition()),
				safe(request.code()).toUpperCase(java.util.Locale.ROOT)));
	}
	private static String digest(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
	private String encodeDefinition(ClassificationDefinition value) {
		return encodeJson(value);
	}
	private String encodeJson(Object value) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(value));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not encode classification definition", e);
		}
	}
	private List<Candidate> decodeCandidates(String value) {
		try {
			return mapper.readValue(Base64.getUrlDecoder().decode(value), new TypeReference<List<Candidate>>() { });
		} catch (IOException | IllegalArgumentException e) {
			throw new IllegalStateException("Invalid candidate cache entry", e);
		}
	}
	private ClassificationDefinition decodeDefinition(String value) {
		try {
			return mapper.readValue(Base64.getUrlDecoder().decode(value), ClassificationDefinition.class);
		} catch (IOException | IllegalArgumentException e) {
			throw new IllegalStateException("Invalid definition cache entry", e);
		}
	}
	private DictionaryMetadata decodeDictionary(String value) {
		try {
			return mapper.readValue(Base64.getUrlDecoder().decode(value), DictionaryMetadata.class);
		} catch (IOException | IllegalArgumentException e) {
			throw new IllegalStateException("Invalid dictionary cache entry", e);
		}
	}

	private static String encode(Resolution value) {
		return encodeText(value.conceptUri()) + ":" + encodeText(value.authority()) + ":"
				+ encodeText(value.authorityVersion()) + ":" + value.confidence() + ":"
				+ value.verification().name() + ":" + encodeText(value.sourceEdition()) + ":"
				+ encodeText(value.dictionaryEdition()) + ":" + value.editionCompatibility().name();
	}
	private static Resolution decode(String value) {
		String[] fields = value.split(":", -1);
		if (fields.length == 4)
			return new Resolution(decodeText(fields[0]), decodeText(fields[1]), decodeText(fields[2]),
					Double.parseDouble(fields[3]));
		if (fields.length != 8) throw new IllegalStateException("Invalid resolver cache entry");
		return new Resolution(decodeText(fields[0]), decodeText(fields[1]), emptyToNull(decodeText(fields[2])),
				Double.parseDouble(fields[3]), IdentifierVerification.valueOf(fields[4]),
				emptyToNull(decodeText(fields[5])), emptyToNull(decodeText(fields[6])),
				EditionCompatibility.valueOf(fields[7]));
	}
	private static String encodeText(String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(safe(value).getBytes(StandardCharsets.UTF_8));
	}
	private static String decodeText(String value) {
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}
	private static String safe(String value) { return value == null ? "" : value; }
	private static String normalize(String value) { return safe(value).replaceAll("[^A-Za-z0-9]", "")
			.toLowerCase(java.util.Locale.ROOT); }
	private static String emptyToNull(String value) { return value.isEmpty() ? null : value; }
	private static String safeFilename(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return value;
	}
}
