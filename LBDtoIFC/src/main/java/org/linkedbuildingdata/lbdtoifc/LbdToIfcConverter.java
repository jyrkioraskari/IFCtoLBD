package org.linkedbuildingdata.lbdtoifc;

import static org.linkedbuildingdata.lbdtoifc.StepFile.ref;
import static org.linkedbuildingdata.lbdtoifc.StepFile.refs;
import static org.linkedbuildingdata.lbdtoifc.StepFile.string;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

/** Converts the reconstructible semantic subset of an LBD RDF graph to IFC4 STEP. */
public final class LbdToIfcConverter {
    private static final Comparator<Resource> RESOURCE_ORDER = Comparator.comparing(LbdToIfcConverter::key);
    private static final Set<Property> STRUCTURAL_PROPERTIES = Set.of(
            Vocabulary.LEGACY_GLOBAL_ID, Vocabulary.OBJECT_TYPE, Vocabulary.LONG_NAME,
            Vocabulary.BAT_ID, Vocabulary.DOOR_HEIGHT, Vocabulary.DOOR_WIDTH,
            Vocabulary.WINDOW_HEIGHT, Vocabulary.WINDOW_WIDTH);

    private static final Map<String, String> IFC_ELEMENT_TYPES = elementTypes();

    public record Options(String projectName, Path artifactRoot, long maximumObjBytes,
            int maximumVertices, int maximumTriangles) {
        private static final long DEFAULT_MAXIMUM_OBJ_BYTES = 128L * 1024L * 1024L;
        private static final int DEFAULT_MAXIMUM_VERTICES = 10_000_000;
        private static final int DEFAULT_MAXIMUM_TRIANGLES = 20_000_000;

        public Options(String projectName) {
            this(projectName, null, DEFAULT_MAXIMUM_OBJ_BYTES,
                    DEFAULT_MAXIMUM_VERTICES, DEFAULT_MAXIMUM_TRIANGLES);
        }

        public Options(String projectName, Path artifactRoot) {
            this(projectName, artifactRoot, DEFAULT_MAXIMUM_OBJ_BYTES,
                    DEFAULT_MAXIMUM_VERTICES, DEFAULT_MAXIMUM_TRIANGLES);
        }

        public Options {
            projectName = projectName == null || projectName.isBlank()
                    ? "LBD reconstructed project" : projectName;
            artifactRoot = artifactRoot == null ? null : artifactRoot.toAbsolutePath().normalize();
            if (maximumObjBytes < 1 || maximumVertices < 1 || maximumTriangles < 1) {
                throw new IllegalArgumentException("Geometry limits must be positive");
            }
        }

        public static Options defaults() {
            return new Options("LBD reconstructed project");
        }

        Options withDefaultArtifactRoot(Path root) {
            return artifactRoot == null
                    ? new Options(projectName, root, maximumObjBytes, maximumVertices, maximumTriangles)
                    : this;
        }
    }

    /** Parses RDF based on the input extension and writes an IFC4 STEP file atomically. */
    public ConversionReport convert(Path input, Path output, Options options) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Model model = ModelFactory.createDefaultModel();
        Lang language = RDFLanguages.filenameToLang(input.getFileName().toString(), Lang.TURTLE);
        try (var stream = Files.newInputStream(input)) {
            RDFDataMgr.read(model, stream, input.toUri().toString(), language);
        }
        options = (options == null ? Options.defaults() : options)
                .withDefaultArtifactRoot(input.toAbsolutePath().getParent());

        Path absoluteOutput = output.toAbsolutePath();
        Path parent = Optional.ofNullable(absoluteOutput.getParent()).orElse(Path.of(".").toAbsolutePath());
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".lbdtoifc-", ".ifc.tmp");
        try {
            ConversionReport report;
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                report = convert(model, writer, input.getFileName().toString(), options);
            }
            try {
                Files.move(temporary, absoluteOutput, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absoluteOutput, StandardCopyOption.REPLACE_EXISTING);
            }
            return report;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Converts an already parsed graph. The caller owns both the model and writer. */
    public ConversionReport convert(Model model, Writer output, String sourceName, Options options)
            throws IOException {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(output, "output");
        options = options == null ? Options.defaults() : options;

        List<String> warnings = new ArrayList<>();
        List<Resource> sites = resourcesOfType(model, Vocabulary.SITE);
        List<Resource> buildings = resourcesOfType(model, Vocabulary.BUILDING);
        List<Resource> storeys = resourcesOfType(model, Vocabulary.STOREY);
        List<Resource> spaces = resourcesOfType(model, Vocabulary.SPACE);
        List<Resource> elements = resourcesOfType(model, Vocabulary.ELEMENT).stream()
                .filter(resource -> !resource.hasProperty(RDF.type, Vocabulary.SITE)
                        && !resource.hasProperty(RDF.type, Vocabulary.BUILDING)
                        && !resource.hasProperty(RDF.type, Vocabulary.STOREY)
                        && !resource.hasProperty(RDF.type, Vocabulary.SPACE))
                .toList();

        if (sites.isEmpty()) {
            sites = List.of(model.createResource("urn:lbdtoifc:synthetic:site"));
            warnings.add("No bot:Site was found; a site was synthesized.");
        }
        if (buildings.isEmpty()) {
            buildings = List.of(model.createResource("urn:lbdtoifc:synthetic:building"));
            warnings.add("No bot:Building was found; a building was synthesized.");
        }
        if (storeys.isEmpty()) {
            storeys = List.of(model.createResource("urn:lbdtoifc:synthetic:storey"));
            warnings.add("No bot:Storey was found; a storey was synthesized.");
        }

        Map<Resource, Resource> buildingParent = assignParents(sites, Vocabulary.HAS_BUILDING, buildings);
        Map<Resource, Resource> storeyParent = assignParents(buildings, Vocabulary.HAS_STOREY, storeys);
        Map<Resource, Resource> spaceParent = assignParents(storeys, Vocabulary.HAS_SPACE, spaces);
        Map<Resource, Resource> elementParent = assignElementContainers(
                List.of(spaces, storeys, buildings, sites), elements);

        Map<Resource, Resource> subElementParent = assignParents(elements,
                Vocabulary.HAS_SUB_ELEMENT, elements, false);
        subElementParent.keySet().forEach(elementParent::remove);

        LinkedHashSet<Resource> allResources = new LinkedHashSet<>();
        allResources.addAll(sites);
        allResources.addAll(buildings);
        allResources.addAll(storeys);
        allResources.addAll(spaces);
        allResources.addAll(elements);
        Map<Resource, String> guids = allocateGuids(allResources, warnings);
        GeometryLoader.Result geometry = GeometryLoader.load(allResources, options, warnings);

        StepFile step = new StepFile();
        int origin = step.add("IFCCARTESIANPOINT", "(0.,0.,0.)");
        int worldAxis = step.add("IFCAXIS2PLACEMENT3D", ref(origin), "$", "$");
        int context = step.add("IFCGEOMETRICREPRESENTATIONCONTEXT",
                "$", string("Model"), "3", "1.E-5", ref(worldAxis), "$");
        int bodyContext = step.add("IFCGEOMETRICREPRESENTATIONSUBCONTEXT",
                string("Body"), string("Model"), "*", "*", "*", "*",
                ref(context), "$", ".MODEL_VIEW.", "$");
        int lengthUnit = step.add("IFCSIUNIT", "*", ".LENGTHUNIT.", "$", ".METRE.");
        int areaUnit = step.add("IFCSIUNIT", "*", ".AREAUNIT.", "$", ".SQUARE_METRE.");
        int volumeUnit = step.add("IFCSIUNIT", "*", ".VOLUMEUNIT.", "$", ".CUBIC_METRE.");
        int unitAssignment = step.add("IFCUNITASSIGNMENT",
                "(" + ref(lengthUnit) + "," + ref(areaUnit) + "," + ref(volumeUnit) + ")");
        String projectGuid = IfcGuid.forRelationship("project", options.projectName(), key(sites.getFirst()));
        int project = step.add("IFCPROJECT", string(projectGuid), "$", string(options.projectName()),
                "$", "$", "$", "$", "(" + ref(context) + ")", ref(unitAssignment));

        Map<Resource, Integer> entityIds = new LinkedHashMap<>();
        Map<Resource, Integer> placementIds = new HashMap<>();
        createSites(step, sites, guids, entityIds, placementIds, worldAxis,
                bodyContext, geometry.meshes());
        createBuildings(step, buildings, buildingParent, guids, entityIds, placementIds, worldAxis,
                bodyContext, geometry.meshes());
        createStoreys(step, storeys, storeyParent, guids, entityIds, placementIds, worldAxis,
                bodyContext, geometry.meshes());
        createSpaces(step, spaces, spaceParent, guids, entityIds, placementIds, worldAxis,
                bodyContext, geometry.meshes());
        createElements(step, elements, elementParent, subElementParent, guids,
                entityIds, placementIds, worldAxis, bodyContext, geometry.meshes());

        addAggregate(step, project, sites, entityIds, "project-sites", options.projectName());
        addAggregates(step, sites, buildings, buildingParent, entityIds, "site-buildings");
        addAggregates(step, buildings, storeys, storeyParent, entityIds, "building-storeys");
        addAggregates(step, storeys, spaces, spaceParent, entityIds, "storey-spaces");
        addAggregates(step, elements, elements, subElementParent, entityIds, "element-parts");
        addContainment(step, elementParent, entityIds);

        int propertySetCount = addProperties(step, allResources, entityIds, guids, warnings);
        if (!elements.isEmpty() && geometry.meshes().isEmpty()) {
            warnings.add("LBD has no generally reversible solid representation; elements were written without geometry.");
        } else if (geometry.linkedProducts() > geometry.meshes().size()) {
            warnings.add((geometry.linkedProducts() - geometry.meshes().size())
                    + " product(s) linked geometry but had no usable OBJ mesh.");
        }

        BufferedWriter buffered = output instanceof BufferedWriter writer ? writer : new BufferedWriter(output);
        step.write(buffered, sourceName == null ? "input.rdf" : sourceName);
        buffered.flush();
        long vertexCount = geometry.meshes().values().stream().mapToLong(mesh -> mesh.vertices().size()).sum();
        long triangleCount = geometry.meshes().values().stream().mapToLong(mesh -> mesh.triangles().size()).sum();
        return new ConversionReport(sites.size(), buildings.size(), storeys.size(), spaces.size(),
                elements.size(), propertySetCount, geometry.meshes().size(), vertexCount, triangleCount, warnings);
    }

    private static void createSites(StepFile step, List<Resource> sites, Map<Resource, String> guids,
            Map<Resource, Integer> ids, Map<Resource, Integer> placements, int axis,
            int bodyContext, Map<Resource, ObjMesh> meshes) {
        for (Resource site : sites) {
            int placement = step.add("IFCLOCALPLACEMENT", "$", ref(axis));
            placements.put(site, placement);
            String representation = representation(step, meshes.get(site), bodyContext);
            ids.put(site, step.add("IFCSITE", string(guids.get(site)), "$", string(name(site, "Site")),
                    "$", string(objectType(site)), ref(placement), representation, string(longName(site)),
                    ".ELEMENT.", "$", "$", "$", "$", "$"));
        }
    }

    private static void createBuildings(StepFile step, List<Resource> buildings,
            Map<Resource, Resource> parents, Map<Resource, String> guids, Map<Resource, Integer> ids,
            Map<Resource, Integer> placements, int axis, int bodyContext, Map<Resource, ObjMesh> meshes) {
        for (Resource building : buildings) {
            int placement = localPlacement(step, parents.get(building), placements, axis);
            placements.put(building, placement);
            String representation = representation(step, meshes.get(building), bodyContext);
            ids.put(building, step.add("IFCBUILDING", string(guids.get(building)), "$",
                    string(name(building, "Building")), "$", string(objectType(building)),
                    ref(placement), representation, string(longName(building)), ".ELEMENT.", "$", "$", "$"));
        }
    }

    private static void createStoreys(StepFile step, List<Resource> storeys,
            Map<Resource, Resource> parents, Map<Resource, String> guids, Map<Resource, Integer> ids,
            Map<Resource, Integer> placements, int axis, int bodyContext, Map<Resource, ObjMesh> meshes) {
        for (Resource storey : storeys) {
            int placement = localPlacement(step, parents.get(storey), placements, axis);
            placements.put(storey, placement);
            String representation = representation(step, meshes.get(storey), bodyContext);
            ids.put(storey, step.add("IFCBUILDINGSTOREY", string(guids.get(storey)), "$",
                    string(name(storey, "Storey")), "$", string(objectType(storey)),
                    ref(placement), representation, string(longName(storey)), ".ELEMENT.", "$"));
        }
    }

    private static void createSpaces(StepFile step, List<Resource> spaces,
            Map<Resource, Resource> parents, Map<Resource, String> guids, Map<Resource, Integer> ids,
            Map<Resource, Integer> placements, int axis, int bodyContext, Map<Resource, ObjMesh> meshes) {
        for (Resource space : spaces) {
            int placement = localPlacement(step, parents.get(space), placements, axis);
            placements.put(space, placement);
            String representation = representation(step, meshes.get(space), bodyContext);
            ids.put(space, step.add("IFCSPACE", string(guids.get(space)), "$",
                    string(name(space, "Space")), "$", string(objectType(space)),
                    ref(placement), representation, string(longName(space)), ".ELEMENT.",
                    ".NOTDEFINED.", "$"));
        }
    }

    private static void createElements(StepFile step, List<Resource> elements,
            Map<Resource, Resource> containers, Map<Resource, Resource> subParents,
            Map<Resource, String> guids, Map<Resource, Integer> ids,
            Map<Resource, Integer> placements, int axis,
            int bodyContext, Map<Resource, ObjMesh> meshes) {
        for (Resource element : elements) {
            Resource parent = Optional.ofNullable(subParents.get(element)).orElse(containers.get(element));
            int placement = localPlacement(step, parent, placements, axis);
            placements.put(element, placement);
            String ifcClass = elementIfcClass(element);
            String shape = representation(step, meshes.get(element), bodyContext);
            List<String> args = new ArrayList<>(List.of(string(guids.get(element)), "$",
                    string(name(element, "Element")), "$", string(objectType(element)),
                    ref(placement), shape, string(literal(element, Vocabulary.BAT_ID))));
            if (ifcClass.equals("IFCDOOR")) {
                args.add(numberOrNull(element, Vocabulary.DOOR_HEIGHT));
                args.add(numberOrNull(element, Vocabulary.DOOR_WIDTH));
                args.add(".NOTDEFINED.");
                args.add(".NOTDEFINED.");
                args.add("$");
            } else if (ifcClass.equals("IFCWINDOW")) {
                args.add(numberOrNull(element, Vocabulary.WINDOW_HEIGHT));
                args.add(numberOrNull(element, Vocabulary.WINDOW_WIDTH));
                args.add(".NOTDEFINED.");
                args.add(".NOTDEFINED.");
                args.add("$");
            } else if (ifcClass.equals("IFCSTAIRFLIGHT")) {
                args.add("$");
                args.add("$");
                args.add("$");
                args.add("$");
                args.add(".NOTDEFINED.");
            } else if (ifcClass.equals("IFCPILE")) {
                args.add(".NOTDEFINED.");
                args.add(".NOTDEFINED.");
            } else if (ifcClass.equals("IFCFURNISHINGELEMENT")) {
                // IfcFurnishingElement adds no explicit attributes to IfcElement.
            } else {
                args.add(".NOTDEFINED.");
            }
            ids.put(element, step.add(ifcClass, args.toArray(String[]::new)));
        }
    }

    private static String representation(StepFile step, ObjMesh mesh, int bodyContext) {
        if (mesh == null) {
            return "$";
        }
        StringBuilder coordinates = new StringBuilder("(");
        for (int index = 0; index < mesh.vertices().size(); index++) {
            if (index > 0) coordinates.append(',');
            ObjMesh.Vertex vertex = mesh.vertices().get(index);
            coordinates.append('(').append(number(vertex.x())).append(',')
                    .append(number(vertex.y())).append(',').append(number(vertex.z())).append(')');
        }
        coordinates.append(')');
        int points = step.add("IFCCARTESIANPOINTLIST3D", coordinates.toString());

        StringBuilder indices = new StringBuilder("(");
        for (int index = 0; index < mesh.triangles().size(); index++) {
            if (index > 0) indices.append(',');
            ObjMesh.Triangle triangle = mesh.triangles().get(index);
            indices.append('(').append(triangle.a()).append(',')
                    .append(triangle.b()).append(',').append(triangle.c()).append(')');
        }
        indices.append(')');
        int faceSet = step.add("IFCTRIANGULATEDFACESET", ref(points), "$",
                mesh.closed() ? ".T." : ".F.", indices.toString(), "$");
        int shape = step.add("IFCSHAPEREPRESENTATION", ref(bodyContext),
                string("Body"), string("Tessellation"), "(" + ref(faceSet) + ")");
        return ref(step.add("IFCPRODUCTDEFINITIONSHAPE", "$", "$", "(" + ref(shape) + ")"));
    }

    private static String number(double value) {
        String lexical = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return lexical.indexOf('.') >= 0 ? lexical : lexical + ".";
    }

    private static int localPlacement(StepFile step, Resource parent,
            Map<Resource, Integer> placements, int axis) {
        Integer parentPlacement = placements.get(parent);
        return step.add("IFCLOCALPLACEMENT", parentPlacement == null ? "$" : ref(parentPlacement), ref(axis));
    }

    private static void addAggregates(StepFile step, List<Resource> parents, List<Resource> children,
            Map<Resource, Resource> parentByChild, Map<Resource, Integer> ids, String kind) {
        for (Resource parent : parents) {
            List<Resource> related = children.stream()
                    .filter(child -> parent.equals(parentByChild.get(child))).toList();
            addAggregate(step, ids.get(parent), related, ids, kind, key(parent));
        }
    }

    private static void addAggregate(StepFile step, int parentId, List<Resource> children,
            Map<Resource, Integer> ids, String kind, String parentKey) {
        List<Integer> childIds = children.stream().map(ids::get).filter(Objects::nonNull).toList();
        if (!childIds.isEmpty()) {
            step.add("IFCRELAGGREGATES", string(IfcGuid.forRelationship(kind, parentKey)), "$", "$", "$",
                    ref(parentId), refs(childIds));
        }
    }

    private static void addContainment(StepFile step, Map<Resource, Resource> parentByElement,
            Map<Resource, Integer> ids) {
        Map<Resource, List<Resource>> byContainer = parentByElement.entrySet().stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
        byContainer.entrySet().stream().sorted(Map.Entry.comparingByKey(RESOURCE_ORDER)).forEach(entry -> {
            List<Integer> elementIds = entry.getValue().stream().sorted(RESOURCE_ORDER)
                    .map(ids::get).filter(Objects::nonNull).toList();
            Integer containerId = ids.get(entry.getKey());
            if (containerId != null && !elementIds.isEmpty()) {
                step.add("IFCRELCONTAINEDINSPATIALSTRUCTURE",
                        string(IfcGuid.forRelationship("containment", key(entry.getKey()))),
                        "$", "$", "$", refs(elementIds), ref(containerId));
            }
        });
    }

    private static int addProperties(StepFile step, Set<Resource> resources,
            Map<Resource, Integer> ids, Map<Resource, String> guids, List<String> warnings) {
        int count = 0;
        for (Resource resource : resources) {
            List<Statement> propertyStatements = resource.listProperties().toList().stream()
                    .filter(statement -> statement.getPredicate().getURI() != null
                            && statement.getPredicate().getURI().startsWith(Vocabulary.PROPS))
                    .filter(statement -> !STRUCTURAL_PROPERTIES.contains(statement.getPredicate()))
                    .filter(statement -> statement.getObject().isLiteral())
                    .sorted(Comparator.comparing(statement -> statement.getPredicate().getURI()))
                    .toList();
            if (propertyStatements.isEmpty() || !ids.containsKey(resource)) {
                continue;
            }
            List<Integer> propertyIds = new ArrayList<>();
            Map<String, Integer> occurrences = new HashMap<>();
            for (Statement statement : propertyStatements) {
                String baseName = cleanPropertyName(statement.getPredicate().getLocalName());
                int occurrence = occurrences.merge(baseName, 1, Integer::sum);
                String propertyName = occurrence == 1 ? baseName : baseName + " [" + occurrence + "]";
                String value = ifcValue(statement.getLiteral(), warnings, propertyName);
                if (value != null) {
                    propertyIds.add(step.add("IFCPROPERTYSINGLEVALUE", string(propertyName), "$", value, "$"));
                }
            }
            if (!propertyIds.isEmpty()) {
                String guid = guids.get(resource);
                int propertySet = step.add("IFCPROPERTYSET",
                        string(IfcGuid.forRelationship("property-set", guid)), "$",
                        string("LBD Properties"), string("Properties reconstructed from LBD"),
                        refs(propertyIds));
                step.add("IFCRELDEFINESBYPROPERTIES",
                        string(IfcGuid.forRelationship("properties", guid)), "$", "$", "$",
                        "(" + ref(ids.get(resource)) + ")", ref(propertySet));
                count++;
            }
        }
        return count;
    }

    private static String ifcValue(Literal literal, List<String> warnings, String propertyName) {
        String datatype = literal.getDatatypeURI();
        String lexical = literal.getLexicalForm();
        try {
            if (XSDDatatype.XSDboolean.getURI().equals(datatype)) {
                return "IFCBOOLEAN(" + (literal.getBoolean() ? ".T." : ".F.") + ")";
            }
            if (Set.of(XSDDatatype.XSDinteger.getURI(), XSDDatatype.XSDint.getURI(),
                    XSDDatatype.XSDlong.getURI(), XSDDatatype.XSDshort.getURI(),
                    XSDDatatype.XSDnonNegativeInteger.getURI(), XSDDatatype.XSDpositiveInteger.getURI())
                    .contains(datatype)) {
                return "IFCINTEGER(" + literal.getLong() + ")";
            }
            if (Set.of(XSDDatatype.XSDdecimal.getURI(), XSDDatatype.XSDdouble.getURI(),
                    XSDDatatype.XSDfloat.getURI()).contains(datatype)) {
                BigDecimal decimal = new BigDecimal(lexical);
                return "IFCREAL(" + decimal.stripTrailingZeros().toPlainString() + ")";
            }
        } catch (RuntimeException invalid) {
            warnings.add("Property " + propertyName + " had an invalid numeric value and was kept as text.");
        }
        return (lexical.length() <= 255 ? "IFCLABEL(" : "IFCTEXT(") + string(lexical) + ")";
    }

    private static Map<Resource, Resource> assignElementContainers(List<List<Resource>> levels,
            List<Resource> elements) {
        Map<Resource, Resource> result = new LinkedHashMap<>();
        for (List<Resource> containers : levels) {
            for (Resource container : containers) {
                container.listProperties(Vocabulary.CONTAINS_ELEMENT).mapWith(Statement::getObject)
                        .filterKeep(RDFNode::isResource).mapWith(RDFNode::asResource)
                        .filterKeep(elements::contains).toList().stream().sorted(RESOURCE_ORDER)
                        .forEach(element -> result.putIfAbsent(element, container));
            }
        }
        Resource fallback = levels.size() > 1 && !levels.get(1).isEmpty()
                ? levels.get(1).getFirst()
                : levels.stream().filter(level -> !level.isEmpty()).findFirst()
                        .map(List::getFirst).orElseThrow();
        elements.forEach(element -> result.putIfAbsent(element, fallback));
        return result;
    }

    private static Map<Resource, Resource> assignParents(List<Resource> parents, Property relation,
            List<Resource> children) {
        return assignParents(parents, relation, children, true);
    }

    private static Map<Resource, Resource> assignParents(List<Resource> parents, Property relation,
            List<Resource> children, boolean assignOrphans) {
        Map<Resource, Resource> result = new LinkedHashMap<>();
        for (Resource parent : parents) {
            parent.listProperties(relation).mapWith(Statement::getObject)
                    .filterKeep(RDFNode::isResource).mapWith(RDFNode::asResource)
                    .filterKeep(children::contains).toList().stream().sorted(RESOURCE_ORDER)
                    .filter(child -> !child.equals(parent))
                    .forEach(child -> result.putIfAbsent(child, parent));
        }
        if (assignOrphans && !parents.isEmpty()) {
            children.forEach(child -> result.putIfAbsent(child, parents.getFirst()));
        }
        return result;
    }

    private static Map<Resource, String> allocateGuids(Set<Resource> resources, List<String> warnings) {
        Map<Resource, String> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Resource resource : resources) {
            String candidate = firstNonBlank(literal(resource, Vocabulary.GLOBAL_ID),
                    literal(resource, Vocabulary.LEGACY_GLOBAL_ID));
            if (candidate != null && !IfcGuid.isValid(candidate)) {
                warnings.add("Invalid IFC GlobalId on " + key(resource)
                        + "; a deterministic replacement was generated.");
            }
            String guid = IfcGuid.preserveOrCreate(candidate, key(resource));
            if (!used.add(guid)) {
                warnings.add("Duplicate IFC GlobalId " + guid + " on " + key(resource)
                        + "; a deterministic replacement was generated.");
                guid = IfcGuid.preserveOrCreate(null, "duplicate\u0000" + key(resource));
                while (!used.add(guid)) {
                    guid = IfcGuid.preserveOrCreate(null, "duplicate\u0000" + guid);
                }
            }
            result.put(resource, guid);
        }
        return result;
    }

    private static List<Resource> resourcesOfType(Model model, Resource type) {
        return model.listResourcesWithProperty(RDF.type, type).toList().stream()
                .sorted(RESOURCE_ORDER).toList();
    }

    private static String elementIfcClass(Resource element) {
        List<String> localTypes = element.listProperties(RDF.type).mapWith(Statement::getObject)
                .filterKeep(RDFNode::isResource).mapWith(RDFNode::asResource)
                .mapWith(Resource::getLocalName).filterKeep(Objects::nonNull).toList();
        for (Map.Entry<String, String> mapping : IFC_ELEMENT_TYPES.entrySet()) {
            if (localTypes.contains(mapping.getKey())) {
                return mapping.getValue();
            }
        }
        return "IFCBUILDINGELEMENTPROXY";
    }

    private static Map<String, String> elementTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("Door", "IFCDOOR");
        types.put("Window", "IFCWINDOW");
        types.put("Wall", "IFCWALL");
        types.put("Slab", "IFCSLAB");
        types.put("Beam", "IFCBEAM");
        types.put("Column", "IFCCOLUMN");
        types.put("Roof", "IFCROOF");
        types.put("StairFlight", "IFCSTAIRFLIGHT");
        types.put("Stair", "IFCSTAIR");
        types.put("Railing", "IFCRAILING");
        types.put("RampFlight", "IFCRAMPFLIGHT");
        types.put("Ramp", "IFCRAMP");
        types.put("Footing", "IFCFOOTING");
        types.put("Pile", "IFCPILE");
        types.put("Plate", "IFCPLATE");
        types.put("Member", "IFCMEMBER");
        types.put("Covering", "IFCCOVERING");
        types.put("CurtainWall", "IFCCURTAINWALL");
        types.put("Chimney", "IFCCHIMNEY");
        types.put("ShadingDevice", "IFCSHADINGDEVICE");
        types.put("OpeningElement", "IFCOPENINGELEMENT");
        types.put("Furniture", "IFCFURNITURE");
        types.put("FurnishingElement", "IFCFURNISHINGELEMENT");
        return Collections.unmodifiableMap(types);
    }

    private static String name(Resource resource, String fallback) {
        String label = literal(resource, RDFS.label);
        if (label != null && !label.isBlank()) {
            return label;
        }
        String localName = resource.getLocalName();
        return localName == null || localName.isBlank() ? fallback : localName;
    }

    private static String objectType(Resource resource) {
        return literal(resource, Vocabulary.OBJECT_TYPE);
    }

    private static String longName(Resource resource) {
        return literal(resource, Vocabulary.LONG_NAME);
    }

    private static String numberOrNull(Resource resource, Property property) {
        String value = literal(resource, property);
        if (value == null) {
            return "$";
        }
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException invalid) {
            return "$";
        }
    }

    private static String literal(Resource resource, Property property) {
        Statement statement = resource.getProperty(property);
        return statement != null && statement.getObject().isLiteral()
                ? statement.getString() : null;
    }

    private static String cleanPropertyName(String name) {
        if (name == null || name.isBlank()) {
            return "LBD property";
        }
        return name.replaceFirst("_(property|attribute)_simple$", "")
                .replaceFirst("_simple$", "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String key(Resource resource) {
        return resource.isURIResource() ? resource.getURI() : "_:" + resource.getId().getLabelString();
    }
}
