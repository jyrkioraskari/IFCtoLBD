package de.rwth_aachen.dc.lbd;

import java.io.File;

public class GeometryTest {

	public static void main(String[] args) {
			IFCGeometry ib=new IFCGeometry(new File("c:\\ifc\\Duplex_A_20110505.ifc"));
			BoundingBox b=ib.getBoundingBox("1aj$VJZFn2TxepZUBcKpZw");
			System.out.println(b.getMax().x);
			System.out.println(b.getMin().x);

	}

}
