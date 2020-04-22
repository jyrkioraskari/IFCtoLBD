package nl.tue.isbe.IFC;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.ifcspftools.Guid;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;

public class IfcRelAggregates {

    private IFCVO lineEntry;
    public static List<IfcRelAggregates> RelAggregatesList  = new ArrayList<IfcRelAggregates>();

    private IfcObjectDefinition relatingObject;
    private List<IfcObjectDefinition> relatedObjects = new ArrayList<IfcObjectDefinition>();

    public IfcRelAggregates(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        RelAggregatesList.add(this);
        this.parse();
    }

    private void parse(){
        //relatingObject
        relatingObject = new IfcObjectDefinition((IFCVO)lineEntry.getObjectList().get(8));
        //relatedObjects
        List<Object> lvo = (List<Object>)lineEntry.getObjectList().get(10);
        for(IFCVO j : removeClutterFromList(lvo)) {
            relatedObjects.add(new IfcObjectDefinition(j));
        }
    }

    public static List<IfcObjectDefinition> getRelatedObjectsForRelatingObject(long lineNum){
        for(IfcRelAggregates ira : RelAggregatesList){
            if(ira.getRelatingObject().getLineNum() == lineNum)
                return ira.getRelatedObjects();
        }
        return null;
    }

    private List<IFCVO> removeClutterFromList(List<Object> lvo){
        List<IFCVO> theRealList = new ArrayList<IFCVO>();
        for(Object o : lvo) {
            if(o.getClass().equals(IFCVO.class))
                theRealList.add((IFCVO)o);
        }
        return theRealList;
    }

    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public IfcObjectDefinition getRelatingObject() {
        return relatingObject;
    }

    public List<IfcObjectDefinition> getRelatedObjects() {
        return relatedObjects;
    }
}
