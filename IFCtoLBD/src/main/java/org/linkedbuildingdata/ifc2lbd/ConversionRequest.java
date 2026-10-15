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
	private final List<EtimPropertyMapping> etimPropertyMappings;
	private final boolean suggestEtimClassifications;
	private final List<EtimClassificationAcceptance> acceptedEtimClassifications;

	public ConversionRequest(String ifcFilename, ConversionProperties properties) {
		this(ifcFilename, null, properties, null, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of(), List.of(), false, List.of());
	}

	public ConversionRequest(String ifcFilename, String targetFile, ConversionProperties properties) {
		this(ifcFilename, targetFile, properties, null, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of(), List.of(), false, List.of());
	}

	public ConversionRequest(String ifcFilename, ConversionProfile profile) {
		this(ifcFilename, null, Objects.requireNonNull(profile, "profile").toConversionProperties(), profile, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of(), List.of(), false, List.of());
	}

	public ConversionRequest(String ifcFilename, String targetFile, ConversionProfile profile) {
		this(ifcFilename, targetFile, Objects.requireNonNull(profile, "profile").toConversionProperties(), profile, List.of(), ClassificationResolver.none(), null, Set.of(), Set.of(), Map.of(), List.of(), false, List.of());
	}

	private ConversionRequest(String ifcFilename, String targetFile, ConversionProperties properties,
			ConversionProfile profile, List<ValidationShapePack> validationShapePacks,
			ClassificationResolver classificationResolver, String modelScope, Set<String> selectedTypes,
			Set<String> selectedPropertySets, Map<String, String> propertyReplacements,
			List<EtimPropertyMapping> etimPropertyMappings, boolean suggestEtimClassifications,
			List<EtimClassificationAcceptance> acceptedEtimClassifications) {
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
		this.etimPropertyMappings = List.copyOf(etimPropertyMappings);
		this.suggestEtimClassifications = suggestEtimClassifications;
		this.acceptedEtimClassifications = List.copyOf(acceptedEtimClassifications);
	}

	/** Returns a request that validates the converted dataset without modifying it. */
	public ConversionRequest withValidation(ValidationShapePack... shapePacks) {
		Objects.requireNonNull(shapePacks, "shapePacks");
		return copy(List.of(shapePacks), classificationResolver, modelScope, selectedTypes, selectedPropertySets, propertyReplacements, etimPropertyMappings, suggestEtimClassifications, acceptedEtimClassifications);
	}

	/** Returns a request using all standard validation packs. */
	public ConversionRequest withStandardValidation() {
		return withValidation(ValidationShapePack.values());
	}

	public ConversionRequest withClassificationResolver(ClassificationResolver resolver) {
		Objects.requireNonNull(resolver, "resolver");
		if (resolver.allowsNetworkAccess())
			throw new IllegalArgumentException("Conversion classification resolvers must be offline; "
					+ "refresh a VersionedClassificationResolverCache before conversion");
		return copy(validationShapePacks, resolver, modelScope, selectedTypes, selectedPropertySets, propertyReplacements, etimPropertyMappings, suggestEtimClassifications, acceptedEtimClassifications);
	}

	/** Returns a request using a persistent identity namespace shared by all revisions of one model. */
	public ConversionRequest withModelScope(String modelScope) {
		return copy(validationShapePacks, classificationResolver, requireText(modelScope, "modelScope"), selectedTypes,
				selectedPropertySets, propertyReplacements, etimPropertyMappings, suggestEtimClassifications, acceptedEtimClassifications);
	}

	public ConversionRequest withSelectedTypes(Set<String> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, Objects.requireNonNull(values), selectedPropertySets, propertyReplacements, etimPropertyMappings, suggestEtimClassifications, acceptedEtimClassifications);
	}
	public ConversionRequest withSelectedPropertySets(Set<String> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, selectedTypes, Objects.requireNonNull(values), propertyReplacements, etimPropertyMappings, suggestEtimClassifications, acceptedEtimClassifications);
	}
	public ConversionRequest withPropertyReplacements(Map<String, String> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, selectedTypes, selectedPropertySets, Objects.requireNonNull(values), etimPropertyMappings, suggestEtimClassifications, acceptedEtimClassifications);
	}
	/** Returns a request with reviewed, context-specific IFC-to-ETIM property mappings. */
	public ConversionRequest withEtimPropertyMappings(List<EtimPropertyMapping> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, selectedTypes, selectedPropertySets,
				propertyReplacements, Objects.requireNonNull(values), suggestEtimClassifications, acceptedEtimClassifications);
	}
	/** Enables deterministic ETIM candidate discovery for elements without a supplied ETIM class. */
	public ConversionRequest withEtimClassificationSuggestions() {
		return copy(validationShapePacks, classificationResolver, modelScope, selectedTypes, selectedPropertySets,
				propertyReplacements, etimPropertyMappings, true, acceptedEtimClassifications);
	}
	/** Enables suggestions and marks the listed candidate decisions as reviewed and accepted. */
	public ConversionRequest withAcceptedEtimClassifications(List<EtimClassificationAcceptance> values) {
		return copy(validationShapePacks, classificationResolver, modelScope, selectedTypes, selectedPropertySets,
				propertyReplacements, etimPropertyMappings, true, Objects.requireNonNull(values));
	}
	private ConversionRequest copy(List<ValidationShapePack> packs, ClassificationResolver resolver, String scope,
			Set<String> types, Set<String> psets, Map<String, String> replacements,
			List<EtimPropertyMapping> etimMappings, boolean suggestions,
			List<EtimClassificationAcceptance> acceptances) {
		return new ConversionRequest(ifcFilename, targetFile, properties, profile, packs, resolver, scope, types, psets,
				replacements, etimMappings, suggestions, acceptances);
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
	public List<EtimPropertyMapping> getEtimPropertyMappings() { return etimPropertyMappings; }
	public boolean hasEtimClassificationSuggestions() { return suggestEtimClassifications; }
	public List<EtimClassificationAcceptance> getAcceptedEtimClassifications() {
		return acceptedEtimClassifications;
	}

	public List<ValidationShapePack> getValidationShapePacks() { return validationShapePacks; }
	public ClassificationResolver getClassificationResolver() { return classificationResolver; }

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
