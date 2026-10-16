/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.linkedbuildingdata.ifc2lbd.desktop;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Portable desktop settings, without model-specific input and output paths. */
record ConversionPreset(String name, Map<String, String> settings, boolean builtIn) {
	static final String FORMAT = "format";
	static final String LEVEL = "propertyLevel";
	static final String BASE_URI = "baseUri";
	static final List<String> SWITCHES = List.of("buildingElements", "separateElements", "properties",
			"separateProperties", "blankNodes", "geolocation", "materials", "geometry", "ifcOwl", "performance",
			"boundingBoxWkt", "interfaces", "wireframe", "units", "hierarchicalNames",
			"simpleProperties", "propertySets", "ifcBasedElements", "trig");
	static final List<String> REQUIRED = List.of(FORMAT, LEVEL, "buildingElements", "separateElements",
			"properties", "separateProperties", "blankNodes", "geolocation", "materials", "geometry", "ifcOwl",
			"performance", "boundingBoxWkt", "interfaces", "wireframe", "units", "hierarchicalNames",
			"simpleProperties", "propertySets", "ifcBasedElements", "trig");

	ConversionPreset {
		if (name == null || name.isBlank() || name.length() > 80) {
			throw new IllegalArgumentException("Preset name must be 1–80 characters.");
		}
		name = name.trim();
		settings = Map.copyOf(settings);
		for (String key : REQUIRED) {
			if (!settings.containsKey(key)) throw new IllegalArgumentException("Missing preset setting: " + key);
		}
		if (!List.of("Turtle TTL", "JSON-LD", "ICDD package").contains(settings.get(FORMAT))) {
			throw new IllegalArgumentException("Unsupported preset output format.");
		}
		if (!List.of("1", "2", "3").contains(settings.get(LEVEL))) {
			throw new IllegalArgumentException("Property level must be 1, 2, or 3.");
		}
		for (String key : SWITCHES) {
			if (!List.of("true", "false").contains(settings.get(key))) {
				throw new IllegalArgumentException("Invalid boolean setting: " + key);
			}
		}
		if ("true".equals(settings.get("performance"))
				&& ("true".equals(settings.get("ifcOwl")) || "true".equals(settings.get("materials")))) {
			throw new IllegalArgumentException("ifcOWL/material export and performance mode cannot both be enabled.");
		}
		for (String key : settings.keySet()) {
			if (!REQUIRED.contains(key) && !BASE_URI.equals(key)) {
				throw new IllegalArgumentException("Unknown preset setting: " + key);
			}
		}
	}

	ConversionPreset renamed(String newName) {
		return new ConversionPreset(newName, settings, false);
	}

	@Override
	public String toString() {
		return name;
	}

	void write(Writer writer) throws IOException {
		Properties properties = new Properties();
		properties.setProperty("presetVersion", "1");
		properties.setProperty("name", name);
		settings.forEach(properties::setProperty);
		properties.store(writer, "IFCtoLBD desktop conversion preset");
	}

	static ConversionPreset read(Reader reader) throws IOException {
		Properties properties = new Properties();
		properties.load(reader);
		if (!"1".equals(properties.getProperty("presetVersion"))) {
			throw new IllegalArgumentException("Unsupported preset file version.");
		}
		Map<String, String> settings = new LinkedHashMap<>();
		for (String key : properties.stringPropertyNames()) {
			if ("presetVersion".equals(key) || "name".equals(key)) continue;
			settings.put(key, properties.getProperty(key));
		}
		// Presets created before material export was added remain valid.
		settings.putIfAbsent("materials", "false");
		return new ConversionPreset(properties.getProperty("name"), settings, false);
	}

	static List<ConversionPreset> builtIns() {
		Map<String, String> base = new LinkedHashMap<>();
		for (String key : SWITCHES) base.put(key, "false");
		base.put("buildingElements", "true");
		base.put("hierarchicalNames", "true");
		base.put(LEVEL, "1");
		base.put(FORMAT, "Turtle TTL");
		return List.of(
				builtin("Lightweight topology", base),
				builtin("Asset and properties", base, "properties", "true", "units", "true", "geolocation", "true"),
				builtin("Materials and properties", base, "properties", "true", "materials", "true",
						"units", "true", "geolocation", "true"),
				builtin("Geometry preview", base, "properties", "true", "units", "true", "geometry", "true"),
				builtin("Full preservation", base, "properties", "true", "units", "true", "geolocation", "true",
						"geometry", "true", "wireframe", "true", "ifcOwl", "true", "ifcBasedElements", "true",
						"separateElements", "true", "separateProperties", "true", LEVEL, "3"),
				builtin("ICDD package", base, "properties", "true", "units", "true", "geometry", "true",
						"geolocation", "true", FORMAT, "ICDD package"));
	}

	private static ConversionPreset builtin(String name, Map<String, String> base, String... overrides) {
		Map<String, String> values = new LinkedHashMap<>(base);
		for (int i = 0; i < overrides.length; i += 2) values.put(overrides[i], overrides[i + 1]);
		return new ConversionPreset(name, values, true);
	}
}
