package de.rwth_aachen.dc.lbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.vecmath.Point3d;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.bimserver.geometry.Matrix;
import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.RenderEngineGeometry;
import org.ifcopenshell.IfcGeomServerClientEntity;
import org.ifcopenshell.IfcOpenShellEngine;
import org.ifcopenshell.IfcOpenShellEntityInstance;
import org.ifcopenshell.IfcOpenShellModel;

import de.rwth_aachen.dc.OperatingSystemCopyOf_IfcGeomServer;

/*
 *   
 *  Copyright (c) 2023 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

public class IFCGeometry {

	private IfcOpenShellModel renderEngineModel = null;

	public IFCGeometry(File ifcFile) throws DeserializeException, IOException, RenderEngineException {

		ExecutorService executor = Executors.newCachedThreadPool();
		Callable<IfcOpenShellModel> task = new Callable<IfcOpenShellModel>() {
			public IfcOpenShellModel call() {
				renderEngineModel = getRenderEngineModel(ifcFile);
				return renderEngineModel;
			}
		};
		Future<IfcOpenShellModel> future = executor.submit(task);
		try {
			// Should be done in 4 minutes
			this.renderEngineModel = future.get(4, TimeUnit.MINUTES);
		} catch (TimeoutException ex) {
			System.out.println("Timeout");
		} catch (InterruptedException e) {
		} catch (ExecutionException e) {
		} finally {
			future.cancel(true); // may or may not desire this
		}

	}

	public BoundingBox getBoundingBox(String guid) {
		BoundingBox boundingBox = null;

		if (renderEngineModel == null)
			return null;
		IfcOpenShellEntityInstance renderEngineInstance;
		renderEngineInstance = renderEngineModel.getInstanceFromGUID(guid);

		if (renderEngineInstance == null) {
			return null;
		}

		try {
			RenderEngineGeometry geometry = renderEngineInstance.generateGeometry();
			if (geometry != null && geometry.getIndices().limit() > 0) {
				boundingBox = new BoundingBox();
				double[] tranformationMatrix = new double[16];
				Matrix.setIdentityM(tranformationMatrix, 0);
				if (renderEngineInstance.getTransformationMatrix() != null) {
					tranformationMatrix = renderEngineInstance.getTransformationMatrix();
				}
				ByteBuffer ver = geometry.getVertices().order(ByteOrder.nativeOrder());
				
				while (ver.hasRemaining()) {
					Point3d p = processExtends(tranformationMatrix, ver);
					boundingBox.add(p);
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return boundingBox;
	}

	private Point3d processExtends(double[] transformationMatrix, ByteBuffer byteBuffer) {
		double x = byteBuffer.getDouble();
		double y = byteBuffer.getDouble();
		double z = byteBuffer.getDouble();

		double[] result = new double[4];
		Matrix.multiplyMV(result, 0, transformationMatrix, 0, new double[] { x, y, z, 1 }, 0);

		Point3d point = new Point3d(result[0], result[2], result[1]);
		return point;

	}

	public ObjDescription getOBJ(String guid) {
		ObjDescription obj_desc = null;

		if (renderEngineModel == null)
			return null;
		IfcOpenShellEntityInstance renderEngineInstance;
		renderEngineInstance = renderEngineModel.getInstanceFromGUID(guid);

		if (renderEngineInstance == null) {
			return null;
		}

		try {
			RenderEngineGeometry geometry = renderEngineInstance.generateGeometry();

			if (geometry != null && geometry.getIndices().limit() > 0) {
				obj_desc = new ObjDescription();
				double[] tranformationMatrix = new double[16];
				Matrix.setIdentityM(tranformationMatrix, 0);
				if (renderEngineInstance.getTransformationMatrix() != null) {
					tranformationMatrix = renderEngineInstance.getTransformationMatrix();
				}

				ByteBuffer ver = geometry.getVertices().order(ByteOrder.nativeOrder());
				ver=ver.position(0);
				while (ver.hasRemaining()) {
					Point3d p = processVertex(tranformationMatrix, ver);
					obj_desc.addVertex(p);
				}

				ByteBuffer inx = geometry.getIndices().order(ByteOrder.nativeOrder());

				while (inx.hasRemaining()) {
					ImmutableTriple<Integer, Integer, Integer> f = processSurface(inx);
					obj_desc.addFace(f);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return obj_desc;
	}

	private Point3d processVertex(double[] transformationMatrix, ByteBuffer byteBuffer) {
		double x = byteBuffer.getDouble();
		double y = byteBuffer.getDouble();
		double z = byteBuffer.getDouble();

		double[] result = new double[4];
		Matrix.multiplyMV(result, 0, transformationMatrix, 0, new double[] { x, y, z, 1 }, 0);

		Point3d point = new Point3d(result[0], result[2], result[1]);
		return point;

	}

	private ImmutableTriple<Integer, Integer, Integer> processSurface(ByteBuffer byteBuffer) {

		int xi = byteBuffer.getInt();
		int yi = byteBuffer.getInt();
		int zi = byteBuffer.getInt();

		ImmutableTriple<Integer, Integer, Integer> point = new ImmutableTriple<Integer, Integer, Integer>(xi + 1,
				yi + 1, zi + 1);
		return point;

	}

	private IfcOpenShellModel getRenderEngineModel(File ifcFile) {
		try {
			String ifcGeomServerLocation = OperatingSystemCopyOf_IfcGeomServer.getIfcGeomServer();
			System.out.println("ifcGeomServerLocation: " + ifcGeomServerLocation);
			Path ifcGeomServerLocationPath = Paths.get(ifcGeomServerLocation);
			IfcOpenShellEngine ifcOpenShellEngine = new IfcOpenShellEngine(ifcGeomServerLocationPath, false, false);
			ifcOpenShellEngine.init();
			FileInputStream ifcFileInputStream = new FileInputStream(ifcFile);

			System.out.println("ifcFile: " + ifcFile);
			IfcOpenShellModel model = ifcOpenShellEngine.openModel(ifcFileInputStream);
			System.out.println("IfcOpenShell opens ifc: " + ifcFile.getAbsolutePath());

			model.generateGeneralGeometry();
			return model;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public void close() {
		if (this.renderEngineModel != null) {
			try {
				this.renderEngineModel.close();
			} catch (RenderEngineException e) {
				// Just do it
			}
		}
	}

}