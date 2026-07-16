package examples;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.jena.rdf.model.Model;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Example2 {

    private static final String IFC_FILE_NAME = "Duplex_A.ifc";
    private static final String BASE_URI = "https://example.com/";

    public static void main(String[] args) {
        try {
            File ifcFile = getIfcFile(IFC_FILE_NAME);
            convertIfcFile(ifcFile);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static File getIfcFile(String fileName) throws URISyntaxException {
        URL ifcFileUrl = ClassLoader.getSystemResource(fileName);
        return new File(ifcFileUrl.toURI());
    }

    private static void convertIfcFile(File ifcFile) {
        try (IFCtoLBDConverter converter = new IFCtoLBDConverter(BASE_URI, false, 1)) {
            Model model = converter.convert(ifcFile.getAbsolutePath());
            model.listSubjects().forEachRemaining(System.out::println);
        } catch (Exception e) {
            System.err.println("Conversion Error: " + e.getMessage());
        }
    }
}