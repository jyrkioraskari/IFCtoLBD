package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

class EtimPropertyAnnotationStageTest {
	private static final String IFC = "https://example.test/ifc#";
	private static final String SUPPLY = SupplyChainStage.NS;
	private static final String CLASS_URI = "https://identifier.example/etim/class/EC0001";
	private static final String CLASS_PROPERTY_URI =
			"https://identifier.example/etim/class-property/EC0001-EF0001";
	private static final String PROPERTY_URI = "https://identifier.example/etim/property/EF0001";

	@Test
	void annotatesExplicitlyIdentifiedPropertyOnlyOnEtimClassifiedElement() {
		Model source = ModelFactory.createDefaultModel();
		Model general = ModelFactory.createDefaultModel();
		Model properties = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource element = source.createResource("urn:ifc:pump")
				.addProperty(RDF.type, source.createResource(IFC + "IfcPump"));
		Resource lbdElement = general.createResource("urn:lbd:pump");
		Resource property = property(source, ifc, "urn:ifc:diameter", "Diameter", "20");
		Resource set = occurrenceSet(source, ifc, element, "urn:ifc:dimensions", "Pset_Dimensions", property);
		classifyAsEtim(source, ifc, element);
		externalIdentifier(source, ifc, property, "EF0001");
		Resource millimetre = siUnit(source, ifc, "urn:ifc:mm");
		property.addProperty(ifc.getUnit_IfcPropertySingleValue(), millimetre);

		SupplyChainStage.enrichSupplyChain(source, ifc, general, properties,
				Map.of(element, lbdElement), resolver(definition("Real", "20")), List.of());

		Resource annotation = annotation(properties);
		assertTrue(annotation.hasProperty(properties.createProperty(SUPPLY + "classProperty"),
				properties.createResource(CLASS_PROPERTY_URI)));
		assertTrue(annotation.hasProperty(properties.createProperty(SUPPLY + "property"),
				properties.createResource(PROPERTY_URI)));
		assertEquals("explicit-identifier",
				annotation.getProperty(properties.createProperty(SUPPLY + "mappingMethod")).getString());
		assertTrue(annotation.hasProperty(properties.createProperty(SUPPLY + "sourcePropertySet"), set));
		assertTrue(annotation.hasProperty(properties.createProperty(SUPPLY + "sourceObject"), element));
		assertTrue(annotation.hasProperty(properties.createProperty(SUPPLY + "sourceUnit"), millimetre));
		assertEquals("Pset_Dimensions",
				annotation.getProperty(properties.createProperty(SUPPLY + "sourcePropertySetName")).getString());
		assertEquals("Diameter",
				annotation.getProperty(properties.createProperty(SUPPLY + "sourcePropertyName")).getString());
		assertEquals("20", annotation.getProperty(properties.createProperty(SUPPLY + "observedValue"))
				.getLiteral().getLexicalForm());
		assertTrue(annotation.hasProperty(properties.createProperty("http://qudt.org/schema/qudt/unit"),
				properties.createResource("http://qudt.org/vocab/unit/MilliM")));
		assertFalse(properties.contains(set, RDF.type, properties.createResource(CLASS_URI)));
		assertTrue(source.contains(set, ifc.getHasProperties_IfcPropertySet(), property));
	}

	@Test
	void reviewedContextMappingRetainsTypeAndOccurrenceProvenanceAndOverride() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource element = source.createResource("urn:ifc:pump")
				.addProperty(RDF.type, source.createResource(IFC + "IfcPump"));
		Resource lbdElement = output.createResource("urn:lbd:pump");
		Resource type = source.createResource("urn:ifc:pump-type");
		Resource typeProperty = property(source, ifc, "urn:ifc:type-diameter", "NominalDiameter", "10");
		typeProperty.addProperty(ifc.getUnit_IfcPropertySingleValue(), siUnit(source, ifc, "urn:ifc:type-mm"));
		Resource typeSet = propertySet(source, ifc, "urn:ifc:type-set", "Pset_Dimensions", typeProperty);
		type.addProperty(ifc.getProperty("hasPropertySets_IfcTypeObject"), typeSet);
		source.createResource("urn:ifc:type-relation")
				.addProperty(ifc.getProperty("relatedObjects_IfcRelDefinesByType"), element)
				.addProperty(ifc.getProperty("relatingType_IfcRelDefinesByType"), type);
		Resource occurrenceProperty = property(source, ifc, "urn:ifc:occurrence-diameter",
				"NominalDiameter", "20");
		occurrenceProperty.addProperty(ifc.getUnit_IfcPropertySingleValue(),
				siUnit(source, ifc, "urn:ifc:occurrence-mm"));
		occurrenceSet(source, ifc, element, "urn:ifc:occurrence-set", "Pset_Dimensions", occurrenceProperty);
		EtimPropertyMapping mapping = new EtimPropertyMapping(CLASS_URI, "IfcPump", "Pset_Dimensions",
				"NominalDiameter", CLASS_PROPERTY_URI, PROPERTY_URI, "review:ETIM-2026-04");

		EtimPropertyAnnotationStage.annotate(source, ifc, output, element, lbdElement,
				definition("Real", null), List.of(mapping));

		List<Resource> annotations = output.listResourcesWithProperty(RDF.type,
				output.createResource(SUPPLY + "EtimPropertyAnnotation")).toList();
		assertEquals(2, annotations.size());
		Resource occurrence = annotations.stream().filter(value -> value.hasLiteral(
				output.createProperty(SUPPLY + "sourceKind"), "occurrence")).findFirst().orElseThrow();
		Resource inherited = annotations.stream().filter(value -> value.hasLiteral(
				output.createProperty(SUPPLY + "sourceKind"), "type")).findFirst().orElseThrow();
		assertTrue(occurrence.hasProperty(output.createProperty(SUPPLY + "overrides"), inherited));
		assertTrue(occurrence.hasProperty(output.createProperty(SUPPLY + "sourceObject"), element));
		assertTrue(inherited.hasProperty(output.createProperty(SUPPLY + "sourceObject"), type));
		assertTrue(occurrence.hasLiteral(output.createProperty(SUPPLY + "reviewReference"),
				"review:ETIM-2026-04"));
		assertEquals("20", occurrence.getProperty(output.createProperty(SUPPLY + "observedValue"))
				.getLiteral().getLexicalForm());
		assertEquals("10", inherited.getProperty(output.createProperty(SUPPLY + "observedValue"))
				.getLiteral().getLexicalForm());
	}

	@Test
	void labelSimilarityAloneDoesNotCreateMapping() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource element = source.createResource("urn:ifc:pump");
		Resource property = property(source, ifc, "urn:ifc:diameter", "Diameter", "20");
		occurrenceSet(source, ifc, element, "urn:ifc:set", "Dimensions", property);

		EtimPropertyAnnotationStage.annotate(source, ifc, output, element,
				output.createResource("urn:lbd:pump"), definition("Real", null), List.of());

		assertFalse(output.contains(null, RDF.type, output.createResource(SUPPLY + "EtimPropertyAnnotation")));
	}

	@Test
	void rejectsReviewedMappingWhenAllowedValueIsIncompatible() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource element = source.createResource("urn:ifc:pump");
		Resource property = property(source, ifc, "urn:ifc:diameter", "NominalDiameter", "20");
		property.addProperty(ifc.getUnit_IfcPropertySingleValue(), siUnit(source, ifc, "urn:ifc:mm"));
		occurrenceSet(source, ifc, element, "urn:ifc:set", "Pset_Dimensions", property);
		EtimPropertyMapping mapping = new EtimPropertyMapping(CLASS_URI, "", "Pset_Dimensions",
				"NominalDiameter", CLASS_PROPERTY_URI, null, "review:1");

		EtimPropertyAnnotationStage.annotate(source, ifc, output, element,
				output.createResource("urn:lbd:pump"), definition("Real", "25"), List.of(mapping));

		assertFalse(output.contains(null, RDF.type, output.createResource(SUPPLY + "EtimPropertyAnnotation")));
	}

	@Test
	void rejectsReviewedMappingWhenDatatypeIsIncompatible() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource element = source.createResource("urn:ifc:pump");
		Resource property = property(source, ifc, "urn:ifc:diameter", "NominalDiameter", "20");
		property.addProperty(ifc.getUnit_IfcPropertySingleValue(), siUnit(source, ifc, "urn:ifc:mm"));
		occurrenceSet(source, ifc, element, "urn:ifc:set", "Pset_Dimensions", property);
		EtimPropertyMapping mapping = new EtimPropertyMapping(CLASS_URI, "", "Pset_Dimensions",
				"NominalDiameter", CLASS_PROPERTY_URI, null, "review:1");

		EtimPropertyAnnotationStage.annotate(source, ifc, output, element,
				output.createResource("urn:lbd:pump"), definition("String", null), List.of(mapping));

		assertFalse(output.contains(null, RDF.type, output.createResource(SUPPLY + "EtimPropertyAnnotation")));
	}

	@Test
	void rejectsReviewedMappingWhenUnitIsIncompatible() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource element = source.createResource("urn:ifc:pump");
		Resource property = property(source, ifc, "urn:ifc:diameter", "NominalDiameter", "20");
		Resource metre = source.createResource("urn:ifc:metre")
				.addProperty(ifc.getUnitType_IfcNamedUnit(), source.createResource(IFC + "LENGTHUNIT"))
				.addProperty(ifc.getName_IfcSIUnit(), source.createResource(IFC + "METRE"));
		property.addProperty(ifc.getUnit_IfcPropertySingleValue(), metre);
		occurrenceSet(source, ifc, element, "urn:ifc:set", "Pset_Dimensions", property);
		EtimPropertyMapping mapping = new EtimPropertyMapping(CLASS_URI, "", "Pset_Dimensions",
				"NominalDiameter", CLASS_PROPERTY_URI, null, "review:1");

		EtimPropertyAnnotationStage.annotate(source, ifc, output, element,
				output.createResource("urn:lbd:pump"), definition("Real", null), List.of(mapping));

		assertFalse(output.contains(null, RDF.type, output.createResource(SUPPLY + "EtimPropertyAnnotation")));
	}

	private static Resource property(Model model, IfcOWL ifc, String uri, String name, String value) {
		Resource wrappedValue = model.createResource()
				.addProperty(RDF.type, model.createResource(IFC + "IfcLengthMeasure"))
				.addLiteral(model.createProperty("https://w3id.org/express#hasDecimal"),
						model.createTypedLiteral(value, XSD.decimal.getURI()));
		return model.createResource(uri)
				.addProperty(ifc.getProperty("name_IfcProperty"), wrapped(model, name))
				.addProperty(ifc.getNominalValue_IfcPropertySingleValue(), wrappedValue);
	}

	private static Resource occurrenceSet(Model model, IfcOWL ifc, Resource element, String uri,
			String name, Resource property) {
		Resource set = propertySet(model, ifc, uri, name, property);
		model.createResource()
				.addProperty(RDF.type, model.createResource(IFC + "IfcRelDefinesByProperties"))
				.addProperty(ifc.getRelatedObjects_IfcRelDefines(), element)
				.addProperty(ifc.getRelatingPropertyDefinition_IfcRelDefinesByProperties(), set);
		return set;
	}

	private static Resource propertySet(Model model, IfcOWL ifc, String uri, String name, Resource property) {
		return model.createResource(uri)
				.addProperty(RDF.type, model.createResource(IFC + "IfcPropertySet"))
				.addProperty(ifc.getProperty("name_IfcRoot"), wrapped(model, name))
				.addProperty(ifc.getHasProperties_IfcPropertySet(), property);
	}

	private static void classifyAsEtim(Model model, IfcOWL ifc, Resource element) {
		Resource system = model.createResource("urn:ifc:etim-system")
				.addProperty(ifc.getProperty("name_IfcClassification"), wrapped(model, "ETIM"));
		Resource reference = model.createResource("urn:ifc:etim-class")
				.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(model, "EC0001"))
				.addProperty(ifc.getProperty("referencedSource_IfcClassificationReference"), system);
		model.createResource("urn:ifc:classification-relation")
				.addProperty(RDF.type, model.createResource(IFC + "IfcRelAssociatesClassification"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), element)
				.addProperty(ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"), reference);
	}

	private static void externalIdentifier(Model model, IfcOWL ifc, Resource property, String identifier) {
		Resource reference = model.createResource("urn:ifc:property-reference")
				.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(model, identifier));
		model.createResource("urn:ifc:property-reference-relation")
				.addProperty(ifc.getProperty("relatedResourceObjects_IfcExternalReferenceRelationship"), property)
				.addProperty(ifc.getProperty("relatingReference_IfcExternalReferenceRelationship"), reference);
	}

	private static Resource siUnit(Model model, IfcOWL ifc, String uri) {
		return model.createResource(uri)
				.addProperty(ifc.getUnitType_IfcNamedUnit(), model.createResource(IFC + "LENGTHUNIT"))
				.addProperty(ifc.getPrefix_IfcSIUnit(), model.createResource(IFC + "MILLI"))
				.addProperty(ifc.getName_IfcSIUnit(), model.createResource(IFC + "METRE"));
	}

	private static ClassificationDefinition definition(String dataType, String allowedValue) {
		List<ClassificationDefinition.AllowedValue> allowed = allowedValue == null ? List.of()
				: List.of(new ClassificationDefinition.AllowedValue(
						"https://identifier.example/etim/value/" + allowedValue, allowedValue, allowedValue, null));
		var reusable = new ClassificationDefinition.Property(PROPERTY_URI, "EF0001", "Diameter", null);
		var assignment = new ClassificationDefinition.ClassProperty(CLASS_PROPERTY_URI, reusable,
				"ETIM dimensions", dataType, null, null, null, null, allowed,
				List.of(new ClassificationDefinition.Unit("mm", "unit:MILLIMETER")));
		return new ClassificationDefinition(CLASS_URI, "https://identifier.example/etim/10", "EC0001",
				"Pump", null, List.of("IfcPump"), List.of(assignment), List.of());
	}

	private static ClassificationResolver resolver(ClassificationDefinition definition) {
		return new ClassificationResolver() {
			@Override public Optional<Resolution> resolve(Request request) {
				return Optional.of(new Resolution(CLASS_URI, "test", "10", 1));
			}
			@Override public Optional<ClassificationDefinition> definition(Resolution resolution) {
				return Optional.of(definition);
			}
		};
	}

	private static Resource annotation(Model model) {
		return model.listResourcesWithProperty(RDF.type,
				model.createResource(SUPPLY + "EtimPropertyAnnotation")).next();
	}

	private static Resource wrapped(Model model, String value) {
		return model.createResource().addLiteral(model.createProperty("https://w3id.org/express#hasString"), value);
	}
}
