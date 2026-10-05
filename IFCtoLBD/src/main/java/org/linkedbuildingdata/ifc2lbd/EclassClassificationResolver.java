package org.linkedbuildingdata.ifc2lbd;

import java.util.Map;
import java.util.Optional;

/** Exact-code adapter for an ECLASS dictionary published through bSDD. */
public final class EclassClassificationResolver implements ClassificationResolver {
	private final ClassificationResolver delegate;

	public EclassClassificationResolver(String dictionaryUri) {
		this(new BsddClassificationResolver(Map.of("ECLASS", dictionaryUri)));
	}

	EclassClassificationResolver(ClassificationResolver delegate) { this.delegate = delegate; }
	@Override public String id() { return "eclass"; }
	@Override public String version() { return "1"; }
	@Override public String configurationId() { return id() + "@" + version() + "|" + delegate.configurationId(); }

	@Override public Optional<Resolution> resolve(Request request) {
		if (request.system() == null || !request.system().replaceAll("[^A-Za-z0-9]", "")
				.equalsIgnoreCase("ECLASS")) return Optional.empty();
		return delegate.resolve(new Request("ECLASS", request.edition(), request.code(), request.label(),
				request.sourceLocation()));
	}
}
