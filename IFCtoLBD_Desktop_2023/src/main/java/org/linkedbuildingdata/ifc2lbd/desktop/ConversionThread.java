package org.linkedbuildingdata.ifc2lbd.desktop;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.messages.ProcessReadyEvent;

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
	@SuppressWarnings("unused")
	private final static Logger logger = Logger.getLogger(ConversionThread.class.getName());
	private final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
	
	final private IFCtoLBDConverter converter;
	final private Set<String> selected_types;
	//final private String ifc_filename;
	//final private String uriBase;
	//final private String target_file;
	//final private int props_level;
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
    final boolean hasHierarchicalNaming;

	public ConversionThread(IFCtoLBDConverter converter,Set<String> selected_types,String ifc_filename, String uriBase, String target_file,int props_level,boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,boolean hasSeparatePropertiesModel,boolean hasPropertiesBlankNodes, boolean hasGeolocation,boolean hasGeometry,boolean exportIfcOWL,boolean hasUnits,boolean hasPerformanceBoost,boolean hasBoundingBoxWKT,boolean hasHierarchicalNaming) {
		super();
		this.converter=converter;
		this.selected_types=selected_types;
		//this.ifc_filename = ifc_filename;
		//this.uriBase = uriBase;
		//this.target_file = target_file;
		//this.props_level=props_level;
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
		this.hasHierarchicalNaming=hasHierarchicalNaming;
	}

	public Integer call() throws Exception {
		try {
			try {
				converter.setSelected_types(selected_types);
				converter.convert_LBD_phase(hasBuildingElements,
						hasSeparateBuildingElementsModel, hasBuildingProperties, hasSeparatePropertiesModel,
						hasGeolocation, hasGeometry, exportIfcOWL, hasUnits, hasBoundingBoxWKT, hasHierarchicalNaming);
			} catch (OutOfMemoryError e) {
				e.printStackTrace();
				eventBus.post(new IFCtoLBD_SystemStatusEvent(e.getMessage()));
				eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.ERROR));
				return -1;
			}
			eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.CONVERT));
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.ERROR));
			eventBus.post(new IFCtoLBD_SystemStatusEvent(e.getMessage()));
		}
		return -1;
	}


}
