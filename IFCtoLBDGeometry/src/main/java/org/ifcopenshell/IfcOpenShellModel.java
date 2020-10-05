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
* This class communicates with the JNI wrapper of IfcOpenShell. Note that,     *
* contrary to the Bonsma IFC engine, if the wrapper crashes it will take the   *
* BIMserver down with her. Since loading the wrapper involves loading a        *
* considerable binary into memory, it would have been better to make the       *
* System.load() call somewhere in IfcOpenShellEngine.java.                     *
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
import java.util.HashMap;

import org.bimserver.plugins.renderengine.RenderEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfcOpenShellModel  {
	private static final Logger LOGGER = LoggerFactory.getLogger(IfcOpenShellModel.class);
	
	private InputStream ifcInputStream;

	private HashMap<Integer,IfcOpenShellEntityInstance> instancesById;
	private HashMap<String,IfcOpenShellEntityInstance> instancesByGUID;
	
	private IfcGeomServerClient client;
	
	public IfcOpenShellModel(IfcGeomServerClient client, InputStream ifcInputStream) throws RenderEngineException, IOException {
		this.client = client;
		this.ifcInputStream = ifcInputStream;
		
		client.loadModel(ifcInputStream);
	}

	public IfcOpenShellModel(IfcGeomServerClient client, InputStream ifcInputStream, long length) throws RenderEngineException, IOException {
		this.client = client;
		this.ifcInputStream = ifcInputStream;
		
		client.loadModel(ifcInputStream, length);
	}

	public void close() throws RenderEngineException {
		if (instancesById != null) {
			instancesById.clear();
		}
		try {
			ifcInputStream.close();
		} catch (IOException e) {
			LOGGER.error("", e);
		}
	}

	public void generateGeneralGeometry() throws RenderEngineException {
		// We keep track of instances ourselves
		instancesById = new HashMap<Integer,IfcOpenShellEntityInstance>();
		instancesByGUID = new HashMap<String,IfcOpenShellEntityInstance>();

		final double t0 = (double) System.nanoTime();

		while (client.hasNext()) {
			IfcGeomServerClientEntity next = client.getNext();
			if(next==null) // JO 2020
				break;
			IfcOpenShellEntityInstance instance = new IfcOpenShellEntityInstance(next);
			System.out.println("GEN GEOM:  "+next.getGuid());
			// Store the instance in our dictionary
			instancesById.put(next.getId(), instance);
			instancesByGUID.put(next.getGuid(), instance);
		}
		
		final double t1 = (double) System.nanoTime();
		
		LOGGER.debug(String.format("Took %.2f seconds to obtain representations for %d entities", (t1-t0) / 1.E9, instancesById.size()));
	}

	public IfcOpenShellEntityInstance getInstanceFromExpressId(int oid) {
		if ( instancesById.containsKey(oid) ) {
			return instancesById.get(oid);
		} else {
			// Probably something went wrong with the processing of this element in
			// the IfcOpenShell binary, as it has not been included in the enumerated
			// set of elements with geometry.
			//throw new EntityNotFoundException("Entity " + oid + " not found in model");
		    return null;
		}
	}
	public IfcOpenShellEntityInstance getInstanceFromGUID(String guid) {
		if ( instancesByGUID.containsKey(guid) ) {
			return instancesByGUID.get(guid);
		} else {
		    return null;
		}
	}
}