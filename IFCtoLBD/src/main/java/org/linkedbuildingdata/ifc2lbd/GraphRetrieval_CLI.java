package org.linkedbuildingdata.ifc2lbd;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;

import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Standalone adapter for deterministic retrieval from already converted RDF. */
@Command(name = "IFCtoLBD_Retrieve", mixinStandardHelpOptions = true,
		description = "Find or inspect entities in converted LBD RDF without executing caller-supplied SPARQL.")
public final class GraphRetrieval_CLI implements Callable<Integer> {
	@Option(names = "--data", required = true, description = "Converted RDF graph.") Path dataPath;
	@Option(names = "--external-links", description = "Optional separately managed external-link graph.") Path linksPath;
	@Option(names = "--external-link-report", description = "Optional external-link mapping report.") Path linkReportPath;
	@Option(names = "--provenance", description = "Optional provenance companion graph.") Path provenancePath;
	@Option(names = "--task", required = true, description = "find, inspect, or links") String task;
	@Option(names = "--identifier", description = "Entity IRI or IFC GlobalId.") String identifier;
	@Option(names = "--type", description = "Full type IRI or local type name.") String type;
	@Option(names = "--spatial-location", description = "Spatial resource IRI or IFC GlobalId.") String spatialLocation;
	@Option(names = "--offset", defaultValue = "0") int offset;
	@Option(names = "--limit", defaultValue = "100") int limit;

	@Override
	public Integer call() throws Exception {
		Model data = RDFDataMgr.loadModel(dataPath.toString());
		Model links = linksPath == null ? ModelFactory.createDefaultModel() : RDFDataMgr.loadModel(linksPath.toString());
		Model provenance = provenancePath == null ? ModelFactory.createDefaultModel() : RDFDataMgr.loadModel(provenancePath.toString());
		Model linkReport = linkReportPath == null ? ModelFactory.createDefaultModel() : RDFDataMgr.loadModel(linkReportPath.toString());
		try {
			GraphRetrievalService service = new GraphRetrievalService(data, links, provenance, linkReport);
			GraphRetrievalService.Result result = switch (task.toLowerCase()) {
			case "find" -> service.findElements(new GraphRetrievalService.ElementQuery(identifier, type, spatialLocation), offset, limit);
			case "inspect" -> service.inspectElement(identifier, limit);
			case "links" -> service.linkedRecords(identifier, offset, limit);
			default -> throw new IllegalArgumentException("task must be find, inspect, or links");
			};
			System.out.println(new ObjectMapper().writeValueAsString(result));
			return result.status() == GraphRetrievalService.Status.ERROR ? 2 : 0;
		} finally {
			linkReport.close();
			provenance.close();
			links.close();
			data.close();
		}
	}

	public static void main(String[] args) {
		System.exit(new CommandLine(new GraphRetrieval_CLI()).execute(args));
	}
}
