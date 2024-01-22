package examples;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.ConversionProperties;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example8 {
	
	// Search by a bounding box
	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());
			ConversionProperties props = new ConversionProperties();
			props.setHasGeometry(true);
			try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://lbd.org/", false, 1);){
                converter.convert(ifc_file.getAbsolutePath(),props);            
				double geom_min_x= converter.getGeometryMinX();
				double geom_min_y= converter.getGeometryMinY();
				double geom_min_z= converter.getGeometryMinZ();
				double geom_max_x= converter.getGeometryMaxX();
				double geom_max_y= converter.getGeometryMaxY();
				double geom_max_z= converter.getGeometryMaxZ();
				
				System.out.println(converter.getGeometryMaxX());

				List<Resource> matching_elements = converter.getElementByGeometry(geom_min_x,geom_min_y, geom_min_z, geom_max_x, geom_max_y, geom_max_z);
				for(Resource element:matching_elements)
					System.out.println(element);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}