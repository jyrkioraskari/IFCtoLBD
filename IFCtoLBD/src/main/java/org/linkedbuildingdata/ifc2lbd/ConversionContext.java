package org.linkedbuildingdata.ifc2lbd;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Mutable state passed through the explicitly selected conversion modules. */
public final class ConversionContext {
	private final ConversionRequest request;
	private final ConversionProperties properties;
	private final Set<String> validationResources = new LinkedHashSet<>();
	private boolean productOntologies;
	private boolean propertySetOntologies;
	private Model ifcModel;
	private IfcOWL ifcOntology;
	private Model generalModel;
	private Model productModel;
	private Model propertyModel;
	private Map<Resource, Resource> resources;

	ConversionContext(ConversionRequest request) {
		this.request = Objects.requireNonNull(request, "request");
		this.properties = request.getProperties();
	}

	public ConversionRequest request() { return request; }
	public ConversionProperties properties() { return properties; }
	public void requireProductOntologies() { productOntologies = true; }
	public void requirePropertySetOntologies() { propertySetOntologies = true; }
	boolean usesProductOntologies() { return productOntologies; }
	boolean usesPropertySetOntologies() { return propertySetOntologies; }
	public void addValidationResource(String resource) { validationResources.add(resource); }
	Set<String> validationResources() { return Set.copyOf(validationResources); }

	void attachMappedData(Model ifcModel, IfcOWL ifcOntology, Model generalModel, Model productModel,
			Model propertyModel, Map<Resource, Resource> resources) {
		this.ifcModel = ifcModel;
		this.ifcOntology = ifcOntology;
		this.generalModel = generalModel;
		this.productModel = productModel;
		this.propertyModel = propertyModel;
		this.resources = resources;
	}

	public Model ifcModel() { return requireMapped(ifcModel); }
	public IfcOWL ifcOntology() { return requireMapped(ifcOntology); }
	public Model generalModel() { return requireMapped(generalModel); }
	public Model productModel() { return requireMapped(productModel); }
	public Model propertyModel() { return requireMapped(propertyModel); }
	public Map<Resource, Resource> mappedResources() { return requireMapped(resources); }
	public ClassificationResolver classificationResolver() { return request.getClassificationResolver(); }

	private static <T> T requireMapped(T value) {
		if (value == null) throw new IllegalStateException("Mapped data is not available during configuration");
		return value;
	}
}
