package org.linkedbuildingdata.ifc2lbd;

import org.apache.log4j.BasicConfigurator;

public class Main {

    public static void main(String[] args) {
        BasicConfigurator.configure();
    	IFCtoLBD_Desktop.main(args);
    }
}