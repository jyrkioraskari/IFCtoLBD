package org.linkedbuildingdata.ifc2lbd;

import java.util.Map;
import java.util.Optional;

/** Exact-code adapter for an ETIM dictionary published through bSDD. */
public final class EtimClassificationResolver implements ClassificationResolver {
	private final ClassificationResolver delegate;

	public EtimClassificationResolver(String dictionaryUri) {
		this(new BsddClassificationResolver(Map.of("ETIM", dictionaryUri)));
	}

	public EtimClassificationResolver(String dictionaryUri, String modellingClassDictionaryUri) {
		this(new BsddClassificationResolver(Map.of("ETIM", dictionaryUri, "ETIM-MC", modellingClassDictionaryUri)));
	}

	EtimClassificationResolver(ClassificationResolver delegate) { this.delegate = delegate; }
	@Override public String id() { return "etim"; }
	@Override public String version() { return "1"; }
	@Override public String configurationId() { return id() + "@" + version() + "|" + delegate.configurationId(); }
	@Override public boolean allowsNetworkAccess() { return delegate.allowsNetworkAccess(); }

	@Override public Optional<Resolution> resolve(Request request) {
		if (request.system() == null || !request.system().replaceAll("[^A-Za-z0-9]", "")
				.equalsIgnoreCase("ETIM")) return Optional.empty();
		return delegate.resolve(new Request("ETIM", request.edition(), request.code(), request.label(),
				request.sourceLocation()));
	}

	@Override public Optional<ClassificationDefinition> definition(Resolution resolution) {
		return delegate.definition(resolution);
	}

	@Override public java.util.List<Candidate> candidates(CandidateRequest request) {
		if (request.system() == null || !request.system().replaceAll("[^A-Za-z0-9]", "")
				.equalsIgnoreCase("ETIM")) return java.util.List.of();
		return delegate.candidates(new CandidateRequest("ETIM", request.ifcEntity()));
	}

	@Override public Optional<DictionaryMetadata> dictionary(String system) {
		if (system == null || !system.replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase("ETIM"))
			return Optional.empty();
		return delegate.dictionary("ETIM");
	}

	@Override public java.util.List<DictionaryMetadata> dictionaries() { return delegate.dictionaries(); }
}
