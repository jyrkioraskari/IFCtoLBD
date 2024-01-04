package org.linkedbuildingdata.ifc2lbd.core.utils.ontology;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.core.utils.FileUtils;

public class CreatePsetDescriptionOntologies {

    public static void readInOntologyTTL(Model model, String ontology_file) {

    	// JO 2024
        try (InputStream in = new FileInputStream(new File(ontology_file))) {
            model.read(in, null, "TTL");
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static String write(String ontology,String ontfile) {
        ontology = ontology.substring(0, ontology.lastIndexOf("."));
        StringBuilder sb = new StringBuilder();
        sb.append("abstract=Open BIM standards PSets - Property Set Extension of the IFC schema for bSDD buildingSMART Data Dictionary http://bsdd.buildingsmart.org/\r\n");
        sb.append("ontologyTitle=IFC4-PSD " + ontology + "\r\n");
        sb.append("ontologyPrefix=IFC4-PSD\r\n");
        sb.append("ontologyNamespaceURI= http://http://www.linkedbuildingdata.net/ifcOWL/IFC4-PSD/\r\n");
        sb.append("ontologyName=" + ontology + "\r\n");
        sb.append("thisVersionURI=http://http://www.linkedbuildingdata.net/ifcOWL/IFC4-PSD/+ontology+t\r\n");
        sb.append("latestVersionURI=http://http://www.linkedbuildingdata.net/ifcOWL/IFC4-PSD/+ontology+\r\n");
        sb.append("previousVersionURI=\r\n");
        sb.append("dateOfRelease=12.6.2019\r\n");
        sb.append("ontologyRevisionNumber=v1.0.0\r\n");
        sb.append("licenseURI=\r\n");
        sb.append("licenseName=Unknown\r\n");
        sb.append("licenseIconURL=\r\n");
        sb.append("citeAs=\r\n");
        sb.append("DOI=\r\n");
        sb.append("status=\r\n");
        sb.append("backwardsCompatibleWith=\r\n");
        sb.append("publisher=\r\n");
        sb.append("publisherURI=\r\n");
        sb.append("publisherInstitution= Linked Building Data (LBD) community\r\n");
        sb.append("publisherInstitutionURI=http://www.linkedbuildingdata.net/\r\n");
        sb.append("authors=Peter Willems\r\n");
        sb.append("authorsURI=http://www.linkedin.com/pub/peter-willems/11/498/274\r\n");
        sb.append("authorsInstitution=TNO\r\n");
        sb.append("authorsInstitutionURI=https://www.tno.nl/\r\n");
        sb.append("contributors=Peter Willems\r\n");
        sb.append("contributorsURI=http://www.linkedin.com/pub/peter-willems/11/498/274\r\n");
        sb.append("contributorsInstitution=TNO\r\n");
        sb.append("contributorsInstitutionURI=https://www.tno.nl/\r\n");
        sb.append("importedOntologyNames=\r\n");
        sb.append("importedOntologyURIs=\r\n");
        sb.append("extendedOntologyNames=\r\n");
        sb.append("extendedOntologyURIs=\r\n");
        sb.append("RDFXMLSerialization=ontology.xml\r\n");
        sb.append("TurtleSerialization=ontology.ttl\r\n");
        sb.append("N3Serialization=ontology.nt\r\n");

        File f = new File("C:\\temp\\IFC-PSD\\config." + ontology);
        
        // JO 2024
        try (FileOutputStream fo = new FileOutputStream(f)) {
        	fo.write(sb.toString().getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        CreatePsetDescriptionOntologies.run(f.getAbsolutePath(),ontfile,ontology);
        return f.getAbsolutePath();
    }

    public static void run(String config,String ontfile,String ontology) {
        System.out.println("java -jar C:\\temp\\IFC-PSD\\widoco-1.4.14-jar-with-dependencies.jar -confFile "+config+" -ontFile "+ontfile+" -rewriteAll -excludeIntroduction -includeAnnotationProperties -uniteSections -outFolder C:\\temp\\IFC-PSD\\out\\"+ontology);
    }

    public static void generate(@SuppressWarnings("unused") String[] args) {

        
        List<String> files = FileUtils.listFiles("C:\\temp\\IFC-PSD\\psets");
        for (String file : files) {
            Model ontology_model = ModelFactory.createDefaultModel();
            CreatePsetDescriptionOntologies.readInOntologyTTL(ontology_model, file);
            for (Statement s : ontology_model.listStatements().toSet()) {
                if (s.getPredicate().getLocalName().endsWith("definition"))
                    s.getSubject().addProperty(RDFS.comment, s.getObject());
                //if (s.getPredicate().getLocalName().endsWith("definitionAlias"))
                //    s.getSubject().addProperty(RDFS.comment, s.getObject());
                if (s.getPredicate().getLocalName().endsWith("name"))
                    s.getSubject().addProperty(RDFS.label, s.getObject());
                //if (s.getPredicate().getLocalName().endsWith("nameAlias"))
                //    s.getSubject().addProperty(RDFS.label, s.getObject());
            }
            
            try {
                File out_file = new File("C:\\temp\\IFC-PSD\\psets2\\" + (new File(file)).getAbsoluteFile().getName());
                try (FileOutputStream fo = new FileOutputStream(out_file)) {  // JO 2024
					ontology_model.write(fo, "TTL");
				} catch (FileNotFoundException e) {
					throw e;
				} catch (IOException e) {
					e.printStackTrace();
				}
                CreatePsetDescriptionOntologies.write((new File(file)).getAbsoluteFile().getName(),out_file.getAbsolutePath());
                
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    public  static void copyFolder(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
        }
    }

    private static void copy(Path source, Path dest) {
        try {
            Files.copy(source, dest, REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    
    public static void main(String[] args) {
        File folder = new File("C:\\temp\\IFC-PSD\\out");
        File[] listOfFiles = folder.listFiles();
        for(File f:listOfFiles)
            try {
                CreatePsetDescriptionOntologies.copyFolder(
                                Paths.get(f+"\\doc"),
                                Paths.get("C:\\temp\\IFC-PSD\\ifcOWL\\IFC4-PSD\\"+f.getAbsoluteFile().getName()) );
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
}
