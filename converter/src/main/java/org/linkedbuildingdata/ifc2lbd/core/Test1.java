package org.linkedbuildingdata.ifc2lbd.core;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class Test1 {

    
    public static void main(String[] args) {        try {
            File ifc_file = new File("C:\\test\\bim4ren\\BIM4Ren_DUNANT_AR2Build-RawFile_IFC4.ifc");
            File temp_file = File.createTempFile("ifc2lbd", "test.ttl");
            new IFCtoLBDConverter(ifc_file.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#", temp_file.getAbsolutePath(), 3, true, false, true, false, false, false);
        } catch (Exception e) {
            fail("Conversion had an error: " + e.getMessage());
        }        
    }
}
