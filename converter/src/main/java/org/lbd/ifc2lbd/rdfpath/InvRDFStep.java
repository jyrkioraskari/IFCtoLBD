package org.lbd.ifc2lbd.rdfpath;

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


public class InvRDFStep extends RDFStep {
	public InvRDFStep(Property property) {
		super(property);
	}
	

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
