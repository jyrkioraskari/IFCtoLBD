package org.linkedbuildingdata.ifc2lbd.namespace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Offline index of authoritative bSDD references for the IFC 4.3 ADD2
 * property-set publication.
 *
 * <p>The index is deliberately set-aware: a property is resolved only when
 * the official PSD publication declares it in the named set. This prevents a
 * custom set or a misspelled name from being presented as an IFC bSDD
 * concept.</p>
 */
public final class IfcBsddDictionary {
	private static final String INDEX = "/ifc-bsdd/ifc-4.3-add2.tsv.gz";
	private static final String VERSION = "IFC4X3_ADD2";

	private final Map<String, Concept> sets;
	private final Map<String, Map<String, Concept>> propertiesBySet;

	private IfcBsddDictionary() {
		Map<String, Concept> loadedSets = new HashMap<>();
		Map<String, Map<String, Concept>> loadedProperties = new HashMap<>();
		try (InputStream resource = IfcBsddDictionary.class.getResourceAsStream(INDEX)) {
			if (resource == null)
				throw new IllegalStateException("Missing bundled IFC bSDD index " + INDEX);
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					new GZIPInputStream(resource), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.isBlank() || line.charAt(0) == '#') continue;
					String[] fields = line.split("\\t", -1);
					if (fields.length != 4) throw new IllegalStateException("Invalid IFC bSDD index row");
					if ("C".equals(fields[0])) {
						loadedSets.put(key(fields[1]), new Concept(fields[1], fields[3]));
					} else if ("P".equals(fields[0])) {
						loadedProperties.computeIfAbsent(key(fields[1]), ignored -> new HashMap<>())
								.put(key(fields[2]), new Concept(fields[2], fields[3]));
					} else {
						throw new IllegalStateException("Unknown IFC bSDD index row kind " + fields[0]);
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not read bundled IFC bSDD index " + INDEX, e);
		}
		sets = Map.copyOf(loadedSets);
		Map<String, Map<String, Concept>> immutableProperties = new HashMap<>();
		loadedProperties.forEach((set, properties) -> immutableProperties.put(set, Map.copyOf(properties)));
		propertiesBySet = Map.copyOf(immutableProperties);
	}

	public static IfcBsddDictionary get() {
		return Holder.INSTANCE;
	}

	public String version() {
		return VERSION;
	}

	public Optional<Concept> propertySet(String name) {
		return Optional.ofNullable(sets.get(key(name)));
	}

	public Optional<Concept> property(String propertySetName, String propertyName) {
		Map<String, Concept> properties = propertiesBySet.get(key(propertySetName));
		return properties == null ? Optional.empty() : Optional.ofNullable(properties.get(key(propertyName)));
	}

	private static String key(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	public record Concept(String code, String uri) { }

	private static final class Holder {
		private static final IfcBsddDictionary INSTANCE = new IfcBsddDictionary();
	}
}
