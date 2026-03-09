/*
Copyright (c) 2014, 2024, 2025, 2026 Jyrki Oraskari, RWTH AAáchen University (oraskarii [at] ip.rwth-aachen [dot] de)
Copyright (c) 2014 Pieter Pauwels, Ghent University (modifications - pipauwel [dot] pauwels [at] ugent [dot] be / pipauwel [at] gmail [dot] com)
Copyright (c) 2016 Lewis John McGibbney, Apache (mavenized - lewismc [at] apache [dot] org)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    //private boolean removeDuplicates = false;
    private static final int FLAG_BASEURI = 0;
    private static final int FLAG_DIR = 1;
    //private static final int FLAG_KEEP_DUPLICATES = 2;

    private String exp = "";
    protected String ontURI = "";
    private Map<String, EntityVO> ent;
    private Map<String, TypeVO> typ;
    private static final Map<String, String> EXPRESS_SCHEMA_MAPPING = Map.of(
            "IFC2X3", "IFC2X3_TC1",
            "IFC4X2", "IFC4x3_RC1",
            "IFC4X3", "IFC4x3_RC1",
            "IFC4X3_RC1", "IFC4x3_RC1",
            "IFC4X1", "IFC4x1",
            "IFC4", "IFC4_ADD2"
    );
    private static final Set<String> SUPPORTED_SCHEMAS = new HashSet<>(Arrays.asList(
            "IFC2X3_FINAL", "IFC2X3_TC1", "IFC4_ADD2", "IFC4_ADD1", "IFC4", "IFC4X1", "IFC4X3_RC1"
    ));

    /**
     *  The main method, when called from a command line.
     *  Run this without any input parameters for descriptions of runtime parameters.
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

                IfcSpfReader reader = new IfcSpfReader();

                //r.removeDuplicates = !optionValues[FLAG_KEEP_DUPLICATES];

                LOG.info("Converting file: " + inputFile + "\r\n");

                reader.setup(inputFile);
                reader.convert(inputFile, outputFile, baseURI,false);
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
        if (!folder.exists() || !folder.isDirectory()) {
            LOG.warn("Directory does not exist or is not accessible: {}", dir);
            return goodFiles;
        }
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null) {
            LOG.warn("Could not list files in directory: {}", dir);
            return goodFiles;
        }

        for (File listOfFile : listOfFiles) {
            if (listOfFile.isFile())
                goodFiles.add(listOfFile.getAbsolutePath());
            else if (listOfFile.isDirectory())
                goodFiles.addAll(showFiles(listOfFile.getAbsolutePath()));
        }
        return goodFiles;
    }

    public static String getExpressSchema(String ifcFile) {
	    try (BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(ifcFile))))) {
	        String strLine;
	        while ((strLine = br.readLine()) != null) {
	            if (!strLine.isEmpty() && strLine.startsWith("FILE_SCHEMA")) {
	                final String schemaLineUpper = strLine.toUpperCase(Locale.ROOT);
	                for (Map.Entry<String, String> entry : EXPRESS_SCHEMA_MAPPING.entrySet()) {
	                    if (schemaLineUpper.contains(entry.getKey())) {
	                        return entry.getValue();
	                    }
	                }
	                return "";
	            }
	        }
	    } catch (IOException e) {
	        LOG.error("Failed reading IFC schema from file: {}", ifcFile, e);
	    }
	    return "";
	}


  
    /**
     * Sets up the environment for processing an IFC file.
     * <p>
     * This method initializes the EXPRESS schema and loads the necessary serialized entity and type maps
     * based on the provided IFC file. It also sets the ontology URI for the conversion process.
     * </p>
     *
     * @param ifcFileIn The input IFC file name. If the file name does not end with ".ifc", the extension is added.
     * @throws IOException If an I/O error occurs during the setup process.
     */
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
        if (this.exp.isBlank()) {
            throw new IOException("Could not detect IFC EXPRESS schema from file: " + ifcFile);
        }
        if (!SUPPORTED_SCHEMAS.contains(this.exp.toUpperCase(Locale.ROOT))) {
            throw new IOException("Unsupported EXPRESS schema: " + this.exp
                    + ". Supported: IFC2X3_FINAL, IFC2X3_TC1, IFC4_ADD1, IFC4_ADD2, IFC4, IFC4x1, IFC4x3_RC1.");
        }

        this.ent = loadSerializedMap("ent" + this.exp + ".ser");
        this.typ = loadSerializedMap("typ" + this.exp + ".ser");

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
    }

    
    /**
     * Converts an IFC file to RDF.
     *
     * @param ifcFile the path to the input IFC file
     * @param outputFile the path to the output file
     * @param baseURI the base URI for the RDF model
     * @param hasPerformanceBoost flag indicating if performance optimizations should be applied
     * @throws IOException if an I/O error occurs during the conversion process
     */
    
    public void convert(String ifcFile, String outputFile, String baseURI,boolean hasPerformanceBoost) throws IOException {
        OntModel om;


        //JO 2021/12/10 fix for: java.lang.NoClassDefFoundError: org/apache/jena/riot/web/HttpOp 
        //HttpOp.setDefaultHttpClient(HttpClientBuilder.create().useSystemProperties().build());
        om = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);

        try (InputStream inSchema = getResourceStream("/" + this.exp + ".ttl", "/resources/" + this.exp + ".ttl")) {
            om.read(inSchema, null, "TTL");
        }

        //JO 2023/02/08 fix for: Cannot invoke "org.apache.jena.ontology.OntResource.asClass()" because "listrange" is null if no internet 
        try (InputStream inList = getResourceStream("/list.ttl", "/resources/list.ttl")) {
            om.read(inList, null, "TTL");
        }

        //JO 2023/02/08 fix for: Cannot invoke "org.apache.jena.ontology.OntProperty.toString()" because "valueProp" is null if no internet
        try (InputStream inExpress = getResourceStream("/express.ttl", "/resources/express.ttl")) {
            om.read(inExpress, null, "TTL");
        }

        //conv.setRemoveDuplicates(this.removeDuplicates);
        // JO 2024: performance
        try (FileInputStream input = new FileInputStream(ifcFile);
             FileOutputStream out = new FileOutputStream(outputFile);
             BufferedOutputStream bout = new BufferedOutputStream(out)) {
            RDFWriter conv = new RDFWriter(om, input, baseURI, this.ent, this.typ, this.ontURI,hasPerformanceBoost);
                String s = "# baseURI: " + baseURI;
                s += "\r\n# imports: " + this.ontURI + "\r\n\r\n";
                bout.write(s.getBytes(StandardCharsets.UTF_8));
                LOG.info("Started parsing stream");
                conv.parseModel2Stream(bout);
                LOG.info("Finished!!");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> loadSerializedMap(String resourceFileName) throws IOException {
        try (InputStream fis = getResourceStream("/resources/" + resourceFileName, "/" + resourceFileName);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            Object object = ois.readObject();
            return (Map<String, T>) object;
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize resource map: " + resourceFileName, e);
        }
    }

    private InputStream getResourceStream(String primaryPath, String fallbackPath) throws IOException {
        InputStream stream = IfcSpfReader.class.getResourceAsStream(primaryPath);
        if (stream == null) {
            stream = IfcSpfReader.class.getResourceAsStream(fallbackPath);
        }
        if (stream == null) {
            throw new IOException("Required classpath resource not found: " + primaryPath + " or " + fallbackPath);
        }
        return stream;
    }

}
