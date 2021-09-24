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

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.bimserver.plugins.renderengine.RenderEngineException;

public class ClientRunner {
	
	public static void main(String [] args)
	{
		try (IfcGeomServerClient client = new IfcGeomServerClient(IfcGeomServerClient.ExecutableSource.S3, IfcOpenShellEnginePlugin.DEFAULT_COMMIT_SHA)) {
			try {
				client.setCalculateQuantities(true);
			} catch (RenderEngineException e1) {
				e1.printStackTrace();
			}
			try {
				client.setApplyLayersets(true);
			} catch (RenderEngineException e1) {
				e1.printStackTrace();
			}
			
			try {
				client.loadModel(new FileInputStream(args[0]));
			} catch (FileNotFoundException | RenderEngineException e) {
				e.printStackTrace();
				return;
			}
			
			double t0 = java.lang.System.nanoTime();

			while (client.hasNext()) {
				try {
					IfcGeomServerClientEntity instance = client.getNext();
					if (instance == null) {
						System.out.println("Internal error");
						return;
					}
					System.out.println(String.format("%s %s", instance.getType(), instance.getGuid()));
					System.out.println(instance.getAllExtendedData().toString());
				} catch (RenderEngineException e) {
					e.printStackTrace();
					return;
				}
			}
			
			System.out.println(String.format("Conversion took %.2f seconds", (java.lang.System.nanoTime() - t0) / 1.e9));
			
		} catch (RenderEngineException e) {
			e.printStackTrace();
			return;
		}
		System.exit(0);
	}
}