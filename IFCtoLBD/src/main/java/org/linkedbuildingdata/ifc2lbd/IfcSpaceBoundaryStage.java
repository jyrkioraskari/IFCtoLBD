package org.linkedbuildingdata.ifc2lbd;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Maps objectified IFC space boundaries without requiring mesh geometry. */
final class IfcSpaceBoundaryStage {
	private static final String PROV = "http://www.w3.org/ns/prov#";

	private IfcSpaceBoundaryStage() { }

	static void map(Model ifcModel, IfcOWL ifc, Model output, Map<Resource, Resource> mappedResources,
			BiFunction<Resource, String, Resource> resourceMapper,
			BiConsumer<Resource, Resource> metadataMapper) {
		IFCtoLBDMapping.addNameSpace(output);
		Map<Resource, Resource> boundaries = new LinkedHashMap<>();
		for (Resource source : IfcMappingSupport.instances(ifcModel, "IfcRelSpaceBoundary")) {
			Resource target = resourceMapper.apply(source, "Interface");
			boundaries.put(source, target);
			target.addProperty(RDF.type, BOT.bot_interface)
					.addProperty(IFCtoLBDMapping.interfaceOrigin, IFCtoLBDMapping.ifcSpaceBoundaryOrigin)
					.addProperty(output.createProperty(PROV + "wasDerivedFrom"), source);
			String guid = IfcOWLUtils.getGUID(source, ifc);
			if (guid != null) target.addLiteral(IFCtoLBDMapping.ifcGlobalId, guid);
			Resource type = source.getPropertyResourceValue(RDF.type);
			if (type != null && type.isURIResource()) {
				target.addLiteral(IFCtoLBDMapping.sourceIfcType, type.getLocalName());
				String level = boundaryLevel(type.getLocalName(), IfcOWLUtils.getName(source, ifc));
				if (level != null) target.addLiteral(IFCtoLBDMapping.boundaryLevel, level);
			}
			addLiteral(target, IFCtoLBDMapping.physicalOrVirtualBoundary,
					IfcMappingSupport.scalar(source, ifc, "physicalOrVirtualBoundary_IfcRelSpaceBoundary"));
			addLiteral(target, IFCtoLBDMapping.internalOrExternalBoundary,
					IfcMappingSupport.scalar(source, ifc, "internalOrExternalBoundary_IfcRelSpaceBoundary"));
			Resource geometry = IfcMappingSupport.objectResource(source, ifc,
					"connectionGeometry_IfcRelSpaceBoundary");
			if (geometry != null) target.addProperty(IFCtoLBDMapping.connectionGeometry, geometry);
			metadataMapper.accept(source, target);
			mappedResources.put(source, target);
		}

		boundaries.forEach((source, target) -> {
			addEndpoint(target, mappedResources, IfcMappingSupport.objectResource(source, ifc,
					"relatingSpace_IfcRelSpaceBoundary"));
			addEndpoint(target, mappedResources, IfcMappingSupport.objectResource(source, ifc,
					"relatedBuildingElement_IfcRelSpaceBoundary"));
			Resource corresponding = IfcMappingSupport.objectResource(source, ifc,
					"correspondingBoundary_IfcRelSpaceBoundary2ndLevel");
			if (corresponding != null) {
				Resource mappedBoundary = boundaries.get(corresponding);
				if (mappedBoundary != null)
					target.addProperty(IFCtoLBDMapping.correspondingBoundary, mappedBoundary);
				addEndpoint(target, mappedResources, IfcMappingSupport.objectResource(corresponding, ifc,
						"relatingSpace_IfcRelSpaceBoundary"));
			}
		});
	}

	private static void addEndpoint(Resource boundary, Map<Resource, Resource> mappedResources, Resource source) {
		if (source == null) return;
		Resource endpoint = mappedResources.get(source);
		if (endpoint != null) boundary.addProperty(BOT.bot_interfaceOf, endpoint);
	}

	private static void addLiteral(Resource subject, org.apache.jena.rdf.model.Property property, String value) {
		if (value != null && !value.isBlank()) subject.addLiteral(property, value);
	}

	private static String boundaryLevel(String ifcType, String name) {
		if (ifcType != null && ifcType.contains("1stLevel")) return "1stLevel";
		if (ifcType != null && ifcType.contains("2ndLevel")) return "2ndLevel";
		if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("1stlevel")) return "1stLevel";
		if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("2ndlevel")) return "2ndLevel";
		return "Unspecified";
	}
}
