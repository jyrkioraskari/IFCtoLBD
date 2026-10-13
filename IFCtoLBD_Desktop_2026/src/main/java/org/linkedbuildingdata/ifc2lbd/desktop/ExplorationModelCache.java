/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.linkedbuildingdata.ifc2lbd.desktop;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import org.apache.jena.rdf.model.Model;

/** Owns one read-only exploration model and keeps it alive until all users release it. */
final class ExplorationModelCache implements AutoCloseable {
	private final Function<File, Model> loader;
	private Snapshot current;
	private long generation;
	private boolean closed;

	ExplorationModelCache(Function<File, Model> loader) {
		this.loader = Objects.requireNonNull(loader);
	}

	Lease acquire(File file, long revision) {
		Key key = new Key(file.getAbsolutePath(), inputStamps(file), revision);
		long loadingGeneration;
		synchronized (this) {
			checkOpen();
			if (current != null && current.key.equals(key)) {
				current.users++;
				return new Lease(current);
			}
			loadingGeneration = generation;
		}
		Model loaded = loader.apply(file);
		synchronized (this) {
			if (closed || generation != loadingGeneration || !inputStamps(file).equals(key.inputs())) {
				loaded.close();
				throw new IllegalStateException("RDF output changed while loading; run the query again.");
			}
			if (current != null && current.key.equals(key)) {
				loaded.close();
				current.users++;
				return new Lease(current);
			}
			retireCurrent();
			current = new Snapshot(key, loaded);
			current.users++;
			return new Lease(current);
		}
	}

	synchronized void invalidate() {
		generation++;
		retireCurrent();
	}

	@Override
	public synchronized void close() {
		closed = true;
		invalidate();
	}

	private void checkOpen() {
		if (closed) throw new IllegalStateException("Exploration model cache is closed.");
	}

	private void retireCurrent() {
		if (current == null) return;
		current.retired = true;
		if (current.users == 0) current.model.close();
		current = null;
	}

	private static List<FileStamp> inputStamps(File file) {
		List<FileStamp> stamps = new ArrayList<>();
		stamps.add(stamp(file));
		String path = file.getAbsolutePath();
		int separator = Math.max(path.lastIndexOf(File.separatorChar), path.lastIndexOf('/'));
		int dot = path.lastIndexOf('.');
		String stem = dot > separator ? path.substring(0, dot) : path;
		String extension = path.toLowerCase(Locale.ROOT).endsWith(".ttl") ? ".ttl" : ".json";
		for (String suffix : List.of("_building_elements", "_element_properties")) {
			stamps.add(stamp(new File(stem + suffix + extension)));
		}
		return List.copyOf(stamps);
	}

	private static FileStamp stamp(File file) {
		return new FileStamp(file.getAbsolutePath(), file.isFile(), file.length(), file.lastModified());
	}

	private record FileStamp(String path, boolean exists, long length, long modified) {
	}

	private record Key(String path, List<FileStamp> inputs, long revision) {
	}

	private static final class Snapshot {
		private final Key key;
		private final Model model;
		private int users;
		private boolean retired;

		private Snapshot(Key key, Model model) {
			this.key = key;
			this.model = model;
		}
	}

	final class Lease implements AutoCloseable {
		private Snapshot snapshot;

		private Lease(Snapshot snapshot) {
			this.snapshot = snapshot;
		}

		Model model() {
			if (snapshot == null) throw new IllegalStateException("Model lease is closed.");
			return snapshot.model;
		}

		@Override
		public void close() {
			synchronized (ExplorationModelCache.this) {
				if (snapshot == null) return;
				snapshot.users--;
				if (snapshot.retired && snapshot.users == 0) snapshot.model.close();
				snapshot = null;
			}
		}
	}
}
