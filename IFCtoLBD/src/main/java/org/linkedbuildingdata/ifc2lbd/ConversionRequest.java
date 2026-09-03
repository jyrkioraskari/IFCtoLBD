package org.linkedbuildingdata.ifc2lbd;

import java.util.Objects;
import java.util.List;
import java.util.Optional;

/** Input values for a conversion. */
public final class ConversionRequest {

	private final String ifcFilename;
	private final String targetFile;
	private final ConversionProperties properties;
	private final ConversionProfile profile;
	private final List<ValidationShapePack> validationShapePacks;
	private final ClassificationResolver classificationResolver;

	public ConversionRequest(String ifcFilename, ConversionProperties properties) {
		this(ifcFilename, null, properties, null, List.of(), ClassificationResolver.none());
	}

	public ConversionRequest(String ifcFilename, String targetFile, ConversionProperties properties) {
		this(ifcFilename, targetFile, properties, null, List.of(), ClassificationResolver.none());
	}

	public ConversionRequest(String ifcFilename, ConversionProfile profile) {
		this(ifcFilename, null, Objects.requireNonNull(profile, "profile").toConversionProperties(), profile, List.of(), ClassificationResolver.none());
	}

	public ConversionRequest(String ifcFilename, String targetFile, ConversionProfile profile) {
		this(ifcFilename, targetFile, Objects.requireNonNull(profile, "profile").toConversionProperties(), profile, List.of(), ClassificationResolver.none());
	}

	private ConversionRequest(String ifcFilename, String targetFile, ConversionProperties properties,
			ConversionProfile profile, List<ValidationShapePack> validationShapePacks,
			ClassificationResolver classificationResolver) {
		this.ifcFilename = requireText(ifcFilename, "ifcFilename");
		this.targetFile = targetFile;
		this.properties = Objects.requireNonNull(properties, "properties");
		this.profile = profile;
		this.validationShapePacks = List.copyOf(validationShapePacks);
		this.classificationResolver = Objects.requireNonNull(classificationResolver, "classificationResolver");
	}

	/** Returns a request that validates the converted dataset without modifying it. */
	public ConversionRequest withValidation(ValidationShapePack... shapePacks) {
		Objects.requireNonNull(shapePacks, "shapePacks");
		return new ConversionRequest(ifcFilename, targetFile, properties, profile, List.of(shapePacks), classificationResolver);
	}

	/** Returns a request using all standard validation packs. */
	public ConversionRequest withStandardValidation() {
		return withValidation(ValidationShapePack.values());
	}

	public ConversionRequest withClassificationResolver(ClassificationResolver resolver) {
		return new ConversionRequest(ifcFilename, targetFile, properties, profile, validationShapePacks, resolver);
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

	public List<ValidationShapePack> getValidationShapePacks() { return validationShapePacks; }
	public ClassificationResolver getClassificationResolver() { return classificationResolver; }

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
