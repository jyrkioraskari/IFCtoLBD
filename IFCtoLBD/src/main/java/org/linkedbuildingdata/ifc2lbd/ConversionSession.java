package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.tdb2.sys.TDBInternal;

/**
 * Isolated working state for one IFC conversion lifecycle.
 *
 * <p>Each session owns its temporary TDB2 dataset and directory. A session must
 * not be shared by conversions running concurrently.</p>
 */
public final class ConversionSession implements AutoCloseable {
	private static final int DELETE_ATTEMPTS = 20;
	private static final long DELETE_RETRY_DELAY_MILLIS = 50;

	private final Path workingDirectory;
	private final Dataset dataset;
	private final Clock clock;
	private final UriPolicy uriPolicy;
	private final GeometryProvider geometryProvider;
	private final GeometryArtifactStore geometryArtifactStore;
	private boolean closed;

	public ConversionSession() {
		this(createWorkingDirectory(), Clock.systemUTC(), LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE);
	}

	ConversionSession(Path workingDirectory) {
		this(workingDirectory, Clock.systemUTC(), LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE);
	}

	public ConversionSession(Clock clock) {
		this(createWorkingDirectory(), clock, LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE);
	}

	ConversionSession(Path workingDirectory, Clock clock) {
		this(workingDirectory, clock, LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE);
	}

	public ConversionSession(UriPolicy uriPolicy) {
		this(createWorkingDirectory(), Clock.systemUTC(), uriPolicy, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE);
	}

	public ConversionSession(Clock clock, UriPolicy uriPolicy) {
		this(createWorkingDirectory(), clock, uriPolicy, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE);
	}

	public ConversionSession(GeometryProvider geometryProvider) {
		this(createWorkingDirectory(), Clock.systemUTC(), LegacyUriPolicy.INSTANCE, geometryProvider,
				NoGeometryArtifactStore.INSTANCE);
	}

	public ConversionSession(GeometryArtifactStore geometryArtifactStore) {
		this(createWorkingDirectory(), Clock.systemUTC(), LegacyUriPolicy.INSTANCE,
				IfcOpenShellGeometryProvider.INSTANCE, geometryArtifactStore);
	}

	public ConversionSession(Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider) {
		this(createWorkingDirectory(), clock, uriPolicy, geometryProvider, NoGeometryArtifactStore.INSTANCE);
	}

	public ConversionSession(Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider,
			GeometryArtifactStore geometryArtifactStore) {
		this(createWorkingDirectory(), clock, uriPolicy, geometryProvider, geometryArtifactStore);
	}

	ConversionSession(Path workingDirectory, Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider,
			GeometryArtifactStore geometryArtifactStore) {
		this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath();
		this.clock = Objects.requireNonNull(clock, "clock");
		this.uriPolicy = Objects.requireNonNull(uriPolicy, "uriPolicy");
		this.geometryProvider = Objects.requireNonNull(geometryProvider, "geometryProvider");
		this.geometryArtifactStore = Objects.requireNonNull(geometryArtifactStore, "geometryArtifactStore");
		this.dataset = TDB2Factory.connectDataset(this.workingDirectory.toString());
	}

	public UriPolicy getUriPolicy() {
		return uriPolicy;
	}

	public GeometryProvider getGeometryProvider() { return geometryProvider; }
	public GeometryArtifactStore getGeometryArtifactStore() { return geometryArtifactStore; }

	Instant now() {
		return clock.instant();
	}

	public synchronized Dataset getDataset() {
		if (closed) {
			throw new IllegalStateException("Conversion session is closed");
		}
		return dataset;
	}

	public Path getWorkingDirectory() {
		return workingDirectory;
	}

	public synchronized boolean isClosed() {
		return closed;
	}

	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		var datasetGraph = dataset.asDatasetGraph();
		geometryProvider.close();
		geometryArtifactStore.close();
		dataset.close();
		// A disk-backed TDB2 dataset is held in Jena's process-wide connection
		// cache. DatasetGraphSwitchable.close() is intentionally a no-op, so merely
		// closing the Dataset leaves its memory-mapped index files open on Windows.
		// Expelling the graph closes the backing store and removes both cached
		// connections before we delete this session's temporary directory.
		TDBInternal.expel(datasetGraph, true);
		deleteWorkingDirectory();
	}

	private static Path createWorkingDirectory() {
		try {
			return Files.createTempDirectory("IFCtoLBD_");
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create a conversion working directory", e);
		}
	}

	private void deleteWorkingDirectory() {
		if (!Files.exists(workingDirectory)) {
			return;
		}
		List<Path> paths;
		try (var pathStream = Files.walk(workingDirectory)) {
			// Materialize the traversal before deleting. This closes all directory
			// handles opened by Files.walk, which matters on Windows.
			paths = pathStream.sorted(Comparator.reverseOrder()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not delete conversion working directory " + workingDirectory, e);
		}

		List<Path> remaining = new ArrayList<>(paths);
		IOException lastFailure = null;
		for (int attempt = 1; attempt <= DELETE_ATTEMPTS && !remaining.isEmpty(); attempt++) {
			for (var iterator = remaining.iterator(); iterator.hasNext();) {
				Path path = iterator.next();
				try {
					Files.deleteIfExists(path);
					iterator.remove();
				} catch (IOException e) {
					lastFailure = e;
				}
			}
			if (!remaining.isEmpty() && attempt < DELETE_ATTEMPTS) {
				try {
					Thread.sleep(DELETE_RETRY_DELAY_MILLIS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new UncheckedIOException("Interrupted while deleting conversion working directory "
							+ workingDirectory, new IOException(e));
				}
			}
		}
		if (!remaining.isEmpty()) {
			throw new UncheckedIOException("Could not delete conversion working directory " + workingDirectory
					+ "; remaining path: " + remaining.get(0), lastFailure);
		}
	}
}
