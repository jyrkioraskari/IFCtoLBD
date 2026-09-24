package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.AttributeSet;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.PropertySet;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.SMLS;

class UnitResolverTest {
	private static final String IFC = "https://example.test/ifc#";
	private static final String EVIDENCE = "https://w3id.org/ifctolbd/evidence#";

	@Test
	void explicitIfcPrefixWinsOverProjectUnit() {
		Model model = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource project = model.createResource("urn:project").addProperty(RDF.type,
				model.createResource(ifc.getIfcProject()));
		Resource assignment = model.createResource("urn:assignment");
		Resource projectMetre = siUnit(model, ifc, "urn:metre", "LENGTHUNIT", null, "METRE");
		project.addProperty(ifc.getUnitsInContext_IfcProject(), assignment);
		assignment.addProperty(ifc.getUnits_IfcUnitAssignment(), projectMetre);

		UnitResolver resolver = UnitResolver.fromProject(model, ifc);
		Resource explicitMillimetre = siUnit(model, ifc, "urn:millimetre", "LENGTHUNIT", "MILLI", "METRE");
		Resource lengthType = model.createResource(IFC + "IfcLengthMeasure");

		assertEquals(UnitResolver.QUDT_UNIT + "MilliM",
				resolver.resolve(explicitMillimetre, lengthType, ifc).qudtUri());
		assertEquals(UnitResolver.QUDT_UNIT + "M", resolver.resolve(null, lengthType, ifc).qudtUri());
	}

	@Test
	void unresolvedAndCompoundCodesArePreservedWithoutGuessing() {
		UnitResolver.Resolution unknown = UnitResolver.empty().resolve(null,
				ModelFactory.createDefaultModel().createResource(IFC + "IfcLengthMeasure"));
		assertFalse(unknown.isResolved());
		assertEquals(null, unknown.originalCode());

		String compound = "kg CO₂ eq/m²";
		UnitResolver.Resolution preserved = UnitResolver.fromText(compound);
		assertFalse(preserved.isResolved());
		assertEquals(compound, preserved.originalCode());
	}

	@Test
	void textualAliasConversionChangesNumericValueWithUnit() {
		Model model = ModelFactory.createDefaultModel();
		UnitResolver.Resolution grams = UnitResolver.fromText("g");
		assertEquals(UnitResolver.QUDT_UNIT + "KiloGM", grams.qudtUri());
		assertEquals(new BigDecimal("0.001"), grams.multiplier());
		assertEquals("1.5", UnitResolver.normalizeValue(model,
				model.createTypedLiteral("1500", XSD.decimal.getURI()), grams).asLiteral().getLexicalForm());
	}

	@Test
	void bsddQudtIdentifierWinsWhileOriginalCodeIsRetained() {
		UnitResolver.Resolution resolution = UnitResolver.fromBsdd("unit:MILLIMETER", "mm (bSDD)");
		assertEquals(UnitResolver.QUDT_UNIT + "MilliM", resolution.qudtUri());
		assertEquals("mm (bSDD)", resolution.originalCode());
		assertEquals("2026-09-01", UnitResolver.ALIAS_TABLE_VERSION);
	}

	@Test
	void propertyOutputUsesQudtAndKeepsIfcDatatypeMetadata() {
		Model model = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource explicit = siUnit(model, ifc, "urn:mm", "LENGTHUNIT", "MILLI", "METRE");
		Resource sourceType = model.createResource(IFC + "IfcPositiveLengthMeasure");
		Resource futureType = model.createResource(IFC + "IfcDataTypeExample");
		PropertySet set = new PropertySet("https://example.test/", model, ModelFactory.createDefaultModel(),
				"Qto_Test", 3, false, UnitResolver.empty(), true);
		set.putPnameValue("Length", model.createTypedLiteral("12", XSD.decimal.getURI()));
		set.putPnameType("Length", sourceType);
		set.putPnameIfcDataType("Length", futureType);
		set.putPnameUnit("Length", explicit);
		Resource owner = model.createResource("urn:owner");
		set.connect(owner, "guid");

		Resource property = owner.listProperties().filterKeep(s -> s.getObject().isResource()
				&& s.getResource().hasProperty(RDF.type, OPM.property)).next().getResource();
		Resource state = property.getPropertyResourceValue(OPM.hasPropertyState);
		assertEquals(UnitResolver.QUDT_UNIT + "MilliM",
				state.getPropertyResourceValue(model.createProperty(UnitResolver.QUDT_SCHEMA + "unit")).getURI());
		assertFalse(state.hasProperty(SMLS.unit));
		assertEquals(sourceType, state.getPropertyResourceValue(model.createProperty(UnitResolver.META + "sourceIFCType")));
		assertEquals(futureType, state.getPropertyResourceValue(model.createProperty(UnitResolver.META + "ifcDataType")));
		assertNotNull(state.getProperty(model.createProperty(UnitResolver.META + "originalUnitCode")));
		assertEquals("IFC_EXPLICIT_UNIT",
				state.getProperty(model.createProperty(EVIDENCE + "unitResolutionMethod")).getString());
		assertEquals(UnitResolver.ALIAS_TABLE_VERSION,
				state.getProperty(model.createProperty(EVIDENCE + "unitResolverVersion")).getString());
		assertTrue(model.getNsPrefixURI("qudt").equals(UnitResolver.QUDT_SCHEMA));
	}

	@Test
	void stringPropertiesAndAttributesDoNotEmitUnitResolutionEvidence() {
		Model model = ModelFactory.createDefaultModel();
		Resource labelType = model.createResource(IFC + "IfcLabel");
		PropertySet properties = new PropertySet("https://example.test/", model,
				ModelFactory.createDefaultModel(), "Pset_Test", 3, false, UnitResolver.empty(), true);
		properties.putPnameValue("Description", model.createLiteral("unquantified text"));
		properties.putPnameType("Description", labelType);
		properties.connect(model.createResource("urn:property-owner"), "property-guid");

		AttributeSet attributes = new AttributeSet("https://example.test/", model, 3, false,
				UnitResolver.empty(), false, Map.of());
		attributes.putAnameValue("name_IfcRoot", model.createLiteral("Wall name"), Optional.of(labelType));
		attributes.connect(model.createResource("urn:attribute-owner"), "attribute-guid");

		assertTrue(model.contains(null, OPM.value, "unquantified text"));
		assertTrue(model.contains(null, OPM.value, "Wall name"));
		assertFalse(model.contains(null, model.createProperty(EVIDENCE + "unitResolutionMethod")));
		assertFalse(model.contains(null, model.createProperty(EVIDENCE + "unitResolverVersion")));
	}

	@Test
	void unresolvedNumericUnitsDoNotEmitResolutionEvidence() {
		Model model = ModelFactory.createDefaultModel();
		Resource lengthType = model.createResource(IFC + "IfcLengthMeasure");
		PropertySet properties = new PropertySet("https://example.test/", model,
				ModelFactory.createDefaultModel(), "Qto_Test", 3, false, UnitResolver.empty(), true);
		properties.putPnameValue("Length", model.createTypedLiteral("12", XSD.decimal.getURI()));
		properties.putPnameType("Length", lengthType);
		properties.connect(model.createResource("urn:property-owner"), "property-guid");

		AttributeSet attributes = new AttributeSet("https://example.test/", model, 3, false,
				UnitResolver.empty(), false, Map.of());
		attributes.putAnameValue("length_IfcThing", model.createTypedLiteral("3", XSD.decimal.getURI()),
				Optional.of(lengthType));
		attributes.connect(model.createResource("urn:attribute-owner"), "attribute-guid");

		assertTrue(model.contains(null, OPM.value, model.createTypedLiteral("12", XSD.decimal.getURI())));
		assertTrue(model.contains(null, OPM.value, model.createTypedLiteral("3", XSD.decimal.getURI())));
		assertFalse(model.contains(null, model.createProperty(EVIDENCE + "unitResolutionMethod")));
		assertFalse(model.contains(null, model.createProperty(EVIDENCE + "unitResolverVersion")));
	}

	private static Resource siUnit(Model model, IfcOWL ifc, String uri, String kind, String prefix, String name) {
		Resource unit = model.createResource(uri)
				.addProperty(ifc.getUnitType_IfcNamedUnit(), model.createResource(IFC + kind))
				.addProperty(ifc.getName_IfcSIUnit(), model.createResource(IFC + name));
		if (prefix != null) unit.addProperty(ifc.getPrefix_IfcSIUnit(), model.createResource(IFC + prefix));
		return unit;
	}
}
