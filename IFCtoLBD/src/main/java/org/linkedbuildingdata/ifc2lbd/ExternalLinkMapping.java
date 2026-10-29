package org.linkedbuildingdata.ifc2lbd;

/** One externally supplied link. Field names are also the JSON interchange contract. */
public record ExternalLinkMapping(String globalId, String externalResourceIri, String predicateIri,
		String source, String method, Double confidence, String validFrom, String validTo, String recordedAt) {
	/** Source-compatible constructor for the original mapping contract. */
	public ExternalLinkMapping(String globalId, String externalResourceIri, String predicateIri,
			String source, String method, Double confidence) {
		this(globalId, externalResourceIri, predicateIri, source, method, confidence, null, null, null);
	}
}
