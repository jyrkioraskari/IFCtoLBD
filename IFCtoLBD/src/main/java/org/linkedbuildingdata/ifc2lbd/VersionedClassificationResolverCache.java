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
import java.util.Optional;
import java.util.Properties;

/** Persistent, versioned cache decorator for authoritative registry lookups. */
public final class VersionedClassificationResolverCache implements ClassificationResolver {
	private final Path cacheFile;
	private final String cacheVersion;
	private final ClassificationResolver delegate;
	private final Properties entries = new Properties();

	public VersionedClassificationResolverCache(Path cacheDirectory, String cacheVersion,
			ClassificationResolver delegate) {
		this.cacheVersion = requireText(cacheVersion, "cacheVersion");
		this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
		this.cacheFile = java.util.Objects.requireNonNull(cacheDirectory, "cacheDirectory")
				.resolve("classification-resolutions-" + safeFilename(cacheVersion) + ".properties");
		load();
	}

	@Override
	public String id() { return "versioned-cache"; }
	@Override public String version() { return cacheVersion; }
	@Override public String configurationId() { return id() + "@" + version() + "|" + delegate.configurationId(); }

	@Override
	public synchronized Optional<Resolution> resolve(Request request) {
		String key = key(request);
		String cached = entries.getProperty(key);
		if (cached != null) return Optional.of(decode(cached));
		Optional<Resolution> resolved = delegate.resolve(request);
		resolved.ifPresent(value -> {
			entries.setProperty(key, encode(value));
			store();
		});
		return resolved;
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
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	private static String encode(Resolution value) {
		return encodeText(value.conceptUri()) + ":" + encodeText(value.authority()) + ":"
				+ encodeText(value.authorityVersion()) + ":" + value.confidence();
	}
	private static Resolution decode(String value) {
		String[] fields = value.split(":", -1);
		if (fields.length != 4) throw new IllegalStateException("Invalid resolver cache entry");
		return new Resolution(decodeText(fields[0]), decodeText(fields[1]), decodeText(fields[2]), Double.parseDouble(fields[3]));
	}
	private static String encodeText(String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(safe(value).getBytes(StandardCharsets.UTF_8));
	}
	private static String decodeText(String value) {
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}
	private static String safe(String value) { return value == null ? "" : value; }
	private static String safeFilename(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return value;
	}
}
