package org.lbd.ifc2lbd.utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.buildingsmart.tech.ifcowl.IfcSpfReader;
import org.lbd.ifc2lbd.IFCtoLBDConverter;
import org.lbd.ifc2lbd.messages.SystemStatusEvent;
import org.lbd.ifc2lbd.utils.rdfpath.RDFStep;

import com.google.common.eventbus.EventBus;

/*
* The GNU Affero General Public License
* 
* Copyright (c) 2017, 2018, 2019 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

public class RDFUtils {

	/**
	 * 
	 * This method is used to write the Turtle formatted output files that are the result of of the conversion
	 * process.
	 * 
	 * An utility method to export an Apache Jena RDF storage content into a Turtle formatted file- 
	 * 
	 * @param m an Apache Jena model
	 * @param target_file absolute path name for an output file
	 
	 * @param eventBus A queue to communicate between program component. Here: status messages and errors are sent to user interface
	 * If no user interface is present, adding messages to the channel does nothing.
	 * 
	 */
	public static void writeModel(Model m, String target_file,EventBus eventBus) {
		FileOutputStream fo = null;
		try {
			fo = new FileOutputStream(new File(target_file));
			m.write(fo, "TTL");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			eventBus.post(new SystemStatusEvent("Error : " + e.getMessage()));
		} finally {
			if (fo != null)
				try {
					fo.close();
				} catch (IOException e) {
				}
		}
	}
	
	/**
	 * Reads in a Turtle formatted ontology file into a Jena RDF store
	 * 
	 * @param ifc_file The absolute path of the Turtle formatted ontology file
	 * @param model Am Apache Jene model where the ontology triples are added.
	 */
	public static void readIfcOWLOntology(String ifc_file, Model model)
	{
		String exp = RDFUtils.getExpressSchema(ifc_file);
		InputStream in = null;
		try {
			in = IfcSpfReader.class.getResourceAsStream("/" + exp + ".ttl");

			if (in == null)
				in = IfcSpfReader.class.getResourceAsStream("/resources/" + exp + ".ttl");
			model.read(in, null, "TTL");
		} finally {
			try {
				in.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	/**
	 * 
	 * Reads in a Turtle - Terse RDF Triple Language (TTL) formatted ontology file:
	 * Turtle - Terse RDF Triple Language:  https://www.w3.org/TeamSubmission/turtle/
	 * 
	 * The extra lines make sure that the files are found if run under Eclipse IDE or as a
	 * runnable Java Archive file (JAR). 
	 * 
	 * Eclipse: https://www.eclipse.org/
	 * 
	 * @param model  An Apache Jena model: RDF store run on memory. 
	 * @param ontology_file An Apache Jena ontokigy model: RDF store run on memory containing an ontology engine. 
	 * @param eventBus A queue to communicate between program component. Here: status messages and errors are sent to user interface
	 * If no user interface is present, adding messages to the channel does nothing.

	 */
	public static void readInOntologyTTL(Model model, String ontology_file,EventBus eventBus) {

		InputStream in = null;
		try {
			in = IFCtoLBDConverter.class.getResourceAsStream("/" + ontology_file);
			if (in == null) {
				try {
					in = IFCtoLBDConverter.class.getResourceAsStream("/resources/" + ontology_file);
					if (in == null)
						in = IFCtoLBDConverter.class.getResourceAsStream("/" + ontology_file);
				} catch (Exception e) {
					eventBus.post(new SystemStatusEvent("Error : " + e.getMessage()));
					e.printStackTrace();
					return;
				}
			}
			model.read(in, null, "TTL");
			in.close();

		} catch (Exception e) {
			eventBus.post(new SystemStatusEvent("Error : " + e.getMessage()));
			System.out.println("missing file: " + ontology_file);
			e.printStackTrace();
		}

	}
	/**
	 * 
	 * This is a direct copy from the IFCtoRDF
	 * https://github.com/pipauwel/IFCtoRDF
	 * 
	 * The idea is to make sure that we are using exactly the same ontology files that 
	 * the IFCtoRDF is using for the associated Abox output.
	 * 
	 * @param ifc_file  the absolute path (For example:  c:\ifcfiles\ifc_file.ifc) for the IFC file 
	 * @return the IFC Express chema of the IFC file. 
	 */
	public static String getExpressSchema(String ifc_file) {
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


	/**
	 * An utility method to copy of conected Abox triples unmodified from one Jena model to another.
	 * This is used to copy ifcOWL property set data as is.
	 * 
	 * @param level  how many steps from the start node
	 * @param r  A RDF node to start the copying
	 * @param output_model A Jena model where the triples are copied to
	 */
	public static void copyTriples(int level, RDFNode r, Model output_model) {
		if (level > 4)
			return;
		if (!r.isResource())
			return;
		r.asResource().listProperties().forEachRemaining(s -> {
			// No ontology
			if (!s.getPredicate().asResource().getURI().startsWith("http://www.w3.org/2000/01/rdf-schema#")) {
				output_model.add(s);
				copyTriples(level + 1, s.getObject(), output_model);
			}
		});
	}

	/**
	 * A helper method to find a list of nodes that match a given RDF path pattern
	 * 
	 * @param r      the starting point 
	 * @param path   the path pattern
	 * @return       the list of found noded at the RDF graoh
	 */
	public static List<RDFNode> pathQuery(Resource r, RDFStep[] path) {
		List<RDFStep> path_list = Arrays.asList(path);
		if (r.getModel() == null)
			return new ArrayList<RDFNode>();
		Optional<RDFStep> step = path_list.stream().findFirst();
		if (step.isPresent()) {
			List<RDFNode> step_result = step.get().next(r);
			if (path.length > 1) {
				final List<RDFNode> result = new ArrayList<RDFNode>();
				step_result.stream().filter(rn1 -> rn1.isResource()).map(rn2 -> rn2.asResource()).forEach(r1 -> {
					List<RDFStep> tail = path_list.stream().skip(1).collect(Collectors.toList());
					result.addAll(pathQuery(r1, tail.toArray(new RDFStep[tail.size()])));
				});
				return result;
			} else
				return step_result;
		}
		return new ArrayList<RDFNode>();
	}
	
	/**
	 *  
	 * Gives the corresponding RDF ontology class type of the RDF node in the Apache Jena RDF model. 
	 * 
	 * @param r  An RDF recource in a Apache Jena RDF store.
	 * @return The ontology class Resource node as an optional, that is, if exists. An empty return is given,
	 * if the triples does not exists at the graph. 
	 */
	public static Optional<Resource> getType(Resource r) {
		RDFStep[] path = { new RDFStep(RDF.type) };
		return RDFUtils.pathQuery(r, path).stream().map(rn -> rn.asResource()).findAny();
	}

}
