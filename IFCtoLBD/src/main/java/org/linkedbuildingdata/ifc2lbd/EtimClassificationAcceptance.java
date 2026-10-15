package org.linkedbuildingdata.ifc2lbd;

/** A reviewed acceptance of one suggested ETIM class for one converted element URI. */
public record EtimClassificationAcceptance(String elementUri, String classUri, String reviewReference) {
	public EtimClassificationAcceptance {
		elementUri = required(elementUri, "elementUri");
		classUri = required(classUri, "classUri");
		reviewReference = required(reviewReference, "reviewReference");
	}

	private static String required(String value, String name) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return value.trim();
	}
}
