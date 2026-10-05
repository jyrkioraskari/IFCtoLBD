package de.rwth_aachen.dc.ifc2lbd.rest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.linkedbuildingdata.ifc2lbd.ConversionProfiles;
import org.linkedbuildingdata.ifc2lbd.ConversionRequest;
import org.linkedbuildingdata.ifc2lbd.ConversionResult;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/")
public class IFCtoLBD_OpenAPI {
	@GET
	@Path("/hello")
	public Response hello() {
		return Response.ok("OK!", MediaType.TEXT_PLAIN).build();
	}

	@POST
	@Path("/convertIFCtoLBD")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({ "text/turtle", "application/ld+json", "application/rdf+xml", "application/trig" })
	public Response convertIFCtoLBD(@HeaderParam(HttpHeaders.ACCEPT) String accept,
			@FormDataParam("ifcFile") InputStream ifcFile,
			@DefaultValue("properties-opm") @FormDataParam("profile") String profile,
			@DefaultValue("false") @FormDataParam("validate") boolean validate) {
		if (ifcFile == null) {
			return Response.status(Response.Status.BAD_REQUEST).entity("Missing multipart field: ifcFile")
					.type(MediaType.TEXT_PLAIN).build();
		}
		java.nio.file.Path temporaryIfc = null;
		IFCtoLBDConverter converter = null;
		ConversionResult result = null;
		try {
			temporaryIfc = Files.createTempFile("ifc2lbd-", ".ifc");
			try (InputStream input = ifcFile) {
				Files.copy(input, temporaryIfc, StandardCopyOption.REPLACE_EXISTING);
			}
			ConversionRequest request = new ConversionRequest(temporaryIfc.toString(), ConversionProfiles.named(profile));
			if (validate) request = request.withStandardValidation();
			converter = new IFCtoLBDConverter("https://lbd.example.com/", false, 3);
			result = converter.convert(request);
			Files.deleteIfExists(temporaryIfc);
			temporaryIfc = null;

			OutputSelection output = selectOutput(accept);
			IFCtoLBDConverter ownedConverter = converter;
			ConversionResult ownedResult = result;
			StreamingOutput stream = outputStream -> {
				try (ownedConverter; ownedResult) {
					if (output.dataset()) RDFDataMgr.write(outputStream, ownedResult.getDataset(), output.format());
					else RDFDataMgr.write(outputStream, ownedResult.getModel(), output.format());
				}
			};
			return Response.ok(stream, output.mediaType())
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + output.fileName() + "\"")
					.build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			close(result, converter);
			return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
		} catch (Exception e) {
			close(result, converter);
			return Response.serverError().entity("IFC conversion failed").type(MediaType.TEXT_PLAIN).build();
		} finally {
			if (temporaryIfc != null) {
				try { Files.deleteIfExists(temporaryIfc); } catch (IOException ignored) { }
			}
		}
	}

	private static OutputSelection selectOutput(String accept) {
		String requested = accept == null ? "" : accept.toLowerCase(java.util.Locale.ROOT);
		if (requested.contains("application/trig"))
			return new OutputSelection("application/trig", "ifc2lbd.trig", RDFFormat.TRIG_PRETTY, true);
		if (requested.contains("application/ld+json"))
			return new OutputSelection("application/ld+json", "ifc2lbd.jsonld", RDFFormat.JSONLD, false);
		if (requested.contains("application/rdf+xml"))
			return new OutputSelection("application/rdf+xml", "ifc2lbd.rdf", RDFFormat.RDFXML, false);
		return new OutputSelection("text/turtle", "ifc2lbd.ttl", RDFFormat.TURTLE_PRETTY, false);
	}

	private static void close(ConversionResult result, IFCtoLBDConverter converter) {
		if (result != null) result.close();
		if (converter != null) converter.close();
	}

	private record OutputSelection(String mediaType, String fileName, RDFFormat format, boolean dataset) { }
}
