package nl.tue.isbe.ifc2lbd;

/*
 *
 * Copyright 2019 Pieter Pauwels, Eindhoven University of Technology
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

import be.ugent.IfcSpfParser;
import be.ugent.IfcSpfReader;
import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;



public class IFCtoLBDConverter {

	// --------------------
	// VARIABLES
	// --------------------

	private static final Logger LOG = LoggerFactory.getLogger(IFCtoLBDConverter.class);

	private static final int FLAG_BASEURI = 0;
	private static final int FLAG_BELEMENTS = 1;
	private static final int FLAG_PROPS = 2;

	private final String ifcFilename;
	private final String uriBase;
	private final String targetFile;

	private final boolean hasBuildingElements;
	private final boolean hasBuildingProperties;

	private int idCounter = 0;
	private Map<Long, IFCVO> linemap = new HashMap<>();

	private String timeLog = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
	public static String DEFAULT_PATH = "";

	// --------------------
	// CONSTRUCTOR
	// --------------------
	public IFCtoLBDConverter(String uriBase, String ifcFilename, String targetFile,
			boolean hasBuildingElements, boolean hasBuildingProperties) {
		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = uriBase;

		this.ifcFilename = ifcFilename;
		this.targetFile = targetFile;

		this.hasBuildingElements = !hasBuildingElements;
		this.hasBuildingProperties = !hasBuildingProperties;
	}

	public static void main(String[] args) {
		String[] options = new String[]{"--baseURI", "--excludeElements", "--excludeProps"};
		Boolean[] optionValues = new Boolean[]{false, false, false};

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

		int numRequiredOptions = 2;
		if (optionValues[FLAG_BASEURI])
			numRequiredOptions++;

		if (argsList.size() != numRequiredOptions) {
			LOG.info("Usage:\n"
					+ "    IfcSpfReader [--baseURI <baseURI>] [--excludeElements] [--excludeProps] <input_file.ifc> <output_file.ttl>\n"
					+ "    Example: IfcSpfReader --baseURI https://mybuildingdata.net/myproject# --excludeElements myFile.ifc myFile.ttl\n");
			return;
		}

		String timeLog = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
		DEFAULT_PATH = "http://linkedbuildingdata.net/ifc/resources" + timeLog + "/";

		String inputFile;
		String outputFile;
		String baseURI = "";
		if (optionValues[FLAG_BASEURI]) {
			baseURI = argsList.get(0);
			inputFile = argsList.get(1);
			outputFile = argsList.get(2);
		} else {
			baseURI = DEFAULT_PATH;
			inputFile = argsList.get(0);
			outputFile = argsList.get(1);
		}


		IFCtoLBDConverter conv = new IFCtoLBDConverter(baseURI, inputFile, outputFile, optionValues[FLAG_BELEMENTS], optionValues[FLAG_PROPS]);

		//reading file - IfcSpfReader
		IfcSpfReader r = new IfcSpfReader();
		try {
			r.setRemoveDuplicates(false);
			r.setup(conv.ifcFilename);

			//parsing file into linemap
			FileInputStream is = null;
			is = new FileInputStream(conv.ifcFilename);
			IfcSpfParser parser = new IfcSpfParser(is);

			// Read the whole file into a linemap Map object
			parser.readModel();

			LOG.info("Model parsed");

			if (false) {
				parser.resolveDuplicates(); //makes no sense to remove duplicates in an LBD approach. There are none.
			}

			// map entries of the linemap Map object to the ontology Model and make
			// new instances in the model
			boolean parsedSuccessfully = parser.mapEntries();

			if (!parsedSuccessfully)
				return;

			//recover data from parser
			conv.idCounter = parser.getIdCounter();
			conv.linemap = parser.getLinemap();

			RDFWriter w = new RDFWriter(is, conv.uriBase, r.getEntityMap(), r.getTypeMap(), conv.linemap,
					conv.hasBuildingElements, conv.hasBuildingProperties);
			w.setIfcReader(r);
			try (FileOutputStream out = new FileOutputStream(conv.targetFile)) {
				LOG.info("Started writing to stream");
				w.writeModelToStream(out);
				LOG.info("Finished!!");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
