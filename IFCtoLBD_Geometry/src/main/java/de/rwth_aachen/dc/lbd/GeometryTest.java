package de.rwth_aachen.dc.lbd;

import java.io.File;
import java.io.IOException;

import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.plugins.renderengine.RenderEngineException;

public class GeometryTest {

	public static void main(String[] args) {
		try {
			IFCBoundingBoxes ib=new IFCBoundingBoxes(new File("c:\\ifc\\Duplex_A_20110505.ifc"));
			BoundingBox b=ib.getBoundingBox("1aj$VJZFn2TxepZUBcKpZw");
			System.out.println(b.getMax().x);
			System.out.println(b.getMin().x);
		
		} catch (RenderEngineException | DeserializeException | IOException e) {
			e.printStackTrace();
		}

	}

}
