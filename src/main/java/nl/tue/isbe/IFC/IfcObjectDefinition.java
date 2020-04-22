package nl.tue.isbe.IFC;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class IfcObjectDefinition {

    private Guid guid = new Guid();
    private long lineNum;
    private String name;
    private IFCVO lineEntry;
    public static List<IfcObjectDefinition> IfcObjectDefinitionList  = new ArrayList<IfcObjectDefinition>();

    public IfcObjectDefinition(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        IfcObjectDefinitionList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "IfcObjectDefinition_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
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

    public IFCVO getLineEntry() {
        return lineEntry;
    }
}
