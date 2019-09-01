package org.lbd.ifc2lbd;

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
	
	final boolean hasGeolocation;

	public ConversionThread(String ifc_filename, String uriBase, String target_file,int props_level,boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,boolean hasSeparatePropertiesModel,boolean hasPropertiesBlankNodes, boolean hasGeolocation) {
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
		this.hasGeolocation=hasGeolocation;
	}

	public Integer call() throws Exception {
		try {
			try {
				new IFCtoLBDConverter(ifc_filename, uriBase, target_file,this.props_level,this.hasBuildingElements,this.hasSeparateBuildingElementsModel,
						this.hasBuildingProperties,this.hasSeparatePropertiesModel,this.hasPropertiesBlankNodes, this.hasGeolocation);
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
