package org.linkedbuildingdata.ifc2lbd.geo;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.core.utils.LBD_RDF_Utils;
import org.linkedbuildingdata.ifc2lbd.namespace.GEO;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

import com.google.common.eventbus.EventBus;
import com.openifctools.guidcompressor.GuidCompressor;

public class IfcOWL_GeolocationUtil {
    /**
     * 
     * Adds Geolocation triples to the RDF model. Ontology:
     * http://www.opengis.net
     * 
     * @param ifcowl_model
     * @param ifcOWL_ns
     * @param lbd_general_output_model
     */
    public static void addGeolocation2BOT(Model ifcowl_model,IfcOWL ifcOWL_ns,Model lbd_general_output_model,String uriBase, String ontoURI) {

        IFC_Geolocation c = new IFC_Geolocation(ontoURI);
        String wkt_point;
        try {
            wkt_point = c.addGeolocation(ifcowl_model);
        } catch (Exception e) {
            System.err.println("No wkt_point");
            return; // no geolocation
        }

        
        IfcOWLUtils.listSites(ifcOWL_ns, ifcowl_model).stream().map(RDFNode::asResource).forEach(site -> {
            
            
            // Create a resource and add to bot model (resource, model, string)
            Resource sio = LBD_RDF_Utils.createformattedURIRecource(site, lbd_general_output_model, "Site",ifcOWL_ns, uriBase,false);

            // Create a resource geosparql:Feature;
            Resource geof = lbd_general_output_model.createResource("http://www.opengis.net/ont/geosparql#Feature");
            // Add geosparl:Feature as a type to site;
            sio.addProperty(RDF.type, geof);
            // Create a resource geosparql:hasGeometry;
            Property geo_hasGeometry = lbd_general_output_model.createProperty("http://www.opengis.net/ont/geosparql#hasGeometry");

            // For the moment we will use a seperate graph for geometries, to
            // "encourage"
            // people to not link to geometries
            // This could also be done using blanknodes, although, hard to
            // maintain
            // provenance if required in future versions.

            String wktLiteralID = "urn:bot:geom:pt:";
            String guid_site = IfcOWLUtils.getGUID(site, ifcOWL_ns);
            String uncompressed_guid_site = GuidCompressor.uncompressGuidString(guid_site);
            String uncompressed_wktLiteralID = wktLiteralID + uncompressed_guid_site;

            // Create a resource <urn:bot:geom:pt:guid>
            Resource rr = lbd_general_output_model.createResource(uncompressed_wktLiteralID);
            rr.addProperty(RDF.type, GEO.Geometry);
            sio.addProperty(geo_hasGeometry, rr);

            // Create a property asWKT
            Property geo_asWKT = lbd_general_output_model.createProperty("http://www.opengis.net/ont/geosparql#asWKT");
            // add a data type
            RDFDatatype rtype = WktLiteral.wktLiteralType;
            TypeMapper.getInstance().registerDatatype(rtype);
            // add a typed wkt literal
            Literal l = lbd_general_output_model.createTypedLiteral(wkt_point, rtype);

            rr.addProperty(geo_asWKT, l);

        });
        EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
        eventBus.post(new IFCtoLBD_SystemStatusEvent("LDB geom read"));

    }
    
    

 

}
