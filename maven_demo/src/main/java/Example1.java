
import java.io.File;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example1 {

	/* 
	 * In Eclipse:
	 * maven install
	 * and then Maven Update Project
	 */
	
	public static void main(String[] args) {
		URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifcFile = new File(ifcFileUrl.toURI());
			System.out.println("Demo");
			IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);
			Model model = converter.convert(ifcFile.getAbsolutePath());
			model.write(System.out, "TTL");
			converter.close();

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error: " + e.getMessage());
		}
	}
}
