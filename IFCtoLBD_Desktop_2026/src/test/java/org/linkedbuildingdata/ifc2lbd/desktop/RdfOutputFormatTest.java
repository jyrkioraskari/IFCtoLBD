package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RdfOutputFormatTest {
	private static final String ELEMENT_URI = "urn:element:wall_12345678-1234-1234-1234-123456789abc";
	private static final String GEOMETRY_URI = "urn:geometry:1";
	private static final String OBJ_PROPERTY = "https://w3id.org/fog#asObj_v3.0-obj";
	private static final String MTL_COLOR_PROPERTY = "https://lbd.org/#asMTL_kd";
	private static final String OBJ = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n";

	@Test
	void readsTurtleAndJsonLdOutputModels(@TempDir Path directory) throws Exception {
		for (RDFFormat format : new RDFFormat[] { RDFFormat.TURTLE_BLOCKS, RDFFormat.JSONLD11_PRETTY }) {
			File output = writeGeometry(directory, format);
			Model loaded = (Model) invokeStatic("readOutputModel", new Class<?>[] { File.class }, output);
			try {
				assertTrue(loaded.contains(null, loaded.createProperty(OBJ_PROPERTY)));
			} finally {
				loaded.close();
			}
		}
	}

	@Test
	void extractsGeometryFromTurtleAndJsonLd(@TempDir Path directory) throws Exception {
		IFCtoLBDController controller = new IFCtoLBDController();
		try {
			for (RDFFormat format : new RDFFormat[] { RDFFormat.TURTLE_BLOCKS, RDFFormat.JSONLD11_PRETTY }) {
				Object preview = invoke("loadPreviewMesh", controller, new Class<?>[] { File.class },
						writeGeometry(directory, format));
				assertEquals(1, invokeRecordAccessor(preview, "objectCount"));
				assertEquals(1, invokeRecordAccessor(preview, "triangleCount"));
				assertEquals(ELEMENT_URI, ((String[]) invokeRecordAccessor(preview, "triangleResourceUris"))[0]);
				@SuppressWarnings("unchecked")
				Map<String, Object> resources = (Map<String, Object>) invokeRecordAccessor(preview, "resources");
				Object details = resources.get(ELEMENT_URI);
				assertEquals("Wall", invokeRecordAccessor(details, "ifcType"));
				assertEquals("12345678-1234-1234-1234-123456789abc", invokeRecordAccessor(details, "guid"));
				@SuppressWarnings("unchecked")
				Map<String, String> aliases = (Map<String, String>) invokeRecordAccessor(preview, "resourceAliases");
				assertEquals(ELEMENT_URI, aliases.get(GEOMETRY_URI));
			}
		} finally {
			controller.shutdown();
		}
	}

	private static File writeGeometry(Path directory, RDFFormat format) throws Exception {
		String extension = format.equals(RDFFormat.JSONLD11_PRETTY) ? ".jsonld" : ".ttl";
		File output = directory.resolve("geometry" + extension).toFile();
		Model model = ModelFactory.createDefaultModel();
		try {
			var geometry = model.createResource(GEOMETRY_URI)
					.addLiteral(model.createProperty(OBJ_PROPERTY),
							Base64.getEncoder().encodeToString(OBJ.getBytes(StandardCharsets.UTF_8)))
					.addLiteral(model.createProperty(MTL_COLOR_PROPERTY), "#336699");
			model.createResource(ELEMENT_URI)
					.addProperty(org.apache.jena.vocabulary.RDF.type, model.createResource("https://w3id.org/product#Wall"))
					.addProperty(model.createProperty("https://w3id.org/omg#hasGeometry"), geometry)
					.addLiteral(model.createProperty("urn:test:name"), "Exterior wall");
			try (OutputStream stream = Files.newOutputStream(output.toPath())) {
				RDFDataMgr.write(stream, model, format);
			}
		} finally {
			model.close();
		}
		return output;
	}

	private static Object invoke(String name, Object receiver, Class<?>[] parameters, Object... arguments)
			throws Exception {
		Method method = IFCtoLBDController.class.getDeclaredMethod(name, parameters);
		method.setAccessible(true);
		return method.invoke(receiver, arguments);
	}

	private static Object invokeStatic(String name, Class<?>[] parameters, Object... arguments) throws Exception {
		return invoke(name, null, parameters, arguments);
	}

	private static Object invokeRecordAccessor(Object record, String name) throws Exception {
		Method method = record.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		return method.invoke(record);
	}
}
