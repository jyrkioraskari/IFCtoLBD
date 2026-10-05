package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashSet;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

@Tag("integration")
class IfcSpaceBoundaryIntegrationTest {
	@Test
	void explicitBoundariesAreTheDefaultAndDoNotRequireGeometry(@TempDir Path temporaryDirectory) throws Exception {
		File ifcFile = new File(getClass().getResource("/Duplex_A.ifc").toURI());
		ConversionProperties properties = new ConversionProperties();
		properties.setHasGeometry(false);
		properties.setGeometryInferredInterfaces(false);
		properties.setHasBuildingProperties(false);
		properties.setExportIfcOWL(false);
		properties.setStableIdentity(true);
		ConversionRequest request = new ConversionRequest(ifcFile.getAbsolutePath(),
				temporaryDirectory.resolve("duplex.ttl").toString(), properties)
				.withModelScope("duplex-boundary-test");

		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(request)) {
			var model = result.getModel();
			assertTrue(model.contains(null, RDF.type, BOT.bot_interface));
			assertTrue(model.contains(null, IFCtoLBDMapping.interfaceOrigin,
					IFCtoLBDMapping.ifcSpaceBoundaryOrigin));
			assertTrue(model.contains(null, IFCtoLBDMapping.physicalOrVirtualBoundary, "PHYSICAL"));
			assertTrue(model.contains(null, IFCtoLBDMapping.internalOrExternalBoundary, "EXTERNAL"));
			assertTrue(model.contains(null, IFCtoLBDMapping.connectionGeometry));
			Resource boundary = model.listResourcesWithProperty(IFCtoLBDMapping.ifcGlobalId,
					"36tIOu57f4IPSzVKH7isUP").nextResource();
			assertTrue(boundary.getURI().contains("/model/duplex-boundary-test/element/"));
		}
		assertTrue(RDFDataMgr.loadModel(temporaryDirectory.resolve("duplex.ttl").toString())
				.contains(null, IFCtoLBDMapping.interfaceOrigin, IFCtoLBDMapping.ifcSpaceBoundaryOrigin));
	}

	@Test
	void zonesPreserveOverlappingAssignsToGroupMembership(@TempDir Path temporaryDirectory) throws Exception {
		Path source = Path.of(getClass().getResource("/Duplex_A.ifc").toURI());
		String step = Files.readString(source);
		int dataEnd = step.lastIndexOf("ENDSEC;");
		String zones = """
				#60000= IFCZONE('0BTBFw6f90Nfh9rP1dlZa1',#47,'Thermal Zone',$,$);
				#60001= IFCZONE('0BTBFw6f90Nfh9rP1dlZa2',#47,'Apartment',$,$);
				#60002= IFCRELASSIGNSTOGROUP('0BTBFw6f90Nfh9rP1dlZb1',#47,$,$,(#196),$,#60000);
				#60003= IFCRELASSIGNSTOGROUP('0BTBFw6f90Nfh9rP1dlZb2',#47,$,$,(#196,#629),$,#60001);
				""";
		Path input = temporaryDirectory.resolve("duplex-zones.ifc");
		Files.writeString(input, step.substring(0, dataEnd) + zones + step.substring(dataEnd));
		ConversionProperties properties = new ConversionProperties();
		properties.setIfcSpaceBoundaries(false);
		properties.setIfcZones(true);
		properties.setHasGeometry(false);
		properties.setHasBuildingProperties(false);
		properties.setExportIfcOWL(false);

		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(),
						temporaryDirectory.resolve("zones.ttl").toString(), properties))) {
			var model = result.getModel();
			var zonesFound = model.listResourcesWithProperty(RDF.type, BOT.zone).toList();
			assertTrue(zonesFound.size() == 2);
			var firstMembers = new HashSet<>(model.listObjectsOfProperty(zonesFound.get(0), BOT.containsZone).toList());
			var secondMembers = new HashSet<>(model.listObjectsOfProperty(zonesFound.get(1), BOT.containsZone).toList());
			firstMembers.retainAll(secondMembers);
			assertFalse(firstMembers.isEmpty());
			assertTrue(model.listStatements(null, BOT.containsZone, (org.apache.jena.rdf.model.RDFNode) null)
					.toList().size() == 3);
		}
	}
}
