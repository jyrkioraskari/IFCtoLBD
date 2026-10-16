package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.BMO;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

class MaterialConversionStageTest {
	private static final String IFC = "https://example.test/ifc#";

	@Test
	void mapsOrderedLayersMaterialsThicknessAndProjectLengthUnit() {
		Fixture fixture = new Fixture();
		Resource firstMaterial = fixture.resource("IfcMaterial_1", "IfcMaterial")
				.addProperty(fixture.ifc.getProperty("name_IfcMaterial"), fixture.wrapped("Brick"));
		Resource secondMaterial = fixture.resource("IfcMaterial_2", "IfcMaterial")
				.addProperty(fixture.ifc.getProperty("name_IfcMaterial"), fixture.wrapped("Insulation"));
		Resource firstLayer = fixture.layer("IfcMaterialLayer_3", firstMaterial, "100");
		Resource secondLayer = fixture.layer("IfcMaterialLayer_4", secondMaterial, "50.5");
		Resource thirdLayer = fixture.layer("IfcMaterialLayer_5", firstMaterial, "20");
		Resource listEnd = fixture.source.createResource("urn:list:end")
				.addProperty(fixture.source.createProperty("https://w3id.org/list#hasContents"), thirdLayer);
		Resource listTail = fixture.source.createResource("urn:list:tail")
				.addProperty(fixture.source.createProperty("https://w3id.org/list#hasContents"), secondLayer)
				.addProperty(fixture.source.createProperty("https://w3id.org/list#hasNext"), listEnd);
		Resource listHead = fixture.source.createResource("urn:list:head")
				.addProperty(fixture.source.createProperty("https://w3id.org/list#hasContents"), firstLayer)
				.addProperty(fixture.source.createProperty("https://w3id.org/list#hasNext"), listTail);
		Resource layerSet = fixture.resource("IfcMaterialLayerSet_5", "IfcMaterialLayerSet")
				.addProperty(fixture.ifc.getProperty("materialLayers_IfcMaterialLayerSet"), listHead);
		Resource usage = fixture.resource("IfcMaterialLayerSetUsage_6", "IfcMaterialLayerSetUsage")
				.addProperty(fixture.ifc.getProperty("forLayerSet_IfcMaterialLayerSetUsage"), layerSet)
				.addProperty(fixture.ifc.getProperty("layerSetDirection_IfcMaterialLayerSetUsage"),
						fixture.source.createResource(IFC + "AXIS2"))
				.addProperty(fixture.ifc.getProperty("directionSense_IfcMaterialLayerSetUsage"),
						fixture.source.createResource(IFC + "POSITIVE"))
				.addProperty(fixture.ifc.getProperty("offsetFromReferenceLine_IfcMaterialLayerSetUsage"),
						fixture.wrappedDouble("12.5"));
		fixture.assignment(usage);
		fixture.projectLengthUnit("MILLI", "METRE");

		fixture.map();

		Resource mappedSet = fixture.mappedProduct(layerSet);
		Resource mappedFirst = fixture.mappedProduct(firstLayer);
		Resource mappedSecond = fixture.mappedProduct(secondLayer);
		Resource mappedThird = fixture.mappedProduct(thirdLayer);
		Resource assignment = fixture.productElement().getPropertyResourceValue(IFCtoLBDMapping.materialAssignment);
		assertTrue(assignment.hasProperty(IFCtoLBDMapping.assignedMaterialDefinition, mappedSet));
		assertTrue(mappedSet.hasProperty(BMO.hasLayers, mappedFirst));
		assertTrue(mappedSet.hasProperty(BMO.hasLayers, mappedSecond));
		assertTrue(mappedSet.hasProperty(BMO.hasLayers, mappedThird));
		assertTrue(membershipAt(mappedSet, 1).hasProperty(IFCtoLBDMapping.layer, mappedFirst));
		assertTrue(membershipAt(mappedSet, 2).hasProperty(IFCtoLBDMapping.layer, mappedSecond));
		assertTrue(membershipAt(mappedSet, 3).hasProperty(IFCtoLBDMapping.layer, mappedThird));
		assertTrue(mappedThird.hasProperty(BMO.hasMaterial, fixture.mappedProduct(firstMaterial)));
		Resource mappedUsage = fixture.mappedProduct(usage);
		assertTrue(assignment.hasProperty(IFCtoLBDMapping.layerSetUsage, mappedUsage));
		assertTrue(mappedUsage.hasProperty(IFCtoLBDMapping.layerSetDirection, "AXIS2"));
		assertTrue(mappedUsage.hasProperty(IFCtoLBDMapping.directionSense, "POSITIVE"));
		assertEquals("12.5", fixture.propertyFor(mappedUsage).getProperty(IFCtoLBDMapping.offsetFromReferenceLine)
				.getLiteral().getLexicalForm());
		Resource propertyLayer = fixture.propertyFor(mappedFirst);
		assertEquals("0.1", propertyLayer.getProperty(BMO.hasThickness).getLiteral().getLexicalForm());
		Resource quantity = propertyLayer.getPropertyResourceValue(IFCtoLBDMapping.thicknessQuantity);
		assertEquals("0.1", quantity.getProperty(
				fixture.properties.createProperty(UnitResolver.QUDT_SCHEMA + "numericValue"))
				.getLiteral().getLexicalForm());
		assertEquals(UnitResolver.QUDT_UNIT + "M", quantity.getPropertyResourceValue(
				fixture.properties.createProperty(UnitResolver.QUDT_SCHEMA + "unit")).getURI());
		assertEquals("100", quantity.getProperty(
				fixture.properties.createProperty(UnitResolver.META + "originalValue")).getLiteral().getLexicalForm());
		assertEquals("MILLI METRE", quantity.getProperty(
				fixture.properties.createProperty(UnitResolver.META + "originalUnitCode")).getString());
		assertFalse(mappedFirst.hasProperty(BMO.hasThickness));
		assertFalse(mappedFirst.hasProperty(BMO.hasUnit));
		assertFalse(propertyLayer.hasProperty(BMO.hasUnit));
		assertTrue(!fixture.products.contains(BMO.Material, RDF.type, OWL.Class));
		assertTrue(!fixture.products.contains(null, OWL.imports));
		assertTrue(fixture.general.isEmpty());
		assertTrue(fixture.products.intersection(fixture.properties).isEmpty());
		Model combined = ModelFactory.createUnion(fixture.products, fixture.properties);
		assertEquals(fixture.products.size() + fixture.properties.size(), combined.size());
		assertTrue(fixture.context.diagnosticsModel().isEmpty());
	}

	@Test
	void normalizesConversionBasedProjectLengthUnitsToMetres() {
		Fixture fixture = new Fixture();
		Resource material = fixture.material("IfcMaterial_1", "Timber");
		Resource layer = fixture.layer("IfcMaterialLayer_2", material, "2");
		Resource layerSet = fixture.resource("IfcMaterialLayerSet_3", "IfcMaterialLayerSet")
				.addProperty(fixture.ifc.getProperty("materialLayers_IfcMaterialLayerSet"), layer);
		fixture.assignment(layerSet);
		fixture.projectConversionBasedLengthUnit("FOOT", "0.3048");

		fixture.map();

		Resource mappedLayer = fixture.mappedProduct(layer);
		Resource propertyLayer = fixture.propertyFor(mappedLayer);
		assertEquals("0.6096", propertyLayer.getProperty(BMO.hasThickness).getLiteral().getLexicalForm());
		Resource quantity = propertyLayer.getPropertyResourceValue(IFCtoLBDMapping.thicknessQuantity);
		assertEquals(UnitResolver.QUDT_UNIT + "M", quantity.getPropertyResourceValue(
				fixture.properties.createProperty(UnitResolver.QUDT_SCHEMA + "unit")).getURI());
		assertEquals(UnitResolver.QUDT_UNIT + "FT", quantity.getPropertyResourceValue(
				fixture.properties.createProperty(UnitResolver.META + "sourceUnit")).getURI());
		assertTrue(fixture.context.diagnosticsModel().isEmpty());
	}

	@Test
	void retainsSourceThicknessAndReportsAnUnresolvedProjectUnit() {
		Fixture fixture = new Fixture();
		Resource material = fixture.material("IfcMaterial_1", "Unknown-unit material");
		Resource layer = fixture.layer("IfcMaterialLayer_2", material, "25");
		Resource layerSet = fixture.resource("IfcMaterialLayerSet_3", "IfcMaterialLayerSet")
				.addProperty(fixture.ifc.getProperty("materialLayers_IfcMaterialLayerSet"), layer);
		fixture.assignment(layerSet);

		fixture.map();

		Resource mappedLayer = fixture.mappedProduct(layer);
		Resource propertyLayer = fixture.propertyFor(mappedLayer);
		assertFalse(propertyLayer.hasProperty(BMO.hasThickness));
		Resource quantity = propertyLayer.getPropertyResourceValue(IFCtoLBDMapping.thicknessQuantity);
		assertEquals("25", quantity.getProperty(
				fixture.properties.createProperty(UnitResolver.META + "originalValue")).getLiteral().getLexicalForm());
		assertFalse(quantity.hasProperty(fixture.properties.createProperty(UnitResolver.QUDT_SCHEMA + "unit")));
		assertTrue(fixture.context.diagnosticsModel().contains(null,
				fixture.context.diagnosticsModel().createProperty(MaterialConversionStage.REPORT_NS + "code"),
				"UNRESOLVED_LAYER_THICKNESS_UNIT"));
	}

	@Test
	void retainsAnExplicitUnknownProjectUnitWithoutCallingItMetres() {
		Fixture fixture = new Fixture();
		Resource material = fixture.material("IfcMaterial_1", "Unknown-unit material");
		Resource layer = fixture.layer("IfcMaterialLayer_2", material, "25");
		Resource layerSet = fixture.resource("IfcMaterialLayerSet_3", "IfcMaterialLayerSet")
				.addProperty(fixture.ifc.getProperty("materialLayers_IfcMaterialLayerSet"), layer);
		fixture.assignment(layerSet);
		fixture.projectUnknownLengthUnit();

		fixture.map();

		Resource propertyLayer = fixture.propertyFor(fixture.mappedProduct(layer));
		assertFalse(propertyLayer.hasProperty(BMO.hasThickness));
		Resource quantity = propertyLayer.getPropertyResourceValue(IFCtoLBDMapping.thicknessQuantity);
		assertEquals("25", quantity.getProperty(
				fixture.properties.createProperty(UnitResolver.META + "originalValue")).getLiteral().getLexicalForm());
		assertTrue(quantity.getProperty(
				fixture.properties.createProperty(UnitResolver.META + "originalUnitCode")).getString()
				.contains("unknown-length-unit"));
		assertFalse(quantity.hasProperty(fixture.properties.createProperty(UnitResolver.QUDT_SCHEMA + "unit")));
		assertTrue(fixture.context.diagnosticsModel().contains(null,
				fixture.context.diagnosticsModel().createProperty(MaterialConversionStage.REPORT_NS + "code"),
				"UNRESOLVED_LAYER_THICKNESS_UNIT"));
	}

	@Test
	void reportsUnsupportedMaterialListsInsteadOfSilentlyDroppingThem() {
		Fixture fixture = new Fixture();
		Resource materialList = fixture.resource("IfcMaterialList_1", "IfcMaterialList");
		fixture.assignment(materialList);

		fixture.map();

		Model report = fixture.context.diagnosticsModel();
		assertTrue(report.contains(null, report.createProperty(MaterialConversionStage.REPORT_NS + "code"),
				"UNSUPPORTED_MATERIAL_STRUCTURE"));
		assertTrue(report.contains(null,
				report.createProperty(MaterialConversionStage.REPORT_NS + "ifcStructureType"), "IfcMaterialList"));
	}

	@Test
	void resolvesDirectMaterialAssignmentsInheritedFromIfcTypes() {
		Fixture fixture = new Fixture();
		Resource type = fixture.resource("IfcWallType_1", "IfcWallType");
		Resource typeRelation = fixture.resource("IfcRelDefinesByType_2", "IfcRelDefinesByType")
				.addProperty(fixture.ifc.getProperty("relatingType_IfcRelDefinesByType"), type)
				.addProperty(fixture.ifc.getProperty("relatedObjects_IfcRelDefinesByType"), fixture.ifcElement);
		Resource material = fixture.resource("IfcMaterial_3", "IfcMaterial")
				.addProperty(fixture.ifc.getProperty("name_IfcMaterial"), fixture.wrapped("Concrete"));
		fixture.assignment(type, material);

		fixture.map();

		Resource provenance = fixture.productElement().getPropertyResourceValue(IFCtoLBDMapping.materialAssignment);
		Resource definition = provenance.getPropertyResourceValue(IFCtoLBDMapping.assignedMaterialDefinition);
		assertTrue(definition.hasProperty(RDF.type, BMO.MaterialDefinition));
		assertTrue(definition.hasProperty(BMO.hasMaterial, fixture.mappedProduct(material)));
		assertTrue(fixture.mappedProduct(material).hasProperty(RDF.type, BMO.Material));
		assertTrue(provenance.hasProperty(IFCtoLBDMapping.assignedMaterial, fixture.mappedProduct(material)));
		assertFalse(fixture.productElement().hasProperty(BMO.hasMaterial));
		assertFalse(fixture.mappedProduct(material).hasProperty(RDF.type, BMO.Concrete));
		assertFalse(fixture.mappedProduct(material).hasProperty(BMO.hasMaterialName));
		assertTrue(typeRelation.hasProperty(RDF.type, fixture.source.createResource(IFC + "IfcRelDefinesByType")));
		assertTrue(provenance.getProperty(IFCtoLBDMapping.materialAssignmentInherited).getBoolean());
		assertTrue(provenance.hasProperty(IFCtoLBDMapping.sourceIfcTypeResource, type));
		assertTrue(provenance.hasProperty(IFCtoLBDMapping.sourceIfcType, "IfcWallType"));
	}

	@Test
	void occurrenceAssignmentOverridesTypeAssignment() {
		Fixture fixture = new Fixture();
		Resource type = fixture.resource("IfcWallType_1", "IfcWallType");
		fixture.resource("IfcRelDefinesByType_2", "IfcRelDefinesByType")
				.addProperty(fixture.ifc.getProperty("relatingType_IfcRelDefinesByType"), type)
				.addProperty(fixture.ifc.getProperty("relatedObjects_IfcRelDefinesByType"), fixture.ifcElement);
		Resource typeMaterial = fixture.material("IfcMaterial_3", "Type material");
		Resource occurrenceMaterial = fixture.material("IfcMaterial_4", "Occurrence material");
		fixture.assignment("IfcRelAssociatesMaterial_20", type, typeMaterial);
		Resource occurrenceAssociation = fixture.assignment("IfcRelAssociatesMaterial_21",
				fixture.ifcElement, occurrenceMaterial);

		fixture.map();

		Resource provenance = fixture.productElement().getPropertyResourceValue(IFCtoLBDMapping.materialAssignment);
		assertTrue(provenance.hasProperty(IFCtoLBDMapping.assignedMaterial, fixture.mappedProduct(occurrenceMaterial)));
		assertFalse(fixture.mapped.containsKey(typeMaterial));
		assertFalse(provenance.getProperty(IFCtoLBDMapping.materialAssignmentInherited).getBoolean());
		assertTrue(provenance.hasProperty(IFCtoLBDMapping.sourceIfcAssociation, occurrenceAssociation));
	}

	@Test
	void unsupportedOccurrenceAssignmentDoesNotFallBackToType() {
		Fixture fixture = new Fixture();
		Resource type = fixture.resource("IfcWallType_1", "IfcWallType");
		fixture.resource("IfcRelDefinesByType_2", "IfcRelDefinesByType")
				.addProperty(fixture.ifc.getProperty("relatingType_IfcRelDefinesByType"), type)
				.addProperty(fixture.ifc.getProperty("relatedObjects_IfcRelDefinesByType"), fixture.ifcElement);
		Resource typeMaterial = fixture.material("IfcMaterial_3", "Type material");
		fixture.assignment("IfcRelAssociatesMaterial_20", type, typeMaterial);
		fixture.assignment("IfcRelAssociatesMaterial_21", fixture.ifcElement,
				fixture.resource("IfcMaterialList_4", "IfcMaterialList"));

		fixture.map();

		Resource assignment = fixture.productElement().getPropertyResourceValue(IFCtoLBDMapping.materialAssignment);
		assertFalse(assignment.hasProperty(IFCtoLBDMapping.assignedMaterialDefinition));
		assertFalse(fixture.mapped.containsKey(typeMaterial));
		assertTrue(fixture.context.diagnosticsModel().contains(null,
				fixture.context.diagnosticsModel().createProperty(MaterialConversionStage.REPORT_NS + "code"),
				"UNSUPPORTED_MATERIAL_STRUCTURE"));
	}

	@Test
	void followsIfc2x3InheritedRelatedObjectsProperty() {
		Fixture fixture = new Fixture();
		Resource type = fixture.resource("IfcWallType_1", "IfcWallType");
		fixture.resource("IfcRelDefinesByType_2", "IfcRelDefinesByType")
				.addProperty(fixture.ifc.getProperty("relatingType_IfcRelDefinesByType"), type)
				.addProperty(fixture.ifc.getProperty("relatedObjects_IfcRelDefines"), fixture.ifcElement);
		Resource material = fixture.material("IfcMaterial_3", "Legacy material");
		fixture.assignment("IfcRelAssociatesMaterial_20", type, material);

		fixture.map();

		assertTrue(fixture.productElement().hasProperty(IFCtoLBDMapping.materialAssignment));
		assertTrue(fixture.productElement().getPropertyResourceValue(IFCtoLBDMapping.materialAssignment)
				.getProperty(IFCtoLBDMapping.materialAssignmentInherited).getBoolean());
	}

	@Test
	void preservesOptionalMaterialMetadataWhenPresent() {
		Fixture fixture = new Fixture();
		Resource material = fixture.material("IfcMaterial_1", "Brick")
				.addProperty(fixture.ifc.getProperty("description_IfcMaterial"), fixture.wrapped("Fired clay"))
				.addProperty(fixture.ifc.getProperty("category_IfcMaterial"), fixture.wrapped("Masonry"));
		fixture.assignment(material);

		fixture.map();

		Resource mapped = fixture.mappedProduct(material);
		assertTrue(mapped.hasProperty(RDFS.label, "Brick"));
		assertTrue(mapped.hasProperty(org.apache.jena.vocabulary.DCTerms.description, "Fired clay"));
		assertTrue(mapped.hasProperty(IFCtoLBDMapping.materialCategory, "Masonry"));
	}

	private static Resource membershipAt(Resource layerSet, int position) {
		return layerSet.listProperties(IFCtoLBDMapping.layerMembership)
				.mapWith(statement -> statement.getResource())
				.filterKeep(membership -> membership.hasLiteral(IFCtoLBDMapping.position, position))
				.next();
	}

	private static final class Fixture {
		final Model source = ModelFactory.createDefaultModel();
		final Model general = ModelFactory.createDefaultModel();
		final Model products = ModelFactory.createDefaultModel();
		final Model properties = ModelFactory.createDefaultModel();
		final IfcOWL ifc = new IfcOWL(IFC);
		final Resource ifcElement = resource("IfcWall_10", "IfcWall");
		final Resource element = general.createResource("urn:lbd:wall");
		final Map<Resource, Resource> mapped = new HashMap<>(Map.of(ifcElement, element));
		final ConversionContext context = new ConversionContext(
				new ConversionRequest("model.ifc", ConversionProfiles.MATERIALS_BMO));

		Fixture() {
			context.attachMappedData(source, ifc, general, products, properties, mapped,
					(sourceResource, type) -> mapped.computeIfAbsent(sourceResource,
							key -> general.createResource("urn:lbd:" + type + ":" + key.getLocalName())));
		}

		Resource mappedProduct(Resource sourceResource) {
			return products.createResource(mapped.get(sourceResource).getURI());
		}

		Resource productElement() {
			return products.createResource(element.getURI());
		}

		Resource propertyFor(Resource resource) {
			return properties.createResource(resource.getURI());
		}

		Resource resource(String localName, String type) {
			return source.createResource("urn:ifc:" + localName)
					.addProperty(RDF.type, source.createResource(IFC + type));
		}

		Resource wrapped(String value) {
			return source.createResource().addLiteral(IfcOWL.Express.getHasString(), value);
		}

		Resource wrappedDouble(String value) {
			return source.createResource().addLiteral(IfcOWL.Express.getHasDouble(), value);
		}

		Resource layer(String localName, Resource material, String thickness) {
			return resource(localName, "IfcMaterialLayer")
					.addProperty(ifc.getProperty("material_IfcMaterialLayer"), material)
					.addProperty(ifc.getProperty("layerThickness_IfcMaterialLayer"),
							source.createResource().addLiteral(IfcOWL.Express.getHasDouble(), thickness));
		}

		Resource material(String localName, String name) {
			return resource(localName, "IfcMaterial")
					.addProperty(ifc.getProperty("name_IfcMaterial"), wrapped(name));
		}

		void assignment(Resource structure) {
			assignment(ifcElement, structure);
		}

		void assignment(Resource assignedObject, Resource structure) {
			assignment("IfcRelAssociatesMaterial_20", assignedObject, structure);
		}

		Resource assignment(String localName, Resource assignedObject, Resource structure) {
			return resource(localName, "IfcRelAssociatesMaterial")
					.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), assignedObject)
					.addProperty(ifc.getProperty("relatingMaterial_IfcRelAssociatesMaterial"), structure);
		}

		void projectLengthUnit(String prefix, String name) {
			Resource unit = source.createResource("urn:ifc:length-unit")
					.addProperty(ifc.getUnitType_IfcNamedUnit(), source.createResource(IFC + "LENGTHUNIT"))
					.addProperty(ifc.getPrefix_IfcSIUnit(), source.createResource(IFC + prefix))
					.addProperty(ifc.getName_IfcSIUnit(), source.createResource(IFC + name));
			Resource assignment = source.createResource("urn:ifc:units")
					.addProperty(ifc.getUnits_IfcUnitAssignment(), unit);
			source.createResource("urn:ifc:project")
					.addProperty(RDF.type, source.createResource(ifc.getIfcProject()))
					.addProperty(ifc.getUnitsInContext_IfcProject(), assignment);
		}

		void projectConversionBasedLengthUnit(String name, String factor) {
			Resource metre = source.createResource("urn:ifc:conversion-base-metre")
					.addProperty(ifc.getUnitType_IfcNamedUnit(), source.createResource(IFC + "LENGTHUNIT"))
					.addProperty(ifc.getName_IfcSIUnit(), source.createResource(IFC + "METRE"));
			Resource measure = source.createResource("urn:ifc:conversion-factor")
					.addProperty(ifc.getProperty("valueComponent_IfcMeasureWithUnit"), wrappedDouble(factor))
					.addProperty(ifc.getProperty("unitComponent_IfcMeasureWithUnit"), metre);
			Resource unit = source.createResource("urn:ifc:length-unit")
					.addProperty(ifc.getUnitType_IfcNamedUnit(), source.createResource(IFC + "LENGTHUNIT"))
					.addProperty(ifc.getProperty("name_IfcConversionBasedUnit"), wrapped(name))
					.addProperty(ifc.getProperty("conversionFactor_IfcConversionBasedUnit"), measure);
			Resource assignment = source.createResource("urn:ifc:units")
					.addProperty(ifc.getUnits_IfcUnitAssignment(), unit);
			source.createResource("urn:ifc:project")
					.addProperty(RDF.type, source.createResource(ifc.getIfcProject()))
					.addProperty(ifc.getUnitsInContext_IfcProject(), assignment);
		}

		void projectUnknownLengthUnit() {
			Resource unit = source.createResource("urn:ifc:unknown-length-unit")
					.addProperty(ifc.getUnitType_IfcNamedUnit(), source.createResource(IFC + "LENGTHUNIT"));
			Resource assignment = source.createResource("urn:ifc:units")
					.addProperty(ifc.getUnits_IfcUnitAssignment(), unit);
			source.createResource("urn:ifc:project")
					.addProperty(RDF.type, source.createResource(ifc.getIfcProject()))
					.addProperty(ifc.getUnitsInContext_IfcProject(), assignment);
		}

		void map() { MaterialConversionStage.map(context); }
	}
}
