package org.linkedbuildingdata.ifc2lbd;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;
import org.linkedbuildingdata.ifc2lbd.namespace.GEO;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;
import org.linkedbuildingdata.ifc2lbd.namespace.LBD;
import org.linkedbuildingdata.ifc2lbd.namespace.OMG;

/** Writes an ISO 21597-1 ICDD package for a completed conversion. */
public final class IcddPackageWriter {
	public static final String CONTAINER_ONTOLOGY =
			"https://standards.iso.org/iso/21597/-1/ed-1/en/Container";
	private static final String CT = CONTAINER_ONTOLOGY + "#";
	private static final String PAYLOAD_DOCUMENTS = "Payload documents/";
	private static final String PAYLOAD_TRIPLES = "Payload triples/";
	private static final String ONTOLOGY_RESOURCES = "Ontology resources/";

	private IcddPackageWriter() {
	}

	/**
	 * Creates an ICDD container containing the source IFC and distinct BOT topology,
	 * building-element, geometry, and property/quantity-set RDF documents. IFC
	 * attributes are kept with the topology or element resource they describe.
	 */
	public static void write(Path target, Path sourceIfc, Model general, Model products, Model properties)
			throws IOException {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(sourceIfc, "sourceIfc");
		Objects.requireNonNull(general, "general");
		Objects.requireNonNull(products, "products");
		Objects.requireNonNull(properties, "properties");
		if (!Files.isRegularFile(sourceIfc)) {
			throw new IOException("The source IFC file does not exist: " + sourceIfc);
		}

		Path absoluteTarget = target.toAbsolutePath();
		Path parent = absoluteTarget.getParent();
		if (parent != null) Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, ".ifctolbd-", ".icdd.tmp");
		try {
			writeArchive(temporary, sourceIfc, general, products, properties);
			try {
				Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void writeArchive(Path target, Path sourceIfc, Model general, Model products, Model properties)
			throws IOException {
		String containerId = "urn:uuid:" + UUID.randomUUID();
		List<DocumentDescription> documents = new ArrayList<>();
		Set<String> generatedPropertyTerms = findUsedGeneratedPropertyTerms(general, products, properties);
		Model propsOntology = createGeneratedPropertiesOntology(generatedPropertyTerms, general, products, properties);
		Model generalPayload = withoutOntologyDefinitions(general, generatedPropertyTerms);
		Model productPayload = withoutOntologyDefinitions(products, generatedPropertyTerms);
		Model propertyPayload = withoutOntologyDefinitions(properties, generatedPropertyTerms);
		Model geometryPayload = extractGeometry(generalPayload, productPayload, propertyPayload);
		ConversionGraphPartition.makeDisjoint(generalPayload, productPayload, propertyPayload);
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
			addDirectory(zip, ONTOLOGY_RESOURCES);
			addDirectory(zip, PAYLOAD_DOCUMENTS);
			addDirectory(zip, PAYLOAD_DOCUMENTS + "source/");
			addDirectory(zip, PAYLOAD_DOCUMENTS + "lbd/");
			addDirectory(zip, PAYLOAD_TRIPLES);

			String sourceName = safeFileName(sourceIfc.getFileName().toString());
			documents.add(writeFile(zip, sourceIfc, "source/" + sourceName, sourceName,
					ifcFileType(sourceName), mediaType(sourceName)));
			documents.add(writeClasspathResource(zip, "/bot.ttl", "bot.ttl", "BOT 0.3.2 ontology", "TTL"));
			documents.add(writeOntologyModel(zip, propsOntology, "props.ttl", "Generated properties ontology", "TTL"));
			documents.add(writeModel(zip, generalPayload, "lbd/general.ttl",
					"BOT topology and spatial IFC attributes", "TTL"));
			documents.add(writeModel(zip, productPayload, "lbd/building-elements.ttl",
					"Product/BEO building elements and IFC attributes", "TTL"));
			documents.add(writeModel(zip, geometryPayload, "lbd/geometry.ttl",
					"Geometry representations and element-to-geometry links", "TTL"));
			documents.add(writeModel(zip, propertyPayload, "lbd/properties.ttl",
					"PROPS/OPM property and quantity sets", "TTL"));

			Model index = createIndex(containerId, documents);
			try {
				zip.putNextEntry(new ZipEntry("Index.rdf"));
				RDFDataMgr.write(new NonClosingOutputStream(zip), index, RDFFormat.RDFXML_PRETTY);
				zip.closeEntry();
			} finally {
				index.close();
			}
		} finally {
			propsOntology.close();
			generalPayload.close();
			productPayload.close();
			geometryPayload.close();
			propertyPayload.close();
		}
	}

	private static Model extractGeometry(Model... payloads) {
		Model union = ModelFactory.createDefaultModel();
		Model geometry = ModelFactory.createDefaultModel();
		for (Model payload : payloads) {
			union.add(payload);
			geometry.setNsPrefixes(payload.getNsPrefixMap());
		}

		Resource geometryArtifact = union.createResource("https://w3id.org/ifctolbd/geometry#GeometryArtifact");
		Property hasArtifact = union.createProperty("https://w3id.org/ifctolbd/geometry#artifact");

		ArrayDeque<Resource> pending = new ArrayDeque<>();
		for (Property link : List.of(OMG.hasGeometry, GEO.hasGeometry)) {
			union.listStatements(null, link, (RDFNode) null).forEachRemaining(statement -> {
				geometry.add(statement);
				if (statement.getObject().isResource()) pending.add(statement.getResource());
			});
		}
		union.listResourcesWithProperty(RDF.type, GEO.Geometry).forEachRemaining(pending::add);
		union.listResourcesWithProperty(RDF.type, geometryArtifact).forEachRemaining(pending::add);

		Set<Resource> visited = new HashSet<>();
		while (!pending.isEmpty()) {
			Resource subject = pending.removeFirst();
			if (!visited.add(subject)) continue;
			union.listStatements(subject, null, (RDFNode) null).forEachRemaining(statement -> {
				geometry.add(statement);
				if (statement.getObject().isResource()
						&& (statement.getPredicate().equals(LBD.hasBoundingBox)
								|| statement.getPredicate().equals(hasArtifact)))
					pending.add(statement.getResource());
			});
		}

		for (Property derivedGeometry : List.of(LBD.containsInBoundingBox, IFCtoLBDMapping.connectionGeometry))
			union.listStatements(null, derivedGeometry, (RDFNode) null).forEachRemaining(geometry::add);

		for (Model payload : payloads) payload.remove(geometry);
		union.close();
		return geometry;
	}

	private static Set<String> findUsedGeneratedPropertyTerms(Model... models) {
		Set<String> declared = new LinkedHashSet<>();
		for (Model model : models) {
			model.listStatements(null, RDF.type, OWL.DatatypeProperty).forEachRemaining(statement -> {
				if (statement.getSubject().isURIResource()) declared.add(statement.getSubject().getURI());
			});
			model.listStatements(null, RDF.type, OWL.ObjectProperty).forEachRemaining(statement -> {
				if (statement.getSubject().isURIResource()) declared.add(statement.getSubject().getURI());
			});
		}
		declared.removeIf(uri -> {
			for (Model model : models) {
				if (model.contains(null, model.createProperty(uri))) return false;
			}
			return true;
		});
		return declared;
	}

	private static Model createGeneratedPropertiesOntology(Set<String> terms, Model... sources) {
		Model ontology = ModelFactory.createDefaultModel();
		for (Model source : sources) {
			ontology.setNsPrefixes(source.getNsPrefixMap());
			source.listStatements().filterKeep(statement -> statement.getSubject().isURIResource()
					&& terms.contains(statement.getSubject().getURI())).forEachRemaining(ontology::add);
		}
		return ontology;
	}

	private static Model withoutOntologyDefinitions(Model source, Set<String> terms) {
		Model payload = ModelFactory.createDefaultModel();
		payload.setNsPrefixes(source.getNsPrefixMap());
		source.listStatements().filterDrop(statement -> statement.getSubject().isURIResource()
				&& terms.contains(statement.getSubject().getURI())).forEachRemaining(payload::add);
		return payload;
	}

	private static DocumentDescription writeFile(ZipOutputStream zip, Path source, String relativeName,
			String displayName, String fileType, String mediaType) throws IOException {
		MessageDigest digest = sha256();
		zip.putNextEntry(new ZipEntry(PAYLOAD_DOCUMENTS + relativeName));
		try (OutputStream output = new DigestOutputStream(new NonClosingOutputStream(zip), digest)) {
			Files.copy(source, output);
		}
		zip.closeEntry();
		return new DocumentDescription(relativeName, displayName, fileType, mediaType,
				HexFormat.of().formatHex(digest.digest()));
	}

	private static DocumentDescription writeClasspathResource(ZipOutputStream zip, String resourceName,
			String relativeName, String displayName, String fileType) throws IOException {
		MessageDigest digest = sha256();
		try (InputStream input = IcddPackageWriter.class.getResourceAsStream(resourceName)) {
			if (input == null) throw new IOException("Bundled ontology resource is unavailable: " + resourceName);
			zip.putNextEntry(new ZipEntry(ONTOLOGY_RESOURCES + relativeName));
			try (OutputStream output = new DigestOutputStream(new NonClosingOutputStream(zip), digest)) {
				input.transferTo(output);
			}
			zip.closeEntry();
		}
		return new DocumentDescription(relativeName, displayName, fileType, "text/turtle",
				HexFormat.of().formatHex(digest.digest()));
	}

	private static DocumentDescription writeOntologyModel(ZipOutputStream zip, Model model, String relativeName,
			String displayName, String fileType) throws IOException {
		return writeModelAt(zip, model, ONTOLOGY_RESOURCES + relativeName, relativeName, displayName, fileType);
	}

	private static DocumentDescription writeModel(ZipOutputStream zip, Model model, String relativeName,
			String displayName, String fileType) throws IOException {
		return writeModelAt(zip, model, PAYLOAD_DOCUMENTS + relativeName, relativeName, displayName, fileType);
	}

	private static DocumentDescription writeModelAt(ZipOutputStream zip, Model model, String entryName,
			String relativeName, String displayName, String fileType) throws IOException {
		MessageDigest digest = sha256();
		zip.putNextEntry(new ZipEntry(entryName));
		try (OutputStream output = new DigestOutputStream(new NonClosingOutputStream(zip), digest)) {
			RDFUtils.writeRdf(output, model, RDFFormat.TURTLE_PRETTY);
		}
		zip.closeEntry();
		return new DocumentDescription(relativeName, displayName, fileType, "text/turtle",
				HexFormat.of().formatHex(digest.digest()));
	}

	private static Model createIndex(String containerId, List<DocumentDescription> documents) {
		Model model = ModelFactory.createDefaultModel();
		model.setNsPrefix("ct", CT);
		model.setNsPrefix("owl", OWL.NS);
		model.setNsPrefix("rdf", RDF.getURI());

		Property containsDocument = model.createProperty(CT, "containsDocument");
		Property belongsToContainer = model.createProperty(CT, "belongsToContainer");
		Resource container = model.createResource(containerId)
				.addProperty(RDF.type, model.createResource(CT + "ContainerDescription"))
				.addProperty(OWL.imports, model.createResource(CONTAINER_ONTOLOGY))
				.addLiteral(model.createProperty(CT, "conformanceIndicator"), "ICDD-Part1-Container")
				.addLiteral(model.createProperty(CT, "description"),
						"IFC source and Linked Building Data submodels generated by IFCtoLBD")
				.addLiteral(model.createProperty(CT, "creationDate"),
						ResourceFactory.createTypedLiteral(Instant.now().toString(), XSDDatatype.XSDdateTime));
		Resource publisher = model.createResource(containerId + "/publisher")
				.addProperty(RDF.type, model.createResource(CT + "Organisation"))
				.addLiteral(model.createProperty(CT, "name"), "IFCtoLBD");
		container.addProperty(model.createProperty(CT, "publishedBy"), publisher);

		for (int i = 0; i < documents.size(); i++) {
			DocumentDescription document = documents.get(i);
			Resource resource = model.createResource(containerId + "/document/" + (i + 1))
					.addProperty(RDF.type, model.createResource(CT + "InternalDocument"))
					.addProperty(RDF.type, model.createResource(CT + "SecuredDocument"))
					.addLiteral(model.createProperty(CT, "name"), document.displayName())
					.addLiteral(model.createProperty(CT, "filename"), document.relativeName())
					.addLiteral(model.createProperty(CT, "filetype"), document.fileType())
					.addLiteral(model.createProperty(CT, "format"), document.mediaType())
					.addLiteral(model.createProperty(CT, "checksumAlgorithm"), "SHA-256")
					.addLiteral(model.createProperty(CT, "checksum"), document.checksum())
					.addProperty(belongsToContainer, container);
			container.addProperty(containsDocument, resource);
		}
		return model;
	}

	private static void addDirectory(ZipOutputStream zip, String name) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.closeEntry();
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static String safeFileName(String value) {
		String sanitized = value.replace('\\', '_').replace('/', '_');
		return sanitized.isBlank() ? "source.ifc" : sanitized;
	}

	private static String ifcFileType(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".ifcxml") || lower.endsWith(".xml")) return "IFC-XML";
		if (lower.endsWith(".ifcjson") || lower.endsWith(".json")) return "IFC-JSON";
		if (lower.endsWith(".ifczip")) return "IFC-ZIP";
		return "IFC";
	}

	private static String mediaType(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".ifcxml") || lower.endsWith(".xml")) return "application/xml";
		if (lower.endsWith(".ifcjson") || lower.endsWith(".json")) return "application/json";
		if (lower.endsWith(".ifczip")) return "application/zip";
		return "application/step";
	}

	private record DocumentDescription(String relativeName, String displayName, String fileType,
			String mediaType, String checksum) {
	}

	/** Prevents a serializer or digest stream from closing the surrounding ZIP. */
	private static final class NonClosingOutputStream extends FilterOutputStream {
		private NonClosingOutputStream(OutputStream output) {
			super(output);
		}

		@Override
		public void close() throws IOException {
			flush();
		}
	}
}
