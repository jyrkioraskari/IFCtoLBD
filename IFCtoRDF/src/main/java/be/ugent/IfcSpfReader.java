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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.web.HttpOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;

/**
 * Main method is the primary integration point for the IFCtoRDF codebase. See
 * method description for guidance on input parameters.
 */
public class IfcSpfReader {

    private static final Logger LOG = LoggerFactory.getLogger(IfcSpfReader.class);

    public static String DEFAULT_PATH = "";

    private boolean removeDuplicates = false;
    private static final int FLAG_BASEURI = 0;
    private static final int FLAG_DIR = 1;
    private static final int FLAG_KEEP_DUPLICATES = 2;

    // used in conversion
    private String ifcFile;
    private InputStream in = null;
    private String exp = "";
    protected String ontURI = "";
    private Map<String, EntityVO> ent;
    private Map<String, TypeVO> typ;

    /**
     * Primary integration point for the IFCtoRDF codebase. Run the method
     * without any input parameters for descriptions of runtime parameters.
     */
    public static void main(String[] args) throws IOException {
		String[] options = new String[] {"--baseURI", "--dir", "--keep-duplicates"};
		Boolean[] optionValues = new Boolean[] { false, false, false};

		String timeLog = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
		DEFAULT_PATH = "http://linkedbuildingdata.net/ifc/resources" + timeLog + "/";

		List<String> argsList = new ArrayList<>(Arrays.asList(args));
		for (int i = 0; i < options.length; ++i) {
			optionValues[i] = argsList.contains(options[i]);
		}

		// State of flags has been stored in optionValues. Remove them from our
		// option
		// strings in order to make testing the required amount of positional
		// arguments easier.
		for (String flag : options) {
			argsList.remove(flag);
		}

		int numRequiredOptions = 0;
		if(optionValues[FLAG_DIR])
			numRequiredOptions++;
		else
			numRequiredOptions = 2;
		if(optionValues[FLAG_BASEURI])
			numRequiredOptions++;

		if (argsList.size() != numRequiredOptions) {
			LOG.info("Usage:\n"
					+ "    IfcSpfReader [--baseURI <baseURI>] [--keep-duplicates] <input_file> <output_file>\n"
					+ "    IfcSpfReader [--baseURI <baseURI>] [--keep-duplicates] --dir <directory>\n");
			return;
		}

		final List<String> inputFiles;
		final List<String> outputFiles;
		String baseURI = "";

		if (optionValues[FLAG_DIR]) {
			if(optionValues[FLAG_BASEURI]){
				baseURI = argsList.get(0);
				inputFiles = showFiles(argsList.get(1));
				outputFiles = null;
			}
			else{
				baseURI = DEFAULT_PATH;
				inputFiles = showFiles(argsList.get(0));
				outputFiles = null;
			}
		} else {
			if(optionValues[FLAG_BASEURI]){
				baseURI = argsList.get(0);
				inputFiles = Arrays.asList(new String[] { argsList.get(1) });
				outputFiles = Arrays.asList(new String[] { argsList.get(2) });
			}
			else{
				baseURI = DEFAULT_PATH;
				inputFiles = Arrays.asList(new String[] { argsList.get(0) });
				outputFiles = Arrays.asList(new String[] { argsList.get(1) });
			}
		}

		for (int i = 0; i < inputFiles.size(); ++i) {
			final String inputFile = inputFiles.get(i);
			final String outputFile;
			if (inputFile.endsWith(".ifc")) {
				if (outputFiles == null) {
					outputFile = inputFile.substring(0, inputFile.length() - 4) + ".ttl";
				} else {
					outputFile = outputFiles.get(i);
				}

				IfcSpfReader r = new IfcSpfReader();

				r.removeDuplicates = !optionValues[FLAG_KEEP_DUPLICATES];

				LOG.info("Converting file: " + inputFile + "\r\n");

				r.setup(inputFile);
				r.convert(inputFile, outputFile, baseURI);
			}
		}

	}

    /**
     * List all files in a particular directory.
     * 
     * @param dir
     *            the input directory for which you wish to list file.
     * @return a {@link java.util.List} of Strings denoting files.
     */
    public static List<String> showFiles(String dir) {
		List<String> goodFiles = new ArrayList<>();

		File folder = new File(dir);
		File[] listOfFiles = folder.listFiles();

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile())
				goodFiles.add(listOfFiles[i].getAbsolutePath());
			else if (listOfFiles[i].isDirectory())
				goodFiles.addAll(showFiles(listOfFiles[i].getAbsolutePath()));
		}
		return goodFiles;
	}

    private static String getExpressSchema(String ifcFile) {
		try (FileInputStream fstream = new FileInputStream(ifcFile)) {
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			try {
				String strLine;
				while ((strLine = br.readLine()) != null) {
					if (strLine.length() > 0) {
						if (strLine.startsWith("FILE_SCHEMA")) {
							if (strLine.indexOf("IFC2X3") != -1)
								return "IFC2X3_TC1";
							if (strLine.indexOf("IFC4x3") != -1)
								return "IFC4x3_RC1";
							if (strLine.indexOf("IFC4X3") != -1)
								return "IFC4x3_RC1";
							if (strLine.indexOf("IFC4x3_RC1") != -1)
								return "IFC4x3_RC1";
							if (strLine.indexOf("IFC4X3_RC1") != -1)
								return "IFC4x3_RC1";
							if (strLine.indexOf("IFC4X1") != -1)
								return "IFC4x1";
							if (strLine.indexOf("IFC4x1") != -1)
								return "IFC4x1";
							if (strLine.indexOf("IFC4") != -1)
								return "IFC4_ADD1";
							else
								return "";
						}
					}
				}
			} finally {
				br.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

    public static String slurp(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        byte[] b = new byte[4096];
        for (int n; (n = in.read(b)) != -1;) {
            out.append(new String(b, 0, n));
        }
        return out.toString();
    }

    public void setup(String ifcFileIn) throws IOException {
        ifcFile = ifcFileIn;
        if (!ifcFile.endsWith(".ifc")) {
            ifcFile += ".ifc";
        }

        exp = getExpressSchema(ifcFile);

        // check if we are able to convert this: only four schemas are supported
        if (!exp.equalsIgnoreCase("IFC2X3_Final") && !exp.equalsIgnoreCase("IFC2X3_TC1") && !exp.equalsIgnoreCase("IFC4_ADD2") && !exp.equalsIgnoreCase("IFC4_ADD1") && !exp.equalsIgnoreCase("IFC4")
                        && !exp.equalsIgnoreCase("IFC4x1") && !exp.equalsIgnoreCase("IFC4x3_RC1")) {
            LOG.error("Unrecognised EXPRESS schema: " + exp + ". File should be in IFC4x3_RC1, IFC4X1, IFC4 or IFC2X3 schema. Quitting." + "\r\n");
        }

        try {
            InputStream fis = IfcSpfReader.class.getResourceAsStream("/ent" + exp + ".ser");
			if (fis == null)
				fis = IfcSpfReader.class.getResourceAsStream("/resources/ent" + exp + ".ser");  // Eclipse FIX
			ObjectInputStream ois = new ObjectInputStream(fis);
			
            ent = null;
            try {
                ent = (Map<String, EntityVO>) ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                ois.close();
            }

            fis = IfcSpfReader.class.getResourceAsStream("/typ" + exp + ".ser");
            
			if (fis == null)
				fis = IfcSpfReader.class.getResourceAsStream("/resources/typ" + exp + ".ser"); // Eclipse FIX

			
            ois = new ObjectInputStream(fis);
            typ = null;
            try {
                typ = (Map<String, TypeVO>) ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                ois.close();
            }

            String inAlt = exp;
            if (exp.equalsIgnoreCase("IFC2X3_Final"))
                inAlt = "IFC2x3/FINAL/";
            if (exp.equalsIgnoreCase("IFC2X3_TC1"))
                inAlt = "IFC2x3/TC1/";
            if (exp.equalsIgnoreCase("IFC4_ADD1"))
                inAlt = "IFC4/ADD1/";
            if (exp.equalsIgnoreCase("IFC4_ADD2"))
                inAlt = "IFC4/ADD2/";
            if (exp.equalsIgnoreCase("IFC4_ADD2_TC1"))
                inAlt = "IFC4/ADD2_TC1/";
            if (exp.equalsIgnoreCase("IFC4x1"))
                inAlt = "IFC4_1/";
            if (exp.equalsIgnoreCase("IFC4x3"))
                inAlt = "IFC4_3/RC1/";
            if (exp.equalsIgnoreCase("IFC4X3"))
                inAlt = "IFC4_3/RC1/";
            if (exp.equalsIgnoreCase("IFC4x3_RC1"))
                inAlt = "IFC4_3/RC1/";
            if (exp.equalsIgnoreCase("IFC4X3_RC1"))
                inAlt = "IFC4_3/RC1/";
            if (exp.equalsIgnoreCase("IFC4"))
                inAlt = "IFC4/FINAL/";

            ontURI = "http://standards.buildingsmart.org/IFC/DEV/" + inAlt + "OWL";
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
	public void convert(String ifcFile, String outputFile, String baseURI) throws IOException {
		// CONVERSION
		OntModel om = null;

		in = null;
		HttpOp.setDefaultHttpClient(HttpClientBuilder.create().useSystemProperties().build());
		om = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
		in = IfcSpfReader.class.getResourceAsStream("/" + exp + ".ttl");
		if (in == null)
			in = IfcSpfReader.class.getResourceAsStream("/resources/" + exp + ".ttl");  // Eclipse FIX
		
		om.read(in, null, "TTL");

		try {
			RDFWriter conv = new RDFWriter(om, new FileInputStream(ifcFile), baseURI, ent, typ, ontURI);
			conv.setRemoveDuplicates(removeDuplicates);
			conv.setIfcReader(this);
			try (FileOutputStream out = new FileOutputStream(outputFile)) {
				String s = "# baseURI: " + baseURI;
				s += "\r\n# imports: " + ontURI + "\r\n\r\n";
				out.write(s.getBytes());
				LOG.info("Started parsing stream");
				conv.parseModel2Stream(out);
				LOG.info("Finished!!");
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

    public void setRemoveDuplicates(boolean val) {
        removeDuplicates = val;
    }

    public Map<String, EntityVO> getEntityMap() {
        return ent;
    }

    public Map<String, TypeVO> getTypeMap() {
        return typ;
    }

    public String getOntURI() {
        return ontURI;
    }
}
