package nl.tue.isbe.IFC;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;

import java.util.ArrayList;
import java.util.List;

public class IfcRelVoidsElement {

    private IFCVO lineEntry;
    public static List<IfcRelVoidsElement> relVoidsElementList  = new ArrayList<IfcRelVoidsElement>();

    private IFCVO relatingBuildingElement;
    private IFCVO relatingOpeningElement;

    public IfcRelVoidsElement(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        relVoidsElementList.add(this);
        this.parse();
    }

    private void parse(){
        relatingBuildingElement = (IFCVO)lineEntry.getObjectList().get(8);
        relatingOpeningElement = (IFCVO)lineEntry.getObjectList().get(10);
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
