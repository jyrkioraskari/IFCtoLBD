package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.InvRDFStep;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;

class RDFUtilsPerformanceTest {
	@Test
	void iterativePathTraversalPreservesBranchesAndDuplicates() {
		var model = ModelFactory.createDefaultModel();
		var first = model.createProperty("urn:first");
		var second = model.createProperty("urn:second");
		var start = model.createResource("urn:start");
		var left = model.createResource("urn:left");
		var right = model.createResource("urn:right");
		var result = model.createResource("urn:result");
		start.addProperty(first, left).addProperty(first, right);
		left.addProperty(second, result);
		right.addProperty(second, result);

		assertEquals(2, RDFUtils.pathQuery(start,
				new RDFStep[] { new RDFStep(first), new RDFStep(second) }).size());
		assertEquals(2, RDFUtils.pathQuery(result,
				new RDFStep[] { new InvRDFStep(second), new InvRDFStep(first) }).size());
	}
}
