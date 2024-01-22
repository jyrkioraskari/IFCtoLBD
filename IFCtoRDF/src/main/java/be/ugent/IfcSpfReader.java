/*
 * 2024
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

import java.io.BufferedOutputStream;
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
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
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

    private static String DEFAULT_PATH = "";

    private boolean removeDuplicates = false;
    private static final int FLAG_BASEURI = 0;
    private static final int FLAG_DIR = 1;
    private static final int FLAG_KEEP_DUPLICATES = 2;

    private String exp = "";
    protected String ontURI = "";
    private Map<String, EntityVO> ent;
    private Map<String, TypeVO> typ;

    /**
     * Primary integration point for the IFCtoRDF codebase. Run the method
     * without any input parameters for descriptions of runtime parameters.
     */
    public static void main(String[] args) throws IOException {
        String[] options = new String[] { "--baseURI", "--dir", "--keep-duplicates" };
        Boolean[] optionValues = new Boolean[] {Boolean.FALSE, Boolean.FALSE, Boolean.FALSE};

        String timeLog = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());  // JO 2024: performance
        DEFAULT_PATH = "http://linkedbuildingdata.net/ifc/resources" + timeLog + "/";

        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        for (int i = 0; i < options.length; ++i) {
            optionValues[i] = Boolean.valueOf(argsList.contains(options[i]));
        }

        // State of flags has been stored in optionValues. Remove them from our
        // option
        // strings in order to make testing the required amount of positional
        // arguments easier.
        for (String flag : options) {
            argsList.remove(flag);
        }

        int numRequiredOptions = 0;
        if (optionValues[FLAG_DIR].booleanValue())
            numRequiredOptions++;
        else
            numRequiredOptions = 2;
        if (optionValues[FLAG_BASEURI].booleanValue())
            numRequiredOptions++;

        if (argsList.size() != numRequiredOptions) {
            LOG.info("""
                    Usage:
                        IfcSpfReader [--baseURI <baseURI>] [--keep-duplicates] <input_file> <output_file>
                        IfcSpfReader [--baseURI <baseURI>] [--keep-duplicates] --dir <directory>
                    """);
            return;
        }

        List<String> inputFiles;
        List<String> outputFiles = null;
        String baseURI;

        if (optionValues[FLAG_DIR].booleanValue()) {
            if (optionValues[FLAG_BASEURI].booleanValue()) {
                baseURI = argsList.get(0);
                inputFiles = showFiles(argsList.get(1));
            } else {
                baseURI = DEFAULT_PATH;
                inputFiles = showFiles(argsList.get(0));
            }
        } else {
            if (optionValues[FLAG_BASEURI].booleanValue()) {
                baseURI = argsList.get(0);
                inputFiles = Arrays.asList(new String[] { argsList.get(1) });
                outputFiles = Arrays.asList(new String[] { argsList.get(2) });
            } else {
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
                r.convert(inputFile, outputFile, baseURI,false);
            }
        }

    }

    /**
     * List all files in a particular directory.
     * 
     * @param dir
     *            the input directory for which you wish to list file.
     * @return a {@link List} of Strings denoting files.
     */
    public static List<String> showFiles(String dir) {
        List<String> goodFiles = new ArrayList<>();

        File folder = new File(dir);
        File[] listOfFiles = folder.listFiles();

        for (File listOfFile : listOfFiles) {
            if (listOfFile.isFile())
                goodFiles.add(listOfFile.getAbsolutePath());
            else if (listOfFile.isDirectory())
                goodFiles.addAll(showFiles(listOfFile.getAbsolutePath()));
        }
        return goodFiles;
    }

    private static String getExpressSchema(String ifcFile) {
        try (FileInputStream fstream = new FileInputStream(ifcFile)) {
        	// Fix by JO 2024: finally is deprecated (https://openjdk.org/jeps/421)
            try ( DataInputStream in = new DataInputStream(fstream);
                    BufferedReader br = new BufferedReader(new InputStreamReader(in))){
                String strLine;
                while ((strLine = br.readLine()) != null) {
                    if (!strLine.isEmpty()) {
                        if (strLine.startsWith("FILE_SCHEMA")) {
                            if (strLine.contains("IFC2X3"))
                                return "IFC2X3_TC1";
                            if (strLine.contains("IFC4x2"))
                                return "IFC4x3_RC1";
                            if (strLine.contains("IFC4X2"))
                                return "IFC4x3_RC1";
                            if (strLine.contains("IFC4x3"))
                                return "IFC4x3_RC1";
                            if (strLine.contains("IFC4X3"))
                                return "IFC4x3_RC1";
                            if (strLine.contains("IFC4x3_RC1"))
                                return "IFC4x3_RC1";
                            if (strLine.contains("IFC4X3_RC1"))
                                return "IFC4x3_RC1";
                            if (strLine.contains("IFC4s1"))
                                return "IFC4x1";
                            if (strLine.contains("IFC4X1"))
                                return "IFC4x1";
                            if (strLine.contains("IFC4"))     // Should do also IFC4X2
                                return "IFC4_ADD2";                //JO 2020  to enable IFCPOLYGONALFACESET that was found in an IFC4 model
							return "";
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

   /* public static String slurp(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        byte[] b = new byte[4096];
        for (int n; (n = in.read(b)) != -1;) {
            out.append(new String(b, 0, n));
        }
        return out.toString();
    }*/

    @SuppressWarnings("unchecked")
    public void setup(String ifcFileIn) throws IOException {
        // used in conversion
        String ifcFile = ifcFileIn;
        if (!ifcFile.endsWith(".ifc")) {
            ifcFile += ".ifc";
        }

        this.exp = getExpressSchema(ifcFile);
        //System.out.println("express schema: "+this.exp);

        // check if we are able to convert this: only four schemas are supported
        if (!this.exp.equalsIgnoreCase("IFC2X3_Final") && !this.exp.equalsIgnoreCase("IFC2X3_TC1") && !this.exp.equalsIgnoreCase("IFC4_ADD2") && !this.exp.equalsIgnoreCase("IFC4_ADD1") && !this.exp.equalsIgnoreCase("IFC4")
                        && !this.exp.equalsIgnoreCase("IFC4x1") && !this.exp.equalsIgnoreCase("IFC4x3_RC1")) {
            LOG.error("Unrecognised EXPRESS schema: " + this.exp + ". File should be in IFC4x3_RC1, IFC4X1, IFC4 or IFC2X3 schema. Quitting." + "\r\n");
        }

        try {

            //JO -->>> 
            InputStream fis = IfcSpfReader.class.getResourceAsStream("/resources/ent" + this.exp + ".ser");
            if (fis == null)
                fis = IfcSpfReader.class.getResourceAsStream("/ent" + this.exp + ".ser");
            
            
         // Fix by JO 2024: finally is deprecated
            if(fis==null)
            {
            	System.err.println(this.exp + ".ser not found");
            }
            try (ObjectInputStream ois = new ObjectInputStream(fis)){
                this.ent = (Map<String, EntityVO>) ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } 

            //JO -->>> 
            fis = IfcSpfReader.class.getResourceAsStream("/resources/typ" + this.exp + ".ser");
            if (fis == null)
                fis = IfcSpfReader.class.getResourceAsStream("/typ" + this.exp + ".ser");

            //  Fix by JO 2024: finally is deprecated
            try (ObjectInputStream ois = new ObjectInputStream(fis)){
                this.typ = (Map<String, TypeVO>) ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } 
            
            
            if(fis!=null)  // Potential leak
              fis.close();

            String inAlt = this.exp;
            if (this.exp.equalsIgnoreCase("IFC2X3_Final"))
                inAlt = "IFC2x3/FINAL/";
            if (this.exp.equalsIgnoreCase("IFC2X3_TC1"))
                inAlt = "IFC2x3/TC1/";
            if (this.exp.equalsIgnoreCase("IFC4_ADD1"))
                inAlt = "IFC4/ADD1/";
            if (this.exp.equalsIgnoreCase("IFC4_ADD2"))
                inAlt = "IFC4/ADD2/";
            if (this.exp.equalsIgnoreCase("IFC4_ADD2_TC1"))
                inAlt = "IFC4/ADD2_TC1/";
            if (this.exp.equalsIgnoreCase("IFC4x1"))
                inAlt = "IFC4_1/";
            if (this.exp.equalsIgnoreCase("IFC4x3"))
                inAlt = "IFC4_3/RC1/";
            if (this.exp.equalsIgnoreCase("IFC4X3"))
                inAlt = "IFC4_3/RC1/";
            if (this.exp.equalsIgnoreCase("IFC4x3_RC1"))
                inAlt = "IFC4_3/RC1/";
            if (this.exp.equalsIgnoreCase("IFC4X3_RC1"))
                inAlt = "IFC4_3/RC1/";
            if (this.exp.equalsIgnoreCase("IFC4"))
                inAlt = "IFC4/FINAL/";

            this.ontURI = "https://standards.buildingsmart.org/IFC/DEV/" + inAlt + "OWL";
            //System.out.println("IFCtoRDF ont uri: "+this.ontURI);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
    }

    public void convert(String ifcFile, String outputFile, String baseURI,boolean hasPerformanceBoost) throws IOException {
        OntModel om;

        
        //JO 2021/12/10 fix for: java.lang.NoClassDefFoundError: org/apache/jena/riot/web/HttpOp 
        //HttpOp.setDefaultHttpClient(HttpClientBuilder.create().useSystemProperties().build());
        om = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
        InputStream in = IfcSpfReader.class.getResourceAsStream("/" + this.exp + ".ttl");
        if (in == null)
            in = IfcSpfReader.class.getResourceAsStream("/resources/" + this.exp + ".ttl");
        om.read(in, null, "TTL");
        
        //JO 2023/02/08 fix for: Cannot invoke "org.apache.jena.ontology.OntResource.asClass()" because "listrange" is null if no internet 
        in = IfcSpfReader.class.getResourceAsStream("/list.ttl");
        if (in == null)
        	in = IfcSpfReader.class.getResourceAsStream("/resources/list.ttl");
        om.read(in, null, "TTL");
        
        //JO 2023/02/08 fix for: Cannot invoke "org.apache.jena.ontology.OntProperty.toString()" because "valueProp" is null if no internet
        in = IfcSpfReader.class.getResourceAsStream("/express.ttl");
        if (in == null)
        	in = IfcSpfReader.class.getResourceAsStream("/resources/express.ttl");
        om.read(in, null, "TTL");

        try {
            RDFWriter conv = new RDFWriter(om, new FileInputStream(ifcFile), baseURI, this.ent, this.typ, this.ontURI,hasPerformanceBoost);
            conv.setRemoveDuplicates(this.removeDuplicates);
            // JO 2024: performance
            try (FileOutputStream out = new FileOutputStream(outputFile);BufferedOutputStream bout = new BufferedOutputStream(out)
            ) {
                String s = "# baseURI: " + baseURI;
                s += "\r\n# imports: " + this.ontURI + "\r\n\r\n";
                bout.write(s.getBytes());
                LOG.info("Started parsing stream");
                conv.parseModel2Stream(bout);
                LOG.info("Finished!!");
            }
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } finally {
        	//TODO JO 2024:  finally is deprecated
            try {
                in.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    
    
}
