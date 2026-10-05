package org.linkedbuildingdata.ifc2lbd;

import java.util.List;

/** A versioned, composable unit of conversion configuration. */
public interface ConversionModule {

	String id();

	String version();

	/** Configure the shared mapping pass before the IFC is read. */
	default void configure(ConversionContext context) { configure(context.properties()); }

	/** Legacy flag configuration used when materialising a profile's properties. */
	default void configure(ConversionProperties properties) { }

	/** Perform module-specific mapping after the compatibility mapping pass. */
	default void map(ConversionContext context) { }

	/** Add assertions owned by this module. */
	default void enrich(ConversionContext context) { }

	/** Register or execute validation owned by this module. */
	default void validate(ConversionContext context) {
		shapeResources().forEach(context::addValidationResource);
	}

	/** Classpath resources containing SHACL shapes supplied by this module. */
	default List<String> shapeResources() {
		return List.of();
	}
}
