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
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.tdb2.sys.TDBInternal;
import com.google.common.eventbus.EventBus;

/**
 * Isolated working state for one IFC conversion lifecycle.
 *
 * <p>Each session owns its staging dataset and temporary directory. A session must
 * not be shared by conversions running concurrently.</p>
 */
public final class ConversionSession implements AutoCloseable {
	/** Staging-store trade-off for a conversion session. */
	public enum Storage {
		/** Faster for ordinary conversions, but bounded by the JVM heap. */
		MEMORY,
		/** Disk-backed storage for models that do not comfortably fit in memory. */
		TDB2
	}
	private static final int DELETE_ATTEMPTS = 20;
	private static final long DELETE_RETRY_DELAY_MILLIS = 50;

	private final Path workingDirectory;
	private final Dataset dataset;
	private final Clock clock;
	private final UriPolicy uriPolicy;
	private final GeometryProvider geometryProvider;
	private final GeometryArtifactStore geometryArtifactStore;
	private final Storage storage;
	private final EventBus eventBus = new EventBus();
	private boolean closed;

	public ConversionSession() {
		this(createWorkingDirectory(), Clock.systemUTC(), LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE, Storage.MEMORY);
	}

	public ConversionSession(Storage storage) {
		this(createWorkingDirectory(), Clock.systemUTC(), LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE, storage);
	}

	ConversionSession(Path workingDirectory) {
		this(workingDirectory, Clock.systemUTC(), LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE, Storage.TDB2);
	}

	public ConversionSession(Clock clock) {
		this(createWorkingDirectory(), clock, LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE, Storage.MEMORY);
	}

	ConversionSession(Path workingDirectory, Clock clock) {
		this(workingDirectory, clock, LegacyUriPolicy.INSTANCE, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE, Storage.TDB2);
	}

	public ConversionSession(UriPolicy uriPolicy) {
		this(createWorkingDirectory(), Clock.systemUTC(), uriPolicy, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE, Storage.MEMORY);
	}

	public ConversionSession(Clock clock, UriPolicy uriPolicy) {
		this(createWorkingDirectory(), clock, uriPolicy, IfcOpenShellGeometryProvider.INSTANCE,
				NoGeometryArtifactStore.INSTANCE, Storage.MEMORY);
	}

	public ConversionSession(GeometryProvider geometryProvider) {
		this(createWorkingDirectory(), Clock.systemUTC(), LegacyUriPolicy.INSTANCE, geometryProvider,
				NoGeometryArtifactStore.INSTANCE, Storage.MEMORY);
	}

	public ConversionSession(GeometryArtifactStore geometryArtifactStore) {
		this(createWorkingDirectory(), Clock.systemUTC(), LegacyUriPolicy.INSTANCE,
				IfcOpenShellGeometryProvider.INSTANCE, geometryArtifactStore, Storage.MEMORY);
	}

	public ConversionSession(Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider) {
		this(createWorkingDirectory(), clock, uriPolicy, geometryProvider, NoGeometryArtifactStore.INSTANCE, Storage.MEMORY);
	}

	public ConversionSession(Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider,
			GeometryArtifactStore geometryArtifactStore) {
		this(createWorkingDirectory(), clock, uriPolicy, geometryProvider, geometryArtifactStore, Storage.MEMORY);
	}

	public ConversionSession(Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider,
			GeometryArtifactStore geometryArtifactStore, Storage storage) {
		this(createWorkingDirectory(), clock, uriPolicy, geometryProvider, geometryArtifactStore, storage);
	}

	ConversionSession(Path workingDirectory, Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider,
			GeometryArtifactStore geometryArtifactStore) {
		this(workingDirectory, clock, uriPolicy, geometryProvider, geometryArtifactStore, Storage.TDB2);
	}

	private ConversionSession(Path workingDirectory, Clock clock, UriPolicy uriPolicy, GeometryProvider geometryProvider,
			GeometryArtifactStore geometryArtifactStore, Storage storage) {
		this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath();
		this.clock = Objects.requireNonNull(clock, "clock");
		this.uriPolicy = Objects.requireNonNull(uriPolicy, "uriPolicy");
		this.geometryProvider = Objects.requireNonNull(geometryProvider, "geometryProvider");
		this.geometryArtifactStore = Objects.requireNonNull(geometryArtifactStore, "geometryArtifactStore");
		this.storage = Objects.requireNonNull(storage, "storage");
		this.dataset = storage == Storage.TDB2
				? TDB2Factory.connectDataset(this.workingDirectory.toString())
				: DatasetFactory.createTxnMem();
	}

	public UriPolicy getUriPolicy() {
		return uriPolicy;
	}

	public GeometryProvider getGeometryProvider() { return geometryProvider; }
	public GeometryArtifactStore getGeometryArtifactStore() { return geometryArtifactStore; }
	public EventBus getEventBus() { return eventBus; }

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

	public Storage getStorage() {
		return storage;
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
		if (storage == Storage.TDB2) TDBInternal.expel(datasetGraph, true);
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
