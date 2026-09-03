package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/** An independently owned snapshot of a completed conversion. */
public final class ConversionResult implements AutoCloseable {

	public static final String PRODUCT_GRAPH = "urn:ifctolbd:graph:product";
	public static final String PROPERTY_GRAPH = "urn:ifctolbd:graph:property";
	public static final String MANIFEST_GRAPH = "urn:ifctolbd:graph:manifest";
	public static final String VALIDATION_GRAPH = "urn:ifctolbd:graph:validation";

	private final Dataset dataset;

	private ConversionResult(Dataset dataset) {
		this.dataset = dataset;
	}

	static ConversionResult copyOf(Model general, Model product, Model property) {
		return copyOf(general, product, property, ModelFactory.createDefaultModel(), ModelFactory.createDefaultModel());
	}

	static ConversionResult copyOf(Model general, Model product, Model property, Model manifest, Model validation) {
		Dataset result = DatasetFactory.createTxnMem();
		result.setDefaultModel(copy(general));
		result.addNamedModel(PRODUCT_GRAPH, copy(product));
		result.addNamedModel(PROPERTY_GRAPH, copy(property));
		result.addNamedModel(MANIFEST_GRAPH, copy(manifest));
		result.addNamedModel(VALIDATION_GRAPH, copy(validation));
		return new ConversionResult(result);
	}

	private static Model copy(Model source) {
		return ModelFactory.createDefaultModel().add(source).setNsPrefixes(source.getNsPrefixMap());
	}

	public Dataset getDataset() {
		return dataset;
	}

	public Model getModel() {
		return dataset.getDefaultModel();
	}

	public Model getProductModel() {
		return dataset.getNamedModel(PRODUCT_GRAPH);
	}

	public Model getPropertyModel() {
		return dataset.getNamedModel(PROPERTY_GRAPH);
	}

	public Model getManifestModel() {
		return dataset.getNamedModel(MANIFEST_GRAPH);
	}

	public Model getValidationModel() {
		return dataset.getNamedModel(VALIDATION_GRAPH);
	}

	@Override
	public void close() {
		dataset.close();
	}
}
