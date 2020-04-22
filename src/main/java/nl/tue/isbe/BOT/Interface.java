package nl.tue.isbe.BOT;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Interface {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;

    private Space relatingSpace;
    private Element relatedElement;

    private IFCVO lineEntry;
    public static List<Interface> interfaceList = new ArrayList<Interface>();

    public Interface(IFCVO lineEntry, Guid guid){
        this.lineEntry = lineEntry;
        lineNum = lineEntry.getLineNum();
        name = "interface_"+lineEntry.getLineNum();
        interfaceList.add(this);
        this.guid = guid;
    }


    //------------
    // ACCESSORS
    //------------

    public Guid getGuid() {
        return guid;
    }

    public long getLineNum() {
        return lineNum;
    }

    public String getName() {
        return name;
    }

    /*This should only be run once, from the constructor. It creates the correct name, based on the IFC input, which is not stored*/
    public IFCVO getLineEntry(IFCVO lineEntry) {
        return lineEntry;
    }

    private void setConnectionGeometry(){
        /*#154= IFCCARTESIANPOINT((-13084.0719280682,-9080.72887478923,0.));
        #156= IFCCARTESIANPOINT((8800.,0.));
        #158= IFCCARTESIANPOINT((8800.,10300.));
        #160= IFCCARTESIANPOINT((5800.,10300.));
        #162= IFCCARTESIANPOINT((-0.,10300.));
        #164= IFCPOLYLINE((#9,#156,#158,#160,#162,#9));
        #166= IFCAXIS2PLACEMENT3D(#154,#21,#15);
        #167= IFCPLANE(#166);
        #168= IFCCURVEBOUNDEDPLANE(#167,#164,());
        #170= IFCCONNECTIONSURFACEGEOMETRY(#168,$);*/
    }

    public Space getRelatingSpace() {
        return relatingSpace;
    }

    public void setRelatingSpace(Space relatingSpace) {
        this.relatingSpace = relatingSpace;
    }

    public Element getRelatedElement() {
        return relatedElement;
    }

    public void setRelatedElement(Element relatedElement) {
        this.relatedElement = relatedElement;
    }
}
