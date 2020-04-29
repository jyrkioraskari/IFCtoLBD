package org.lbd.ifc2lbd;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;

import com.openifctools.guidcompressor.GuidCompressor;

public class Tester {
	public static void main(String[] args) {
		String compressed_guid = GuidCompressor.compressGuidString("ad8339ce-4378-4e54-b917-ed305724ca9b");
		System.out.println(compressed_guid);

		// String ifcFileName="c:\\ifc\\231110AC-11-Smiley-West-04-07-2007.ifc";
		String ifcFileName = "c:\\ifc\\Duplex_A_20110505.ifc";
		File ifcFile = new File(ifcFileName);
		try {
			byte[] fileContent = Files.readAllBytes(ifcFile.toPath());

			File tempFile;
			tempFile = File.createTempFile("model-", ".ifc");
			tempFile.deleteOnExit();
			FileUtils.writeByteArrayToFile(tempFile, fileContent);
			System.out.println("Temp ifc file:" + tempFile.getAbsolutePath());
			String outputFile = tempFile.getAbsolutePath().substring(0, tempFile.getAbsolutePath().length() - 4)
					+ ".ttl";

			new IFCtoLBDConverter(ifcFile.getAbsolutePath(), "https://dot.dc.rwth-aachen.de/IFCtoLBDset#", outputFile,
					0, true, false, true, false, false, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
