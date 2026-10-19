package org.linkedbuildingdata.ifc2lbd.core.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;

import jakarta.json.Json;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

/**
 * JSON-LD 1.1 exports intended for JSON consumers. The plain mode retains the
 * established Jena serializer; compact and flattened modes use the bundled,
 * versioned IFCtoLBD context.
 */
public final class JsonLdExport {

    public enum Mode { PLAIN, COMPACT, FLATTENED }

    public static final String CONTEXT_RESOURCE = "/jsonld/ifctolbd-v2.jsonld";

    private JsonLdExport() {
    }

    public static void write(OutputStream output, Model model, Mode mode) throws IOException {
        if (mode == Mode.PLAIN) {
            RDFWriter.create().source(model).format(RDFFormat.JSONLD11_PRETTY).output(output);
            return;
        }

        try (JsonWriter writer = Json.createWriterFactory(
                java.util.Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(output)) {
            writer.write(compactOrFlatten(model, mode));
        } catch (JsonLdError e) {
            throw new IOException("Could not produce JSON-LD " + mode.name().toLowerCase() + " output", e);
        }
    }

    private static JsonStructure compactOrFlatten(Model model, Mode mode) throws JsonLdError, IOException {
        JsonDocument input = JsonDocument.of(new StringReader(
                RDFWriter.create().source(model).format(RDFFormat.JSONLD11_PRETTY).asString()));
        JsonStructure context = context();
        return switch (mode) {
            case COMPACT -> JsonLd.compact(input, JsonDocument.of(context)).get();
            case FLATTENED -> JsonLd.flatten(input).context(context).get();
            case PLAIN -> throw new IllegalArgumentException("Plain JSON-LD does not require processing");
        };
    }

    private static JsonStructure context() throws IOException, JsonLdError {
        try (InputStream stream = JsonLdExport.class.getResourceAsStream(CONTEXT_RESOURCE)) {
            if (stream == null) throw new IOException("Missing bundled JSON-LD context: " + CONTEXT_RESOURCE);
            return JsonDocument.of(new StringReader(new String(stream.readAllBytes(), StandardCharsets.UTF_8)))
                    .getJsonContent().orElseThrow(() -> new IOException("Invalid bundled JSON-LD context"));
        }
    }
}
