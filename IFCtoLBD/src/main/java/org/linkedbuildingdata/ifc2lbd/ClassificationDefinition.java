package org.linkedbuildingdata.ifc2lbd;

import java.util.List;

/**
 * Registry metadata for one classification class.
 *
 * <p>Every URI in this model is supplied by the registry. In particular,
 * {@link ClassProperty#uri()} and {@link Property#uri()} identify different
 * things: the former is the class-specific assignment and the latter is the
 * reusable property.</p>
 */
public record ClassificationDefinition(String uri, String dictionaryUri, String code, String name,
		String description, List<String> relatedIfcEntities, List<ClassProperty> classProperties,
		List<Relationship> relationships) {
	public ClassificationDefinition {
		relatedIfcEntities = immutable(relatedIfcEntities);
		classProperties = immutable(classProperties);
		relationships = immutable(relationships);
	}

	public record ClassProperty(String uri, Property property, String propertySet, String dataType,
			String description, String predefinedValue, Boolean required, Boolean writable,
			List<AllowedValue> allowedValues, List<Unit> units) {
		public ClassProperty {
			allowedValues = immutable(allowedValues);
			units = immutable(units);
		}
	}

	public record Property(String uri, String code, String name, String description) { }

	public record AllowedValue(String uri, String code, String value, String description) { }

	/** A bSDD unit label and its optional bSDD-supplied QUDT code. */
	public record Unit(String code, String qudtCode) { }

	public record Relationship(String uri, String type, String relatedClassUri, String relatedClassName) { }

	private static <T> List<T> immutable(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
