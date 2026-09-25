package org.linkedbuildingdata.lbdtoifc;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

final class GeometryLoader {
    record Result(Map<Resource, ObjMesh> meshes, int linkedProducts) {
        Result {
            meshes = Map.copyOf(meshes);
        }
    }

    private GeometryLoader() {}

    static Result load(Iterable<Resource> products, LbdToIfcConverter.Options options, List<String> warnings) {
        Map<Resource, ObjMesh> meshes = new LinkedHashMap<>();
        int linkedProducts = 0;
        for (Resource product : products) {
            List<Resource> geometries = product.listProperties(Vocabulary.HAS_GEOMETRY)
                    .mapWith(Statement::getObject)
                    .filterKeep(RDFNode::isResource)
                    .mapWith(RDFNode::asResource)
                    .toList().stream()
                    .sorted(Comparator.comparing(GeometryLoader::key))
                    .toList();
            if (geometries.isEmpty()) {
                continue;
            }
            linkedProducts++;
            Optional<ObjMesh> mesh = Optional.empty();
            for (Resource geometry : geometries) {
                mesh = embedded(geometry, options, warnings, product);
                if (mesh.isEmpty()) {
                    mesh = artifact(geometry, options, warnings, product);
                }
                if (mesh.isPresent()) {
                    meshes.put(product, mesh.get());
                    break;
                }
            }
            if (mesh.isEmpty()) {
                warnings.add("No usable OBJ geometry was found for " + key(product) + ".");
            }
        }
        return new Result(meshes, linkedProducts);
    }

    private static Optional<ObjMesh> embedded(Resource geometry, LbdToIfcConverter.Options options,
            List<String> warnings, Resource product) {
        List<Statement> values = geometry.listProperties(Vocabulary.AS_OBJ).toList();
        for (Statement statement : values) {
            if (!statement.getObject().isLiteral()) {
                continue;
            }
            String encoded = statement.getLiteral().getLexicalForm();
            if (encoded.length() > encodedLimit(options.maximumObjBytes())) {
                warnings.add("Embedded OBJ for " + key(product) + " exceeds the encoded size limit.");
                continue;
            }
            try {
                byte[] content = Base64.getMimeDecoder().decode(encoded);
                return Optional.of(parse(content, options));
            } catch (IllegalArgumentException | IOException invalid) {
                warnings.add("Embedded OBJ for " + key(product) + " was rejected: " + invalid.getMessage());
            }
        }
        return Optional.empty();
    }

    private static Optional<ObjMesh> artifact(Resource geometry, LbdToIfcConverter.Options options,
            List<String> warnings, Resource product) {
        List<Resource> artifacts = geometry.listProperties(Vocabulary.GEOMETRY_ARTIFACT)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .toList().stream()
                .sorted(Comparator.comparing(GeometryLoader::key))
                .toList();
        for (Resource artifact : artifacts) {
            String mediaType = literal(artifact, Vocabulary.ARTIFACT_MEDIA_TYPE);
            if (mediaType != null && !mediaType.equalsIgnoreCase("model/obj")) {
                continue;
            }
            if (options.artifactRoot() == null) {
                warnings.add("OBJ artifact for " + key(product)
                        + " was not loaded because no artifact root was configured.");
                continue;
            }
            Path candidate;
            try {
                URI uri = URI.create(artifact.getURI());
                String uriPath = uri.getPath();
                if (uriPath == null || Path.of(uriPath).getFileName() == null) {
                    throw new IllegalArgumentException("artifact URI has no filename");
                }
                Path root = options.artifactRoot().toAbsolutePath().normalize();
                candidate = root.resolve(Path.of(uriPath).getFileName()).normalize();
                if (!candidate.startsWith(root)) {
                    throw new IllegalArgumentException("artifact path escapes its configured root");
                }
            } catch (RuntimeException invalidUri) {
                warnings.add("OBJ artifact for " + key(product) + " was rejected: " + invalidUri.getMessage());
                continue;
            }
            try {
                long size = Files.size(candidate);
                if (size > options.maximumObjBytes()) {
                    throw new IOException("artifact exceeds the byte limit");
                }
                byte[] content = Files.readAllBytes(candidate);
                String expectedHash = literal(artifact, Vocabulary.ARTIFACT_SHA256);
                if (expectedHash != null && !expectedHash.equalsIgnoreCase(sha256(content))) {
                    throw new IOException("SHA-256 does not match the graph metadata");
                }
                return Optional.of(parse(content, options));
            } catch (IOException invalid) {
                warnings.add("OBJ artifact for " + key(product) + " was rejected: " + invalid.getMessage());
            }
        }
        return Optional.empty();
    }

    private static ObjMesh parse(byte[] content, LbdToIfcConverter.Options options) throws IOException {
        if (content.length > options.maximumObjBytes()) {
            throw new IOException("OBJ exceeds the byte limit");
        }
        return ObjMesh.parse(content, options.maximumVertices(), options.maximumTriangles());
    }

    private static int encodedLimit(long decodedLimit) {
        long limit = decodedLimit > (Integer.MAX_VALUE - 4L) / 4L * 3L
                ? Integer.MAX_VALUE : ((decodedLimit + 2L) / 3L) * 4L + 1024L;
        return (int) Math.min(Integer.MAX_VALUE, limit);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String literal(Resource resource, org.apache.jena.rdf.model.Property property) {
        Statement statement = resource.getProperty(property);
        return statement != null && statement.getObject().isLiteral() ? statement.getString() : null;
    }

    private static String key(Resource resource) {
        return resource.isURIResource() ? resource.getURI() : "_:" + resource.getId().getLabelString();
    }
}
