package nl.tue.isbe.BOT;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Building {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;

    private String label = "";
    private String description = "";

    private Site site;
    private List<Storey> storeys = new ArrayList<Storey>();

    private IFCVO lineEntry;
    public static List<Building> buildingList = new ArrayList<Building>();

    public Building(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        buildingList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "building_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        label = ((String) lineEntry.getObjectList().get(4)).substring(1);
        description = ((String) lineEntry.getObjectList().get(6)).substring(1);
    }


    //------------
    // ACCESSORS
    //------------

    public void addStorey(Storey bs){
        storeys.add(bs);
    }

    public void setSite(Site s){
        site = s;
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

    public Site getSite(){
        return site;
    }

    public List<Storey> getStoreys() {
        return storeys;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
