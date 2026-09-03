package org.linkedbuildingdata.ifc2lbd;

import java.util.Map;
import java.util.Optional;

/** Exact-code adapter for an ETIM dictionary published through bSDD. */
public final class EtimClassificationResolver implements ClassificationResolver {
	private final ClassificationResolver delegate;

	public EtimClassificationResolver(String dictionaryUri) {
		this(new BsddClassificationResolver(Map.of("ETIM", dictionaryUri)));
	}

	EtimClassificationResolver(ClassificationResolver delegate) { this.delegate = delegate; }

	@Override public Optional<Resolution> resolve(Request request) {
		if (request.system() == null || !request.system().replaceAll("[^A-Za-z0-9]", "")
				.equalsIgnoreCase("ETIM")) return Optional.empty();
		return delegate.resolve(new Request("ETIM", request.edition(), request.code(), request.label(),
				request.sourceLocation()));
	}
}
