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

/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.bimserver.plugins.renderengine.RenderEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfcOpenShellEngine  {
	private static final Logger LOGGER = LoggerFactory.getLogger(IfcOpenShellEngine.class);
	private Path executableFilename;

	private IfcGeomServerClient client;
	private boolean calculateQuantities;
	private boolean applyLayerSets;
	
	public IfcOpenShellEngine(Path executableFilename, boolean calculateQuantities, boolean applyLayerSets) throws IOException {
		this.executableFilename = executableFilename;
		this.setCalculateQuantities(calculateQuantities);
		this.setApplyLayerSets(applyLayerSets);
	}

	public void init() throws RenderEngineException {
		LOGGER.debug("Initializing IfcOpenShell engine");
		
		client = new IfcGeomServerClient(executableFilename);
		client.setCalculateQuantities(isCalculateQuantities());
		client.setApplyLayersets(isApplyLayerSets());
	}
	
	public void close() throws RenderEngineException {
		LOGGER.debug("Closing IfcOpenShell engine");
		if (client.isRunning()) {
			client.close();
		}
	}

	public IfcOpenShellModel openModel(InputStream inputStream, long size) throws RenderEngineException {
		if (!client.isRunning()) {
			client = new IfcGeomServerClient(executableFilename);
		}
		try {
			return new IfcOpenShellModel(client, inputStream, size);
		} catch (IOException e) {
			throw new RenderEngineException(e);
		}
	}

	public IfcOpenShellModel openModel(InputStream inputStream) throws RenderEngineException {
		if (!client.isRunning()) {
			client = new IfcGeomServerClient(executableFilename);
		}
		try {
			return new IfcOpenShellModel(client, inputStream);
		} catch (IOException e) {
			throw new RenderEngineException(e);
		}
	}

	public boolean isCalculateQuantities() {
		return calculateQuantities;
	}

	public void setCalculateQuantities(boolean calculateQuantities) {
		this.calculateQuantities = calculateQuantities;
	}

	public boolean isApplyLayerSets() {
		return applyLayerSets;
	}

	public void setApplyLayerSets(boolean applyLayerSets) {
		this.applyLayerSets = applyLayerSets;
	}
}