package org.linkedbuildingdata.ifc2lbd.core;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

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

	

	public Optional<String> convert_into_rdf(String ifcFile, String outputFile, String baseURI,boolean hasPerformanceBoost) {
		this.i = 0;
		PrintStream orgSystemOut = System.out;
		PrintStream orgSystemError = System.err;
		
		
		//System.out.println("ifcfile is: "+ifcFile);
		try {
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					IFCtoRDF.this.eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF running  " + IFCtoRDF.this.i++));
				}
			}, 1000, 1000);

			setup(ifcFile);
			convert(ifcFile, outputFile, baseURI,hasPerformanceBoost);
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
