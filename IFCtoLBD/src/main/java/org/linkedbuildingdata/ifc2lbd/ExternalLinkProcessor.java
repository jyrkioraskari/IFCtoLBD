package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.datatypes.xsd.XSDDatatype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Offline resolver for conversion-scoped CSV and JSON GlobalId mappings. */
final class ExternalLinkProcessor {
	static final String NS = "https://w3id.org/ifctolbd/external-link#";
	record Result(Model links, Model report, List<String> checksums) { }
	private record Row(ExternalLinkMapping mapping, Path file, int number) { }
	private record Exact(String globalId, String external, String predicate, String source, String method,
			Double confidence, String validFrom, String validTo, String recordedAt) { }

	static Result process(List<Path> files, Map<String, List<Resource>> resourcesByGlobalId) {
		Model links = ModelFactory.createDefaultModel();
		Model report = ModelFactory.createDefaultModel();
		links.setNsPrefix("extlink", NS); report.setNsPrefix("extlink", NS);
		List<String> checksums = new ArrayList<>();
		Set<Exact> emitted = new HashSet<>();
		for (Path file : files) {
			Path normalized = file.toAbsolutePath().normalize();
			if (!Files.isRegularFile(normalized)) throw new IllegalArgumentException("Cannot read mapping file: " + file);
			checksums.add(ConversionManifest.sha256(normalized));
			for (Row row : read(normalized)) resolve(row, resourcesByGlobalId, emitted, links, report);
		}
		return new Result(links, report, List.copyOf(checksums));
	}

	private static void resolve(Row row, Map<String, List<Resource>> index, Set<Exact> emitted,
			Model links, Model report) {
		ExternalLinkMapping m = row.mapping();
		String error = validate(m);
		List<Resource> matches = error == null ? index.getOrDefault(m.globalId(), List.of()) : List.of();
		String status = error != null ? "invalid" : matches.isEmpty() ? "unmatched" : matches.size() > 1 ? "ambiguous" : "matched";
		String rowId = ConversionManifest.sha256(row.file() + "|" + row.number() + "|" + String.valueOf(m));
		Resource entry = report.createResource("urn:ifctolbd:external-link-row:" + rowId)
				.addProperty(RDF.type, report.createResource(NS + "MappingResult"))
				.addLiteral(report.createProperty(NS + "row"), row.number())
				.addLiteral(report.createProperty(NS + "mappingChecksum"), ConversionManifest.sha256(row.file()))
				.addLiteral(report.createProperty(NS + "status"), status);
		if (m.globalId() != null) entry.addLiteral(report.createProperty(NS + "globalId"), m.globalId());
		if (m.source() != null && !m.source().isBlank()) entry.addLiteral(report.createProperty(NS + "source"), m.source());
		if (m.method() != null && !m.method().isBlank()) entry.addLiteral(report.createProperty(NS + "method"), m.method());
		if (m.confidence() != null) entry.addLiteral(report.createProperty(NS + "confidence"), m.confidence());
		addTime(entry, report, "validFrom", m.validFrom());
		addTime(entry, report, "validTo", m.validTo());
		addTime(entry, report, "recordedAt", m.recordedAt());
		if (error != null) entry.addLiteral(report.createProperty(NS + "message"), error);
		matches.forEach(resource -> entry.addProperty(report.createProperty(NS + "candidate"), report.createResource(resource.getURI())));
		if (!"matched".equals(status)) return;
		Exact exact = new Exact(m.globalId(), m.externalResourceIri(), m.predicateIri(), m.source(), m.method(),
				m.confidence(), m.validFrom(), m.validTo(), m.recordedAt());
		if (!emitted.add(exact)) {
			entry.removeAll(report.createProperty(NS + "status")).addLiteral(report.createProperty(NS + "status"), "duplicate");
			return;
		}
		Resource subject = links.createResource(matches.get(0).getURI());
		subject.addProperty(links.createProperty(m.predicateIri()), links.createResource(m.externalResourceIri()));
		entry.addProperty(report.createProperty(NS + "resolvedElement"), report.createResource(subject.getURI()))
				.addProperty(report.createProperty(NS + "externalResource"), report.createResource(m.externalResourceIri()))
				.addProperty(report.createProperty(NS + "predicate"), report.createResource(m.predicateIri()));
	}

	private static String validate(ExternalLinkMapping m) {
		if (m == null) return "Mapping row must be an object";
		if (m.globalId() == null || !m.globalId().matches("[0-9A-Za-z_$]{22}")) return "IFC GlobalId must be 22 IFC GUID characters";
		if (!absoluteIri(m.externalResourceIri())) return "External resource IRI must be an absolute IRI";
		if (!absoluteIri(m.predicateIri())) return "Relationship predicate IRI must be an absolute IRI";
		if (m.confidence() != null && (!Double.isFinite(m.confidence()) || m.confidence() < 0 || m.confidence() > 1))
			return "Confidence must be between 0 and 1";
		Instant from = instant(m.validFrom()), to = instant(m.validTo());
		if (m.validFrom() != null && !m.validFrom().isBlank() && from == null) return "validFrom must be an ISO-8601 instant";
		if (m.validTo() != null && !m.validTo().isBlank() && to == null) return "validTo must be an ISO-8601 instant";
		if (m.recordedAt() != null && !m.recordedAt().isBlank() && instant(m.recordedAt()) == null)
			return "recordedAt must be an ISO-8601 instant";
		if (from != null && to != null && from.isAfter(to)) return "validFrom must not be after validTo";
		return null;
	}

	private static Instant instant(String value) {
		if (value == null || value.isBlank()) return null;
		try { return Instant.parse(value); } catch (java.time.format.DateTimeParseException e) { return null; }
	}

	private static void addTime(Resource entry, Model model, String property, String value) {
		if (value != null && !value.isBlank()) entry.addLiteral(model.createProperty(NS + property),
				model.createTypedLiteral(value, XSDDatatype.XSDdateTime));
	}

	private static boolean absoluteIri(String value) {
		try { return value != null && !value.isBlank() && URI.create(value).isAbsolute() && !value.contains(" "); }
		catch (IllegalArgumentException e) { return false; }
	}

	private static List<Row> read(Path file) {
		String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		try {
			return name.endsWith(".json") ? readJson(file) : name.endsWith(".csv") ? readCsv(file)
					: throwFormat(file);
		} catch (IOException e) { throw new UncheckedIOException("Could not read mapping file " + file, e); }
	}

	private static List<Row> throwFormat(Path file) {
		throw new IllegalArgumentException("Mapping file must use .csv or .json: " + file);
	}

	private static List<Row> readJson(Path file) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(file.toFile());
		if (!root.isArray()) throw new IllegalArgumentException("JSON mapping file must contain an array: " + file);
		List<Row> rows = new ArrayList<>(); int number = 0;
		for (JsonNode node : root) {
			number++;
			rows.add(new Row(new ExternalLinkMapping(text(node, "globalId", "ifcGlobalId"),
					text(node, "externalResourceIri", "externalIri"), text(node, "predicateIri", "relationshipPredicateIri"),
					text(node, "source"), text(node, "method"), number(node, "confidence"),
					text(node, "validFrom"), text(node, "validTo"), text(node, "recordedAt")), file, number));
		}
		return rows;
	}

	private static String text(JsonNode node, String... names) {
		for (String name : names) if (node.hasNonNull(name)) return node.get(name).asText();
		return null;
	}
	private static Double number(JsonNode node, String name) {
		if (!node.hasNonNull(name)) return null;
		return node.get(name).isNumber() ? node.get(name).doubleValue() : Double.NaN;
	}

	private static List<Row> readCsv(Path file) throws IOException {
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		if (lines.isEmpty()) return List.of();
		List<String> header = csv(lines.get(0)); Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.size(); i++) columns.put(header.get(i).trim(), i);
		List<Row> rows = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			if (lines.get(i).isBlank()) continue;
			List<String> values = csv(lines.get(i)); String confidence = value(values, columns, "confidence");
			Double parsed = null;
			try { if (confidence != null && !confidence.isBlank()) parsed = Double.valueOf(confidence); }
			catch (NumberFormatException ignored) { parsed = Double.NaN; }
			rows.add(new Row(new ExternalLinkMapping(first(values, columns, "globalId", "ifcGlobalId"),
					first(values, columns, "externalResourceIri", "externalIri"),
					first(values, columns, "predicateIri", "relationshipPredicateIri"),
					value(values, columns, "source"), value(values, columns, "method"), parsed,
					value(values, columns, "validFrom"), value(values, columns, "validTo"),
					value(values, columns, "recordedAt")), file, i + 1));
		}
		return rows;
	}

	private static List<String> csv(String line) {
		List<String> values = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
		for (int i = 0; i < line.length(); i++) { char c = line.charAt(i);
			if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; } else quoted = !quoted; }
			else if (c == ',' && !quoted) { values.add(value.toString()); value.setLength(0); } else value.append(c);
		}
		if (quoted) throw new IllegalArgumentException("Unclosed CSV quote"); values.add(value.toString()); return values;
	}
	private static String first(List<String> values, Map<String, Integer> columns, String... names) {
		for (String name : names) { String value = value(values, columns, name); if (value != null) return value; } return null;
	}
	private static String value(List<String> values, Map<String, Integer> columns, String name) {
		Integer index = columns.get(name); return index == null || index >= values.size() ? null : values.get(index).trim();
	}
	private ExternalLinkProcessor() { }
}
