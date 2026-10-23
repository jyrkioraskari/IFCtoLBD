package org.linkedbuildingdata.ifc2lbd;

/** One externally supplied link. Field names are also the JSON interchange contract. */
public record ExternalLinkMapping(String globalId, String externalResourceIri, String predicateIri,
		String source, String method, Double confidence) { }
