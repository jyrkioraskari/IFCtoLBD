package org.lbd.ifc2lbd.utils.rdfpath;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.SimpleSelector;

/*
* 
* Copyright (c) 2017 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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


/**
 * 
 * An inverse relation RDF step in a conceptual RDF path:
 * https://www.w3.org/wiki/RdfPath
 * 
 * 
 *
 */

public class InvRDFStep extends RDFStep {
	/**
	 * @param property  RDF property in an Apache Jena model 
	 */
	public InvRDFStep(Property property) {
		super(property);
	}
	
    /**
     * Returns a list of nodes that are objects of triples that match the following pattern:
     * (any, this.property, r).
     * 
     * So, if this is a step in a RDF path, this gives a list of nodes where the step takes. when a
     * node of the RDF graph is given, and when the "arrow" is followed backwards.
     * 
     * @param r : A RDF node (that has a URI) in the graph
     * @return  a list of RDF nodes
     */
	@Override
    public List<RDFNode> next(Resource r) {
		if(r.getModel()==null)
			return new ArrayList<RDFNode>();
		final List<RDFNode> ret = new ArrayList<>();
		
		r.getModel().listStatements(new SimpleSelector(null, this.property, r))
		.mapWith(t1 -> t1.getSubject()).forEachRemaining(s -> ret.add(s));
		return ret;
    }
}
