package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraphMapLink;
import org.apache.jena.rdf.model.Model;
import java.util.Objects;

/**
 * A zero-copy dataset view of a completed conversion.
 *
 * <p>The result and its converter/session have the same lifetime. Callers must
 * close the result before closing the converter. The default graph contains
 * general/topology data only; {@link #getModel()} is a lazy compatibility union
 * of the three data graphs and does not materialize duplicate statements.</p>
 */
public final class ConversionResult implements AutoCloseable {
	private final Dataset dataset;
	private final ConversionGraphNames graphNames;
	private final Model unionModel;
	private boolean closed;

	private ConversionResult(Dataset dataset, ConversionGraphNames graphNames) {
		this.dataset = Objects.requireNonNull(dataset, "dataset");
		this.graphNames = Objects.requireNonNull(graphNames, "graphNames");
		this.unionModel = org.apache.jena.rdf.model.ModelFactory.createUnion(dataset.getDefaultModel(),
				org.apache.jena.rdf.model.ModelFactory.createUnion(getProductModel(), getPropertyModel()));
	}

	static ConversionResult of(Model general, Model product, Model property, Model manifest, Model validation,
			ConversionGraphNames graphNames) {
		DatasetGraphMapLink linked = new DatasetGraphMapLink(general.getGraph());
		linked.addGraph(NodeFactory.createURI(graphNames.product()), product.getGraph());
		linked.addGraph(NodeFactory.createURI(graphNames.property()), property.getGraph());
		linked.addGraph(NodeFactory.createURI(graphNames.manifest()), manifest.getGraph());
		linked.addGraph(NodeFactory.createURI(graphNames.validation()), validation.getGraph());
		Dataset result = DatasetFactory.wrap(linked);
		return new ConversionResult(result, graphNames);
	}

	public Dataset getDataset() {
		requireOpen();
		return dataset;
	}

	/** Lazy union of the general, product, and property graphs. */
	public Model getModel() {
		requireOpen();
		return unionModel;
	}

	public Model getGeneralModel() { requireOpen(); return dataset.getDefaultModel(); }
	public ConversionGraphNames getGraphNames() { return graphNames; }

	public Model getProductModel() {
		requireOpen(); return dataset.getNamedModel(graphNames.product());
	}

	public Model getPropertyModel() {
		requireOpen(); return dataset.getNamedModel(graphNames.property());
	}

	public Model getManifestModel() {
		requireOpen(); return dataset.getNamedModel(graphNames.manifest());
	}

	public Model getValidationModel() {
		requireOpen(); return dataset.getNamedModel(graphNames.validation());
	}

	@Override
	public void close() {
		if (!closed) { closed = true; dataset.close(); }
	}

	private void requireOpen() {
		if (closed) throw new IllegalStateException("ConversionResult is closed");
	}
}
