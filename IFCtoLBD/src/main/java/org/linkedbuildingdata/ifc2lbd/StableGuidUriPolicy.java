package org.linkedbuildingdata.ifc2lbd;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

import com.openifctools.guidcompressor.GuidCompressor;

/** Canonical identity based only on model ID and IFC GUID. */
public final class StableGuidUriPolicy implements UriPolicy {
	private final String modelId;
	public StableGuidUriPolicy(String modelId) {
		if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("modelId must not be blank");
		this.modelId = modelId;
	}
	public String modelId() { return modelId; }
	@Override public String id() { return "stable-guid-v1"; }
	@Override public String configurationId() { return id() + "@" + modelId; }
	@Override public Resource createResource(Resource source, Model output, String productType, IfcOWL ifcOWL,
			String baseUri, boolean exportIfcOWL) {
		String guid = IfcOWLUtils.getGUID(source, ifcOWL);
		if (guid == null) return LegacyUriPolicy.INSTANCE.createResource(source, output, productType, ifcOWL,
				baseUri, exportIfcOWL);
		String base = Objects.requireNonNull(baseUri, "baseUri").replaceAll("[/#]+$", "");
		String uri = base + "/model/" + encode(modelId) + "/element/"
				+ encode(GuidCompressor.uncompressGuidString(guid));
		Resource resource = output.createResource(uri);
		if (exportIfcOWL) resource.addProperty(OWL.sameAs, source);
		return resource;
	}
	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
