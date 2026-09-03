package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Writes immutable artifacts as {@code sha256.extension}, deduplicating by content. */
public final class FileSystemGeometryArtifactStore implements GeometryArtifactStore {
	private final Path directory;
	private final URI publicBaseUri;

	public FileSystemGeometryArtifactStore(Path directory, URI publicBaseUri) {
		this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath();
		String base = Objects.requireNonNull(publicBaseUri, "publicBaseUri").toString();
		this.publicBaseUri = URI.create(base.endsWith("/") ? base : base + "/");
	}

	@Override public String id() { return "filesystem-content-addressed-v1"; }

	@Override public synchronized Optional<GeometryArtifact> store(byte[] content, String mediaType, String extension,
			String levelOfDetail, String coordinateReferenceSystem) {
		Objects.requireNonNull(content, "content");
		String checksum = sha256(content);
		String safeExtension = extension.replaceFirst("^\\.", "");
		if (!safeExtension.matches("[a-zA-Z0-9]+")) throw new IllegalArgumentException("Invalid extension: " + extension);
		Path artifact = directory.resolve(checksum + "." + safeExtension);
		try {
			Files.createDirectories(directory);
			if (!Files.exists(artifact)) Files.write(artifact, content, StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not store geometry artifact " + artifact, e);
		}
		return Optional.of(new GeometryArtifact(publicBaseUri.resolve(artifact.getFileName().toString()), checksum,
				mediaType, levelOfDetail, coordinateReferenceSystem));
	}

	private static String sha256(byte[] content) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is not available", e); }
	}
}
