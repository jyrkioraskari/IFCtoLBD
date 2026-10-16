package org.linkedbuildingdata.ifc2lbd;

import java.util.List;

/** Optional conversion module for IFC material assignments expressed with BMO. */
public final class MaterialsBmoModule implements ConversionModule {
	public static final String ID = "materials-bmo";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String version() {
		return "1";
	}

	@Override
	public void configure(ConversionProperties properties) {
		properties.setHasBuildingElements(true);
		// The compatibility fast path removes IfcRelAssociatesMaterial statements.
		properties.setHasPerformanceBoost(false);
	}

	@Override
	public void map(ConversionContext context) {
		MaterialConversionStage.map(context);
	}

	@Override
	public List<String> shapeResources() {
		return List.of(ValidationShapePack.MATERIALS_BMO.resource());
	}
}
