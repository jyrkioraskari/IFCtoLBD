package org.linkedbuildingdata.ifc2lbd;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

/** Input values for a conversion. */
public final class ConversionRequest {

	private final String ifcFilename;
	private final String targetFile;
	private final ConversionProperties properties;
	private final ConversionProfile profile;
	private final List<ValidationShapePack> validationShapePacks;
	private final ClassificationResolver classificationResolver;
	private final String modelScope;
	private final Set<String> selectedTypes;
	private final Set<String> selectedPropertySets;
	private final Map<String, String> propertyReplacements;

	public ConversionRequest(String ifcFilename, ConversionProperties properties) {
		this(ifcFilename, null, properties, null, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of());
	}

	public ConversionRequest(String ifcFilename, String targetFile, ConversionProperties properties) {
		this(ifcFilename, targetFile, properties, null, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of());
	}

	public ConversionRequest(String ifcFilename, ConversionProfile profile) {
		this(ifcFilename, null, Objects.requireNonNull(profile, "profile").toConversionProperties(), profile, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of());
	}

	public ConversionRequest(String ifcFilename, String targetFile, ConversionProfile profile) {
		this(ifcFilename, targetFile, Objects.requireNonNull(profile, "profile").toConversionProperties(), profile, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of());
	}

	private ConversionRequest(String ifcFilename, String targetFile, ConversionProperties properties,
			ConversionProfile profile, List<ValidationShapePack> validationShapePacks,
			ClassificationResolver classificationResolver, String modelScope, Set<String> selectedTypes,
			Set<String> selectedPropertySets, Map<String, String> propertyReplacements) {
		this.ifcFilename = requireText(ifcFilename, "ifcFilename");
		this.targetFile = targetFile;
		this.properties = Objects.requireNonNull(properties, "properties");
		this.profile = profile;
		this.validationShapePacks = List.copyOf(validationShapePacks);
		this.classificationResolver = Objects.requireNonNull(classificationResolver, "classificationResolver");
		this.modelScope = modelScope;
		this.selectedTypes = Set.copyOf(selectedTypes);
		this.selectedPropertySets = Set.copyOf(selectedPropertySets);
		this.propertyReplacements = Map.copyOf(propertyReplacements);
	}

	/** Returns a request that validates the converted dataset without modifying it. */
	public ConversionRequest withValidation(ValidationShapePack... shapePacks) {
		Objects.requireNonNull(shapePacks, "shapePacks");
		return copy(List.of(shapePacks), classificationResolver, modelScope, selectedTypes, selectedPropertySets, propertyReplacements);
	}

	/** Returns a request using all standard validation packs. */
	public ConversionRequest withStandardValidation() {
		return withValidation(ValidationShapePack.values());
	}

	public ConversionRequest withClassificationResolver(ClassificationResolver resolver) {
		return copy(validationShapePacks, resolver, modelScope, selectedTypes, selectedPropertySets, propertyReplacements);
	}

	/** Returns a request using a persistent identity namespace shared by all revisions of one model. */
	public ConversionRequest withModelScope(String modelScope) {
		return copy(validationShapePacks, classificationResolver, requireText(modelScope, "modelScope"), selectedTypes,
				selectedPropertySets, propertyReplacements);
	}

	public ConversionRequest withSelectedTypes(Set<String> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, Objects.requireNonNull(values), selectedPropertySets, propertyReplacements);
	}
	public ConversionRequest withSelectedPropertySets(Set<String> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, selectedTypes, Objects.requireNonNull(values), propertyReplacements);
	}
	public ConversionRequest withPropertyReplacements(Map<String, String> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, selectedTypes, selectedPropertySets, Objects.requireNonNull(values));
	}
	private ConversionRequest copy(List<ValidationShapePack> packs, ClassificationResolver resolver, String scope,
			Set<String> types, Set<String> psets, Map<String, String> replacements) {
		return new ConversionRequest(ifcFilename, targetFile, properties, profile, packs, resolver, scope, types, psets, replacements);
	}

	public String getIfcFilename() {
		return ifcFilename;
	}

	public Optional<String> getTargetFile() {
		return Optional.ofNullable(targetFile);
	}

	public ConversionProperties getProperties() {
		return properties;
	}

	public Optional<ConversionProfile> getProfile() {
		return Optional.ofNullable(profile);
	}

	public Optional<String> getModelScope() { return Optional.ofNullable(modelScope); }
	public Set<String> getSelectedTypes() { return selectedTypes; }
	public Set<String> getSelectedPropertySets() { return selectedPropertySets; }
	public Map<String, String> getPropertyReplacements() { return propertyReplacements; }

	public List<ValidationShapePack> getValidationShapePacks() { return validationShapePacks; }
	public ClassificationResolver getClassificationResolver() { return classificationResolver; }

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
