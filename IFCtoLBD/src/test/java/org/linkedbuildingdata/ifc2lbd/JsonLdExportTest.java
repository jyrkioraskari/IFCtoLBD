package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.core.utils.JsonLdExport;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;

class JsonLdExportTest {

    @Test
    void compactJsonLdUsesBundledContextAndPreservesGraph() throws Exception {
        Model source = sampleModel();
        String jsonLd = export(source, JsonLdExport.Mode.COMPACT);
        assertTrue(jsonLd.contains("\"containsElement\""));
        assertTrue(jsonLd.contains("\"asMTL_alpha\""));
        assertTrue(jsonLd.contains("\"lbd:z-max\""));
        assertTrue(jsonLd.contains("\"@context\""));
        assertRoundTrip(source, jsonLd);
    }

    @Test
    void flattenedJsonLdPreservesGraph() throws Exception {
        Model source = sampleModel();
        String jsonLd = export(source, JsonLdExport.Mode.FLATTENED);
        assertTrue(jsonLd.contains("\"@id\""));
        assertTrue(jsonLd.contains("\"@context\""));
        assertRoundTrip(source, jsonLd);
    }

    private static Model sampleModel() {
        Model model = ModelFactory.createDefaultModel();
        Resource storey = model.createResource("https://example.org/building/storey-1");
        Resource wall = model.createResource("https://example.org/building/wall-42");
        storey.addProperty(org.apache.jena.vocabulary.RDF.type, model.createResource(BOT.ns + "Storey"));
        storey.addProperty(model.createProperty(BOT.ns + "containsElement"), wall);
        storey.addLiteral(model.createProperty("https://lbd.org/#asMTL_alpha"), 1.0d);
        storey.addLiteral(model.createProperty("https://linkedbuildingdata.org/LBD#z-max"), 2.795d);
        return model;
    }

    private static String export(Model model, JsonLdExport.Mode mode) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            JsonLdExport.write(output, model, mode);
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static void assertRoundTrip(Model expected, String jsonLd) {
        Model actual = ModelFactory.createDefaultModel();
        RDFDataMgr.read(actual, new java.io.StringReader(jsonLd), null, Lang.JSONLD11);
        assertTrue(expected.isIsomorphicWith(actual), "JSON-LD mode must preserve the RDF graph");
    }
}
