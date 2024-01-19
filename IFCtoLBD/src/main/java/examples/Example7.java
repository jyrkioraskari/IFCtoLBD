package examples;

import java.io.File;
import java.net.URL;

import org.linkedbuildingdata.ifc2lbd.ConversionProperties;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example7 {
	
	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());
			ConversionProperties props = new ConversionProperties();
			props.setHasGeometry(true);
			try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://lbd.org/", false, 1);){
                converter.convert(ifc_file.getAbsolutePath(),props);            
				System.out.println(converter.getObjJSON());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}