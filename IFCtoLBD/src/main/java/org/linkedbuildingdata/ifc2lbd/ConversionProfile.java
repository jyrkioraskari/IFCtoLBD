package org.linkedbuildingdata.ifc2lbd;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable selection of versioned conversion modules. */
public final class ConversionProfile {

	private final String id;
	private final List<ConversionModule> modules;

	private ConversionProfile(String id, List<ConversionModule> modules) {
		this.id = requireText(id);
		this.modules = List.copyOf(modules);
		if (this.modules.isEmpty()) {
			throw new IllegalArgumentException("A conversion profile must contain at least one module");
		}
		Map<String, ConversionModule> unique = new LinkedHashMap<>();
		for (ConversionModule module : this.modules) {
			ConversionModule previous = unique.putIfAbsent(Objects.requireNonNull(module, "module").id(), module);
			if (previous != null) {
				throw new IllegalArgumentException("Duplicate conversion module: " + module.id());
			}
		}
	}

	public static ConversionProfile of(String id, ConversionModule... modules) {
		return new ConversionProfile(id, List.of(modules));
	}

	public String id() { return id; }

	public List<ConversionModule> modules() { return modules; }

	public ConversionProperties toConversionProperties() {
		ConversionProperties properties = disabledProperties();
		modules.forEach(module -> module.configure(properties));
		return properties;
	}

	private static ConversionProperties disabledProperties() {
		ConversionProperties properties = new ConversionProperties();
		properties.setHasBuildingElements(false);
		properties.setHasBuildingProperties(false);
		properties.setExportIfcOWL(false);
		return properties;
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Profile id must not be blank");
		return value;
	}
}
