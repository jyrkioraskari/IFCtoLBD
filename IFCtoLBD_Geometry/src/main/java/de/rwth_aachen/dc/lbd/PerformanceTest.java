package de.rwth_aachen.dc.lbd;

import java.io.File;

public class PerformanceTest {

	@SuppressWarnings("unused")
	public static void main(String[] args) {
			new IFCGeometry(new File("c:\\jo\\Duplex_A_20110907.ifc"));
		System.out.println("done");
	}

}
