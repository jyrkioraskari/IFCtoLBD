package org.linkedbuildingdata.ifc2lbd;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;

/** Separately invocable SHACL validation with machine and human summaries. */
public final class GraphValidationService {
	private static final String SH = "http://www.w3.org/ns/shacl#";

	public record Report(String status, boolean conforms, long violationCount, long warningCount,
			long informationCount, List<String> shapePacks, String summary, Model reportModel)
			implements AutoCloseable {
		@Override public void close() { reportModel.close(); }
	}

	private GraphValidationService() { }

	public static Report validate(Model data, ValidationShapePack... packs) {
		Objects.requireNonNull(data, "data");
		Objects.requireNonNull(packs, "packs");
		List<String> resources = Arrays.stream(packs).distinct().map(ValidationShapePack::resource).sorted().toList();
		ValidationStage.Result result = ValidationStage.validateResources(resources, data);
		return report(result, Arrays.stream(packs).distinct().map(ValidationShapePack::id).sorted().toList());
	}

	public static Report validate(Model data, String... packIds) {
		Objects.requireNonNull(packIds, "packIds");
		ValidationShapePack[] packs = Arrays.stream(packIds).map(GraphValidationService::pack).toArray(ValidationShapePack[]::new);
		return validate(data, packs);
	}

	static Report report(ValidationStage.Result result, List<String> packIds) {
		Model report = result.report();
		long violations = severity(report, "Violation");
		long warnings = severity(report, "Warning");
		long information = severity(report, "Info");
		boolean conforms = "conforms".equals(result.status());
		String summary = result.shapeResources().isEmpty()
				? "Validation was not run because no SHACL shape pack was selected."
				: conforms
						? "SHACL conformance checks passed. This does not establish the factual correctness of the building information."
						: "SHACL found " + violations + " violation(s), " + warnings + " warning(s), and "
								+ information + " informational result(s). This reports conformance, not factual correctness.";
		return new Report(result.status(), conforms, violations, warnings, information, List.copyOf(packIds), summary, report);
	}

	private static long severity(Model report, String localName) {
		RDFNode severity = report.createResource(SH + localName);
		return report.listStatements(null, report.createProperty(SH + "resultSeverity"), severity).toList().size();
	}

	private static ValidationShapePack pack(String id) {
		for (ValidationShapePack pack : ValidationShapePack.values()) if (pack.id().equals(id)) return pack;
		throw new IllegalArgumentException("Unknown SHACL shape pack: " + id);
	}
}
