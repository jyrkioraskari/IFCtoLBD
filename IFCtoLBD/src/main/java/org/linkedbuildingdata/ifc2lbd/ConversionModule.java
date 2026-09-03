package org.linkedbuildingdata.ifc2lbd;

import java.util.List;

/** A versioned, composable unit of conversion configuration. */
public interface ConversionModule {

	String id();

	String version();

	void configure(ConversionProperties properties);

	/** Classpath resources containing SHACL shapes supplied by this module. */
	default List<String> shapeResources() {
		return List.of();
	}
}
