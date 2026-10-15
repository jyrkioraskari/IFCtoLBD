package org.linkedbuildingdata.ifc2lbd;

/**
 * A reviewed, context-specific mapping from an IFC property to an ETIM
 * ClassProperty or reusable Property identifier.
 */
public record EtimPropertyMapping(String etimClassUri, String ifcEntity, String propertySet,
		String propertyName, String classPropertyUri, String propertyUri, String reviewReference) {
	public EtimPropertyMapping {
		etimClassUri = required(etimClassUri, "etimClassUri");
		propertySet = required(propertySet, "propertySet");
		propertyName = required(propertyName, "propertyName");
		reviewReference = required(reviewReference, "reviewReference");
		if (blank(classPropertyUri) && blank(propertyUri))
			throw new IllegalArgumentException("classPropertyUri or propertyUri is required");
		ifcEntity = ifcEntity == null ? "" : ifcEntity.trim();
		classPropertyUri = trim(classPropertyUri);
		propertyUri = trim(propertyUri);
	}

	private static String required(String value, String name) {
		if (blank(value)) throw new IllegalArgumentException(name + " must not be blank");
		return value.trim();
	}
	private static String trim(String value) { return value == null ? null : value.trim(); }
	private static boolean blank(String value) { return value == null || value.isBlank(); }
}
