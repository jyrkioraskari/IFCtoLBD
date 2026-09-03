package org.linkedbuildingdata.ifc2lbd;

import static org.linkedbuildingdata.ifc2lbd.BuiltInConversionModule.*;

/** Profiles supported by the current conversion engine. */
public final class ConversionProfiles {

	public static final ConversionProfile CORE = ConversionProfile.of("core", BOT_TOPOLOGY, PRODUCT_ONTOLOGY);
	public static final ConversionProfile PROPERTIES_SIMPLE = ConversionProfile.of("properties-simple",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, SIMPLE_PROPERTIES);
	public static final ConversionProfile PROPERTIES_OPM = ConversionProfile.of("properties-opm",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, OPM_PROPERTIES);
	public static final ConversionProfile GEOMETRY_ENVELOPE = ConversionProfile.of("geometry-envelope",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, BuiltInConversionModule.GEOMETRY_ENVELOPE);
	public static final ConversionProfile GEOMETRY_FULL = ConversionProfile.of("geometry-full",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, FULL_GEOMETRY);
	public static final ConversionProfile BIM_GIS = ConversionProfile.of("bim-gis",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, BuiltInConversionModule.GEOMETRY_ENVELOPE, GEOLOCATION);
	public static final ConversionProfile COMPLIANCE = ConversionProfile.of("compliance",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, VALIDATION);
	public static final ConversionProfile REVISION_READY = ConversionProfile.of("revision-ready",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, STABLE_IDENTITY);
	public static final ConversionProfile GEOMETRY_EXTERNAL = ConversionProfile.of("geometry-external",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, GEOMETRY_ARTIFACTS);
	public static final ConversionProfile SUPPLY_CHAIN = ConversionProfile.of("supply-chain",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, SIMPLE_PROPERTIES, BuiltInConversionModule.SUPPLY_CHAIN);
	public static final ConversionProfile SUSTAINABILITY = ConversionProfile.of("sustainability",
			BOT_TOPOLOGY, PRODUCT_ONTOLOGY, SIMPLE_PROPERTIES, BuiltInConversionModule.SUSTAINABILITY);

	private ConversionProfiles() { }
}
