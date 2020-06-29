package com.buildingsmart.tech.ifcowl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.lbd.ifc2lbd.EventBusService;
import org.lbd.ifc2lbd.messages.SystemStatusEvent;

import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;
import com.google.common.eventbus.EventBus;

/*
 * Copyright 2016 Pieter Pauwels, Ghent University; Jyrki Oraskari, Aalto University; Lewis John McGibbney, Apache
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

public class IfcSpfReader {
	private final EventBus eventBus = EventBusService.getEventBus();

	private String timeLog = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());

	public final String DEFAULT_PATH = "http://linkedbuildingdata.net/ifc/resources" + timeLog + "/";

	public boolean logToFile = false;
	public BufferedWriter bw;

	private boolean removeDuplicates = false;
	public static List<String> showFiles(String dir) {
		List<String> goodFiles = new ArrayList<String>();

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

	public void setupLogger(String path) {
		String outputFile = path.substring(0, path.length() - 4) + ".log";

		try {
			File file = new File(outputFile);
			if (!file.exists())
				file.createNewFile();

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			bw = new BufferedWriter(fw);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}



	private String getExpressSchema(String ifc_file) {
		try {
			FileInputStream fstream = new FileInputStream(ifc_file);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			try {
				String strLine;
				while ((strLine = br.readLine()) != null) {
					if (strLine.length() > 0) {
						if (strLine.startsWith("FILE_SCHEMA")) {
							if (strLine.indexOf("IFC2X3") != -1)
								return "IFC2X3_TC1";
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
		StringBuffer out = new StringBuffer();
		byte[] b = new byte[4096];
		for (int n; (n = in.read(b)) != -1;) {
			out.append(new String(b, 0, n));
		}
		return out.toString();
	}

	@SuppressWarnings("unchecked")
	public Optional<String> convert(String ifcFile, String outputFile, String baseURI) throws IOException {
		Optional<String> ontURI = Optional.empty();

		if (!ifcFile.endsWith(".ifc")) {
			ifcFile += ".ifc";
		}

		String exp = getExpressSchema(ifcFile);

		// check if we are able to convert this: only four schemas are supported
		if (!exp.equalsIgnoreCase("IFC2X3_Final") && !exp.equalsIgnoreCase("IFC2X3_TC1")
				&& !exp.equalsIgnoreCase("IFC4_ADD2") && !exp.equalsIgnoreCase("IFC4_ADD1")
				&& !exp.equalsIgnoreCase("IFC4")) {
			if (logToFile)
				bw.write("ERROR: Unrecognised EXPRESS schema: " + exp
						+ ". File should be in IFC4 or IFC2X3 schema. Stopping conversion." + "\r\n");
			eventBus.post(new SystemStatusEvent("nrecognised EXPRESS schema: " + exp));
			return Optional.empty();
		}

		// CONVERSION
		OntModel om = null;

		InputStream in = null;
		try {
			om = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
			in = IfcSpfReader.class.getResourceAsStream("/resources/" + exp + ".ttl");
			if (in == null)
				in = IfcSpfReader.class.getResourceAsStream("/" + exp + ".ttl");
			System.out.println("TTL in "+in);
			om.read(in, null, "TTL");

			String expressTtl = "/resources/express.ttl";
			InputStream expressTtlStream = IfcSpfReader.class.getResourceAsStream(expressTtl);
			if (expressTtlStream == null) {
				expressTtl = "/express.ttl";
				expressTtlStream = IfcSpfReader.class.getResourceAsStream(expressTtl);
			}

			OntModel expressModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
			expressModel.read(expressTtlStream, null, "TTL");

			String rdfList = "/resources/list.ttl";
			InputStream rdfListStream = IfcSpfReader.class.getResourceAsStream(rdfList);
			if (rdfListStream == null) {
				rdfList = "/list.ttl";
				rdfListStream = IfcSpfReader.class.getResourceAsStream(rdfList);
			}

			OntModel listModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
			listModel.read(rdfListStream, null, "TTL");
			
			
			om.add(expressModel);
			om.add(listModel);

			
			InputStream fis = IfcSpfReader.class.getResourceAsStream("/resources/ent" + exp + ".ser");
			if (fis == null)
				fis = IfcSpfReader.class.getResourceAsStream("/ent" + exp + ".ser");
			ObjectInputStream ois = new ObjectInputStream(fis);

			Map<String, EntityVO> ent = null;
			try {
				ent = (Map<String, EntityVO>) ois.readObject();
			} catch (ClassNotFoundException e) {
				eventBus.post(new SystemStatusEvent(e.getMessage()));
				e.printStackTrace();
			} finally {
				ois.close();
			}

			fis = IfcSpfReader.class.getResourceAsStream("/resources/typ" + exp + ".ser");
			if (fis == null)
				fis = IfcSpfReader.class.getResourceAsStream("/typ" + exp + ".ser");

			ois = new ObjectInputStream(fis);
			Map<String, TypeVO> typ = null;
			try {
				typ = (Map<String, TypeVO>) ois.readObject();
			} catch (ClassNotFoundException e) {
				eventBus.post(new SystemStatusEvent(e.getMessage()));
				e.printStackTrace();
			} finally {
				ois.close();
			}

			ontURI = Optional.of("http://www.buildingsmart-tech.org/ifcOWL/" + exp);
			RDFWriter conv = new RDFWriter(om, expressModel, listModel, new FileInputStream(ifcFile), baseURI, ent, typ,
					ontURI.get());
			conv.setRemoveDuplicates(removeDuplicates);
			conv.setIfcReader(this);
			FileOutputStream out = new FileOutputStream(outputFile);
			String s = "# baseURI: " + baseURI;
			s += "\r\n# imports: " + ontURI + "\r\n\r\n";
			out.write(s.getBytes());
			out.flush();
			eventBus.post(new SystemStatusEvent("IFCtoRDF start parsing IFC-RDF stream"));
			System.out.println("started parsing stream");
			conv.parseModel2Stream(out);
			eventBus.post(new SystemStatusEvent("IFCtoRDF finished "));
			System.out.println("finished!!");
		} catch (FileNotFoundException e1) {
			eventBus.post(new SystemStatusEvent(e1.getMessage()));
			e1.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (Exception e1) {
				eventBus.post(new SystemStatusEvent(e1.getMessage()));
				e1.printStackTrace();
			}
			try {
			} catch (Exception e1) {
				eventBus.post(new SystemStatusEvent(e1.getMessage()));
				e1.printStackTrace();
			}
		}
		return ontURI;
	}

	@SuppressWarnings("unchecked")
	public Optional<String> convert(String ifcFile, OutputStream outStream, String baseURI) throws IOException {
		Optional<String> ontURI = Optional.empty();

		if (!ifcFile.endsWith(".ifc")) {
			ifcFile += ".ifc";
		}

		String exp = getExpressSchema(ifcFile);

		// check if we are able to convert this: only four schemas are supported
		if (!exp.equalsIgnoreCase("IFC2X3_Final") && !exp.equalsIgnoreCase("IFC2X3_TC1")
				&& !exp.equalsIgnoreCase("IFC4_ADD2") && !exp.equalsIgnoreCase("IFC4_ADD1")
				&& !exp.equalsIgnoreCase("IFC4")) {
			if (logToFile)
				bw.write("ERROR: Unrecognised EXPRESS schema: " + exp
						+ ". File should be in IFC4 or IFC2X3 schema. Stopping conversion." + "\r\n");
			return Optional.empty();
		}

		// CONVERSION
		OntModel om = null;

		InputStream in = null;
		try {
			om = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
			in = IfcSpfReader.class.getResourceAsStream("/resources/" + exp + ".ttl");
			if (in == null)
				in = IfcSpfReader.class.getResourceAsStream("/" + exp + ".ttl");
			om.read(in, null, "TTL");

			String expressTtl = "/resources/express.ttl";
			InputStream expressTtlStream = IfcSpfReader.class.getResourceAsStream(expressTtl);
			if (expressTtlStream == null) {
				expressTtl = "/express.ttl";
				expressTtlStream = IfcSpfReader.class.getResourceAsStream(expressTtl);
			}

			OntModel expressModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
			expressModel.read(expressTtlStream, null, "TTL");

			String rdfList = "/resources/list.ttl";
			InputStream rdfListStream = IfcSpfReader.class.getResourceAsStream(rdfList);
			if (rdfListStream == null) {
				rdfList = "/list.ttl";
				rdfListStream = IfcSpfReader.class.getResourceAsStream(rdfList);
			}

			OntModel listModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
			listModel.read(rdfListStream, null, "TTL");

			om.add(expressModel);
			om.add(listModel);

			InputStream fis = IfcSpfReader.class.getResourceAsStream("/resources/ent" + exp + ".ser");
			if (fis == null)
				fis = IfcSpfReader.class.getResourceAsStream("/ent" + exp + ".ser");
			ObjectInputStream ois = new ObjectInputStream(fis);

			Map<String, EntityVO> ent = null;
			try {
				ent = (Map<String, EntityVO>) ois.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				ois.close();
			}

			fis = IfcSpfReader.class.getResourceAsStream("/resources/typ" + exp + ".ser");
			if (fis == null)
				fis = IfcSpfReader.class.getResourceAsStream("/typ" + exp + ".ser");

			ois = new ObjectInputStream(fis);
			Map<String, TypeVO> typ = null;
			try {
				typ = (Map<String, TypeVO>) ois.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				ois.close();
			}

			ontURI = Optional.of("http://www.buildingsmart-tech.org/ifcOWL/" + exp);
			
			RDFWriter conv = new RDFWriter(om, expressModel, listModel, new FileInputStream(ifcFile), baseURI, ent, typ,
					ontURI.get());
			conv.setRemoveDuplicates(removeDuplicates);
			conv.setIfcReader(this);
			String s = "# baseURI: " + baseURI;
			s += "\r\n# imports: " + ontURI + "\r\n\r\n";
			outStream.write(s.getBytes());
			outStream.flush();
			System.out.println("started parsing stream");
			conv.parseModel2Stream(outStream);
			System.out.println("finished!!");
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			try {
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
		return ontURI;
	}

}
