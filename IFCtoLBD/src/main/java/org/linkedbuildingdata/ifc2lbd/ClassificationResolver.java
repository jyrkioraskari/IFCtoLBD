package org.linkedbuildingdata.ifc2lbd;

import java.util.Optional;

/** Resolves an IFC classification code to a registry-controlled concept URI. */
@FunctionalInterface
public interface ClassificationResolver {
	default String id() { return getClass().getName(); }
	default String version() { return "unspecified"; }
	default String configurationId() { return id() + "@" + version(); }
	record Request(String system, String edition, String code, String label, String sourceLocation) { }
	record Resolution(String conceptUri, String authority, String authorityVersion, double confidence) {
		public Resolution {
			if (conceptUri == null || !conceptUri.startsWith("http"))
				throw new IllegalArgumentException("An authoritative concept URI must be HTTP(S)");
			if (confidence < 0 || confidence > 1)
				throw new IllegalArgumentException("confidence must be between 0 and 1");
		}
	}

	Optional<Resolution> resolve(Request request);

	static ClassificationResolver none() { return NoneHolder.INSTANCE; }
	final class NoneHolder {
		private static final ClassificationResolver INSTANCE = new ClassificationResolver() {
			@Override public String id() { return "none"; }
			@Override public String version() { return "1"; }
			@Override public Optional<Resolution> resolve(Request request) { return Optional.empty(); }
		};
		private NoneHolder() { }
	}
}
