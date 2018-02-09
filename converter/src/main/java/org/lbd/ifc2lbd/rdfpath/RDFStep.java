package org.lbd.ifc2lbd.rdfpath;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

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

public class RDFStep {
	final protected Property property;
	public RDFStep(Property property) {
		super();
		this.property = property;
	}
	

    public List<RDFNode> next(Resource r) {
		final List<RDFNode> ret = new ArrayList<>();
		r.listProperties(this.property).mapWith(t->t.getObject()).forEachRemaining(o -> ret.add(o));
		return ret;
    }
}
