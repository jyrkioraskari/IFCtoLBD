package de.rwth_aachen.dc.lbd;

import javax.vecmath.Point3d;

public class BoundingBox {
    private final Point3d min;
    private final Point3d max;

    public BoundingBox() {
        this.min = new Point3d(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        this.max = new Point3d(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    public void add(Point3d point) {
        if (point.x < min.x)
            min.x = point.x;
        if (point.y < min.y)
            min.y = point.y;
        if (point.z < min.z)
            min.z = point.z;
        if (point.x > max.x)
            max.x = point.x;
        if (point.y > max.y)
            max.y = point.y;
        if (point.z > max.z)
            max.z = point.z;
    }

    public Point3d getMin() {
        return min;
    }

    public Point3d getMax() {
        return max;
    }

    // JO 2020
    @Override
    public String toString() {
        return "MULTIPOINT Z((" + min.x + " " + min.y + " " + min.z + "), (" + max.x + " " + max.y + " " + max.z + "))";
    }
    
    public String toLiteral() {
        return "MULTIPOINT Z((" + min.x + " " + min.y + " " + min.z + "), (" + max.x + " " + max.y + " " + max.z + "))";
    }
}
