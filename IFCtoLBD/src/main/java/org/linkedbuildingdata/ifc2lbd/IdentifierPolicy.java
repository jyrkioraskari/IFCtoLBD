package org.linkedbuildingdata.ifc2lbd;

/** Public, persisted selector for element identifier generation. */
public enum IdentifierPolicy {
	/** Preserve the converter's historical identifier generation. */
	EXISTING,
	/** Use a persistent project namespace and the IFC GlobalId. */
	STABLE_PROJECT
}
