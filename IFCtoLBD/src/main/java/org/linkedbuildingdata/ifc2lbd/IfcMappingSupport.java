package org.linkedbuildingdata.ifc2lbd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

final class IfcMappingSupport {
	private static final String LIST = "https://w3id.org/list#";

	private IfcMappingSupport() { }

	static List<Resource> instances(Model model, String typePrefix) {
		Set<Resource> resources = new java.util.LinkedHashSet<>();
		model.listStatements(null, RDF.type, (RDFNode) null).forEachRemaining(statement -> {
			RDFNode type = statement.getObject();
			if (type.isURIResource() && type.asResource().getLocalName().startsWith(typePrefix))
				resources.add(statement.getSubject());
		});
		return List.copyOf(resources);
	}

	static Resource objectResource(Resource subject, IfcOWL ifc, String localName) {
		Statement statement = subject.getProperty(ifc.getProperty(localName));
		return statement != null && statement.getObject().isResource() ? statement.getResource() : null;
	}

	static List<Resource> objectResources(Resource subject, IfcOWL ifc, String localName) {
		List<Resource> values = new ArrayList<>();
		subject.listProperties(ifc.getProperty(localName)).forEachRemaining(statement ->
			addValue(statement.getObject(), values, new HashSet<>()));
		return values;
	}

	private static void addValue(RDFNode node, List<Resource> values, Set<Resource> visited) {
		if (!node.isResource()) return;
		Resource resource = node.asResource();
		if (!visited.add(resource)) return;
		Statement contents = resource.getProperty(resource.getModel().createProperty(LIST + "hasContents"));
		if (contents == null) {
			values.add(resource);
			return;
		}
		addValue(contents.getObject(), values, visited);
		Statement next = resource.getProperty(resource.getModel().createProperty(LIST + "hasNext"));
		if (next != null) addValue(next.getObject(), values, visited);
	}

	static String scalar(Resource subject, IfcOWL ifc, String localName) {
		Statement statement = subject.getProperty(ifc.getProperty(localName));
		if (statement == null) return null;
		RDFNode value = statement.getObject();
		if (value.isLiteral()) return value.asLiteral().getLexicalForm();
		if (!value.isResource()) return null;
		Resource resource = value.asResource();
		if (resource.isURIResource() && !resource.listProperties().hasNext()) return resource.getLocalName();
		for (var property : List.of(IfcOWL.Express.getHasString(), IfcOWL.Express.getHasLogical(),
				IfcOWL.Express.getHasBoolean(), IfcOWL.Express.getHasInteger(), IfcOWL.Express.getHasDouble())) {
			Statement literal = resource.getProperty(property);
			if (literal != null && literal.getObject().isLiteral()) return literal.getLiteral().getLexicalForm();
		}
		return resource.isURIResource() ? resource.getLocalName() : resource.toString();
	}
}
