package org.linkedbuildingdata.lbdtoifc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class LbdToIfcConverterTest {
    private static final String CUBE_OBJ = """
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            v 0 0 1
            v 1 0 1
            v 1 1 1
            v 0 1 1
            f 1 4 3
            f 1 3 2
            f 5 6 7
            f 5 7 8
            f 1 2 6
            f 1 6 5
            f 2 3 7
            f 2 7 6
            f 3 4 8
            f 3 8 7
            f 4 1 5
            f 4 5 8
            """;

    @Test
    void reconstructsHierarchyElementsIdentityAndProperties() throws Exception {
        Model model = ModelFactory.createDefaultModel();
        try (var input = getClass().getResourceAsStream("/complete-building.ttl")) {
            RDFDataMgr.read(model, input, Lang.TURTLE);
        }
        StringWriter output = new StringWriter();

        ConversionReport report = new LbdToIfcConverter().convert(model, output, "complete-building.ttl",
                new LbdToIfcConverter.Options("Example conversion"));
        String ifc = output.toString();

        assertEquals(1, report.sites());
        assertEquals(1, report.buildings());
        assertEquals(1, report.storeys());
        assertEquals(1, report.spaces());
        assertEquals(3, report.elements());
        assertEquals(1, report.propertySets());
        assertTrue(ifc.startsWith("ISO-10303-21;"));
        assertTrue(ifc.contains("FILE_SCHEMA(('IFC4'))"));
        assertTrue(ifc.contains("IFCSITE('1xS3BCk291UvhgP2a6eflN'"));
        assertTrue(ifc.contains("IFCBUILDING('2O2Fr$t4X7Zf8NOew3FKau'"));
        assertTrue(ifc.contains("IFCWALL('0BTBFw6f90Nfh9rP1dl_3C'"));
        assertTrue(ifc.contains("IFCDOOR("));
        assertTrue(ifc.contains("IFCWINDOW("));
        assertTrue(ifc.contains("'Entrance''s door'"));
        assertTrue(ifc.contains("IFCRELAGGREGATES("));
        assertTrue(ifc.contains("IFCRELCONTAINEDINSPATIALSTRUCTURE("));
        assertTrue(ifc.contains("IFCPROPERTYSINGLEVALUE('isExternal',$,IFCBOOLEAN(.T.),$)"));
        assertTrue(ifc.contains("IFCPROPERTYSINGLEVALUE('thermalTransmittance',$,IFCREAL(0.18),$)"));
        assertTrue(ifc.endsWith("END-ISO-10303-21;\n"));
        assertTrue(report.warnings().stream().anyMatch(warning -> warning.contains("without geometry")));
    }

    @Test
    void synthesizesSpatialHierarchyAndGeneratesValidStableGuids() throws Exception {
        String turtle = """
                @prefix bot: <https://w3id.org/bot#> .
                @prefix beo: <https://pi.pauwel.be/voc/buildingelement#> .
                @prefix ex: <https://example.org/> .
                ex:beam a bot:Element, beo:Beam .
                """;
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(turtle), null, Lang.TURTLE);
        StringWriter first = new StringWriter();
        StringWriter second = new StringWriter();
        LbdToIfcConverter converter = new LbdToIfcConverter();

        ConversionReport report = converter.convert(model, first, "minimal.ttl",
                LbdToIfcConverter.Options.defaults());
        converter.convert(model, second, "minimal.ttl", LbdToIfcConverter.Options.defaults());

        assertEquals(first.toString(), second.toString());
        assertTrue(first.toString().contains("IFCBEAM("));
        assertEquals(1, report.sites());
        assertEquals(1, report.buildings());
        assertEquals(1, report.storeys());
        assertEquals(3, report.warnings().stream().filter(warning -> warning.contains("synthesized")).count());
        assertFalse(first.toString().contains("IFCBEAM($"));
    }

    @Test
    void readsJsonLdByExtensionAndWritesAtomically(@TempDir Path temporaryDirectory) throws Exception {
        Path input = temporaryDirectory.resolve("model.jsonld");
        Path output = temporaryDirectory.resolve("model.ifc");
        Files.writeString(input, """
                {
                  "@context": {"bot": "https://w3id.org/bot#", "ex": "https://example.org/"},
                  "@id": "ex:building", "@type": "bot:Building"
                }
                """);

        ConversionReport report = new LbdToIfcConverter().convert(input, output,
                LbdToIfcConverter.Options.defaults());

        assertEquals(1, report.buildings());
        assertTrue(Files.readString(output).contains("IFCBUILDING("));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".lbdtoifc-")));
        }

        String original = Files.readString(output);
        CommandLine commandLine = new CommandLine(new LbdToIfcCli());
        commandLine.setErr(new PrintWriter(new StringWriter()));
        int exitCode = commandLine.execute(input.toString(), output.toString());
        assertEquals(2, exitCode);
        assertEquals(original, Files.readString(output));
    }

    @Test
    void convertsEmbeddedObjToClosedIfcTessellation() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(CUBE_OBJ.getBytes(StandardCharsets.UTF_8));
        String turtle = """
                @prefix bot: <https://w3id.org/bot#> .
                @prefix beo: <https://pi.pauwel.be/voc/buildingelement#> .
                @prefix omg: <https://w3id.org/omg#> .
                @prefix fog: <https://w3id.org/fog#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex: <https://example.org/> .
                ex:wall a bot:Element, beo:Wall ; omg:hasGeometry ex:geometry .
                ex:geometry fog:asObj_v3.0-obj "%s"^^xsd:base64Binary .
                """.formatted(encoded);
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(turtle), null, Lang.TURTLE);
        StringWriter output = new StringWriter();

        ConversionReport report = new LbdToIfcConverter().convert(model, output, "cube.ttl",
                LbdToIfcConverter.Options.defaults());
        String ifc = output.toString();

        assertEquals(1, report.geometries());
        assertEquals(8, report.geometryVertices());
        assertEquals(12, report.geometryTriangles());
        assertTrue(ifc.contains("IFCGEOMETRICREPRESENTATIONSUBCONTEXT('Body','Model'"));
        assertTrue(ifc.contains("IFCCARTESIANPOINTLIST3D(((0.,0.,0.),(1.,0.,0.)"));
        assertTrue(ifc.contains("IFCTRIANGULATEDFACESET("));
        assertTrue(ifc.contains(",$,.T.,((1,4,3),(1,3,2)"));
        assertTrue(ifc.contains("IFCSHAPEREPRESENTATION("));
        assertTrue(ifc.contains(",'Body','Tessellation',("));
        assertFalse(report.warnings().stream().anyMatch(warning -> warning.contains("without geometry")));
    }

    @Test
    void convertsVerifiedLocalObjArtifact(@TempDir Path temporaryDirectory) throws Exception {
        byte[] obj = CUBE_OBJ.getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(obj));
        Files.write(temporaryDirectory.resolve(hash + ".obj"), obj);
        Path input = temporaryDirectory.resolve("artifact.ttl");
        Path output = temporaryDirectory.resolve("artifact.ifc");
        Files.writeString(input, """
                @prefix bot: <https://w3id.org/bot#> .
                @prefix omg: <https://w3id.org/omg#> .
                @prefix geometry: <https://w3id.org/ifctolbd/geometry#> .
                @prefix ex: <https://example.org/> .
                ex:item a bot:Element ; omg:hasGeometry ex:g .
                ex:g geometry:artifact <https://example.org/artifacts/%s.obj> .
                <https://example.org/artifacts/%s.obj>
                    geometry:mediaType "model/obj" ; geometry:sha256 "%s" .
                """.formatted(hash, hash, hash));

        ConversionReport report = new LbdToIfcConverter().convert(input, output,
                LbdToIfcConverter.Options.defaults());

        assertEquals(1, report.geometries());
        assertEquals(8, report.geometryVertices());
        assertEquals(12, report.geometryTriangles());
        assertTrue(Files.readString(output).contains("IFCTRIANGULATEDFACESET("));
    }

    @Test
    void rejectsMalformedObjWithoutLosingSemanticElement() throws Exception {
        String encoded = Base64.getEncoder().encodeToString("v 0 0 0\nf 1 2 3\n"
                .getBytes(StandardCharsets.UTF_8));
        String turtle = """
                @prefix bot: <https://w3id.org/bot#> .
                @prefix omg: <https://w3id.org/omg#> .
                @prefix fog: <https://w3id.org/fog#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex: <https://example.org/> .
                ex:item a bot:Element ; omg:hasGeometry ex:g .
                ex:g fog:asObj_v3.0-obj "%s"^^xsd:base64Binary .
                """.formatted(encoded);
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(turtle), null, Lang.TURTLE);
        StringWriter output = new StringWriter();

        ConversionReport report = new LbdToIfcConverter().convert(model, output, "bad.ttl",
                LbdToIfcConverter.Options.defaults());

        assertEquals(0, report.geometries());
        assertTrue(output.toString().contains("IFCBUILDINGELEMENTPROXY("));
        assertFalse(output.toString().contains("IFCTRIANGULATEDFACESET("));
        assertTrue(report.warnings().stream().anyMatch(warning -> warning.contains("out of range")));
    }
}
