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
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.URISyntaxException;
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
                reader.convert(inputFile, outputFileNew, "http://linkedbuildingdata.net/ifc/resources/");
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
    private boolean compareFileContents(String testInputTTL, String testOutputTTL) throws IOException {
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
        } else {
            return false;
        }
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

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile())
				goodFiles.add(listOfFiles[i].getAbsolutePath());
			else if (listOfFiles[i].isDirectory())
				goodFiles.addAll(showAllFiles(listOfFiles[i].getAbsolutePath()));
		}
		return goodFiles;
	}
}
