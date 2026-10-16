package org.linkedbuildingdata.ifc2lbd;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Mutable state passed through the explicitly selected conversion modules. */
public final class ConversionContext {
	private final ConversionRequest request;
	private final ConversionProperties properties;
	private final Set<String> validationResources = new LinkedHashSet<>();
	private final Model diagnosticsModel = ModelFactory.createDefaultModel();
	private boolean productOntologies;
	private Model ifcModel;
	private IfcOWL ifcOntology;
	private Model generalModel;
	private Model productModel;
	private Model propertyModel;
	private Map<Resource, Resource> resources;
	private BiFunction<Resource, String, Resource> resourceMapper;

	ConversionContext(ConversionRequest request) {
		this.request = Objects.requireNonNull(request, "request");
		this.properties = request.getProperties();
	}

	public ConversionRequest request() { return request; }
	public ConversionProperties properties() { return properties; }
	public void requireProductOntologies() { productOntologies = true; }
	boolean usesProductOntologies() { return productOntologies; }
	public void addValidationResource(String resource) { validationResources.add(resource); }
	Set<String> validationResources() { return Set.copyOf(validationResources); }

	void attachMappedData(Model ifcModel, IfcOWL ifcOntology, Model generalModel, Model productModel,
			Model propertyModel, Map<Resource, Resource> resources,
			BiFunction<Resource, String, Resource> resourceMapper) {
		this.ifcModel = ifcModel;
		this.ifcOntology = ifcOntology;
		this.generalModel = generalModel;
		this.productModel = productModel;
		this.propertyModel = propertyModel;
		this.resources = resources;
		this.resourceMapper = resourceMapper;
	}

	public Model ifcModel() { return requireMapped(ifcModel); }
	public IfcOWL ifcOntology() { return requireMapped(ifcOntology); }
	public Model generalModel() { return requireMapped(generalModel); }
	public Model productModel() { return requireMapped(productModel); }
	public Model propertyModel() { return requireMapped(propertyModel); }
	public Map<Resource, Resource> mappedResources() { return requireMapped(resources); }
	/** Creates an output resource through the active conversion identity policy. */
	public Resource mapResource(Resource source, String productType) {
		return requireMapped(resourceMapper).apply(source, productType);
	}
	/** Report model merged into the structured result's validation graph. */
	public Model diagnosticsModel() { return diagnosticsModel; }
	public ClassificationResolver classificationResolver() { return request.getClassificationResolver(); }

	private static <T> T requireMapped(T value) {
		if (value == null) throw new IllegalStateException("Mapped data is not available during configuration");
		return value;
	}
}
