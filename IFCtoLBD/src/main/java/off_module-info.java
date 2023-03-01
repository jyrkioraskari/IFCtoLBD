//module io.github.jyrkioraskari.IFCtoLBD {
//   requires transitive io.github.jyrkioraskari.IFC2RDF;
//   requires transitive io.github.jyrkioraskari.IFCtoLBD_Geometry;
//   requires org.apache.jena.core;
//   requires org.apache.jena.arq;
//   requires org.apache.jena.base;
//   requires org.apache.jena.ext.com.google;
//   requires info.picocli;
//   requires rtree.multi;
//   requires org.apache.commons.lang3;
//   requires  org.slf4j;
//   requires com.google.common;
//   
//   exports org.linkedbuildingdata.ifc2lbd; 
//   exports org.linkedbuildingdata.ifc2lbd.core;
//   exports org.linkedbuildingdata.ifc2lbd.application_messaging;
//   exports org.linkedbuildingdata.ifc2lbd.application_messaging.events;
//}