/*
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.ugent;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lewismc
 * 
 */
public class TestIfcSpfReader {

    private IfcSpfReader reader;

    // private static final String testInputTTL =
    // "showfiles/Barcelona_Pavilion.ttl";
    // private static final String testOutputTTL =
    // "target/test_Barcelona_Pavilion.ttl";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        reader = new IfcSpfReader();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() {
        reader = null;
    }

    /**
     * Test method for {@link be.ugent.IfcSpfReader#showFiles(java.lang.String)}
     * .
     */
    @Test
  public final void testShowFiles() {
    List<String> fileList = IfcSpfReader.showFiles(getClass().getClassLoader().getResource("showfiles").getFile());
    List<String> files = new ArrayList<>();
    for (String file : fileList) {
      files.add(file.substring(file.lastIndexOf(File.separatorChar)+1));
    }
    java.util.Collections.sort(files);
    StringBuilder sb = new StringBuilder();
    for (String s : files)
    {
      sb.append(s);
      sb.append(", ");
    }
    Assert.assertEquals(
            "20160414office_model_CV2_fordesign.ifc, 20160414office_model_CV2_fordesign.ttl, Barcelona_Pavilion.ifc, Barcelona_Pavilion.ttl, ootest.txt, ",
            sb.toString());
  }

    /**
     * Test method for {@link be.ugent.IfcSpfReader#slurp(java.io.InputStream)}.
     */
    @Test
    public final void testSlurp() {
        // reader.slurp(in)
    }

    @Test
    public final void readsIfcJson() throws IOException {
        Path input = Files.createTempFile("ifctordf-", ".ifcjson");
        Path output = Files.createTempFile("ifctordf-", ".ttl");
        try {
            Files.writeString(input, """
                    {"type":"ifcJSON","schemaIdentifier":"IFC4","data":[
                      {"type":"IfcWall","globalId":"1hOSvn6df7F8_7GcBWlN4K","name":"JSON wall","predefinedType":"STANDARD"}
                    ]}
                    """, StandardCharsets.UTF_8);
            Assert.assertEquals("IFC4_ADD2", IfcSpfReader.getExpressSchema(input.toString()));
            reader.setup(input.toString());
            reader.convert(input.toString(), output.toString(), "https://example.org/", false);
            String rdf = Files.readString(output, StandardCharsets.UTF_8);
            Assert.assertTrue(rdf, rdf.contains("IfcWall_1"));
            Assert.assertTrue(rdf, rdf.contains("JSON wall"));
            Assert.assertTrue(rdf, rdf.contains("1hOSvn6df7F8_7GcBWlN4K"));
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    @Test
    public final void readsIfcXml() throws IOException {
        Path input = Files.createTempFile("ifctordf-", ".ifcxml");
        Path output = Files.createTempFile("ifctordf-", ".ttl");
        try {
            Files.writeString(input, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <IfcWall xmlns="https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2/XML/IFC4_ADD2.xsd"
                        type="IfcWall" globalId="1hOSvn6df7F8_7GcBWlN4K" name="XML wall" predefinedType="STANDARD"/>
                    """, StandardCharsets.UTF_8);
            Assert.assertEquals("IFC4_ADD2", IfcSpfReader.getExpressSchema(input.toString()));
            reader.setup(input.toString());
            reader.convert(input.toString(), output.toString(), "https://example.org/", false);
            String rdf = Files.readString(output, StandardCharsets.UTF_8);
            Assert.assertTrue(rdf, rdf.contains("IfcWall_1"));
            Assert.assertTrue(rdf, rdf.contains("XML wall"));
            Assert.assertTrue(rdf, rdf.contains("1hOSvn6df7F8_7GcBWlN4K"));
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    @Test
    public final void detectsBomPrefixedStructuredIfc() throws IOException {
        Path json = Files.createTempFile("ifctordf-bom-", ".ifcjson");
        Path xml = Files.createTempFile("ifctordf-bom-", ".ifcxml");
        try {
            byte[] bom = { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };
            Files.write(json, bom);
            Files.writeString(json, "{\"schemaIdentifier\":\"IFC4\",\"data\":[]}",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            Files.write(xml, bom);
            Files.writeString(xml, "<ifcXML schemaIdentifier=\"IFC4\"/>",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

            Assert.assertEquals("IFC4_ADD2", IfcSpfReader.getExpressSchema(json.toString()));
            Assert.assertEquals("IFC4_ADD2", IfcSpfReader.getExpressSchema(xml.toString()));
        } finally {
            Files.deleteIfExists(json);
            Files.deleteIfExists(xml);
        }
    }

    @Test
    public final void directorySelectionSkipsLfsPointersAndAvoidsOutputCollisions() throws IOException {
        Path directory = Files.createTempDirectory("ifctordf-directory-");
        try {
            Path pointer = directory.resolve("model.ifc");
            Path json = directory.resolve("model.json");
            Path xml = directory.resolve("model.xml");
            Files.writeString(pointer, """
                    version https://git-lfs.github.com/spec/v1
                    oid sha256:0000000000000000000000000000000000000000000000000000000000000000
                    size 1234
                    """, StandardCharsets.UTF_8);
            Files.writeString(json, "{\"type\":\"IfcWall\"}", StandardCharsets.UTF_8);
            Files.writeString(xml, "<IfcWall type=\"IfcWall\"/>", StandardCharsets.UTF_8);

            List<String> selected = IfcSpfReader.selectDirectoryInputs(IfcSpfReader.showFiles(directory.toString()));

            Assert.assertEquals(1, selected.size());
            Assert.assertEquals(json.toAbsolutePath().toString(), selected.get(0));
        } finally {
            for (Path path : Files.list(directory).toList()) Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public final void readsIsoSpecArchiveWallInXmlAndJson() throws IOException {
        Path archive = Path.of("ISO Spec archive");
        if (!Files.isDirectory(archive)) archive = Path.of("..", "ISO Spec archive");
        Assume.assumeTrue("ISO Spec archive is not available", Files.isDirectory(archive));

        for (String extension : new String[] { "xml", "json" }) {
            Path input = archive.resolve("wall-standard-case." + extension);
            Path output = Files.createTempFile("ifctordf-iso-", ".ttl");
            try {
                reader.setup(input.toString());
                reader.convert(input.toString(), output.toString(), "https://example.org/", false);
                String rdf = Files.readString(output, StandardCharsets.UTF_8);
                // A namespace-only result is not a successful model conversion.
                Assert.assertTrue(extension, rdf.contains("IfcProject"));
                Assert.assertTrue(extension, rdf.contains("IfcWall"));
                Assert.assertTrue(extension, Files.size(output) > 10_000);
            } finally {
                Files.deleteIfExists(output);
            }
        }
    }

    @Test
    public final void isoSpecArchiveTurtleFilesArePopulatedRdfGraphs() throws IOException {
        Path archive = Path.of("ISO Spec archive");
        if (!Files.isDirectory(archive)) archive = Path.of("..", "ISO Spec archive");
        Assume.assumeTrue("ISO Spec archive is not available", Files.isDirectory(archive));

        List<Path> turtleFiles;
        try (var files = Files.list(archive)) {
            turtleFiles = files.filter(path -> path.getFileName().toString().endsWith(".ttl"))
                    .sorted().toList();
        }
        Assert.assertEquals("Every ISO example should have Turtle output", 41, turtleFiles.size());
        for (Path turtle : turtleFiles) {
            Assert.assertFalse(turtle.toString(), IfcInputReader.isGitLfsPointer(turtle));
            Model model = RDFDataMgr.loadModel(turtle.toString());
            try {
                Assert.assertTrue(turtle.toString(), model.size() > 0);
            } finally {
                model.close();
            }
        }
    }

    @Test
    public final void materializesPixelTextureJsonAsIfcSpfGeometry() throws IOException {
        Path archive = Path.of("ISO Spec archive");
        if (!Files.isDirectory(archive)) archive = Path.of("..", "ISO Spec archive");
        Assume.assumeTrue("ISO Spec archive is not available", Files.isDirectory(archive));

        Path output = Files.createTempFile("pixel-texture-", ".ifc");
        try {
            IfcSpfReader.materializeSpf(archive.resolve("tessellation-with-pixel-texture.json"), output);
            String spf = Files.readString(output, StandardCharsets.UTF_8);
            Assert.assertTrue(spf.contains("FILE_SCHEMA(('IFC4'))"));
            Assert.assertTrue(spf.contains("IFCTRIANGULATEDFACESET("));
            Assert.assertTrue(spf.contains("IFCCARTESIANPOINTLIST3D((("));
            Assert.assertTrue(spf.contains("IFCUNITASSIGNMENT("));
            Assert.assertFalse(spf.contains("IFCCARTESIANPOINTLIST3D((#"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    /**
     * Test method for
     * {@link be.ugent.IfcSpfReader#convert(java.lang.String, java.lang.String, java.lang.String)}
     * .
     * 
     * @throws IOException
     *             if there is an error executing
     *             {@link TestIfcSpfReader#compareFileContents(String, String)}
     */
    
    //TODO The files need to be created once more.  They are missing  @base tags etc.
    //@Test
    public final void testConvertIFCFileToOutputTTL() throws IOException {
        final List<String> inputFiles;
        inputFiles = showAllFiles(getClass().getClassLoader().getResource("convertIFCFileToOutputTTL").getFile());

        for (int i = 0; i < inputFiles.size(); ++i) {
            final String inputFile = inputFiles.get(i);
            final String outputFileBase;
            final String outputFileNew;
            if (inputFile.endsWith(".ifc")) {
                outputFileBase = inputFile.substring(0, inputFile.length() - 4) + ".ttl";
                outputFileNew = "target" + outputFileBase.split("convertIFCFileToOutputTTL")[1];
                reader.setup(inputFile);
                reader.convert(inputFile, outputFileNew, "http://linkedbuildingdata.net/ifc/resources/",false);
                Assert.assertTrue(compareFileContents(outputFileBase, outputFileNew));
            }
        }
    }

    /**
     * Method to read the string contents of two files and compare for equality.
     * 
     * @param testInputTTL
     *            the expected input TTL
     * @param testOutputTTL
     *            the generated output TTL
     * @return true if contents are identical.
     * @throws IOException
     *             if there is an error loading method parameters
     * @throws URISyntaxException
     */
    private static boolean compareFileContents(String testInputTTL, String testOutputTTL) throws IOException {
        FileInputStream finInput = new FileInputStream(testInputTTL);
        @SuppressWarnings("resource")
        BufferedReader brInput = new BufferedReader(new InputStreamReader(finInput));
        StringBuilder sbInput = new StringBuilder();
        String lineInput;
        while ((lineInput = brInput.readLine()) != null) {
            sbInput.append(lineInput);
        }

        FileInputStream finOutput = new FileInputStream(testOutputTTL);
        @SuppressWarnings("resource")
        BufferedReader brOutput = new BufferedReader(new InputStreamReader(finOutput));
        StringBuilder sbOutput = new StringBuilder();
        String lineOutput;
        while ((lineOutput = brOutput.readLine()) != null) {
            sbOutput.append(lineOutput);
        }

        if (sbInput.toString().equals(sbOutput.toString())) {
            return true;
        }
		return false;
    }

    /**
     * List all files in a particular directory.
     * 
     * @param dir
     *            the input directory for which you wish to list file.
     * @return a {@link java.util.List} of Strings denoting files.
     */
	public static List<String> showAllFiles(String dir) {
		List<String> goodFiles = new ArrayList<>();

		File folder = new File(dir);
		File[] listOfFiles = folder.listFiles();
		if (listOfFiles == null) {
			return goodFiles;
		}

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile())
				goodFiles.add(listOfFiles[i].getAbsolutePath());
			else if (listOfFiles[i].isDirectory())
				goodFiles.addAll(showAllFiles(listOfFiles[i].getAbsolutePath()));
		}
		return goodFiles;
	}
}
