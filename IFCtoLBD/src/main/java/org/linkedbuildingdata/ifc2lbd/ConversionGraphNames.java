package org.linkedbuildingdata.ifc2lbd;

import java.util.List;

/** Dataset graph IRIs scoped to one immutable conversion revision. */
public record ConversionGraphNames(String product, String property, String manifest, String validation) {
	public ConversionGraphNames {
		product = requireIri(product, "product");
		property = requireIri(property, "property");
		manifest = requireIri(manifest, "manifest");
		validation = requireIri(validation, "validation");
	}

	public static ConversionGraphNames forConversion(String cacheKey) {
		if (cacheKey == null || !cacheKey.matches("[a-f0-9]{64}"))
			throw new IllegalArgumentException("cacheKey must be a lowercase SHA-256 value");
		String base = "urn:ifctolbd:conversion:" + cacheKey + ":graph:";
		return new ConversionGraphNames(base + "product", base + "property", base + "manifest", base + "validation");
	}

	public List<String> all() { return List.of(product, property, manifest, validation); }
	public String externalLinks() { return manifest.replaceFirst("manifest$", "external-links"); }
	public String externalLinkReport() { return manifest.replaceFirst("manifest$", "external-link-report"); }
	public String identityReport() { return manifest.replaceFirst("manifest$", "identity-report"); }

	private static String requireIri(String value, String role) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " graph IRI must not be blank");
		return value;
	}
}
