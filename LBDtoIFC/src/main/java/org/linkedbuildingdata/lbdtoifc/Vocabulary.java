package org.linkedbuildingdata.lbdtoifc;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

final class Vocabulary {
    static final String BOT = "https://w3id.org/bot#";
    static final String LBD = "https://linkedbuildingdata.org/LBD#";
    static final String PROPS = "http://lbd.arch.rwth-aachen.de/props#";
    static final String BEO = "https://pi.pauwel.be/voc/buildingelement#";
    static final String FURN = "http://pi.pauwel.be/voc/furniture#";
    static final String MEP = "http://pi.pauwel.be/voc/distributionelement#";
    static final String OMG = "https://w3id.org/omg#";
    static final String FOG = "https://w3id.org/fog#";
    static final String GEOMETRY = "https://w3id.org/ifctolbd/geometry#";

    static final Resource SITE = resource(BOT, "Site");
    static final Resource BUILDING = resource(BOT, "Building");
    static final Resource STOREY = resource(BOT, "Storey");
    static final Resource SPACE = resource(BOT, "Space");
    static final Resource ELEMENT = resource(BOT, "Element");

    static final Property HAS_BUILDING = property(BOT, "hasBuilding");
    static final Property HAS_STOREY = property(BOT, "hasStorey");
    static final Property HAS_SPACE = property(BOT, "hasSpace");
    static final Property CONTAINS_ELEMENT = property(BOT, "containsElement");
    static final Property HAS_SUB_ELEMENT = property(BOT, "hasSubElement");
    static final Property HAS_GEOMETRY = property(OMG, "hasGeometry");
    static final Property AS_OBJ = property(FOG, "asObj_v3.0-obj");
    static final Property GEOMETRY_ARTIFACT = property(GEOMETRY, "artifact");
    static final Property ARTIFACT_MEDIA_TYPE = property(GEOMETRY, "mediaType");
    static final Property ARTIFACT_SHA256 = property(GEOMETRY, "sha256");
    static final Property ARTIFACT_LEVEL_OF_DETAIL = property(GEOMETRY, "levelOfDetail");
    static final Property GLOBAL_ID = property(LBD, "globalId");
    static final Property LEGACY_GLOBAL_ID = property(PROPS, "globalIdIfcRoot_attribute_simple");
    static final Property OBJECT_TYPE = property(PROPS, "objectTypeIfcObject_attribute_simple");
    static final Property LONG_NAME = property(PROPS, "longNameIfcSpatialStructureElement_attribute_simple");
    static final Property BAT_ID = property(PROPS, "batid_attribute_simple");
    static final Property DOOR_HEIGHT = property(PROPS, "overallHeightIfcDoor_attribute_simple");
    static final Property DOOR_WIDTH = property(PROPS, "overallWidthIfcDoor_attribute_simple");
    static final Property WINDOW_HEIGHT = property(PROPS, "overallHeightIfcWindow_attribute_simple");
    static final Property WINDOW_WIDTH = property(PROPS, "overallWidthIfcWindow_attribute_simple");

    private Vocabulary() {}

    private static Resource resource(String namespace, String localName) {
        return ResourceFactory.createResource(namespace + localName);
    }

    private static Property property(String namespace, String localName) {
        return ResourceFactory.createProperty(namespace, localName);
    }
}
