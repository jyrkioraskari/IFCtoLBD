package de.rwth_aachen.dc.lbd.obj;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.rwth_aachen.dc.lbd.ObjDescription;

public class ObjGeometryTest {

	public static void main(String[] args) {
		try {
			IFCtoObj ib = new IFCtoObj(new File("c:\\ifc\\Project1.ifc"));
			ObjDescription b = ib.getOBJ("2XR7ZJ3vvFfQ0vDTZymDVE");
			// JO 2024
			try (FileOutputStream outputStream = new FileOutputStream("c:\\jo\\test.obj");) {
				byte[] strToBytes = b.toString().getBytes();
				outputStream.write(strToBytes);

				outputStream.close();
				System.out.println(b);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(1);

	}

}
