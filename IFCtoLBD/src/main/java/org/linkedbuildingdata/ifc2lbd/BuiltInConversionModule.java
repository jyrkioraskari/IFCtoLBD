package org.linkedbuildingdata.ifc2lbd;

import java.util.List;

/** Modules that adapt the converter's existing behavior to the profile API. */
public enum BuiltInConversionModule implements ConversionModule {
	BOT_TOPOLOGY("bot-topology") {
		@Override public void configure(ConversionProperties p) { p.setHasBuildingElements(true); }
	},
	PRODUCT_ONTOLOGY("product-ontology") {
		@Override public void configure(ConversionProperties p) { p.setHasBuildingElements(true); }
	},
	SIMPLE_PROPERTIES("simple-properties") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingProperties(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.SIMPLE);
		}
	},
	OPM_PROPERTIES("opm-properties") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingProperties(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.OPM);
		}
	},
	GEOMETRY_ENVELOPE("geometry-envelope") {
		@Override public void configure(ConversionProperties p) {
			p.setHasGeometry(true);
			p.setHasBoundingBoxWKT(true);
		}
	},
	FULL_GEOMETRY("geometry-full") {
		@Override public void configure(ConversionProperties p) { p.setHasGeometry(true); }
	},
	GEOLOCATION("geolocation") {
		@Override public void configure(ConversionProperties p) { p.setHasGeolocation(true); }
	},
	VALIDATION("validation") {
		@Override public void configure(ConversionProperties p) { }
		@Override public List<String> shapeResources() { return List.of(ValidationShapePack.CORE_BOT.resource()); }
	},
	STABLE_IDENTITY("stable-identity") {
		@Override public void configure(ConversionProperties p) { p.setStableIdentity(true); }
	},
	GEOMETRY_ARTIFACTS("geometry-artifacts") {
		@Override public void configure(ConversionProperties p) {
			p.setHasGeometry(true);
			p.setGeometryArtifacts(true);
		}
	},
	SUPPLY_CHAIN("supply-chain") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingElements(true);
			p.setHasBuildingProperties(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.SIMPLE);
		}
	},
	SUSTAINABILITY("sustainability") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingElements(true);
			p.setHasBuildingProperties(true);
			p.setHasUnits(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.SIMPLE);
		}
	};

	private final String id;

	BuiltInConversionModule(String id) {
		this.id = id;
	}

	@Override public String id() { return id; }

	@Override public String version() { return "1"; }
}
