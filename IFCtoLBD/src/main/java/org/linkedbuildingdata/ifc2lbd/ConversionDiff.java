package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;

/** Independently owned RDF description of the changes between two revisions. */
public final class ConversionDiff implements AutoCloseable {
	public static final String CHANGE_GRAPH = "urn:ifctolbd:graph:changes";
	private final Dataset dataset;
	private final long addedCount;
	private final long removedCount;
	private final long retainedGlobalIdCount;
	private final long addedGlobalIdCount;
	private final long removedGlobalIdCount;
	private final long orphanedLinkCount;

	ConversionDiff(Dataset dataset, long addedCount, long removedCount) {
		this(dataset, addedCount, removedCount, 0, 0, 0, 0);
	}
	ConversionDiff(Dataset dataset, long addedCount, long removedCount, long retainedGlobalIdCount,
			long addedGlobalIdCount, long removedGlobalIdCount, long orphanedLinkCount) {
		this.dataset = dataset;
		this.addedCount = addedCount;
		this.removedCount = removedCount;
		this.retainedGlobalIdCount = retainedGlobalIdCount;
		this.addedGlobalIdCount = addedGlobalIdCount;
		this.removedGlobalIdCount = removedGlobalIdCount;
		this.orphanedLinkCount = orphanedLinkCount;
	}

	public Dataset getDataset() { return dataset; }
	public Model getChangeModel() { return dataset.getNamedModel(CHANGE_GRAPH); }
	public long getAddedCount() { return addedCount; }
	public long getRemovedCount() { return removedCount; }
	public boolean hasChanges() { return addedCount != 0 || removedCount != 0; }
	public long getRetainedGlobalIdCount() { return retainedGlobalIdCount; }
	public long getAddedGlobalIdCount() { return addedGlobalIdCount; }
	public long getRemovedGlobalIdCount() { return removedGlobalIdCount; }
	public long getOrphanedLinkCount() { return orphanedLinkCount; }

	@Override public void close() { dataset.close(); }
}
