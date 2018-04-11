package org.lbd.ifc2lbd;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import org.lbd.ifc2lbd.messages.ProcessReadyEvent;
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
	final private int props_level;
	final private boolean hasBuildingElements;
	final private boolean hasBuildingProperties;
	
	final boolean hasSeparateBuildingElementsModel; 
	final boolean hasPropertiesBlankNodes;
	final boolean hasSeparatePropertiesModel;

	public ConversionThread(String ifc_filename, String uriBase, String target_file,int props_level,boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,boolean hasSeparatePropertiesModel,boolean hasPropertiesBlankNodes) {
		super();
		this.ifc_filename = ifc_filename;
		this.uriBase = uriBase;
		this.target_file = target_file;
		this.props_level=props_level;
		this.hasBuildingElements=hasBuildingElements;
		this.hasBuildingProperties=hasBuildingProperties;
		
		this.hasSeparateBuildingElementsModel=hasSeparateBuildingElementsModel;
		this.hasSeparatePropertiesModel=hasSeparatePropertiesModel;
		this.hasPropertiesBlankNodes=hasPropertiesBlankNodes;
	}

	public Integer call() throws Exception {
		try {
			try {
				new IFCtoLBDConverter(ifc_filename, uriBase, target_file,this.props_level,this.hasBuildingElements,this.hasSeparateBuildingElementsModel,
						this.hasBuildingProperties,this.hasSeparatePropertiesModel,this.hasPropertiesBlankNodes);
			} catch (OutOfMemoryError e) {
				e.printStackTrace();
				eventBus.post(new SystemStatusEvent(e.getMessage()));
				eventBus.post(new ProcessReadyEvent());
				return -1;
			}
			eventBus.post(new ProcessReadyEvent());
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			eventBus.post(new ProcessReadyEvent());
			eventBus.post(new SystemStatusEvent(e.getMessage()));
		}
		return -1;
	}


}
