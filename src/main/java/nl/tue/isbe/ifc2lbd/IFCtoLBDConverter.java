
package nl.tue.isbe.ifc2lbd;

import be.ugent.IfcSpfParser;
import be.ugent.IfcSpfReader;
import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/*
* The GNU Affero General Public License
* 
* Copyright (c) 2017, 2018 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
* 
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
* 
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

public class IFCtoLBDConverter {

	// --------------------
	// VARIABLES
	// --------------------

	private static final Logger LOG = LoggerFactory.getLogger(IFCtoLBDConverter.class);

	private final String ifcFilename;
	private final String uriBase;
	private final String targetFile;

	private final boolean hasBuildingElements;
	private final boolean hasBuildingProperties;

	private int idCounter = 0;
	private Map<Long, IFCVO> linemap = new HashMap<>();

	private String timeLog = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
	public final String DEFAULT_PATH = "http://linkedbuildingdata.net/lbd/resources" + timeLog + "/";

	// --------------------
	// CONSTRUCTOR
	// --------------------
	public IFCtoLBDConverter(String ifcFilename, String uriBase, String targetFile,
			boolean hasBuildingElements, boolean hasBuildingProperties) {
		if (!uriBase.endsWith("#") && !uriBase.endsWith("/"))
			uriBase += "#";
		this.uriBase = uriBase;

		this.ifcFilename = ifcFilename;
		this.targetFile = targetFile;

		this.hasBuildingElements = hasBuildingElements;
		this.hasBuildingProperties = hasBuildingProperties;
	}

	public static void main(String[] args) {
		//TODO: improve signature, so more input values can be given and handled
		if (args.length > 2) {
			IFCtoLBDConverter conv = new IFCtoLBDConverter(args[0], args[1], args[2], true, true);

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
					parser.resolveDuplicates(); //deciding to not take into account duplicates here
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
		else
			System.out.println("Usage: IFCtoLBDConverter ifc_filename base_uri target_file");
	}
}
