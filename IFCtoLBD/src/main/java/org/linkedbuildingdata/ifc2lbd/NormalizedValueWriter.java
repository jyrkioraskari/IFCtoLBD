package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/** Writes normalized values with QUDT units and mapping provenance. */
final class NormalizedValueWriter {
	private static final String QUDT = "http://qudt.org/schema/qudt/";
	private static final String SCHEMA = "https://schema.org/";
	private static final String PROV = "http://www.w3.org/ns/prov#";

	private NormalizedValueWriter() { }

	static Resource write(Model model, Resource owner, String propertyName, NormalizedValue normalized, String id) {
		Resource value = model.createResource("urn:ifctolbd:value:" + id)
				.addProperty(RDF.type, model.createResource(SupplyChainStage.NS + "PropertyValue"))
				.addProperty(model.createProperty(SCHEMA + "value"), normalized.value())
				.addLiteral(model.createProperty(SupplyChainStage.NS + "mappingMethod"), normalized.mappingMethod())
				.addLiteral(model.createProperty(SupplyChainStage.NS + "confidence"), normalized.confidence());
		if (normalized.value().isLiteral() && normalized.value().asLiteral().getValue() instanceof Number)
			value.addProperty(RDF.type, model.createResource(QUDT + "QuantityValue"))
					.addProperty(model.createProperty(QUDT + "numericValue"), normalized.value());
		if (normalized.unitUri() != null && !normalized.unitUri().isBlank())
			value.addProperty(model.createProperty(QUDT + "unit"), model.createResource(normalized.unitUri()));
		if (normalized.originalUnitCode() != null && !normalized.originalUnitCode().isBlank())
			value.addLiteral(model.createProperty(UnitResolver.META + "originalUnitCode"),
					normalized.originalUnitCode());
		if (normalized.sourceIfcType() != null && !normalized.sourceIfcType().isBlank())
			addIdentifier(model, value, UnitResolver.META + "sourceIFCType", normalized.sourceIfcType());
		if (normalized.ifcDataType() != null && !normalized.ifcDataType().isBlank())
			addIdentifier(model, value, UnitResolver.META + "ifcDataType", normalized.ifcDataType());
		if (normalized.sourcePropertyUri() != null && !normalized.sourcePropertyUri().isBlank())
			value.addProperty(model.createProperty(PROV + "wasDerivedFrom"), model.createResource(normalized.sourcePropertyUri()));
		owner.addProperty(model.createProperty(SupplyChainStage.NS + propertyName + "Value"), value);
		return value;
	}

	private static void addIdentifier(Model model, Resource owner, String predicate, String identifier) {
		if (identifier.startsWith("http://") || identifier.startsWith("https://") || identifier.startsWith("urn:"))
			owner.addProperty(model.createProperty(predicate), model.createResource(identifier));
		else
			owner.addLiteral(model.createProperty(predicate), identifier);
	}
}
