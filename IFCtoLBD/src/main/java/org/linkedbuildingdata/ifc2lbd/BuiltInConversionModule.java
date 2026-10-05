package org.linkedbuildingdata.ifc2lbd;

import java.util.List;

/** Modules that adapt the converter's existing behavior to the profile API. */
public enum BuiltInConversionModule implements ConversionModule {
	BOT_TOPOLOGY("bot-topology") {
		@Override public void configure(ConversionProperties p) { p.setHasBuildingElements(true); }
	},
	PRODUCT_ONTOLOGY("product-ontology") {
		@Override public void configure(ConversionProperties p) { p.setHasBuildingElements(true); }
		@Override public void configure(ConversionContext context) {
			configure(context.properties());
			context.requireProductOntologies();
		}
	},
	SIMPLE_PROPERTIES("simple-properties") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingProperties(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.SIMPLE);
		}
		@Override public void configure(ConversionContext context) { configure(context.properties()); }
	},
	OPM_PROPERTIES("opm-properties") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingProperties(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.OPM);
		}
		@Override public void configure(ConversionContext context) { configure(context.properties()); }
	},
	EVIDENCE("evidence") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingProperties(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.OPM);
			p.setHasUnits(true);
			p.setExportIfcOWL(true);
		}
		@Override public void configure(ConversionContext context) { configure(context.properties()); }
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
		@Override public void enrich(ConversionContext context) {
			SupplyChainStage.enrichSupplyChain(context.ifcModel(), context.ifcOntology(), context.generalModel(),
					context.mappedResources(), context.classificationResolver());
		}
	},
	SUSTAINABILITY("sustainability") {
		@Override public void configure(ConversionProperties p) {
			p.setHasBuildingElements(true);
			p.setHasBuildingProperties(true);
			p.setHasUnits(true);
			p.setPropertyMode(ConversionProperties.PropertyMode.SIMPLE);
		}
		@Override public void enrich(ConversionContext context) {
			SustainabilityStage.enrich(context.ifcModel(), context.ifcOntology(), context.generalModel(),
					context.mappedResources());
		}
	};

	private final String id;

	BuiltInConversionModule(String id) {
		this.id = id;
	}

	@Override public String id() { return id; }

	@Override public String version() { return "1"; }
}
