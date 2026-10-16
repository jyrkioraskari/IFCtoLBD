package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConversionPresetTest {
	@Test
	void builtInsSelectDistinctConversionOutcomes() {
		Map<String, ConversionPreset> presets = new LinkedHashMap<>();
		ConversionPreset.builtIns().forEach(preset -> presets.put(preset.name(), preset));
		assertEquals(6, presets.size());
		assertFalse(Boolean.parseBoolean(presets.get("Lightweight topology").settings().get("properties")));
		assertTrue(Boolean.parseBoolean(presets.get("Asset and properties").settings().get("properties")));
		assertTrue(Boolean.parseBoolean(presets.get("Materials and properties").settings().get("materials")));
		assertTrue(Boolean.parseBoolean(presets.get("Materials and properties").settings().get("properties")));
		assertTrue(Boolean.parseBoolean(presets.get("Materials and properties").settings().get("units")));
		assertFalse(Boolean.parseBoolean(presets.get("Materials and properties").settings().get("performance")));
		assertTrue(Boolean.parseBoolean(presets.get("Geometry preview").settings().get("geometry")));
		assertTrue(Boolean.parseBoolean(presets.get("Full preservation").settings().get("ifcOwl")));
		assertFalse(Boolean.parseBoolean(presets.get("Full preservation").settings().get("performance")));
		assertEquals("ICDD package", presets.get("ICDD package").settings().get(ConversionPreset.FORMAT));
	}

	@Test
	void exportedPresetCanBeImportedWithAllSettings() throws Exception {
		ConversionPreset preset = ConversionPreset.builtIns().get(2).renamed("My geometry preset");
		Map<String, String> settings = new LinkedHashMap<>(preset.settings());
		settings.put(ConversionPreset.BASE_URI, "https://example.org/building#");
		preset = new ConversionPreset(preset.name(), settings, false);
		StringWriter writer = new StringWriter();
		preset.write(writer);
		ConversionPreset imported = ConversionPreset.read(new StringReader(writer.toString()));
		assertEquals(preset.name(), imported.name());
		assertEquals(preset.settings(), imported.settings());
		assertFalse(imported.builtIn());
	}

	@Test
	void rejectsIncompleteAndUnknownSettings() {
		assertThrows(IllegalArgumentException.class,
				() -> ConversionPreset.read(new StringReader("presetVersion=1\nname=Bad\nformat=Turtle TTL\n")));
		Map<String, String> settings = new LinkedHashMap<>(ConversionPreset.builtIns().getFirst().settings());
		settings.put("geometry", "perhaps");
		assertThrows(IllegalArgumentException.class, () -> new ConversionPreset("Bad", settings, false));
	}

	@Test
	void importsOlderPresetWithMaterialsDisabled() throws Exception {
		StringWriter writer = new StringWriter();
		ConversionPreset.builtIns().getFirst().write(writer);
		String legacy = writer.toString().replaceFirst("(?m)^materials=false\\R", "");
		ConversionPreset imported = ConversionPreset.read(new StringReader(legacy));
		assertFalse(Boolean.parseBoolean(imported.settings().get("materials")));
	}
}
