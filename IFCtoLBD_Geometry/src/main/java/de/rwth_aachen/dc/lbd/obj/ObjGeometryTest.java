package de.rwth_aachen.dc.lbd.obj;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.plugins.renderengine.RenderEngineException;

import de.rwth_aachen.dc.lbd.ObjDescription;

public class ObjGeometryTest {

	public static void main(String[] args) {
		try {
			IFCtoObj ib=new IFCtoObj(new File("c:\\ifc\\Duplex_A_20110907.ifc"));
			ObjDescription b=ib.getOBJ("2O2Fr$t4X7Zf8NOew3FNhv");
			FileOutputStream outputStream = new FileOutputStream("c:\\jo\\test.obj");
		    byte[] strToBytes = b.toString().getBytes();
		    outputStream.write(strToBytes);

		    outputStream.close();
		    System.out.println(b);
		} catch (RenderEngineException | DeserializeException | IOException e) {
			e.printStackTrace();
		}
		System.exit(1);

	}

}
