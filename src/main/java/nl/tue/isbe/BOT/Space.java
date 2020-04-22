package nl.tue.isbe.BOT;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Space {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;
    private Storey storey;

    private List<Element> adjacentElements = new ArrayList<Element>(); //Relation between a zone and its adjacent building elements, bounding the zone.
    private List<Element> intersectingElements = new ArrayList<Element>(); //Relation between a Zone and a building Element that intersects it.

    private IFCVO lineEntry;
    public static List<Space> spaceList = new ArrayList<Space>();

    public Space(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        spaceList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "space_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
    }

    //------------
    // ACCESSORS
    //------------

    public void setStorey(Storey bs){
        storey = bs;
    }

    public Guid getGuid() {
        return guid;
    }

    public long getLineNum() {
        return lineNum;
    }

    public String getName() {
        return name;
    }

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public List<Element> getAdjacentElements() {
        return adjacentElements;
    }
}
