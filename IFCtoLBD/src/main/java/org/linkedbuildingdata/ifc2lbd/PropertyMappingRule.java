package org.linkedbuildingdata.ifc2lbd;

import java.util.Objects;

/** A declarative mapping from an IFC property to an RDF predicate. */
public record PropertyMappingRule(String entityType, String propertySet, String propertyName,
		String predicate, boolean enabled) {
	public PropertyMappingRule {
		if (propertyName == null || propertyName.isBlank()) throw new IllegalArgumentException("propertyName must not be blank");
		if (predicate == null || predicate.isBlank()) throw new IllegalArgumentException("predicate must not be blank");
		entityType = entityType == null ? "" : entityType.trim();
		propertySet = propertySet == null ? "" : propertySet.trim();
		propertyName = propertyName.trim();
		predicate = predicate.trim();
	}
	public PropertyMappingRule(String entityType, String propertySet, String propertyName, String predicate) {
		this(entityType, propertySet, propertyName, predicate, true);
	}
}
