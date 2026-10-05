package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.core.utils.LBD_RDF_Utils;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** The historical type-and-GUID URI layout. */
public final class LegacyUriPolicy implements UriPolicy {
	public static final LegacyUriPolicy INSTANCE = new LegacyUriPolicy();
	private LegacyUriPolicy() { }
	@Override public String id() { return "legacy-v1"; }
	@Override public Resource createResource(Resource source, Model output, String productType, IfcOWL ifcOWL,
			String baseUri, boolean exportIfcOWL) {
		return LBD_RDF_Utils.createformattedURIRecource(source, output, productType, ifcOWL, baseUri, exportIfcOWL);
	}
}
