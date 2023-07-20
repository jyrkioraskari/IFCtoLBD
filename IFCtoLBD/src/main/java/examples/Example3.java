package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example3 {

	public static void main(String[] args) {
		URL ifc_file_url = ClassLoader.getSystemResource("Duplex_A.ifc");
		try {
			File ifc_file = new File(ifc_file_url.toURI());

			IFCtoLBDConverter c = new IFCtoLBDConverter("https://example.com/", false, 1);
			Model m = c.convert(ifc_file.getAbsolutePath());
			
			Query query = QueryFactory.create("PREFIX bot: <https://w3id.org/bot#>\r\n"
					+ "\r\n"
					+ "SELECT ?e WHERE {\r\n"
					+ "  ?e a bot:Element .\r\n"
					+ "} ");
			QueryExecution queryExecution = QueryExecutionFactory.create(query, m);
			ResultSet rs = queryExecution.execSelect();
			rs.forEachRemaining(qs -> {
				System.out.println("BOT element: "+qs.get("e").asResource().getLocalName());
				
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
