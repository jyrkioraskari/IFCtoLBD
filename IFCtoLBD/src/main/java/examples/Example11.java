package examples;

import java.io.File;

import java.net.URL;
import java.util.Optional;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.core.utils.RDFUtils;

class Site
{
	
}

public class Example11 {

	
	public static void main(String[] args) {
		URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifcFile = new File(ifcFileUrl.toURI());

			try (IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);) {
				Model model = converter.convert(ifcFile.getAbsolutePath());
				model.listSubjects().forEach(s -> {

					Optional<Resource> type = RDFUtils.getType(s);
					if(type.isPresent()&&type.get().toString().equals("https://w3id.org/bot#Site"))
					{
					  Site site = new Site();
					  
 					  System.out.println(site.getClass().getAnnotations());
					}

				});
			}

		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}

	}
}
