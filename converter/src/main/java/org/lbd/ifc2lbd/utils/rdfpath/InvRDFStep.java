package org.lbd.ifc2lbd.utils.rdfpath;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.SimpleSelector;

/*
* The GNU Affero General Public License
* 
* Copyright (c) 2017 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

/**
 * @author Jyrki Oraskari 
 * An inverse relation RDF step in a conceptual RDF path:
 * https://www.w3.org/wiki/RdfPath
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
