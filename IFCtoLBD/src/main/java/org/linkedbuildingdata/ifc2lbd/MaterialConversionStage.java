package org.linkedbuildingdata.ifc2lbd;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.Assignment;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.Finding;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.Layer;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.LayerSet;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.LayerSetUsage;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.Material;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.MaterialSelect;
import org.linkedbuildingdata.ifc2lbd.MaterialExtractor.Unsupported;
import org.linkedbuildingdata.ifc2lbd.namespace.BMO;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

/** Emits BMO RDF and the minimal IFCtoLBD material-assignment extension. */
final class MaterialConversionStage {
	static final String REPORT_NS = "https://w3id.org/ifctolbd/material-report#";

	private final ConversionContext context;
	private final Model structure;
	private final Model quantities;
	private final Model report;
	private final UnitResolver.LengthConversion lengthConversion;
	private final Set<String> warnings = new LinkedHashSet<>();
	private final Map<Resource, Resource> materials = new LinkedHashMap<>();
	private final Map<Resource, Resource> definitions = new LinkedHashMap<>();
	private final Map<Resource, Resource> layers = new LinkedHashMap<>();
	private final Map<Resource, Resource> usages = new LinkedHashMap<>();

	private MaterialConversionStage(ConversionContext context) {
		this.context = context;
		this.structure = context.productModel();
		this.quantities = context.propertyModel();
		this.report = context.diagnosticsModel();
		this.lengthConversion = UnitResolver.fromProject(context.ifcModel(), context.ifcOntology())
				.projectLengthConversion();
	}

	static void map(ConversionContext context) {
		new MaterialConversionStage(context).mapAssignments();
	}

	private void mapAssignments() {
		BMO.addNamespace(structure);
		BMO.addNamespace(quantities);
		IFCtoLBDMapping.addNameSpace(structure);
		IFCtoLBDMapping.addNameSpace(quantities);
		quantities.setNsPrefix("qudt", UnitResolver.QUDT_SCHEMA);
		quantities.setNsPrefix("unit", UnitResolver.QUDT_UNIT);
		quantities.setNsPrefix("unitmeta", UnitResolver.META);
		report.setNsPrefix("materialReport", REPORT_NS);
		MaterialExtractor.Result extracted = MaterialExtractor.extract(context.ifcModel(),
				context.ifcOntology(), context.mappedResources().keySet());
		for (Finding finding : extracted.findings()) warn(finding);
		for (Assignment assignment : extracted.assignments()) mapAssignment(assignment);
	}

	private void mapAssignment(Assignment extracted) {
		Resource mappedElement = context.mappedResources().get(extracted.occurrence());
		if (mappedElement == null) return;
		Resource element = structure.createResource(mappedElement.getURI());
		Resource assignment = mappedStructure(extracted.association(), "MaterialAssignment")
				.addProperty(RDF.type, IFCtoLBDMapping.MaterialAssignment)
				.addProperty(IFCtoLBDMapping.sourceIfcAssociation, extracted.association())
				.addLiteral(IFCtoLBDMapping.materialAssignmentInherited, extracted.inherited());
		if (extracted.inherited()) {
			assignment.addProperty(IFCtoLBDMapping.sourceIfcTypeResource, extracted.assignedObject());
			String sourceType = typeName(extracted.assignedObject());
			if (sourceType != null) assignment.addLiteral(IFCtoLBDMapping.sourceIfcType, sourceType);
		}

		Resource definition = mapDefinition(extracted.selection());
		if (definition != null)
			assignment.addProperty(IFCtoLBDMapping.assignedMaterialDefinition, definition);
		if (extracted.selection() instanceof Material material)
			assignment.addProperty(IFCtoLBDMapping.assignedMaterial, mapMaterial(material));
		if (extracted.selection() instanceof LayerSetUsage usage)
			assignment.addProperty(IFCtoLBDMapping.layerSetUsage, mapUsage(usage));
		element.addProperty(IFCtoLBDMapping.materialAssignment, assignment);
	}

	private Resource mapDefinition(MaterialSelect selection) {
		return switch (selection) {
		case Material material -> mapDirectDefinition(material);
		case LayerSet layerSet -> mapLayerSet(layerSet);
		case LayerSetUsage usage -> usage.layerSet() == null ? null : mapLayerSet(usage.layerSet());
		case Unsupported ignored -> null;
		};
	}

	private Resource mapDirectDefinition(Material extracted) {
		return definitions.computeIfAbsent(extracted.source(), source -> {
			Resource material = mapMaterial(extracted);
			return structure.createResource(material.getURI() + "/definition")
					.addProperty(RDF.type, BMO.MaterialDefinition)
					.addProperty(BMO.hasMaterial, material);
		});
	}

	private Resource mapMaterial(Material extracted) {
		return materials.computeIfAbsent(extracted.source(), source -> {
			Resource material = mappedStructure(source, "Material");
			material.addProperty(RDF.type, BMO.Material);
			addText(material, RDFS.label, extracted.name());
			addText(material, DCTerms.description, extracted.description());
			addText(material, IFCtoLBDMapping.materialCategory, extracted.category());
			return material;
		});
	}

	private Resource mapLayerSet(LayerSet extracted) {
		return definitions.computeIfAbsent(extracted.source(), source -> {
			Resource layerSet = mappedStructure(source, "MaterialLayerSet");
			layerSet.addProperty(RDF.type, BMO.LayerSet);
			addText(layerSet, RDFS.label, extracted.name());
			addText(layerSet, DCTerms.description, extracted.description());
			for (int index = 0; index < extracted.layers().size(); index++) {
				Resource layer = mapLayer(extracted.layers().get(index));
				Resource membership = structure.createResource(
						layerSet.getURI() + "/layer-membership/" + (index + 1))
						.addProperty(RDF.type, IFCtoLBDMapping.LayerMembership)
						.addLiteral(IFCtoLBDMapping.position, index + 1)
						.addProperty(IFCtoLBDMapping.layer, layer);
				layerSet.addProperty(IFCtoLBDMapping.layerMembership, membership)
						.addProperty(BMO.hasLayers, layer);
			}
			return layerSet;
		});
	}

	private Resource mapLayer(Layer extracted) {
		return layers.computeIfAbsent(extracted.source(), source -> {
			Resource layer = mappedStructure(source, "MaterialLayer");
			layer.addProperty(RDF.type, BMO.Layer);
			if (extracted.material() != null)
				layer.addProperty(BMO.hasMaterial, mapMaterial(extracted.material()));
			if (extracted.thickness() != null) {
				try {
					BigDecimal sourceValue = new BigDecimal(extracted.thickness());
					String sourceCanonical = sourceValue.stripTrailingZeros().toPlainString();
					var sourceLiteral = quantities.createTypedLiteral(sourceCanonical, XSDDatatype.XSDdecimal);
					boolean resolved = lengthConversion.isResolved();
					BigDecimal value = resolved ? lengthConversion.toMetres(sourceValue) : sourceValue;
					String canonical = value.stripTrailingZeros().toPlainString();
					var valueLiteral = quantities.createTypedLiteral(canonical, XSDDatatype.XSDdecimal);
					Resource quantityLayer = quantities.createResource(layer.getURI());
					if (resolved) quantityLayer.addLiteral(BMO.hasThickness, valueLiteral);

					UnitResolver.Resolution sourceUnit = lengthConversion.sourceUnit();
					Resource quantity = NormalizedValueWriter.write(quantities, quantityLayer,
							IFCtoLBDMapping.thicknessQuantity,
							new NormalizedValue(valueLiteral,
									resolved ? UnitResolver.QUDT_UNIT + "M" : null,
									context.ifcOntology().getProperty("layerThickness_IfcMaterialLayer").getURI(),
									resolved ? "IFC_PROJECT_LENGTH_UNIT_TO_METRE"
											: "UNRESOLVED_IFC_PROJECT_LENGTH_UNIT",
									resolved ? 1.0 : 0.0,
									sourceUnit.originalCode(), "IfcLengthMeasure", null),
							shortHash(layer.getURI() + "|thickness"));
					quantity.addLiteral(quantities.createProperty(UnitResolver.META + "originalValue"), sourceLiteral)
							.addProperty(quantities.createProperty("http://www.w3.org/ns/prov#wasDerivedFrom"), source);
					if (sourceUnit.isResolved())
						quantity.addProperty(quantities.createProperty(UnitResolver.META + "sourceUnit"),
								quantities.createResource(sourceUnit.qudtUri()));
					if (!resolved) warn(new Finding(null, source, typeName(source),
							"UNRESOLVED_LAYER_THICKNESS_UNIT",
							"IfcMaterialLayer thickness was retained, but the IFC project length unit "
									+ "could not be resolved to metres"));
				} catch (NumberFormatException e) {
					// The extractor reports this value with its assignment provenance.
				}
			}
			return layer;
		});
	}

	private static String shortHash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder text = new StringBuilder(24);
			for (int i = 0; i < 12; i++) text.append(String.format("%02x", digest[i]));
			return text.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	private Resource mapUsage(LayerSetUsage extracted) {
		return usages.computeIfAbsent(extracted.source(), source -> {
			Resource usage = mappedStructure(source, "MaterialLayerSetUsage")
					.addProperty(RDF.type, IFCtoLBDMapping.LayerSetUsage);
			if (extracted.layerSet() != null)
				usage.addProperty(IFCtoLBDMapping.assignedMaterialDefinition, mapLayerSet(extracted.layerSet()));
			addText(usage, IFCtoLBDMapping.layerSetDirection, extracted.layerSetDirection());
			addText(usage, IFCtoLBDMapping.directionSense, extracted.directionSense());
			addDecimalOrText(quantities.createResource(usage.getURI()), IFCtoLBDMapping.offsetFromReferenceLine,
					extracted.offsetFromReferenceLine());
			return usage;
		});
	}

	private void addText(Resource target, Property property, String value) {
		if (value != null && !value.isBlank()) target.addProperty(property, value);
	}

	private void addDecimalOrText(Resource target, Property property, String value) {
		if (value == null || value.isBlank()) return;
		try {
			String canonical = new BigDecimal(value).stripTrailingZeros().toPlainString();
			target.addLiteral(property, target.getModel().createTypedLiteral(canonical, XSDDatatype.XSDdecimal));
		} catch (NumberFormatException e) {
			target.addLiteral(property, value);
		}
	}

	private Resource mappedStructure(Resource source, String productType) {
		Resource mapped = context.mappedResources().get(source);
		if (mapped == null) mapped = context.mapResource(source, productType);
		return structure.createResource(mapped.getURI());
	}

	private String typeName(Resource resource) {
		if (resource == null) return null;
		var types = resource.listProperties(RDF.type);
		while (types.hasNext()) {
			RDFNode type = types.next().getObject();
			if (type.isURIResource()
					&& context.ifcOntology().getIfcURI().equals(type.asResource().getNameSpace()))
				return type.asResource().getLocalName();
		}
		return null;
	}

	private void warn(Finding finding) {
		String key = finding.association() + "|" + finding.structure() + "|" + finding.code();
		if (!warnings.add(key)) return;
		Resource warning = report.createResource()
				.addProperty(RDF.type, report.createResource(REPORT_NS + "MaterialConversionWarning"))
				.addLiteral(report.createProperty(REPORT_NS + "code"), finding.code())
				.addLiteral(report.createProperty(REPORT_NS + "message"), finding.message());
		if (finding.association() != null)
			warning.addProperty(report.createProperty(REPORT_NS + "sourceAssociation"), finding.association());
		if (finding.structure() != null)
			warning.addProperty(report.createProperty(REPORT_NS + "sourceStructure"), finding.structure());
		if (finding.ifcStructureType() != null)
			warning.addLiteral(report.createProperty(REPORT_NS + "ifcStructureType"), finding.ifcStructureType());
	}
}
