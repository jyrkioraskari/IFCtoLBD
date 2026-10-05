package org.linkedbuildingdata.ifc2lbd;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Resolves IFC, project and textual units to QUDT without inventing units. */
public final class UnitResolver {
	public static final String QUDT_SCHEMA = "http://qudt.org/schema/qudt/";
	public static final String QUDT_UNIT = "http://qudt.org/vocab/unit/";
	public static final String META = "https://w3id.org/ifctolbd/unit#";
	public static final String ALIAS_TABLE_VERSION = "2026-09-01";

	/** A resolved unit. multiplier is applied only when an alias changes the numeric unit. */
	public record Resolution(String qudtUri, String originalCode, BigDecimal multiplier) {
		public Resolution {
			if (multiplier == null) multiplier = BigDecimal.ONE;
		}
		public boolean isResolved() { return qudtUri != null && !qudtUri.isBlank(); }
	}

	private record Alias(String qudtLocalName, BigDecimal multiplier) { }
	private static final Map<String, Alias> ALIASES = aliases();
	/* Deliberately small: repairs observed legacy/bSDD spellings, not compound expressions. */
	private static final Map<String, String> BSDD_QUDT_REPAIRS = Map.ofEntries(
			Map.entry("METER", "M"), Map.entry("METRE", "M"),
			Map.entry("MILLIMETER", "MilliM"), Map.entry("MILLIMETRE", "MilliM"),
			Map.entry("SQUARE_METRE", "M2"), Map.entry("CUBIC_METRE", "M3"),
			Map.entry("KILOGRAM", "KiloGM"));
	private static final Map<String, String> PREFIXES = Map.ofEntries(
			Map.entry("EXA", "Exa"), Map.entry("PETA", "Peta"), Map.entry("TERA", "Tera"),
			Map.entry("GIGA", "Giga"), Map.entry("MEGA", "Mega"), Map.entry("KILO", "Kilo"),
			Map.entry("HECTO", "Hecto"), Map.entry("DECA", "Deca"), Map.entry("DECI", "Deci"),
			Map.entry("CENTI", "Centi"), Map.entry("MILLI", "Milli"), Map.entry("MICRO", "Micro"),
			Map.entry("NANO", "Nano"), Map.entry("PICO", "Pico"), Map.entry("FEMTO", "Femto"),
			Map.entry("ATTO", "Atto"));
	private static final Map<String, String> IFC_NAMES = Map.ofEntries(
			Map.entry("METRE", "M"), Map.entry("SQUARE_METRE", "M2"), Map.entry("CUBIC_METRE", "M3"),
			Map.entry("GRAM", "GM"), Map.entry("SECOND", "SEC"), Map.entry("AMPERE", "A"),
			Map.entry("KELVIN", "K"), Map.entry("MOLE", "MOL"), Map.entry("CANDELA", "CD"),
			Map.entry("RADIAN", "RAD"), Map.entry("STERADIAN", "SR"), Map.entry("HERTZ", "HZ"),
			Map.entry("NEWTON", "N"), Map.entry("PASCAL", "PA"), Map.entry("JOULE", "J"),
			Map.entry("WATT", "W"), Map.entry("COULOMB", "C"), Map.entry("VOLT", "V"),
			Map.entry("FARAD", "FARAD"), Map.entry("OHM", "OHM"), Map.entry("SIEMENS", "S"),
			Map.entry("WEBER", "WB"), Map.entry("TESLA", "T"), Map.entry("HENRY", "H"),
			Map.entry("DEGREE_CELSIUS", "DEG_C"), Map.entry("LUMEN", "LM"), Map.entry("LUX", "LUX"),
			Map.entry("BECQUEREL", "BQ"), Map.entry("GRAY", "GY"), Map.entry("SIEVERT", "SV"));

	private final Map<String, Resolution> projectUnits;

	public UnitResolver(Map<String, Resolution> projectUnits) {
		this.projectUnits = Map.copyOf(projectUnits);
	}

	public static UnitResolver empty() { return new UnitResolver(Map.of()); }

	/** Builds the applicable project-unit table, retaining prefixes and unknown unit codes. */
	public static UnitResolver fromProject(Model model, IfcOWL ifc) {
		Map<String, Resolution> units = new LinkedHashMap<>();
		Resource project = org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils.getIfcProject(ifc, model);
		if (project == null) return new UnitResolver(units);
		RDFStep[] path = { new RDFStep(ifc.getUnitsInContext_IfcProject()),
				new RDFStep(ifc.getUnits_IfcUnitAssignment()) };
		for (RDFNode node : RDFUtils.pathQuery(project, path)) {
			if (!node.isResource()) continue;
			Resource unit = node.asResource();
			String measurement = localObject(unit, ifc.getUnitType_IfcNamedUnit());
			if (measurement == null) continue;
			units.put(measurementKey(measurement), resolveIfcUnit(unit, ifc));
		}
		return new UnitResolver(units);
	}

	/** Compatibility bridge for callers which already collected project SI names. */
	public static UnitResolver fromLegacyProjectUnits(Map<String, String> units) {
		Map<String, Resolution> resolved = new LinkedHashMap<>();
		units.forEach((kind, code) -> resolved.put(measurementKey(kind), fromIfcCode(code)));
		return new UnitResolver(resolved);
	}

	/** Explicit IFC unit wins; otherwise the unit assigned to the measurement type is used. */
	public Resolution resolve(Resource explicitUnit, RDFNode measurementType, IfcOWL ifc) {
		if (explicitUnit != null) return resolveIfcUnit(explicitUnit, ifc);
		String key = measurementType != null && measurementType.isResource()
				? measurementKey(measurementType.asResource().getLocalName()) : "";
		return projectUnits.getOrDefault(key, new Resolution(null, null, BigDecimal.ONE));
	}

	/** Resolves an explicit IFC unit by its IFC predicates, independent of schema version. */
	public Resolution resolve(Resource explicitUnit, RDFNode measurementType) {
		if (explicitUnit != null) return resolveIfcUnit(explicitUnit, null);
		return projectUnit(measurementType).orElse(new Resolution(null, null, BigDecimal.ONE));
	}

	public Optional<Resolution> projectUnit(RDFNode measurementType) {
		if (measurementType == null || !measurementType.isResource()) return Optional.empty();
		return Optional.ofNullable(projectUnits.get(measurementKey(measurementType.asResource().getLocalName())));
	}

	public static Resolution fromText(String original) {
		if (original == null || original.isBlank()) return new Resolution(null, original, BigDecimal.ONE);
		Alias alias = ALIASES.get(normalize(original));
		if (alias == null)
			return new Resolution(null, original, BigDecimal.ONE); // compound/free-text expressions stay intact
		return new Resolution(QUDT_UNIT + alias.qudtLocalName(), original, alias.multiplier());
	}

	/**
	 * Resolves bSDD unit metadata. A supplied QUDT identifier has priority; the
	 * original bSDD code remains attached for round-tripping and diagnostics.
	 */
	public static Resolution fromBsdd(String qudtIdentifier, String originalCode) {
		if (qudtIdentifier != null && !qudtIdentifier.isBlank()) {
			String token = qudtIdentifier.trim();
			String local = token.startsWith(QUDT_UNIT) ? token.substring(QUDT_UNIT.length())
					: token.startsWith("unit:") ? token.substring("unit:".length()) : token;
			local = BSDD_QUDT_REPAIRS.getOrDefault(local.toUpperCase(Locale.ROOT), local);
			if (!local.contains(":") && !local.contains("/") && !local.contains(" "))
				return new Resolution(QUDT_UNIT + local, originalCode, BigDecimal.ONE);
		}
		return fromText(originalCode);
	}

	public static RDFNode normalizeValue(Model model, RDFNode value, Resolution unit) {
		if (value == null || unit == null || unit.multiplier().compareTo(BigDecimal.ONE) == 0 || !value.isLiteral())
			return value;
		Literal literal = value.asLiteral();
		if (!(literal.getValue() instanceof Number)) return value;
		try {
			BigDecimal number = new BigDecimal(literal.getLexicalForm()).multiply(unit.multiplier());
			return model.createTypedLiteral(number.stripTrailingZeros().toPlainString(), XSD.decimal.getURI());
		} catch (NumberFormatException ignored) {
			return value;
		}
	}

	public static void write(Model model, Resource owner, Resolution unit) {
		if (unit == null) return;
		if (unit.isResolved()) owner.addProperty(model.createProperty(QUDT_SCHEMA + "unit"),
				model.createResource(unit.qudtUri()));
		if (unit.originalCode() != null && !unit.originalCode().isBlank())
			owner.addLiteral(model.createProperty(META + "originalUnitCode"), unit.originalCode());
		owner.getModel().setNsPrefix("qudt", QUDT_SCHEMA);
		owner.getModel().setNsPrefix("unit", QUDT_UNIT);
		owner.getModel().setNsPrefix("unitmeta", META);
	}

	private static Resolution resolveIfcUnit(Resource unit, IfcOWL ifc) {
		String name = ifc == null ? localObject(unit, "name_IfcSIUnit")
				: localObject(unit, ifc.getName_IfcSIUnit());
		String prefix = ifc == null ? localObject(unit, "prefix_IfcSIUnit")
				: localObject(unit, ifc.getPrefix_IfcSIUnit());
		if (name != null) {
			String original = prefix == null ? name : prefix + " " + name;
			String base = IFC_NAMES.get(name.toUpperCase(Locale.ROOT));
			if (base == null) return new Resolution(null, original, BigDecimal.ONE);
			String local = prefix == null ? base : prefixed(base, prefix);
			return local == null ? new Resolution(null, original, BigDecimal.ONE)
					: new Resolution(QUDT_UNIT + local, original, BigDecimal.ONE);
		}
		String code = unit.isURIResource() ? unit.getURI() : unit.toString();
		return new Resolution(null, code, BigDecimal.ONE);
	}

	private static Resolution fromIfcCode(String code) {
		if (code == null) return new Resolution(null, null, BigDecimal.ONE);
		String normalized = code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		for (String prefix : PREFIXES.keySet()) {
			if (!normalized.startsWith(prefix + "_")) continue;
			String base = IFC_NAMES.get(normalized.substring(prefix.length() + 1));
			String local = base == null ? null : prefixed(base, prefix);
			return local == null ? new Resolution(null, code, BigDecimal.ONE)
					: new Resolution(QUDT_UNIT + local, code, BigDecimal.ONE);
		}
		String base = IFC_NAMES.get(normalized);
		return base == null ? new Resolution(null, code, BigDecimal.ONE)
				: new Resolution(QUDT_UNIT + base, code, BigDecimal.ONE);
	}

	private static String prefixed(String base, String prefix) {
		String qudtPrefix = PREFIXES.get(prefix.toUpperCase(Locale.ROOT));
		if (qudtPrefix == null) return null;
		if (base.equals("M2")) return qudtPrefix + "M2";
		if (base.equals("M3")) return qudtPrefix + "M3";
		return qudtPrefix + base;
	}

	private static String localObject(Resource subject, org.apache.jena.rdf.model.Property property) {
		RDFNode node = subject.getPropertyResourceValue(property);
		return node == null ? null : node.asResource().getLocalName();
	}

	private static String localObject(Resource subject, String predicateLocalName) {
		var statements = subject.listProperties();
		try {
			while (statements.hasNext()) {
				var statement = statements.next();
				if (predicateLocalName.equals(statement.getPredicate().getLocalName())
						&& statement.getObject().isResource())
					return statement.getResource().getLocalName();
			}
			return null;
		} finally { statements.close(); }
	}

	private static String measurementKey(String value) {
		if (value == null) return "";
		String key = value.toLowerCase(Locale.ROOT);
		if (key.startsWith("ifc")) key = key.substring(3);
		if (key.startsWith("positive")) key = key.substring("positive".length());
		if (key.endsWith("measure")) key = key.substring(0, key.length() - "measure".length());
		if (key.endsWith("unit")) key = key.substring(0, key.length() - "unit".length());
		return key;
	}

	private static String normalize(String value) {
		return value.trim().replace("²", "2").replace("³", "3").replace("₂", "2")
				.replaceAll("[\\s._-]+", "").toLowerCase(Locale.ROOT);
	}

	private static Map<String, Alias> aliases() {
		Map<String, Alias> map = new LinkedHashMap<>();
		alias(map, "M", BigDecimal.ONE, "m", "metre", "meter");
		alias(map, "MilliM", BigDecimal.ONE, "mm", "millimetre", "millimeter");
		alias(map, "M2", BigDecimal.ONE, "m2", "sqm", "squaremetre", "squaremeter");
		alias(map, "M3", BigDecimal.ONE, "m3", "cbm", "cubicmetre", "cubicmeter");
		alias(map, "KiloGM", BigDecimal.ONE, "kg", "kilogram", "kilograms");
		alias(map, "KiloGM", new BigDecimal("0.001"), "g", "gram", "grams");
		alias(map, "Each", BigDecimal.ONE, "each", "piece", "pieces", "item");
		alias(map, "RAD", BigDecimal.ONE, "rad", "radian");
		return Map.copyOf(map);
	}

	private static void alias(Map<String, Alias> map, String qudt, BigDecimal multiplier, String... codes) {
		for (String code : codes) map.put(normalize(code), new Alias(qudt, multiplier));
	}
}
