package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;

/** Executes the SHACL shape packs selected by a conversion profile. */
final class ValidationStage {

	record Result(String status, Model report, List<String> shapeResources) { }

	private ValidationStage() { }

	static Result validate(ConversionRequest request, Model... dataModels) {
		return validate(request, java.util.Set.of(), dataModels);
	}

	static Result validate(ConversionRequest request, java.util.Set<String> moduleResources, Model... dataModels) {
		List<String> resources = java.util.stream.Stream.concat(
				moduleResources.stream(),
				request.getValidationShapePacks().stream().map(ValidationShapePack::resource))
				.distinct().sorted().toList();
		return validateResources(resources, dataModels);
	}

	static Result validate(Optional<ConversionProfile> profile, Model... dataModels) {
		List<String> resources = profile.stream().flatMap(value -> value.modules().stream())
				.flatMap(module -> module.shapeResources().stream()).distinct().sorted().toList();
		return validateResources(resources, dataModels);
	}

	private static Result validateResources(List<String> resources, Model... dataModels) {
		if (resources.isEmpty()) {
			return new Result("not-run", ModelFactory.createDefaultModel(), resources);
		}

		Model shapesModel = ModelFactory.createDefaultModel();
		for (String resource : resources) {
			try (InputStream input = ValidationStage.class.getClassLoader().getResourceAsStream(resource)) {
				if (input == null) throw new IllegalStateException("SHACL shape resource not found: " + resource);
				RDFDataMgr.read(shapesModel, input, Lang.TTL);
			} catch (IOException e) {
				throw new IllegalStateException("Could not close SHACL shape resource: " + resource, e);
			}
		}

		Model data = ModelFactory.createDefaultModel();
		for (Model dataModel : dataModels) data.add(dataModel);
		var report = ShaclValidator.get().validate(Shapes.parse(shapesModel.getGraph()), data.getGraph());
		return new Result(report.conforms() ? "conforms" : "violations", report.getModel(), resources);
	}
}
