package org.linkedbuildingdata.ifc2lbd.tests;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class ProfilingTests {

	public static void main(String[] args) {
		
		IFCtoLBDConverter c1nb = new IFCtoLBDConverter("http://example.com//", false, 1);
		c1nb.convert("C:\\Users\\Jyrki\\Downloads\\ci.mines-stetienne.fr_EMSE_EF_EMSE_EF.ifc", null, true, false,true, false, false, false, false, false,true,false);

	}

}
