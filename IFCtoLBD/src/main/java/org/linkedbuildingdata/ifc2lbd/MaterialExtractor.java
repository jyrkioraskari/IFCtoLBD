package org.linkedbuildingdata.ifc2lbd;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/**
 * Extracts the supported IFC material graph without writing conversion RDF.
 *
 * <p>The returned records retain their IFC source resources so RDF emitters can
 * preserve identity and assignment provenance. Lists are immutable and retain
 * the order in the IFC list representation.</p>
 */
final class MaterialExtractor {
    sealed interface MaterialSelect permits Material, LayerSet, LayerSetUsage, Unsupported {
        Resource source();
        String ifcType();
    }

    record Material(Resource source, String name, String description, String category)
            implements MaterialSelect {
        @Override public String ifcType() { return "IfcMaterial"; }
    }

    record Layer(Resource source, Material material, String thickness) { }

    record LayerSet(Resource source, String name, String description, List<Layer> layers)
            implements MaterialSelect {
        LayerSet {
            layers = List.copyOf(layers);
        }

        @Override public String ifcType() { return "IfcMaterialLayerSet"; }
    }

    /** An occurrence-specific IFC usage that refers to a shared layer-set definition. */
    record LayerSetUsage(Resource source, LayerSet layerSet, String layerSetDirection,
            String directionSense, String offsetFromReferenceLine) implements MaterialSelect {
        @Override public String ifcType() { return "IfcMaterialLayerSetUsage"; }
    }

    record Unsupported(Resource source, String ifcType) implements MaterialSelect { }

    record Assignment(Resource occurrence, Resource association, Resource assignedObject,
            MaterialSelect selection, boolean inherited) { }

    record Finding(Resource association, Resource structure, String ifcStructureType,
            String code, String message) { }

    record Result(List<Assignment> assignments, List<Finding> findings) {
        Result {
            assignments = List.copyOf(assignments);
            findings = List.copyOf(findings);
        }
    }

    private record Candidate(Resource occurrence, Resource association, Resource assignedObject,
            Resource structure, boolean inherited) { }

    private final Model model;
    private final IfcOWL ifc;
    private final Set<Resource> eligibleOccurrences;

    private MaterialExtractor(Model model, IfcOWL ifc, Set<Resource> eligibleOccurrences) {
        this.model = model;
        this.ifc = ifc;
        this.eligibleOccurrences = Set.copyOf(eligibleOccurrences);
    }

    static Result extract(Model model, IfcOWL ifc, Set<Resource> eligibleOccurrences) {
        return new MaterialExtractor(model, ifc, eligibleOccurrences).extract();
    }

    private Result extract() {
        Map<Resource, List<Resource>> occurrencesByType = occurrencesByType();
        Map<Resource, List<Candidate>> direct = new LinkedHashMap<>();
        Map<Resource, List<Candidate>> inherited = new LinkedHashMap<>();

        for (Resource association : IfcMappingSupport.instances(model, "IfcRelAssociatesMaterial")) {
            Resource structure = IfcMappingSupport.objectResource(association, ifc,
                    "relatingMaterial_IfcRelAssociatesMaterial");
            for (Resource related : IfcMappingSupport.objectResources(association, ifc,
                    "relatedObjects_IfcRelAssociates")) {
                List<Resource> typedOccurrences = occurrencesByType.get(related);
                if (typedOccurrences != null) {
                    for (Resource occurrence : typedOccurrences) {
                        add(inherited, occurrence,
                                new Candidate(occurrence, association, related, structure, true));
                    }
                } else if (eligibleOccurrences.contains(related)) {
                    add(direct, related, new Candidate(related, association, related, structure, false));
                }
            }
        }

        List<Assignment> assignments = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        Set<Resource> occurrences = new LinkedHashSet<>();
        occurrences.addAll(direct.keySet());
        occurrences.addAll(inherited.keySet());
        for (Resource occurrence : occurrences) {
            // The presence of any occurrence assignment suppresses type fallback, even
            // when its selected structure is malformed or unsupported.
            List<Candidate> selected = direct.containsKey(occurrence)
                    ? direct.get(occurrence) : inherited.get(occurrence);
            for (Candidate candidate : selected) {
                MaterialSelect selection = readSelection(candidate, findings);
                assignments.add(new Assignment(candidate.occurrence(), candidate.association(),
                        candidate.assignedObject(), selection, candidate.inherited()));
            }
        }
        return new Result(assignments, findings);
    }

    private Map<Resource, List<Resource>> occurrencesByType() {
        Map<Resource, LinkedHashSet<Resource>> collected = new LinkedHashMap<>();
        for (Resource relation : IfcMappingSupport.instances(model, "IfcRelDefinesByType")) {
            Resource type = IfcMappingSupport.objectResource(relation, ifc,
                    "relatingType_IfcRelDefinesByType");
            if (type == null) continue;
            // IFC4 declares the subtype-specific property; IFC2x3 inherits the
            // relatedObjects property from IfcRelDefines. Both use the active IFC namespace.
            for (Resource occurrence : IfcMappingSupport.objectResources(relation, ifc,
                    "relatedObjects_IfcRelDefinesByType", "relatedObjects_IfcRelDefines")) {
                if (eligibleOccurrences.contains(occurrence)) {
                    collected.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(occurrence);
                }
            }
        }
        Map<Resource, List<Resource>> result = new LinkedHashMap<>();
        collected.forEach((type, occurrences) -> result.put(type, List.copyOf(occurrences)));
        return result;
    }

    private MaterialSelect readSelection(Candidate candidate, List<Finding> findings) {
        Resource source = candidate.structure();
        String type = typeName(source);
        if (source == null) {
            findings.add(finding(candidate, null, type, "MISSING_RELATING_MATERIAL",
                    "IfcRelAssociatesMaterial has no relating material"));
            return new Unsupported(null, type);
        }
        return switch (type) {
        case "IfcMaterial" -> readMaterial(source);
        case "IfcMaterialLayerSet" -> readLayerSet(candidate, source, findings);
        case "IfcMaterialLayerSetUsage" -> {
            Resource layerSet = IfcMappingSupport.objectResource(source, ifc,
                    "forLayerSet_IfcMaterialLayerSetUsage");
            if (layerSet == null) {
                findings.add(finding(candidate, source, type, "MISSING_LAYER_SET",
                        "IfcMaterialLayerSetUsage has no ForLayerSet value"));
                yield new LayerSetUsage(source, null,
                        scalar(source, "layerSetDirection_IfcMaterialLayerSetUsage"),
                        scalar(source, "directionSense_IfcMaterialLayerSetUsage"),
                        scalar(source, "offsetFromReferenceLine_IfcMaterialLayerSetUsage"));
            }
            yield new LayerSetUsage(source, readLayerSet(candidate, layerSet, findings),
                    scalar(source, "layerSetDirection_IfcMaterialLayerSetUsage"),
                    scalar(source, "directionSense_IfcMaterialLayerSetUsage"),
                    scalar(source, "offsetFromReferenceLine_IfcMaterialLayerSetUsage"));
        }
        default -> {
            findings.add(finding(candidate, source, type, "UNSUPPORTED_MATERIAL_STRUCTURE",
                    "Unsupported IFC material structure: " + type));
            yield new Unsupported(source, type);
        }
        };
    }

    private Material readMaterial(Resource source) {
        return new Material(source,
                IfcMappingSupport.scalar(source, ifc, "name_IfcMaterial"),
                IfcMappingSupport.scalar(source, ifc, "description_IfcMaterial"),
                IfcMappingSupport.scalar(source, ifc, "category_IfcMaterial"));
    }

    private String scalar(Resource source, String property) {
        return IfcMappingSupport.scalar(source, ifc, property);
    }

    private LayerSet readLayerSet(Candidate candidate, Resource source, List<Finding> findings) {
        List<Resource> sourceLayers = IfcMappingSupport.objectResources(source, ifc,
                "materialLayers_IfcMaterialLayerSet");
        if (sourceLayers.isEmpty()) {
            findings.add(finding(candidate, source, typeName(source), "EMPTY_LAYER_SET",
                    "IfcMaterialLayerSet has no material layers"));
        }
        List<Layer> layers = new ArrayList<>();
        for (Resource sourceLayer : sourceLayers) {
            Resource sourceMaterial = IfcMappingSupport.objectResource(sourceLayer, ifc,
                    "material_IfcMaterialLayer");
            Material material = sourceMaterial == null ? null : readMaterial(sourceMaterial);
            if (material == null) {
                findings.add(finding(candidate, sourceLayer, typeName(sourceLayer),
                        "MISSING_LAYER_MATERIAL", "IfcMaterialLayer has no material"));
            }
            String thickness = IfcMappingSupport.scalar(sourceLayer, ifc,
                    "layerThickness_IfcMaterialLayer");
            if (thickness == null) {
                findings.add(finding(candidate, sourceLayer, typeName(sourceLayer),
                        "MISSING_LAYER_THICKNESS", "IfcMaterialLayer has no thickness"));
            } else {
                try {
                    new BigDecimal(thickness);
                } catch (NumberFormatException e) {
                    findings.add(finding(candidate, sourceLayer, typeName(sourceLayer),
                            "INVALID_LAYER_THICKNESS",
                            "IfcMaterialLayer thickness is not numeric: " + thickness));
                }
            }
            layers.add(new Layer(sourceLayer, material, thickness));
        }
        return new LayerSet(source,
                IfcMappingSupport.scalar(source, ifc, "layerSetName_IfcMaterialLayerSet"),
                IfcMappingSupport.scalar(source, ifc, "description_IfcMaterialLayerSet"), layers);
    }

    private Finding finding(Candidate candidate, Resource structure, String structureType,
            String code, String message) {
        return new Finding(candidate.association(), structure, structureType, code, message);
    }

    private String typeName(Resource resource) {
        if (resource == null) return "Unknown";
        var types = resource.listProperties(RDF.type);
        while (types.hasNext()) {
            RDFNode type = types.next().getObject();
            if (type.isURIResource() && ifc.getIfcURI().equals(type.asResource().getNameSpace())) {
                return type.asResource().getLocalName();
            }
        }
        return "Unknown";
    }

    private static void add(Map<Resource, List<Candidate>> target, Resource occurrence,
            Candidate candidate) {
        target.computeIfAbsent(occurrence, ignored -> new ArrayList<>()).add(candidate);
    }
}
