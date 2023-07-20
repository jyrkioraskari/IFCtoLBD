package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example2 {

	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", false, 1);
			Model m = c.convert(ifc_file.getAbsolutePath());
			
			m.listSubjects().forEach(s->System.out.println(s));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
