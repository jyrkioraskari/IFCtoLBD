package nl.tue.isbe.IFC;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;

import java.util.ArrayList;
import java.util.List;

public class IfcRelDecomposes {

    private IFCVO lineEntry;
    public static List<IfcRelDecomposes> RelDecomposesList  = new ArrayList<IfcRelDecomposes>();

    public IfcRelDecomposes(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        RelDecomposesList.add(this);
    }


    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }


}
