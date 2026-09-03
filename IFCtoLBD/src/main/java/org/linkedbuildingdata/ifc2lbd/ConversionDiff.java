package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;

/** Independently owned RDF description of the changes between two revisions. */
public final class ConversionDiff implements AutoCloseable {
	public static final String CHANGE_GRAPH = "urn:ifctolbd:graph:changes";
	private final Dataset dataset;
	private final long addedCount;
	private final long removedCount;

	ConversionDiff(Dataset dataset, long addedCount, long removedCount) {
		this.dataset = dataset;
		this.addedCount = addedCount;
		this.removedCount = removedCount;
	}

	public Dataset getDataset() { return dataset; }
	public Model getChangeModel() { return dataset.getNamedModel(CHANGE_GRAPH); }
	public long getAddedCount() { return addedCount; }
	public long getRemovedCount() { return removedCount; }
	public boolean hasChanges() { return addedCount != 0 || removedCount != 0; }

	@Override public void close() { dataset.close(); }
}
