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

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import be.ugent.IfcSpfReader;

/** Creates the provenance and reproducibility metadata for a conversion. */
final class ConversionManifest {

	static final String NS = "https://w3id.org/ifctolbd/manifest#";
	static final String CONVERTER_VERSION = "2.51.0";
	private static final String PROV = "http://www.w3.org/ns/prov#";

	private ConversionManifest() { }

	static Model create(ConversionRequest request, Instant convertedAt, ValidationStage.Result validation,
			UriPolicy uriPolicy, GeometryProvider geometryProvider, List<GeometryArtifact> geometryArtifacts) {
		Path source = Path.of(request.getIfcFilename()).toAbsolutePath();
		String sourceChecksum = sha256(source);
		ConversionProfile profile = request.getProfile().orElse(null);
		String profileId = profile == null ? "legacy-flags" : profile.id();
		String moduleSignature = profile == null ? "" : profile.modules().stream()
				.map(module -> module.id() + "@" + module.version()).sorted().reduce("", (a, b) -> a + "|" + b);
		String artifactSignature = geometryArtifacts.stream().map(GeometryArtifact::sha256).sorted()
				.reduce("", (a, b) -> a + "|" + b);
		String cacheKey = sha256(sourceChecksum + "|" + CONVERTER_VERSION + "|" + profileId + moduleSignature
				+ "|" + uriPolicy.configurationId() + "|" + geometryProvider.id() + "@" + geometryProvider.version()
				+ artifactSignature);

		Model model = ModelFactory.createDefaultModel();
		model.setNsPrefix("manifest", NS);
		model.setNsPrefix("prov", PROV);
		Resource conversion = model.createResource("urn:ifctolbd:conversion:" + cacheKey)
				.addProperty(RDF.type, model.createResource(NS + "Conversion"))
				.addLiteral(property(model, "cacheKey"), cacheKey)
				.addLiteral(property(model, "sourceChecksum"), sourceChecksum)
				.addLiteral(property(model, "ifcSchema"), IfcSpfReader.getExpressSchema(source.toString()))
				.addLiteral(property(model, "converterVersion"), CONVERTER_VERSION)
				.addLiteral(property(model, "profile"), profileId)
				.addLiteral(property(model, "uriPolicy"), uriPolicy.id())
				.addLiteral(property(model, "geometryProvider"), geometryProvider.id())
				.addLiteral(property(model, "geometryProviderVersion"), geometryProvider.version())
				.addLiteral(property(model, "validationStatus"), validation.status())
				.addLiteral(model.createProperty(PROV + "generatedAtTime"),
						model.createTypedLiteral(convertedAt.toString(), XSDDatatype.XSDdateTime));
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
		return model;
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
