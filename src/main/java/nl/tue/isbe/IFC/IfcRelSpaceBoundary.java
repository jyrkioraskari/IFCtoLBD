package nl.tue.isbe.IFC;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.BOT.Interface;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class IfcRelSpaceBoundary {

    private Guid guid = new Guid();
    private IFCVO lineEntry;
    public static List<IfcRelSpaceBoundary> relSpaceBoundaryList = new ArrayList<IfcRelSpaceBoundary>();

    private IfcObjectDefinition relatingSpace;
    private IfcObjectDefinition relatedBuildingElement;
    private IFCVO connectionGeometry; //useful for creating interface geometry potentially
    private Interface theInterface;

    public IfcRelSpaceBoundary(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        relSpaceBoundaryList.add(this);
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        this.parse();
    }

    private void parse(){
        relatingSpace = new IfcObjectDefinition((IFCVO)lineEntry.getObjectList().get(8));
        relatedBuildingElement = new IfcObjectDefinition((IFCVO)lineEntry.getObjectList().get(10));
        connectionGeometry = (IFCVO)lineEntry.getObjectList().get(12);
        theInterface = new Interface(connectionGeometry, guid);
    }

    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public IfcObjectDefinition getRelatingSpace() {
        return relatingSpace;
    }

    public IfcObjectDefinition getRelatedBuildingElement() {
        return relatedBuildingElement;
    }

    public Interface getInterface() {
        return theInterface;
    }
}
