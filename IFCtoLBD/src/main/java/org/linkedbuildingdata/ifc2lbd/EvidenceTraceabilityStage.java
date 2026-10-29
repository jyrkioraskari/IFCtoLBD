package org.linkedbuildingdata.ifc2lbd;

import java.nio.file.Path;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Builds the optional element-level IFC evidence companion graph. */
final class EvidenceTraceabilityStage {
	static final String NS = "https://w3id.org/ifctolbd/evidence#";
	private static final String PROV = "http://www.w3.org/ns/prov#";

	private EvidenceTraceabilityStage() { }

	static Model create(Path sourceFile, IfcOWL ifcOntology, Map<Resource, Resource> mappedResources) {
		Model model = ModelFactory.createDefaultModel();
		model.setNsPrefix("evidence", NS);
		model.setNsPrefix("prov", PROV);
		model.setNsPrefix("ifctolbd", IFCtoLBDMapping.ns);
		String checksum = ConversionManifest.sha256(sourceFile.toAbsolutePath());
		Resource sourceModel = model.createResource("urn:sha256:" + checksum)
				.addProperty(RDF.type, model.createResource(PROV + "Entity"))
				.addLiteral(model.createProperty(NS + "contentFingerprint"), checksum);

		mappedResources.entrySet().stream()
				.filter(entry -> entry.getValue().isURIResource())
				.forEach(entry -> write(model, sourceModel, ifcOntology, entry.getKey(), entry.getValue(), checksum));
		return model;
	}

	private static void write(Model model, Resource sourceModel, IfcOWL ifcOntology, Resource ifc,
			Resource lbd, String checksum) {
		String globalId = IfcOWLUtils.getGUID(ifc, ifcOntology);
		if (globalId == null || globalId.isBlank()) return;
		String sourceId = ConversionManifest.sha256(checksum + "|" + globalId);
		Resource sourceEntity = model.createResource("urn:ifctolbd:ifc-entity:" + sourceId)
				.addProperty(RDF.type, model.createResource(PROV + "Entity"))
				.addProperty(model.createProperty(PROV + "specializationOf"), sourceModel)
				.addLiteral(IFCtoLBDMapping.ifcGlobalId, globalId)
				.addLiteral(model.createProperty(NS + "scope"), "element");
		ifc.listProperties(RDF.type).toList().stream().map(statement -> statement.getObject())
				.filter(RDFNode::isURIResource).map(node -> node.asResource().getURI()).sorted().findFirst()
				.ifPresent(type -> sourceEntity.addProperty(IFCtoLBDMapping.sourceIfcType, model.createResource(type)));
		model.createResource(lbd.getURI())
				.addProperty(model.createProperty(PROV + "wasDerivedFrom"), sourceEntity)
				.addLiteral(model.createProperty(NS + "mappingKind"), "DIRECT")
				.addLiteral(model.createProperty(NS + "scope"), "element")
				.addLiteral(IFCtoLBDMapping.ifcGlobalId, globalId);
	}
}
