package nl.tue.isbe.BOT;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Site {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;
    private List<Building> buildings = new ArrayList<Building>();

    private String label = "";
    private String description = "";

    private IFCVO lineEntry;
    public static List<Site> siteList = new ArrayList<Site>();

    public Site(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        siteList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "site_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        label = ((String) lineEntry.getObjectList().get(4)).substring(1);
        description = ((String) lineEntry.getObjectList().get(6)).substring(1);
    }


    //------------
    // ACCESSORS
    //------------

    public void addBuilding(Building b){
        buildings.add(b);
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

    public List<Building> getBuildings() {
        return buildings;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
