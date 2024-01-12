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
        URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            File ifcFile = new File(ifcFileUrl.toURI());

            IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);
            Model model = converter.convert(ifcFile.getAbsolutePath());
            
            Query query = QueryFactory.create("""
                    PREFIX bot: <https://w3id.org/bot#>\r
                    \r
                    SELECT ?element WHERE {\r
                      ?element a bot:Element .\r
                    }\s""");
            try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model)) {
                ResultSet resultSet = queryExecution.execSelect();
                resultSet.forEachRemaining(qs -> {
                    System.out.println("BOT element: "+qs.get("element").asResource().getLocalName());
                    
                });
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

    }
}
