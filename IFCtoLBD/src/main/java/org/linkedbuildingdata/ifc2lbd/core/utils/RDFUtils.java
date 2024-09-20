package org.linkedbuildingdata.ifc2lbd.core.utils;

import com.google.common.eventbus.EventBus;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 *  Copyright (c) 2017, 2021 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

public abstract class RDFUtils {

    /**
     * 
     * This method is used to write the Turtle formatted output files that are
     * the result of of the conversion process.
     * 
     * An utility method to export an Apache Jena RDF storage content into a
     * Turtle formatted file- Supports UTF.
     * 
     * @param m
     *            an Apache Jena model
     * @param target_file
     *            absolute path name for an output file
     * 
     * @param eventBus
     *            A queue to communicate between program component. Here: status
     *            messages and errors are sent to user interface If no user
     *            interface is present, adding messages to the channel does
     *            nothing.
     * 
     */
    public static void writeModel(Model m, String target_file, EventBus eventBus) {
    	// Fix by JO 2024: finally is deprecated (https://openjdk.org/jeps/421)
    	// JO 2024 performance
        try (OutputStreamWriter fo = new OutputStreamWriter(new FileOutputStream(target_file), StandardCharsets.UTF_8.newEncoder()); BufferedWriter bfo=new BufferedWriter(fo)){
                       
            m.write(bfo, "TTL");
        } catch (IOException e1) {
			e1.printStackTrace();
			eventBus.post(new IFCtoLBD_SystemStatusEvent("Error : " + e1.getMessage()));
		} 
    }

    public static void writeModelRDFStream(Model m, String target_file, EventBus eventBus) {
        
    	// JO 2024: performance
        try (FileOutputStream fo = new FileOutputStream(new File(target_file));BufferedOutputStream bfo = new BufferedOutputStream(fo)
        ){
           
            StreamRDFWriter.write(bfo, m.getGraph(), RDFFormat.TURTLE_BLOCKS) ;            
        } catch (IOException e1) {
			e1.printStackTrace();
		    eventBus.post(new IFCtoLBD_SystemStatusEvent("Error : " + e1.getMessage()));
		} 
    }
    
    public static void writeDataset(Dataset ds, String target_file, EventBus eventBus) {
    	// Fix by JO 2024: finally is deprecated
    	// JO 2024: performance
    	try (FileOutputStream fo = new FileOutputStream(target_file); BufferedOutputStream bfo = new BufferedOutputStream(fo)
        ){
            RDFDataMgr.write(bfo, ds, RDFFormat.TRIG_PRETTY);
        } catch (IOException e1) {
			e1.printStackTrace();
            eventBus.post(new IFCtoLBD_SystemStatusEvent("Error : " + e1.getMessage()));
		} 
    }
    /**
     * 
     * Reads in a Turtle - Terse RDF Triple Language (TTL) formatted ontology
     * file: Turtle - Terse RDF Triple Language:
     * https://www.w3.org/TeamSubmission/turtle/
     * 
     * The extra lines make sure that the files are found if run under Eclipse
     * IDE or as a runnable Java Archive file (JAR).
     * 
     * Eclipse: https://www.eclipse.org/
     * 
     * @param model
     *            An Apache Jena model: RDF store run on memory.
     * @param ontology_file
     *            An Apache Jena ontokigy model: RDF store run on memory
     *            containing an ontology engine.
     * @param eventBus
     *            A queue to communicate between program component. Here: status
     *            messages and errors are sent to user interface If no user
     *            interface is present, adding messages to the channel does
     *            nothing.
     * 
     */
    public static void readInOntologyTTL(Model model, String ontology_file, EventBus eventBus) {

		InputStream in = null;
        try {
            in = IFCtoLBDConverter.class.getResourceAsStream("/" + ontology_file);
            if (in == null) {
                try {
                    in = IFCtoLBDConverter.class.getResourceAsStream("/resources/" + ontology_file);
                    if (in == null)
                        in = IFCtoLBDConverter.class.getResourceAsStream("/" + ontology_file);
                    
                    if (in == null)
                        in = IFCtoLBDConverter.class.getResourceAsStream(ontology_file);  // Java 9  module version
                    
                    try
                    {
                      if (in == null)
        				in = ClassLoader.getSystemResources("ifcOWL/"+ontology_file).nextElement().openStream(); // the module (Java 9 ) version 
                    }
                    catch (Exception e) {
						System.err.println(e.getMessage());
					}
        	
                } catch (Exception e) {
                    eventBus.post(new IFCtoLBD_SystemStatusEvent("Error : " + e.getMessage()));
                    e.printStackTrace();
                    return;
                }
            }
            model.read(in, null, "TTL");
            in.close();

        } catch (Exception e) {
            eventBus.post(new IFCtoLBD_SystemStatusEvent("Error : " + e.getMessage()));
            System.err.println("Missing file: " + ontology_file);
            System.err.println("In the rare case, when you have a \"pset\" subdirectory at the current folder, \nan extra error message may be given.  ");
            //e.printStackTrace();
        }
        finally {
			if(in!=null)
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

    }

    /**
     * An utility method to copy of conected Abox triples unmodified from one
     * Jena model to another. This is used to copy ifcOWL property set data as
     * is.
     * 
     * @param level
     *            how many steps from the start node
     * @param r
     *            A RDF node to start the copying
     * @param output_model
     *            A Jena model where the triples are copied to
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
     * A helper method to find a list of nodes that match a given RDF path
     * pattern
     * 
     * @param r
     *            the starting point
     * @param path
     *            the path pattern
     * @return the list of found noded at the RDF graoh
     */
    public static List<RDFNode> pathQuery(Resource r, RDFStep[] path) {
        List<RDFStep> path_list = Arrays.asList(path);
        if (r.getModel() == null)
            return new ArrayList<>();
        Optional<RDFStep> step = path_list.stream().findFirst();
        if (step.isPresent()) {
            List<RDFNode> step_result = step.get().next(r);
            if (path.length > 1) {
                final List<RDFNode> result = new ArrayList<>();
                step_result.stream().filter(RDFNode::isResource).map(RDFNode::asResource).forEach(r1 -> {
                    List<RDFStep> tail = path_list.stream().skip(1).collect(Collectors.toList());
                    result.addAll(pathQuery(r1, tail.toArray(new RDFStep[tail.size()])));
                });
                return result;
            }
			return step_result;
        }
        return new ArrayList<>();
    }

    /**
     * 
     * Gives the corresponding RDF ontology class type of the RDF node in the
     * Apache Jena RDF model.
     * 
     * @param r
     *            An RDF recource in a Apache Jena RDF store.
     * @return The ontology class Resource node as an optional, that is, if
     *         exists. An empty return is given, if the triples does not exists
     *         at the graph.
     */
    public static Optional<Resource> getType(Resource r) {
        RDFStep[] path = { new RDFStep(RDF.type) };
        return RDFUtils.pathQuery(r, path).stream().map(RDFNode::asResource).findAny();
    }

    
    
    
}
