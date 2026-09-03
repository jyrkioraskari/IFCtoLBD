package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.rdf.model.RDFNode;

/** Common representation of an extracted or resolved property value. */
public record NormalizedValue(RDFNode value, String unitUri, String sourcePropertyUri,
		String mappingMethod, double confidence) {
	public NormalizedValue {
		if (value == null) throw new IllegalArgumentException("value must not be null");
		if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be between 0 and 1");
	}
}
