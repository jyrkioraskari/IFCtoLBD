/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.linkedbuildingdata.ifc2lbd.desktop;

import java.util.List;
import java.util.Locale;

import org.apache.jena.graph.Node;
import org.apache.jena.shacl.validation.Severity;

/** Immutable, model-independent values copied from SHACL report entries. */
final class ValidationFindings {
	enum Level {
		VIOLATION("Violation"), WARNING("Warning"), INFO("Info");

		private final String label;

		Level(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}
	}

	record Finding(String shapeName, String sourceName, String focusNode, String objectUri,
			String propertyPath, String value, String message, Level level) {
		String shapeKey() {
			return shapeName + " (" + sourceName + ")";
		}

		String objectKey() {
			return objectUri != null && !objectUri.isBlank() ? objectUri
					: focusNode != null && !focusNode.isBlank() ? focusNode : "Unknown object";
		}
	}

	record Counts(long violations, long warnings, long infos) {
		String summary() {
			return violations + " violation" + (violations == 1 ? "" : "s") + ", "
					+ warnings + " warning" + (warnings == 1 ? "" : "s") + ", "
					+ infos + " info";
		}
	}

	private ValidationFindings() {
	}

	static Level levelOf(Severity severity) {
		if (Severity.Warning.equals(severity)) return Level.WARNING;
		if (Severity.Info.equals(severity)) return Level.INFO;
		return Level.VIOLATION;
	}

	static String nodeText(Node node) {
		if (node == null) return null;
		if (node.isURI()) return node.getURI();
		if (node.isLiteral()) {
			String lexical = node.getLiteralLexicalForm();
			String language = node.getLiteralLanguage();
			return language == null || language.isBlank() ? lexical : lexical + " @" + language;
		}
		return node.toString();
	}

	static Counts count(List<Finding> findings) {
		long violations = findings.stream().filter(finding -> finding.level() == Level.VIOLATION).count();
		long warnings = findings.stream().filter(finding -> finding.level() == Level.WARNING).count();
		long infos = findings.stream().filter(finding -> finding.level() == Level.INFO).count();
		return new Counts(violations, warnings, infos);
	}

	static List<Finding> filter(List<Finding> findings, Level level, String shapeKey, String objectText) {
		String needle = objectText == null ? "" : objectText.trim().toLowerCase(Locale.ROOT);
		return findings.stream().filter(finding -> level == null || finding.level() == level)
				.filter(finding -> shapeKey == null || shapeKey.equals(finding.shapeKey()))
				.filter(finding -> needle.isEmpty()
						|| finding.objectKey().toLowerCase(Locale.ROOT).contains(needle)
						|| finding.focusNode() != null && finding.focusNode().toLowerCase(Locale.ROOT).contains(needle))
				.toList();
	}

	static String inspection(Finding finding) {
		return finding.level().label() + " · " + finding.shapeKey() + "\n"
				+ "Focus node: " + show(finding.focusNode()) + "\n"
				+ "Affected object: " + show(finding.objectUri()) + "\n"
				+ "Property path: " + show(finding.propertyPath()) + "\n"
				+ "Offending value: " + show(finding.value()) + "\n"
				+ "Message: " + show(finding.message());
	}

	private static String show(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
}
