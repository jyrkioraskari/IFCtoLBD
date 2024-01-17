package examples;

import java.io.File;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example1 {

    public static void main(String[] args) {
        URL ifcFileUrl = ClassLoader.getSystemResource("Duplex_A.ifc");
        try {
            File ifcFile = new File(ifcFileUrl.toURI());

            try(IFCtoLBDConverter converter = new IFCtoLBDConverter("https://example.com/", false, 1);){
                Model model = converter.convert(ifcFile.getAbsolutePath());
                model.write(System.out, "TTL");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}

