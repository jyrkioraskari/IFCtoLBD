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

package org.lbd.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public class RDFS extends abstract_NS {
	public static final String rdf_ns = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	public static final String rdfs_ns = "http://www.w3.org/2000/01/rdf-schema#";

	public static final Property comment = property(rdfs_ns, "comment");
	public static final Property label = property(rdfs_ns, "label");
	public static final Property subClassOf = property(rdfs_ns, "subClassOf");
	public static final Property type = property(rdf_ns, "type");

	public static void addNameSpace(Model model) {
		model.setNsPrefix("rdfs", rdf_ns);
	}
}
