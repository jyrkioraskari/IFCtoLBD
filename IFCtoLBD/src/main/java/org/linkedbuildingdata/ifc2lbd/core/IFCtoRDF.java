package org.linkedbuildingdata.ifc2lbd.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import be.ugent.IfcSpfReader;

public class IFCtoRDF extends IfcSpfReader {
	protected final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(IFCtoRDF.class);
	private int i = 0;

	private String unzip(String ifcZipFile) {
		ZipInputStream zis;
		try {
			byte[] buffer = new byte[1024];
			zis = new ZipInputStream(new FileInputStream(ifcZipFile));
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				System.out.println("entry: " + zipEntry);
				String name = zipEntry.getName().split("\\.")[0];
				File newFile = File.createTempFile("ifc", ".ifc"); 
				// fix for Windows-created archives
				File parent = newFile.getParentFile();
				if (!parent.isDirectory() && !parent.mkdirs()) {
					throw new IOException("Failed to create directory " + parent);
				}

				// write file content
				FileOutputStream fos = new FileOutputStream(newFile);
				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}
				fos.close();

				zipEntry = zis.getNextEntry();
				return newFile.getAbsolutePath();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public Optional<String> convert_into_rdf(String ifcFile, String outputFile, String baseURI) throws IOException {
		i = 0;
		PrintStream orgSystemOut = System.out;
		PrintStream orgSystemError = System.err;
		if(ifcFile.endsWith(".ifczip"))
			ifcFile=unzip(ifcFile);
		try {
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF running  " + i++));
				}
			}, 1000, 1000);

			setup(ifcFile);
			convert(ifcFile, outputFile, baseURI);
			timer.cancel();
		} catch (Exception e) {
			e.printStackTrace();
			return Optional.empty();
		} finally {
			System.setOut(orgSystemOut);
			System.setOut(orgSystemError);
		}
		return Optional.of(this.ontURI);
	}

	public Optional<String> getOntologyURI(String ifcFile) {
		try {
			setup(ifcFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Optional.of(this.ontURI);
	}
}
