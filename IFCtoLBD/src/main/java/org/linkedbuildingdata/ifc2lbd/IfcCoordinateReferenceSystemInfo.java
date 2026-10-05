package org.linkedbuildingdata.ifc2lbd;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import de.rwth_aachen.dc.lbd.BoundingBox;

/** CRS and map-conversion metadata extracted from an IFC-OWL staging model. */
public record IfcCoordinateReferenceSystemInfo(String identifier, Map<String, String> parameters) {
	public IfcCoordinateReferenceSystemInfo {
		identifier = identifier == null || identifier.isBlank() ? "urn:ogc:def:crs:OGC::EngineeringCRS" : identifier;
		parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
	}
	public static IfcCoordinateReferenceSystemInfo engineering() {
		return new IfcCoordinateReferenceSystemInfo("urn:ogc:def:crs:OGC::EngineeringCRS", Map.of());
	}
	public boolean hasMapConversion() { return parameters.containsKey("eastings") || parameters.containsKey("northings"); }
	private double number(String key, double fallback) { try { return Double.parseDouble(parameters.getOrDefault(key, Double.toString(fallback))); } catch (RuntimeException e) { return fallback; } }
	public String transform(BoundingBox box) {
		double minX=box.getMin().x,maxX=box.getMax().x,minY=box.getMin().y,maxY=box.getMax().y;
		double e=number("eastings",0),n=number("northings",0),h=number("orthogonalHeight",0),a=number("xAxisAbscissa",1),o=number("xAxisOrdinate",0),s=number("scale",1);
		double[][] c={{minX,minY},{minX,maxY},{maxX,minY},{maxX,maxY}}; double loE=Double.POSITIVE_INFINITY,hiE=Double.NEGATIVE_INFINITY,loN=Double.POSITIVE_INFINITY,hiN=Double.NEGATIVE_INFINITY;
		for(double[] p:c){double x=e+s*(p[0]*a-p[1]*o),y=n+s*(p[0]*o+p[1]*a);loE=Math.min(loE,x);hiE=Math.max(hiE,x);loN=Math.min(loN,y);hiN=Math.max(hiN,y);} return "MULTIPOINT Z(("+loE+" "+loN+" "+(box.getMin().z+h)+"), ("+hiE+" "+hiN+" "+(box.getMax().z+h)+"))";
	}
	public String transformWkt(String wkt) { Pattern p=Pattern.compile("(-?\\d+(?:\\.\\d+)?(?:[Ee][+-]?\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?(?:[Ee][+-]?\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?(?:[Ee][+-]?\\d+)?)"); Matcher m=p.matcher(wkt); StringBuffer out=new StringBuffer(); double e=number("eastings",0),n=number("northings",0),h=number("orthogonalHeight",0),a=number("xAxisAbscissa",1),o=number("xAxisOrdinate",0),s=number("scale",1); while(m.find()){double x=Double.parseDouble(m.group(1)),y=Double.parseDouble(m.group(2)); m.appendReplacement(out,e+s*(x*a-y*o)+" "+(n+s*(x*o+y*a))+" "+(Double.parseDouble(m.group(3))+h));} m.appendTail(out); return out.toString(); }
}
