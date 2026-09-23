package org.linkedbuildingdata.ifc2lbd;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Preserves overlapping IfcZone membership expressed by IfcRelAssignsToGroup. */
final class IfcZoneStage {
	private IfcZoneStage() { }

	static void map(Model ifcModel, IfcOWL ifc, Model output, Map<Resource, Resource> mappedResources,
			BiFunction<Resource, String, Resource> resourceMapper,
			BiConsumer<Resource, Resource> metadataMapper) {
		Map<Resource, Resource> zones = new LinkedHashMap<>();
		for (Resource source : IfcMappingSupport.instances(ifcModel, "IfcZone")) {
			Resource target = resourceMapper.apply(source, "Zone");
			target.addProperty(RDF.type, BOT.zone);
			zones.put(source, target);
			mappedResources.put(source, target);
		}
		zones.forEach(metadataMapper);

		zones.forEach((sourceZone, targetZone) -> ifcModel.listStatements(null,
				ifc.getProperty("relatingGroup_IfcRelAssignsToGroup"), sourceZone).forEachRemaining(statement -> {
			Resource relationship = statement.getSubject();
			var members = new java.util.LinkedHashSet<Resource>();
			members.addAll(IfcMappingSupport.objectResources(relationship, ifc,
					"relatedObjects_IfcRelAssigns"));
			members.addAll(IfcMappingSupport.objectResources(relationship, ifc,
					"relatedObjects_IfcRelAssignsToGroup"));
			for (Resource member : members) {
				Resource mappedMember = mappedResources.get(member);
				if (mappedMember != null) targetZone.addProperty(BOT.containsZone, mappedMember);
			}
		}));
	}
}
