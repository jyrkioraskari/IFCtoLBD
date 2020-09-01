package org.lbd.ifc2lbd.core;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.ugent.IfcSpfReader;

public class IFCtoRDF extends IfcSpfReader{

    private static final Logger LOG = LoggerFactory.getLogger(IFCtoRDF.class);


	public Optional<String> convert_into_rdf(String ifcFile, String outputFile, String baseURI) throws IOException {
		try
		{
		setup(ifcFile);
		convert(ifcFile, outputFile, baseURI);
		}
		catch (Exception e) {
			e.printStackTrace();
			return Optional.empty();
		}
		return Optional.of(this.ontURI);
	}
}



