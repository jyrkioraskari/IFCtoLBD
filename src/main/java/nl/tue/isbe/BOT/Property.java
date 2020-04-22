package nl.tue.isbe.BOT;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.IFC.IfcObjectDefinition;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Property {

    private long lineNum;

    private Element relatedElement;
    private String propertyType;
    private String value;
    private String propertyName;
    private String propertyNameNoSpace;

    private IFCVO lineEntry;
    public static List<Property> propertyList = new ArrayList<Property>();

    public Property(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        lineNum = lineEntry.getLineNum();
        propertyList.add(this);
        this.parse();
    }

    private void parse(){
        propertyName = ((String) lineEntry.getObjectList().get(0)).substring(1);
        propertyNameNoSpace = propertyName.replaceAll("\\s+","");
        propertyType = (String) lineEntry.getObjectList().get(4);
        List<Object> prop = (List<Object>)lineEntry.getObjectList().get(5);
        value = prop.get(0).toString();
    }

    //------------
    // ACCESSORS
    //------------

    public long getLineNum() {
        return lineNum;
    }

    public IFCVO getLineEntry(IFCVO lineEntry) {
        return lineEntry;
    }

    public String getValue() {
        return value;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getPropertyNameNoSpace() {
        return propertyNameNoSpace;
    }
}
