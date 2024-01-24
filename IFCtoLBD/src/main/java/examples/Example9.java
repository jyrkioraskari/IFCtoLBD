package examples;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.jena.rdf.model.RDFNode;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.InvRDFStep;
import org.linkedbuildingdata.ifc2lbd.core.utils.rdfpath.RDFStep;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

public class Example9 {
	static final private int props_level = 1;
	static final boolean hasPropertiesBlankNodes = false;
	static final boolean hasGeometry = false;
	static final boolean exportIfcOWL = false;
	static final boolean hasPerformanceBoost = false;

	static final boolean hasBuildingElements=true;
	static final boolean hasBuildingProperties=true;
	static final boolean hasBoundingBoxWKT=false;
	static final boolean hasUnits=false;
	
	
	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", hasPropertiesBlankNodes,
					props_level);) {
				converter.convert_read_in_phase(ifc_file.getAbsolutePath(), null, hasGeometry, hasPerformanceBoost,
						exportIfcOWL,hasBuildingElements,hasBuildingProperties,hasBoundingBoxWKT,hasUnits);
				
				// List ifcOWL IFCSITEs (IFC 2x3)
				List<RDFNode> sites = IfcOWLUtils.listSites(converter.ifcOWL, converter.ifcowl_model);
				System.out.println(sites);
				IfcOWL ifcOWL=converter.ifcOWL;
				 RDFStep[] steps_2x3 = new RDFStep[]{ new InvRDFStep(ifcOWL.getRelatingObject_IfcRelDecomposes()),
							new RDFStep(ifcOWL.getRelatedObjects_IfcRelDecomposes()) };
		        
				for(RDFNode site:sites)
				{
				  List<RDFNode> buildings = RDFUtils.pathQuery(site.asResource(), steps_2x3);
				  System.out.println(buildings);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}