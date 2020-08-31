/*
 * BoundingBox.java
 *
 * Created on June 1, 2007, 3:30 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package nl.tue.ddss.bcf;

import javax.vecmath.*;

/**
 *
 * @author bwjoran
 */
public class BoundingBox {
    private boolean initialized = false;
    private Point3d min = null;
    private Point3d max = null;
    
    /** Creates a new instance of BoundingBox */
    public BoundingBox() {
    }
    
    public BoundingBox(Point3d a) {
        add(a);
    }
    
    public void init(){
    	Point3d p1=new Point3d(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY);
    	Point3d p2=new Point3d(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY);
    	this.add(p1);
    	this.add(p2);
    }

    public BoundingBox(Point3d a, Point3d b) {
        add(a);
        add(b);
    }

    public void add(Point3d point) {
        if(initialized) {
            if(point.x < min.x) min.x = point.x;
            if(point.y < min.y) min.y = point.y;
            if(point.z < min.z) min.z = point.z;
            if(point.x > max.x) max.x = point.x;
            if(point.y > max.y) max.y = point.y;
            if(point.z > max.z) max.z = point.z;            
        }
        else {
            min = new Point3d(point);            
            max = new Point3d(point);
            initialized = true;
        }
    }
    
    public void add(BoundingBox bb) {
        add(bb.getMin());
        add(bb.getMax());
    }
    
    public void inflate(double x, double y, double z) {
        x/=2.0;
        y/=2.0;
        z/=2.0;
        min.x -= x;
        min.y -= y;
        min.x -= x;
        max.x += x;
        max.y += y;
        max.z += z;
    }

    public boolean isInitialized() {
        return initialized;
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
    	return "MULTIPOINT (("+min.x+" "+min.y+" "+min.z+"), ("+max.x+" "+max.y+" "+max.z+"))";
    }
}
