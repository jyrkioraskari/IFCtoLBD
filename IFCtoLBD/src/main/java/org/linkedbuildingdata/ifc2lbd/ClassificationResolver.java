package org.linkedbuildingdata.ifc2lbd;

import java.util.Optional;

/** Resolves an IFC classification code to a registry-controlled concept URI. */
@FunctionalInterface
public interface ClassificationResolver {
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

	static ClassificationResolver none() { return request -> Optional.empty(); }
}
