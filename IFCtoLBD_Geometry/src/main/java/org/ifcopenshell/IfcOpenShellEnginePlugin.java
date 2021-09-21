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
 * This class ensures that a valid binary is available for the platform the     *
 * code is running on.                                                          *
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
import java.nio.file.Path;

import org.bimserver.models.store.ObjectDefinition;
import org.bimserver.models.store.ParameterDefinition;
import org.bimserver.models.store.PrimitiveDefinition;
import org.bimserver.models.store.PrimitiveEnum;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.plugins.PluginConfiguration;
import org.bimserver.plugins.PluginContext;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.VersionInfo;
import org.bimserver.shared.exceptions.PluginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfcOpenShellEnginePlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(IfcOpenShellEnginePlugin.class);
	public static final String BRANCH = "v0.6.0";
	public static final String DEFAULT_COMMIT_SHA = "4380e1d";
	private static final String COMMIT_SHA_SETTING = "commitsha";
	private static final String CALCULATE_QUANTITIES_SETTING = "calculatequantities";
	private static final String APPLY_LAYER_SETS = "applylayersets";
	private Path executableFilename;
	private VersionInfo versionInfo;

	public IfcOpenShellEngine createRenderEngine(PluginConfiguration pluginConfiguration, String schema) throws RenderEngineException {
		try {
			boolean calculateQuantities = pluginConfiguration.getBoolean("CALCULATE_QUANTITIES_SETTING", true);
			boolean applyLayerSets = pluginConfiguration.getBoolean("CALCULATE_QUANTITIES_SETTING", true);
			return new IfcOpenShellEngine(executableFilename, calculateQuantities, applyLayerSets);
		} catch (IOException e) {
			throw new RenderEngineException(e);
		}
	}

	public void init(PluginContext pluginContext, PluginConfiguration systemSettings) throws PluginException {
		// Make sure an executable is downloaded before invoking the plug-in using multiple threads.
		// This also checks whether the version of the executable matches the java source.
		
		String commitSha = DEFAULT_COMMIT_SHA;
		if (systemSettings != null && systemSettings.getString(COMMIT_SHA_SETTING) != null) {
			// Overruled by system settings
			commitSha = systemSettings.getString(COMMIT_SHA_SETTING);
			LOGGER.info("Using overruled system setting for commit sha");
		}
		
		IfcGeomServerClient test = new IfcGeomServerClient(IfcGeomServerClient.ExecutableSource.S3, commitSha, pluginContext.getTempDir());
		executableFilename = test.getExecutableFilename();
		
		versionInfo = new VersionInfo(BRANCH, commitSha, test.getVersion(), test.getBuildDateTime(), test.getPlatform());
		
		LOGGER.info("Using " + executableFilename);
		test.close();
	}

	public ObjectDefinition getUserSettingsDefinition() {
		return null;
	}
	
	public ObjectDefinition getSystemSettingsDefinition() {
		ObjectDefinition settings = StoreFactory.eINSTANCE.createObjectDefinition();
		
		PrimitiveDefinition stringType = StoreFactory.eINSTANCE.createPrimitiveDefinition();
		stringType.setType(PrimitiveEnum.STRING);

		PrimitiveDefinition booleanType = StoreFactory.eINSTANCE.createPrimitiveDefinition();
		booleanType.setType(PrimitiveEnum.BOOLEAN);
		
		ParameterDefinition commitShaParameter = StoreFactory.eINSTANCE.createParameterDefinition();
		commitShaParameter.setIdentifier(COMMIT_SHA_SETTING);
		commitShaParameter.setName("Commit Sha");
		commitShaParameter.setDescription("Commit sha of IfcOpenShell binary, this overrules the default for the currently installated IfcOpenShell plugin");
		commitShaParameter.setType(stringType);
		commitShaParameter.setRequired(false);

		ParameterDefinition calculateQuantities = StoreFactory.eINSTANCE.createParameterDefinition();
		calculateQuantities.setIdentifier(CALCULATE_QUANTITIES_SETTING);
		calculateQuantities.setName("Calculate Quantities");
		calculateQuantities.setDescription("Calculates volumes and areas, Takes a bit more time (about 15%)");
		calculateQuantities.setType(booleanType);
		calculateQuantities.setRequired(false);

		ParameterDefinition applyLayerSets = StoreFactory.eINSTANCE.createParameterDefinition();
		applyLayerSets.setIdentifier(APPLY_LAYER_SETS);
		applyLayerSets.setName("Apply Layer Sets");
		applyLayerSets.setDescription("Splits certain objects into several layers, depending on the model can take about 10x more processing time, and results in more geometry");
		applyLayerSets.setType(booleanType);
		applyLayerSets.setRequired(false);
		
		settings.getParameters().add(commitShaParameter);
		settings.getParameters().add(calculateQuantities);
		settings.getParameters().add(applyLayerSets);
		return settings;
	}
	
	public VersionInfo getVersionInfo() {
		return versionInfo;
	}
}