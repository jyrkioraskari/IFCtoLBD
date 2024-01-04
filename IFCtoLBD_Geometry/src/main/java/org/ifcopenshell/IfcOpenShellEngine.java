/*******************************************************************************
*                                                                              *
* This file is part of IfcOpenShell.                                           *
*                                                                              *
* IfcOpenShell is free software: you can redistribute it and/or modify         *
* it under the terms of the Lesser GNU General Public License as published by  *
* the Free Software Foundation, either version 3.0 of the License, or          *
* (at your option) any later version.                                          *
*                                                                              *
* IfcOpenShell is distributed in the hope that it will be useful,              *
* but WITHOUT ANY WARRANTY; without even the implied warranty of               *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the                 *
* Lesser GNU General Public License for more details.                          *
*                                                                              *
* You should have received a copy of the Lesser GNU General Public License     *
* along with this program. If not, see <http://www.gnu.org/licenses/>.         *
*                                                                              *
********************************************************************************/

/*******************************************************************************
*                                                                              *
* An intermediate between the Plugin implementation and the Model              *
* implementation, nothing fancy going on here.                                 *
*                                                                              *
********************************************************************************/

package org.ifcopenshell;

import java.io.InputStream;
import java.nio.file.Path;

import org.bimserver.plugins.renderengine.Metrics;
import org.bimserver.plugins.renderengine.RenderEngine;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfcOpenShellEngine implements RenderEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger(IfcOpenShellEngine.class);
	private Path executableFilename;

	private IfcGeomServerClient client;
	private boolean calculateQuantities;
	private boolean applyLayerSets;
	
	public IfcOpenShellEngine(Path executableFilename, boolean calculateQuantities, boolean applyLayerSets) {
		this.executableFilename = executableFilename;
		this.setCalculateQuantities(calculateQuantities);
		this.setApplyLayerSets(applyLayerSets);
	}

	@Override
	public void init() throws RenderEngineException {
		LOGGER.debug("Initializing IfcOpenShell engine");
		
		this.client = new IfcGeomServerClient(this.executableFilename);
		this.client.setCalculateQuantities(isCalculateQuantities());
		this.client.setApplyLayersets(isApplyLayerSets());
	}
	
	@Override
	public void close() throws RenderEngineException {
		LOGGER.debug("Closing IfcOpenShell engine");
		if (this.client.isRunning()) {
			this.client.close();
		}
	}

	@Override
	public IfcOpenShellModel openModel(InputStream inputStream, long size) throws RenderEngineException {
		if (!this.client.isRunning()) {
			this.client = new IfcGeomServerClient(this.executableFilename);
		}
		return new IfcOpenShellModel(this.client, inputStream, size);
	}

	@Override
	public IfcOpenShellModel openModel(InputStream inputStream) throws RenderEngineException {
		if (!this.client.isRunning()) {
			this.client = new IfcGeomServerClient(this.executableFilename);
		}
		return new IfcOpenShellModel(this.client, inputStream);
	}

	@Override
	public boolean isCalculateQuantities() {
		return calculateQuantities;
	}

	public void setCalculateQuantities(boolean calculateQuantities) {
		this.calculateQuantities = calculateQuantities;
	}

	@Override
	public boolean isApplyLayerSets() {
		return this.applyLayerSets;
	}

	public void setApplyLayerSets(boolean applyLayerSets) {
		this.applyLayerSets = applyLayerSets;
	}

	@Override
	public Metrics getMetrics() {
		return null;
	}
}