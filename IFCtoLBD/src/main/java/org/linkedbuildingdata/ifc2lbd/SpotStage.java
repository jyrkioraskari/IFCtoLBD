package org.linkedbuildingdata.ifc2lbd;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;

/** Optional SPOT appearances for the elements actually exported by one IFC revision. */
final class SpotStage {
	static final String NS = "https://w3id.org/spot#";
	private static final String REPORT_NS = "https://w3id.org/ifctolbd/spot#";
	private static final String ID_BASE = "urn:ifctolbd:spot:";

	private SpotStage() { }

	static void enrich(ConversionContext context) {
		Model output = context.generalModel();
		String revision = context.request().getSpotModelRevisionIri().orElseGet(() ->
				"urn:sha256:" + ConversionManifest.sha256(Path.of(context.request().getIfcFilename())));
		Resource project = IfcOWLUtils.getIfcProject(context.ifcOntology(), context.ifcModel());
		List<Resource> contexts = project == null ? List.of() : IfcMappingSupport.objectResources(project,
				context.ifcOntology(), "representationContexts_IfcContext", "representationContexts_IfcProject");
		if (contexts.size() != 1 || !"3".equals(IfcMappingSupport.scalar(contexts.getFirst(),
				context.ifcOntology(), "coordinateSpaceDimension_IfcGeometricRepresentationContext"))) {
			diagnostic(context, revision, "UnsupportedModelExtent",
				"SPOT export requires one declared 3D model context; multiple or missing contexts are not mapped.");
			return;
		}

		output.setNsPrefix("spot", NS);
		Resource modelSpace = resource(output, "model-space", revision)
				.addProperty(RDF.type, output.createResource(NS + "VolumeSpace"))
				.addProperty(output.createProperty(NS + "represents"), output.createResource(revision));
		TreeSet<String> elements = new TreeSet<>();
		output.listResourcesWithProperty(RDF.type, BOT.element).forEachRemaining(element -> {
			if (element.isURIResource()) elements.add(element.getURI());
		});
		for (String elementUri : elements) {
			Resource space = resource(output, "entity-space", revision, elementUri)
					.addProperty(RDF.type, output.createResource(NS + "EntitySpace"))
					.addProperty(output.createProperty(NS + "represents"), output.createResource(elementUri));
			Resource appearance = resource(output, "entity-appearance", revision, elementUri)
					.addProperty(RDF.type, output.createResource(NS + "EntityAppearance"))
					.addProperty(output.createProperty(NS + "appearsIn"), modelSpace);
			space.addProperty(output.createProperty(NS + "hasAppearance"), appearance);
		}
		context.request().getSpotAssetIri().ifPresentOrElse(assetIri -> {
			Resource assetSpace = resource(output, "asset-space", assetIri)
					.addProperty(RDF.type, output.createResource(NS + "AssetSpace"))
					.addProperty(output.createProperty(NS + "represents"), output.createResource(assetIri));
			Resource appearance = resource(output, "asset-appearance", revision, assetIri)
					.addProperty(RDF.type, output.createResource(NS + "AssetAppearance"))
					.addProperty(output.createProperty(NS + "appearsIn"), assetSpace);
			modelSpace.addProperty(output.createProperty(NS + "hasAppearance"), appearance);
		}, () -> diagnostic(context, revision, "AssetIdentityMissing",
				"No asset IRI supplied; SPOT model and element appearances were exported without an asset link."));
	}

	private static Resource resource(Model model, String role, String... identities) {
		String key = java.util.Arrays.stream(identities).map(value -> value.length() + ":" + value)
				.reduce("", String::concat);
		return model.createResource(ID_BASE + role + ":" + ConversionManifest.sha256(key));
	}

	private static void diagnostic(ConversionContext context, String revision, String code, String message) {
		Model report = context.diagnosticsModel();
		report.createResource(ID_BASE + "diagnostic:" + ConversionManifest.sha256(revision + "|" + code))
				.addProperty(RDF.type, report.createResource(REPORT_NS + "Diagnostic"))
				.addLiteral(report.createProperty(REPORT_NS + "code"), code)
				.addLiteral(report.createProperty(REPORT_NS + "message"), message);
	}
}
