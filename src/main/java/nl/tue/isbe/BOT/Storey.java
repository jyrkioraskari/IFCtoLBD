package nl.tue.isbe.BOT;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Storey {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;

    private String label = "";
    private String description = "";

    private Building building;
    private List<Space> spaces = new ArrayList<Space>();
    private List<Element> containsElements  = new ArrayList<Element>(); //Relation to a building element contained in a zone.

    private IFCVO lineEntry;
    public static List<Storey> storeyList = new ArrayList<Storey>();

    public Storey(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        storeyList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "storey_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        label = ((String) lineEntry.getObjectList().get(4)).substring(1);
        description = ((String) lineEntry.getObjectList().get(6)).substring(1);
    }

    //------------
    // ACCESSORS
    //------------

    public void addContainedElement(Element el){
        containsElements.add(el);
    }

    public void addSpace(Space sp){
        spaces.add(sp);
    }

    public void setBuilding(Building b){
        building = b;
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

    public List<Space> getSpaces() {
        return spaces;
    }

    public List<Element> getContainedElements() {
        return containsElements;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
