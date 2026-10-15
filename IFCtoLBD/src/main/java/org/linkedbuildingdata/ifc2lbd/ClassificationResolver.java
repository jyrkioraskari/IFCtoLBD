package org.linkedbuildingdata.ifc2lbd;

import java.util.List;
import java.util.Optional;

/** Resolves an IFC classification code to a registry-controlled concept URI. */
@FunctionalInterface
public interface ClassificationResolver {
	default String id() { return getClass().getName(); }
	default String version() { return "unspecified"; }
	default String configurationId() { return id() + "@" + version(); }
	/**
	 * True unless the implementation explicitly guarantees that all lookup methods
	 * are offline. Conversion rejects resolvers that return {@code true}.
	 */
	default boolean allowsNetworkAccess() { return true; }
	record Request(String system, String edition, String code, String label, String sourceLocation) { }
	enum IdentifierVerification { REGISTRY_VERIFIED, SUPPLIED_UNVERIFIED }
	enum EditionCompatibility { MATCH, CONFLICT, SOURCE_UNSPECIFIED, CONFIGURED_UNSPECIFIED }
	record Resolution(String conceptUri, String authority, String authorityVersion, double confidence,
			IdentifierVerification verification, String sourceEdition, String dictionaryEdition,
			EditionCompatibility editionCompatibility) {
		public Resolution(String conceptUri, String authority, String authorityVersion, double confidence) {
			this(conceptUri, authority, authorityVersion, confidence, IdentifierVerification.REGISTRY_VERIFIED,
					null, authorityVersion, EditionCompatibility.SOURCE_UNSPECIFIED);
		}
		public Resolution {
			if (conceptUri == null || !conceptUri.startsWith("http"))
				throw new IllegalArgumentException("An authoritative concept URI must be HTTP(S)");
			if (confidence < 0 || confidence > 1)
				throw new IllegalArgumentException("confidence must be between 0 and 1");
			java.util.Objects.requireNonNull(verification, "verification");
			java.util.Objects.requireNonNull(editionCompatibility, "editionCompatibility");
		}
		/** Certainty that the supplied code or URI identifies this registry identifier. */
		@com.fasterxml.jackson.annotation.JsonIgnore
		public double identifierResolutionConfidence() { return confidence; }
		@com.fasterxml.jackson.annotation.JsonIgnore
		public boolean isRegistryVerified() { return verification == IdentifierVerification.REGISTRY_VERIFIED; }
		@com.fasterxml.jackson.annotation.JsonIgnore
		public boolean hasEditionConflict() { return editionCompatibility == EditionCompatibility.CONFLICT; }
	}
	record CandidateRequest(String system, String ifcEntity) { }
	record Candidate(Resolution resolution, ClassificationDefinition definition, String discoveryMethod,
			String supportingClassUri) { }
	/** Registry-supplied dictionary attribution and usage-rights metadata. */
	record DictionaryMetadata(String uri, String code, String name, String version, String status,
			String organizationCodeOwner, String organizationNameOwner, String license, String licenseUrl) {
		public DictionaryMetadata {
			if (uri == null || !uri.startsWith("http"))
				throw new IllegalArgumentException("A dictionary URI must be HTTP(S)");
		}
	}

	Optional<Resolution> resolve(Request request);

	/**
	 * Retrieves registry metadata for a resolved class when supported.
	 * Implementations which only resolve identifiers remain valid and offline.
	 */
	default Optional<ClassificationDefinition> definition(Resolution resolution) {
		return Optional.empty();
	}

	/** Returns authoritative class candidates published for an IFC entity, when supported. */
	default List<Candidate> candidates(CandidateRequest request) { return List.of(); }

	/** Loads one configured dictionary's registry metadata when supported. */
	default Optional<DictionaryMetadata> dictionary(String system) { return Optional.empty(); }

	/** Returns already available dictionary metadata without initiating network access. */
	default List<DictionaryMetadata> dictionaries() { return List.of(); }

	static ClassificationResolver none() { return NoneHolder.INSTANCE; }
	final class NoneHolder {
		private static final ClassificationResolver INSTANCE = new ClassificationResolver() {
			@Override public String id() { return "none"; }
			@Override public String version() { return "1"; }
			@Override public boolean allowsNetworkAccess() { return false; }
			@Override public Optional<Resolution> resolve(Request request) { return Optional.empty(); }
		};
		private NoneHolder() { }
	}
}
