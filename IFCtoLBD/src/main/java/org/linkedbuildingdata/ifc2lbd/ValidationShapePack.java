package org.linkedbuildingdata.ifc2lbd;

/** Versioned SHACL rule packs that can be applied after conversion. */
public enum ValidationShapePack {
	CORE_BOT("core-bot", "1.0.0"),
	PROPERTIES_UNITS("properties-units", "1.0.0"),
	GEOMETRY_CRS("geometry-crs", "1.0.0"),
	DIGITAL_TWIN_SENSORS("digital-twin-sensors", "1.0.0"),
	FIRE_ACCESSIBILITY("fire-accessibility", "1.0.0"),
	SUPPLY_CHAIN_IDENTIFIERS("supply-chain-identifiers", "1.0.0"),
	SUSTAINABILITY_DECLARATIONS("sustainability-declarations", "1.0.0");

	private final String id;
	private final String version;

	ValidationShapePack(String id, String version) {
		this.id = id;
		this.version = version;
	}

	public String id() { return id; }
	public String version() { return version; }
	public String resource() { return "shacl/" + id + "-v" + version + ".ttl"; }
}
