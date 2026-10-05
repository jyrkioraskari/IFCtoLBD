package org.linkedbuildingdata.ifc2lbd.geo;

import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.impl.LiteralLabel;


public class WktLiteral extends BaseDatatype {
    
    public static final String TypeURI = "http://www.opengis.net/ont/geosparql#wktLiteral";
    public static final String CRS84 = "<http://www.opengis.net/def/crs/OGC/1.3/CRS84>";

    public static final RDFDatatype wktLiteralType = new WktLiteral();

    /** Returns a GeoSPARQL WKT lexical form with the explicit WGS84 longitude/latitude CRS. */
    public static String withCrs84(String wkt) {
        if (wkt == null || wkt.isBlank() || wkt.startsWith(CRS84 + " ")) {
            return wkt;
        }
        return CRS84 + " " + wkt;
    }

    private WktLiteral() {
        super(TypeURI);
    }

    /**
     * Convert a value of this datatype out
     * to lexical form.
     */
    @Override
	public String unparse(Object value) {
        return value.toString();
    }

    /**
     * Parse a lexical form of this datatype to a value
     */
    @Override
	public Object parse(String lexicalForm) {
        String value = lexicalForm == null ? "" : lexicalForm.trim();
        if (!value.startsWith(CRS84 + " ")) {
            value = CRS84 + " " + value;
        }
        return new TypedValue(value, this.getURI());
    }

    /**
     * Compares two instances of values of the given datatype.
     * This does not allow rationals to be compared to other number
     * formats, Lang tag is not significant.
     *
     * @param value1 First value to compare
     * @param value2 Second value to compare
     * @return Value to determine whether both are equal.
     */
    @Override
	public boolean isEqual(LiteralLabel value1, LiteralLabel value2) {
        return value1.getDatatype() == value2.getDatatype()
                && value1.getValue().equals(value2.getValue());
    }
}
