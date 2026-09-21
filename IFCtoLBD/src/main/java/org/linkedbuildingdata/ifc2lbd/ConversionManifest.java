package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcBsddDictionary;

import be.ugent.IfcSpfReader;

/** Creates the provenance and reproducibility metadata for a conversion. */
final class ConversionManifest {

	static final String NS = "https://w3id.org/ifctolbd/manifest#";
	static final String CONVERTER_VERSION = "2.53.0";
	private static final String PROV = "http://www.w3.org/ns/prov#";

	private ConversionManifest() { }
	record Created(Model model, ConversionGraphNames graphNames) { }

	static Created create(ConversionRequest request, Instant convertedAt, ValidationStage.Result validation,
			UriPolicy uriPolicy, GeometryProvider geometryProvider, GeometryArtifactStore artifactStore,
			List<GeometryArtifact> geometryArtifacts, String baseUri, boolean productOntologies) {
		Path source = Path.of(request.getIfcFilename()).toAbsolutePath();
		String sourceChecksum = sha256(source);
		ConversionProfile profile = request.getProfile().orElse(null);
		String profileId = profile == null ? "legacy-flags" : profile.id();
		String moduleSignature = profile == null ? "" : profile.modules().stream()
				.map(module -> module.id() + "@" + module.version()).sorted().reduce("", (a, b) -> a + "|" + b);
		String artifactSignature = geometryArtifacts.stream().map(GeometryArtifact::sha256).sorted()
				.reduce("", (a, b) -> a + "|" + b);
		String ontologySignature = "ifc-schema@source|product@1:" + productOntologies
				+ "|ifc-bsdd@" + IfcBsddDictionary.get().version();
		String requestConfiguration = canonicalRequest(request, baseUri, profileId, moduleSignature, uriPolicy,
				geometryProvider, artifactStore, ontologySignature);
		String requestFingerprint = sha256(requestConfiguration);
		String cacheKey = sha256(sourceChecksum + "|" + CONVERTER_VERSION + "|" + requestFingerprint
				+ "|artifacts" + artifactSignature);
		ConversionGraphNames graphNames = ConversionGraphNames.forConversion(cacheKey);

		Model model = ModelFactory.createDefaultModel();
		model.setNsPrefix("manifest", NS);
		model.setNsPrefix("prov", PROV);
		Resource conversion = model.createResource("urn:ifctolbd:conversion:" + cacheKey)
				.addProperty(RDF.type, model.createResource(NS + "Conversion"))
				.addLiteral(property(model, "cacheKey"), cacheKey)
				.addLiteral(property(model, "sourceChecksum"), sourceChecksum)
				.addLiteral(property(model, "ifcSchema"), IfcSpfReader.getExpressSchema(source.toString()))
				.addLiteral(property(model, "converterVersion"), CONVERTER_VERSION)
				.addLiteral(property(model, "requestFingerprint"), requestFingerprint)
				.addLiteral(property(model, "requestConfiguration"), requestConfiguration)
				.addLiteral(property(model, "baseUri"), baseUri)
				.addLiteral(property(model, "profile"), profileId)
				.addLiteral(property(model, "uriPolicy"), uriPolicy.id())
				.addLiteral(property(model, "uriPolicyConfiguration"), uriPolicy.configurationId())
				.addLiteral(property(model, "geometryProvider"), geometryProvider.id())
				.addLiteral(property(model, "geometryProviderVersion"), geometryProvider.version())
				.addLiteral(property(model, "geometryArtifactStore"), artifactStore.id())
				.addLiteral(property(model, "geometryArtifactStoreVersion"), artifactStore.version())
				.addLiteral(property(model, "geometryArtifactStoreConfiguration"), artifactStore.configurationId())
				.addLiteral(property(model, "classificationResolver"), request.getClassificationResolver().id())
				.addLiteral(property(model, "classificationResolverVersion"), request.getClassificationResolver().version())
				.addLiteral(property(model, "classificationResolverConfiguration"), request.getClassificationResolver().configurationId())
				.addLiteral(property(model, "ontologyConfiguration"), ontologySignature)
				.addLiteral(property(model, "validationStatus"), validation.status())
				.addLiteral(model.createProperty(PROV + "generatedAtTime"),
						model.createTypedLiteral(convertedAt.toString(), XSDDatatype.XSDdateTime));
		request.getModelScope().ifPresent(scope -> conversion.addLiteral(property(model, "modelScope"), scope));
		request.getSelectedTypes().stream().sorted().forEach(value -> conversion.addLiteral(property(model, "selectedType"), value));
		request.getSelectedPropertySets().stream().sorted().forEach(value -> conversion.addLiteral(property(model, "selectedPropertySet"), value));
		request.getPropertyReplacements().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
				conversion.addLiteral(property(model, "propertyReplacement"), entry.getKey() + "=" + entry.getValue()));
		for (String graph : graphNames.all())
			conversion.addProperty(property(model, "hasNamedGraph"), model.createResource(graph));
		Resource sourceEntity = model.createResource("urn:sha256:" + sourceChecksum)
				.addProperty(RDF.type, model.createResource(PROV + "Entity"));
		conversion.addProperty(model.createProperty(PROV + "wasDerivedFrom"), sourceEntity);

		if (profile != null) {
			for (ConversionModule module : profile.modules()) {
				Resource moduleResource = model.createResource("urn:ifctolbd:module:" + module.id() + ":" + module.version())
						.addProperty(RDF.type, model.createResource(NS + "Module"))
						.addLiteral(property(model, "moduleId"), module.id())
						.addLiteral(property(model, "moduleVersion"), module.version());
				conversion.addProperty(property(model, "usesModule"), moduleResource);
			}
		}
		validation.shapeResources().forEach(shape -> conversion.addLiteral(property(model, "shapePack"), shape));
		geometryArtifacts.stream().map(GeometryArtifact::sha256).distinct().sorted()
				.forEach(checksum -> conversion.addLiteral(property(model, "geometryArtifactChecksum"), checksum));
		return new Created(model, graphNames);
	}

	private static String canonicalRequest(ConversionRequest request, String baseUri, String profileId,
			String modules, UriPolicy uriPolicy, GeometryProvider geometryProvider,
			GeometryArtifactStore artifactStore, String ontologies) {
		ConversionProperties p = request.getProperties();
		List<String> values = new ArrayList<>();
		values.add("format=ifctolbd-request-v1"); values.add("baseUri=" + baseUri); values.add("profile=" + profileId);
		values.add("modules=" + modules); values.add("uriPolicy=" + uriPolicy.configurationId());
		values.add("geometryProvider=" + geometryProvider.id() + "@" + geometryProvider.version());
		values.add("artifactStore=" + artifactStore.configurationId());
		values.add("resolver=" + request.getClassificationResolver().configurationId());
		values.add("ontologies=" + ontologies);
		values.add("properties=" + String.join(",", Boolean.toString(p.isHasBuildingElements()),
				Boolean.toString(p.isHasSeparateBuildingElementsModel()), Boolean.toString(p.isHasBuildingProperties()),
				Boolean.toString(p.isHasSeparatePropertiesModel()), Boolean.toString(p.isHasGeolocation()),
				Boolean.toString(p.isHasGeometry()), Boolean.toString(p.isExportIfcOWL()), Boolean.toString(p.isHasUnits()),
				Boolean.toString(p.hasBoundingBoxWKT()), Boolean.toString(p.hasHierarchicalNaming()),
				Boolean.toString(p.hasPerformanceBoost()), Boolean.toString(p.hasNonLBDElement()),
				Boolean.toString(p.isHasInterfaces()), Boolean.toString(p.hasWireframe()), p.getPropertyMode().name(),
				Boolean.toString(p.hasStableIdentity()), Boolean.toString(p.hasGeometryArtifacts())));
		request.getSelectedTypes().stream().sorted().forEach(v -> values.add("type=" + v));
		request.getSelectedPropertySets().stream().sorted().forEach(v -> values.add("pset=" + v));
		request.getValidationShapePacks().stream().map(ValidationShapePack::resource).sorted().forEach(v -> values.add("validation=" + v));
		request.getPropertyReplacements().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(e -> values.add("replacement=" + e.getKey() + "=" + e.getValue()));
		return values.stream().map(v -> v.length() + ":" + v).reduce("", String::concat);
	}

	private static Property property(Model model, String localName) {
		return model.createProperty(NS + localName);
	}

	static String sha256(Path path) {
		try (InputStream input = Files.newInputStream(path);
				DigestInputStream digestInput = new DigestInputStream(input, MessageDigest.getInstance("SHA-256"))) {
			digestInput.transferTo(OutputStream.nullOutputStream());
			return HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not checksum IFC source " + path, e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
}
