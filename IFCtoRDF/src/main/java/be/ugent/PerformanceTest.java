package be.ugent;

import java.io.IOException;
import java.util.Optional;

public class PerformanceTest extends IfcSpfReader {

	//private int i = 0;

	public Optional<String> convert_into_rdf(String ifcFile, String outputFile, String baseURI,boolean hasPerformanceBoost) throws IOException {
		//i = 0;
		//PrintStream orgSystemOut = System.out;
		//PrintStream orgSystemError = System.err;
		
		
		System.out.println("ifcfile is: "+ifcFile);
		
			setup(ifcFile);
			convert(ifcFile, outputFile, baseURI,hasPerformanceBoost);
			return Optional.of(this.ontURI);
	}

	public Optional<String> getOntologyURI(String ifcFile) {
		try {
			setup(ifcFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Optional.of(this.ontURI);
	}
	
	
	public static void main(String[] args) {
		PerformanceTest pt=new PerformanceTest();
		try {
			pt.convert_into_rdf("c:\\jo\\Duplex_A_20110907.ifc", "c:\\jo\\out.ttl", "https://ba.se/",true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("done");
	}
}
