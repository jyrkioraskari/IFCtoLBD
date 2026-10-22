package org.linkedbuildingdata.ifc2lbd;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Reads associated IFC coordinate operations; it never guesses a CRS from unrelated entities. */
public final class IfcCoordinateReferenceSystemExtractor {
	private static final Pattern EPSG = Pattern.compile(
			"(?i)(?:^|[^A-Z0-9])EPSG(?:\\s*[:/#]|\\s+|::)\\s*(?:0\\s*[/ :]\\s*)?(\\d{4,7})(?:$|[^0-9])");
	private static final Pattern OGC_EPSG_URI = Pattern.compile(
			"(?i)^https?://www\\.opengis\\.net/def/crs/EPSG/(?:0|[^/]+)/([0-9]{4,7})/?$");
	private static final Pattern OGC_EPSG_URN = Pattern.compile(
			"(?i)^urn:ogc:def:crs:EPSG(?::[^:]*)?::?([0-9]{4,7})$");

	private IfcCoordinateReferenceSystemExtractor() { }

	public static IfcCoordinateReferenceSystemInfo extract(Model model, IfcOWL ifc) {
		if (ifc == null) return IfcCoordinateReferenceSystemInfo.engineering();
		String ns = ifc.getIfcURI();
		List<String> warnings = new ArrayList<>();
		List<Resource> supported = new ArrayList<>(resourcesOfTypes(model, ns, "IfcMapConversion", "IfcMapConversionScaled"));
		List<Resource> unsupported = new ArrayList<>(resourcesOfTypes(model, ns, "IfcRigidOperation", "IfcCoordinateOperation"));
		unsupported.removeAll(supported);
		if (supported.isEmpty()) {
			if (!unsupported.isEmpty()) warnings.add("Unsupported IFC coordinate operation "
					+ typeName(unsupported.getFirst()) + "; geometry remains local.");
			if (hasLegacyGeoreferencingPset(model)) warnings.add("IFC2X3 ePSet georeferencing was preserved but is not applied because its context association is not explicit.");
			return new IfcCoordinateReferenceSystemInfo(null, Map.of(), warnings,
					unsupported.isEmpty() ? null : typeName(unsupported.getFirst()), 1.0, false);
		}

		Set<Resource> sourceContexts = new LinkedHashSet<>();
		for (Resource operation : supported) {
			sourceContexts.addAll(objectResources(operation, "sourceCRS_IfcCoordinateOperation"));
		}
		Resource operation = supported.getFirst();
		List<Resource> sources = objectResources(operation, "sourceCRS_IfcCoordinateOperation");
		List<Resource> targets = objectResources(operation, "targetCRS_IfcCoordinateOperation");
		boolean associationSafe = supported.size() == 1 && sourceContexts.size() == 1
				&& sources.size() == 1 && targets.size() == 1;
		if (!associationSafe) warnings.add("Multiple or unassociated coordinate-operation contexts are ambiguous; geometry remains local.");
		Resource source = sources.size() == 1 ? sources.getFirst() : null;
		Resource target = targets.size() == 1 ? targets.getFirst() : null;
		if (source == null) warnings.add("Coordinate operation has no SourceCRS engineering context; geometry remains local.");
		if (target == null) warnings.add("Coordinate operation has no TargetCRS; geometry remains local.");
		if (target != null && !target.hasProperty(RDF.type, model.createResource(ns + "IfcProjectedCRS"))) {
			warnings.add("IfcMapConversion TargetCRS is not an IfcProjectedCRS; geometry remains local.");
			associationSafe = false;
		}

		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		putResource(values, "operationResource", operation);
		putResource(values, "sourceCRS", source);
		putResource(values, "targetCRS", target);
		if (target != null) {
			put(target, "name_IfcCoordinateReferenceSystem", "name", values);
			put(target, "description_IfcCoordinateReferenceSystem", "description", values);
			put(target, "geodeticDatum_IfcProjectedCRS", "geodeticDatum", values);
			put(target, "verticalDatum_IfcProjectedCRS", "verticalDatum", values);
			put(target, "mapProjection_IfcProjectedCRS", "mapProjection", values);
			put(target, "mapZone_IfcProjectedCRS", "mapZone", values);
			Resource mapUnit = objectResource(target, "mapUnit_IfcProjectedCRS");
			putResource(values, "mapUnitResource", mapUnit);
			UnitResolver.LengthConversion targetUnit = UnitResolver.explicitLengthConversion(mapUnit, ifc);
			putUnit(values, "targetMapUnit", targetUnit);
			if (!targetUnit.isResolved()) warnings.add("TargetCRS map length unit is missing or unresolved; target values retain their declared numeric unit.");
		}
		for (Map.Entry<String, String> attribute : Map.ofEntries(
				Map.entry("eastings_IfcMapConversion", "eastings"),
				Map.entry("northings_IfcMapConversion", "northings"),
				Map.entry("orthogonalHeight_IfcMapConversion", "orthogonalHeight"),
				Map.entry("xAxisAbscissa_IfcMapConversion", "xAxisAbscissa"),
				Map.entry("xAxisOrdinate_IfcMapConversion", "xAxisOrdinate"),
				Map.entry("scale_IfcMapConversion", "scale"),
				Map.entry("factorX_IfcMapConversionScaled", "factorX"),
				Map.entry("factorY_IfcMapConversionScaled", "factorY"),
				Map.entry("factorZ_IfcMapConversionScaled", "factorZ")).entrySet())
			put(operation, attribute.getKey(), attribute.getValue(), values);

		UnitResolver.LengthConversion sourceUnit = UnitResolver.fromProject(model, ifc).projectLengthConversion();
		putUnit(values, "sourceUnit", sourceUnit);
		UnitResolver.LengthConversion targetUnit = target == null ? null
				: UnitResolver.explicitLengthConversion(objectResource(target, "mapUnit_IfcProjectedCRS"), ifc);
		if (target != null && (targetUnit == null || !targetUnit.isResolved())) associationSafe = false;
		if (!values.containsKey("scale") && sourceUnit.isResolved() && targetUnit != null && targetUnit.isResolved()
				&& sourceUnit.multiplierToMetres().compareTo(targetUnit.multiplierToMetres()) != 0)
			warnings.add("MapConversion omits Scale although source and target length units differ; schema default 1.0 is retained.");
		double sourceUnitToMetres = sourceUnit.isResolved() ? sourceUnit.multiplierToMetres().doubleValue() : Double.NaN;
		String identifier = target == null ? null : explicitIdentifier(values.get("name"));
		if (target != null && identifier == null)
			warnings.add("TargetCRS Name is not an explicit URI, URN, or EPSG identifier; geometry remains local.");
		String type = typeName(operation);
		return new IfcCoordinateReferenceSystemInfo(identifier, values, warnings, type, sourceUnitToMetres,
				associationSafe && source != null && target != null);
	}

	private static List<Resource> resourcesOfTypes(Model model, String namespace, String... localNames) {
		Set<Resource> result = new LinkedHashSet<>();
		for (String name : localNames)
			model.listResourcesWithProperty(RDF.type, model.createResource(namespace + name)).forEachRemaining(result::add);
		return new ArrayList<>(result).stream().sorted(Comparator.comparing(Resource::toString)).toList();
	}

	private static String typeName(Resource resource) {
		for (Statement type : resource.listProperties(RDF.type).toList()) {
			if (!type.getObject().isResource()) continue;
			String local = type.getResource().getLocalName();
			if ("IfcMapConversionScaled".equals(local)) return local;
			if ("IfcMapConversion".equals(local) || "IfcRigidOperation".equals(local)) return local;
		}
		return "IfcCoordinateOperation";
	}

	private static void putUnit(Map<String, String> values, String key, UnitResolver.LengthConversion unit) {
		if (unit == null) return;
		UnitResolver.Resolution source = unit.sourceUnit();
		if (source != null && source.originalCode() != null) values.put(key, source.originalCode());
		if (source != null && source.qudtUri() != null) values.put(key + "Qudt", source.qudtUri());
		BigDecimal multiplier = unit.multiplierToMetres();
		if (multiplier != null) values.put(key + "ToMetres", multiplier.stripTrailingZeros().toPlainString());
	}

	private static String explicitIdentifier(String name) {
		if (name == null || name.isBlank()) return null;
		String candidate = name.trim();
		for (Pattern pattern : List.of(OGC_EPSG_URI, OGC_EPSG_URN, EPSG)) {
			Matcher matcher = pattern.matcher(candidate);
			if (matcher.find()) return "http://www.opengis.net/def/crs/EPSG/0/" + matcher.group(1);
		}
		try {
			URI uri = URI.create(candidate);
			return uri.isAbsolute() ? candidate : null;
		} catch (IllegalArgumentException ignored) { return null; }
	}

	private static Resource objectResource(Resource subject, String predicateLocalName) {
		List<Resource> resources = objectResources(subject, predicateLocalName);
		return resources.isEmpty() ? null : resources.getFirst();
	}

	private static List<Resource> objectResources(Resource subject, String predicateLocalName) {
		if (subject == null) return List.of();
		List<Resource> resources = new ArrayList<>();
		for (Statement statement : subject.listProperties().toList())
			if (predicateLocalName.equalsIgnoreCase(statement.getPredicate().getLocalName())
					&& statement.getObject().isResource()) resources.add(statement.getResource());
		return resources;
	}

	private static void put(Resource subject, String predicateLocalName, String key, Map<String, String> values) {
		if (subject == null) return;
		for (Statement statement : subject.listProperties().toList()) {
			if (!predicateLocalName.equalsIgnoreCase(statement.getPredicate().getLocalName())) continue;
			String value = scalarValue(statement.getObject(), new LinkedHashSet<>());
			if (value != null) values.putIfAbsent(key, value);
		}
	}

	private static String scalarValue(RDFNode node, Set<RDFNode> visited) {
		if (node == null || !visited.add(node)) return null;
		if (node.isLiteral()) return node.asLiteral().getLexicalForm();
		if (!node.isResource()) return null;
		Resource resource = node.asResource();
		for (Statement statement : resource.listProperties().toList()) {
			String local = statement.getPredicate().getLocalName().toLowerCase(Locale.ROOT);
			if (local.startsWith("has") || local.equals("wrappedvalue")) {
				String value = scalarValue(statement.getObject(), visited);
				if (value != null) return value;
			}
		}
		return resource.isURIResource() ? resource.getLocalName() : null;
	}

	private static void putResource(Map<String, String> values, String key, Resource resource) {
		if (resource != null) values.put(key, resource.isURIResource() ? resource.getURI() : resource.toString());
	}

	private static boolean hasLegacyGeoreferencingPset(Model model) {
		return model.listStatements().toList().stream().anyMatch(statement -> statement.getObject().isLiteral()
				&& ("ePSet_MapConversion".equalsIgnoreCase(statement.getString())
						|| "ePSet_ProjectedCRS".equalsIgnoreCase(statement.getString())));
	}
}
