package nl.tue.isbe.IFC;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;

import java.util.ArrayList;
import java.util.List;

public class IfcRelFillsElement {

    private IFCVO lineEntry;
    public static List<IfcRelFillsElement> relFillsElementList  = new ArrayList<IfcRelFillsElement>();

    private IFCVO relatingBuildingElement;
    private IFCVO relatingOpeningElement;

    public IfcRelFillsElement(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        relFillsElementList.add(this);
        this.parse();
    }

    private void parse(){
        relatingBuildingElement = (IFCVO)lineEntry.getObjectList().get(10);
        relatingOpeningElement = (IFCVO)lineEntry.getObjectList().get(8);
    }

    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public IFCVO getRelatingBuildingElement() {
        return relatingBuildingElement;
    }

    public IFCVO getRelatingOpeningElement() {
        return relatingOpeningElement;
    }
}
