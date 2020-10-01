package de.rwth_aachen.dc.ifc2lbd.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

/*
 * Jyrki Oraskari, 2020
 */

@Path("/")
public class IFCtoLBD_OpenAPI {

	/**
     * General IFCtoLBD OPM Level 3  
	 * Converts an IFC file into into the Linked Building Data  (BOT
	 * https://w3c-lbd-cg.github.io/bot/)
	 * 
	 * @param ifcFile an IFC file
	 * @return Returnd RDF output. Formats are:  JSON-LD,  RDF/XML, and TTL
	 */
	@POST
	@Path("/convertIFCtoLBD")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({"text/turtle", "application/ld+json", "application/rdf+xml"})
	public Response convertIFCtoLBD(@HeaderParam(HttpHeaders.ACCEPT) String accept_type,@FormDataParam("ifcFile") InputStream ifcFile) {
		try {
			File tempIfcFile = File.createTempFile("ifc2lbd-", ".ifc");
			tempIfcFile.deleteOnExit();

			Files.copy(ifcFile, tempIfcFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			IOUtils.closeQuietly(ifcFile);
			if (accept_type.equals("application/ld+json")) {
				StringBuilder result_string = new StringBuilder();
				extractIFCtoLBD(tempIfcFile, result_string, RDFFormat.JSONLD_COMPACT_PRETTY);
				return Response.ok(result_string.toString(), "application/ld+json").build();
			} else if (accept_type.equals("application/rdf+xml")) {
				StringBuilder result_string = new StringBuilder();
				extractIFCtoLBD(tempIfcFile, result_string, RDFFormat.RDFXML);
				return Response.ok(result_string.toString(), "application/rdf+xml").build();
			} else {
				StringBuilder result_string = new StringBuilder();
				extractIFCtoLBD(tempIfcFile, result_string, RDFFormat.TURTLE_PRETTY);
				return Response.ok(result_string.toString(), "text/turtle").build();

			}


		} catch (Exception e) {
			e.printStackTrace();
		}

		return Response.noContent().build();
	}


	private void extractIFCtoLBD(File ifcFile, StringBuilder result_string, RDFFormat rdfformat) {
		IFCtoLBDConverter lbdconverter = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset", false, 3);
		Model m = lbdconverter.convert(ifcFile.getAbsolutePath());

		if(m==null)
		{
		    result_string.append("Not a valid IFC version.");
		    return;
		}
		OutputStream ttl_output = new OutputStream() {
			private StringBuilder string = new StringBuilder();

			@Override
			public void write(int b) throws IOException {
				this.string.append((char) b);
			}

			public String toString() {
				return this.string.toString();
			}
		};
		RDFDataMgr.write(ttl_output, m, rdfformat);
		result_string.append(ttl_output.toString());
	}

	

}