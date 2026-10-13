package org.linkedbuildingdata.ifc2lbd.desktop;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.messages.ProcessReadyEvent;
import org.linkedbuildingdata.ifc2lbd.messages.WorkflowProgressEvent;

/* Copyright (C) Fractuscan - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Jyrki Oraskari <Jyrki.Oraskari@gmail.com>, August 2017
 */

import com.google.common.eventbus.EventBus;

/*
 *  Copyright (c) 2017 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class ReadinInThread implements Callable<IFCtoLBDConverter> {
	@SuppressWarnings("unused")
	private final static Logger logger = Logger.getLogger(ReadinInThread.class.getName());
	private final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getDefaultEventBus();
	final private String ifc_filename;
	final private String uriBase;
	final private String target_file;
	final private long operationId;
	final private int props_level;
	final private boolean hasBuildingElements;
	final private boolean hasBuildingProperties;
	
	final boolean hasSeparateBuildingElementsModel; 
	final boolean hasPropertiesBlankNodes;
	final boolean hasSeparatePropertiesModel;
	
	final boolean hasGeolocation;
	
	final boolean hasGeometry;
	final boolean exportIfcOWL;
    final boolean hasUnits;
    
    final boolean hasPerformanceBoost;
    final boolean hasBoundingBoxWKT;
    final boolean hasInterfaces;

    //TODO Check this
	public ReadinInThread(long operationId, String ifc_filename, String uriBase, String target_file,int props_level,@SuppressWarnings("unused") boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, @SuppressWarnings("unused") boolean hasBuildingProperties,boolean hasSeparatePropertiesModel,boolean hasPropertiesBlankNodes, boolean hasGeolocation,boolean hasGeometry,boolean exportIfcOWL,boolean hasUnits,boolean hasPerformanceBoost,boolean hasBoundingBoxWKT,boolean hasInterfaces) {
		super();
		this.operationId = operationId;
		this.ifc_filename = ifc_filename;
		this.uriBase = uriBase;
		this.target_file = target_file;
		this.props_level=props_level;
		this.hasBuildingElements=hasBuildingElements;
		this.hasBuildingProperties=hasBuildingProperties;
		
		this.hasSeparateBuildingElementsModel=hasSeparateBuildingElementsModel;
		this.hasSeparatePropertiesModel=hasSeparatePropertiesModel;
		this.hasPropertiesBlankNodes=hasPropertiesBlankNodes;
		this.hasGeolocation=hasGeolocation;
		this.hasGeometry=hasGeometry;
		this.exportIfcOWL=exportIfcOWL;
		this.hasUnits=hasUnits;
		this.hasPerformanceBoost=hasPerformanceBoost;
		this.hasBoundingBoxWKT=hasBoundingBoxWKT;
		this.hasInterfaces=hasInterfaces;
	}

	@Override
	public IFCtoLBDConverter call() throws Exception {
		try {
			this.eventBus.post(new WorkflowProgressEvent(this.operationId, "Reading IFC model",
					WorkflowProgressEvent.INDETERMINATE));
			IFCtoLBDConverter c1nb;
			try {
				c1nb = new IFCtoLBDConverter(this.uriBase, false, this.props_level);
				boolean loaded = c1nb.convert_read_in_phase(this.ifc_filename,this.target_file, this.hasGeometry,this.hasPerformanceBoost,this.exportIfcOWL,this.hasBuildingElements,this.hasBuildingProperties,this.hasBoundingBoxWKT,this.hasUnits,this.hasInterfaces);
				if (!loaded) {
					c1nb.close();
					this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.ERROR, this.operationId));
					return null;
				}
				
				} catch (OutOfMemoryError e) {
				e.printStackTrace();
				this.eventBus.post(new IFCtoLBD_SystemStatusEvent(e.getMessage()));
				this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.ERROR, this.operationId));
				return null;
			}
			this.eventBus.post(new WorkflowProgressEvent(this.operationId, "Discovering element types and property sets", 0.9));
			this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.READ_IN, this.operationId));
			return c1nb;
		} catch (Exception e) {
			e.printStackTrace();
			this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.ERROR, this.operationId));
			this.eventBus.post(new IFCtoLBD_SystemStatusEvent(e.getMessage()));
		}
		return null;
	}


}
