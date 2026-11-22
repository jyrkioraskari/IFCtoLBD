package org.linkedbuildingdata.ifc2lbd;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

/** Optional geometric screening; candidates never become verified BOT interfaces. */
public final class GeometryInterfaceCandidateDiscovery {
	public record Result(Model graph, List<ReviewedInterfaceImporter.Diagnostic> diagnostics)
			implements AutoCloseable {
		@Override public void close() { graph.close(); }
	}

	public Result discover(List<SurfaceClearance.Element> first, GeometryResult firstGeometry,
			List<SurfaceClearance.Element> second, GeometryResult secondGeometry,
			SurfaceClearance.Alignment alignment, double toleranceMetres) {
		Model graph = ModelFactory.createDefaultModel();
		IFCtoLBDMapping.addNameSpace(graph);
		List<ReviewedInterfaceImporter.Diagnostic> diagnostics = new ArrayList<>();
		if (alignment == null) {
			diagnostics.add(new ReviewedInterfaceImporter.Diagnostic("MISSING_ALIGNMENT",
					"Supply a transform between the two model coordinate systems."));
			return new Result(graph, List.copyOf(diagnostics));
		}
		SurfaceClearance clearance = new SurfaceClearance();
		for (SurfaceClearance.Element a : first) for (SurfaceClearance.Element b : second) {
			SurfaceClearance.Result measured = clearance.measure(a, firstGeometry, b, secondGeometry,
					alignment, toleranceMetres);
			if (measured.status() == SurfaceClearance.Status.UNAVAILABLE
					|| measured.status() == SurfaceClearance.Status.INDETERMINATE) {
				diagnostics.add(new ReviewedInterfaceImporter.Diagnostic("UNUSABLE_GEOMETRY",
						"Skipped " + a.iri() + " / " + b.iri() + ": " + measured.limitation()));
				continue;
			}
			if (measured.status() == SurfaceClearance.Status.SEPARATED) continue;
			String ns = IFCtoLBDMapping.ns;
			Resource candidate = graph.createResource("urn:ifctolbd:interface-candidate:"
					+ java.util.UUID.nameUUIDFromBytes((a.modelScope() + "|" + a.iri() + "|"
							+ b.modelScope() + "|" + b.iri() + "|" + a.revision() + "|"
							+ b.revision()).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
					.addProperty(RDF.type, graph.createResource(ns + "GeometryInterfaceCandidate"))
					.addProperty(graph.createProperty(ns + "candidateFirstElement"), graph.createResource(a.iri()))
					.addProperty(graph.createProperty(ns + "candidateSecondElement"), graph.createResource(b.iri()))
					.addLiteral(graph.createProperty(ns + "candidateStatus"), measured.status().name().toLowerCase())
					.addLiteral(graph.createProperty(ns + "candidateToleranceMetres"), toleranceMetres);
			if (measured.distanceMetres() != null)
				candidate.addLiteral(graph.createProperty(ns + "candidateDistanceMetres"),
						measured.distanceMetres());
		}
		return new Result(graph, List.copyOf(diagnostics));
	}
}
