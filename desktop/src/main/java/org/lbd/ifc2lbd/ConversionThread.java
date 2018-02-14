package org.lbd.ifc2lbd;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import org.lbd.ifc2lbd.messages.SystemStatusEvent;

/* Copyright (C) Fractuscan - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Jyrki Oraskari <Jyrki.Oraskari@gmail.com>, August 2017
 */

import com.google.common.eventbus.EventBus;

public class ConversionThread implements Callable<Integer> {
	private final static Logger logger = Logger.getLogger(ConversionThread.class.getName());
	private final EventBus eventBus = EventBusService.getEventBus();
	final private String ifc_filename;
	final private String uriBase;
	final private String target_file;

	public ConversionThread(String ifc_filename, String uriBase, String target_file) {
		super();
		this.ifc_filename = ifc_filename;
		this.uriBase = uriBase;
		this.target_file = target_file;
	}



	public Integer call() throws Exception {
		try {
			try {
				new IFCtoLBDConverter(ifc_filename, uriBase, target_file);
			} catch (OutOfMemoryError e) {
				e.printStackTrace();
				eventBus.post(new SystemStatusEvent(e.getMessage()));
				return -1;
			}
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			eventBus.post(new SystemStatusEvent(e.getMessage()));
		}
		return -1;
	}


}
