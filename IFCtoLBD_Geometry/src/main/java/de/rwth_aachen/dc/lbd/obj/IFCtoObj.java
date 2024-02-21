package de.rwth_aachen.dc.lbd.obj;

import java.io.File;
import java.io.FileInputStream;
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
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.RenderEngineGeometry;
import org.ifcopenshell.IfcOpenShellEngine;
import org.ifcopenshell.IfcOpenShellEntityInstance;
import org.ifcopenshell.IfcOpenShellModel;

import de.rwth_aachen.dc.OperatingSystemCopyOf_IfcGeomServer;
import de.rwth_aachen.dc.lbd.IFCGeometry;
import de.rwth_aachen.dc.lbd.ObjDescription;

public class IFCtoObj {

	private IfcOpenShellModel renderEngineModel = null;

	public IFCtoObj(File ifcFile) {

		ExecutorService executor = Executors.newCachedThreadPool();
		Callable<IfcOpenShellModel> task = new Callable<>() {
			public IfcOpenShellModel call() {
				IFCtoObj.this.renderEngineModel = getRenderEngineModel(ifcFile);
				return IFCtoObj.this.renderEngineModel;
			}
		};
		Future<IfcOpenShellModel> future = executor.submit(task);
		try {
			// Should be done in 4 minutes
			this.renderEngineModel = future.get(12, TimeUnit.MINUTES);
		} catch (TimeoutException ex) {
			System.out.println("Timeout");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} finally {
			future.cancel(true); // may or may not desire this
		}

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

				while (ver.hasRemaining()) {
					Point3d p = processVertex(tranformationMatrix, ver);
					obj_desc.addVertex(p);
				}

				/*
				 * for (int i = 0; i < geometry.getNrIndices() / 3; i++) {
				 * ImmutableTriple<Integer, Integer, Integer> f =
				 * processSurface(geometry.getIndices(), i * 3); obj_desc.addFace(f);
				 * 
				 * }
				 */
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

	private static Point3d processVertex(double[] transformationMatrix, ByteBuffer byteBuffer) {
		double x = byteBuffer.getDouble();
		double y = byteBuffer.getDouble();
		double z = byteBuffer.getDouble();

		double[] result = new double[4];
		Matrix.multiplyMV(result, 0, transformationMatrix, 0, new double[] { x, y, z, 1 }, 0);

		Point3d point = new Point3d(result[0], result[1], result[2]);
		point = new Point3d(x, y, z);
		return point;

	}

	private static ImmutableTriple<Integer, Integer, Integer> processSurface(ByteBuffer byteBuffer) {

		int xi = byteBuffer.getInt();
		int yi = byteBuffer.getInt();
		int zi = byteBuffer.getInt();

		ImmutableTriple<Integer, Integer, Integer> point = new ImmutableTriple<>(xi + 1,
				yi + 1, zi + 1);
		return point;

	}

	private static IfcOpenShellModel getRenderEngineModel(File ifcFile) {
		try {
			String ifcGeomServerLocation = OperatingSystemCopyOf_IfcGeomServer.getIfcGeomServer();
			System.out.println("ifcGeomServerLocation: " + ifcGeomServerLocation);
			Path ifcGeomServerLocationPath = Paths.get(ifcGeomServerLocation);
			if (IFCGeometry.ifcOpenShellEngine_singlethon == null) {
				IFCGeometry.ifcOpenShellEngine_singlethon = new IfcOpenShellEngine(ifcGeomServerLocationPath, false,
						true);
				IFCGeometry.ifcOpenShellEngine_singlethon.init();
			}
			try (FileInputStream ifcFileInputStream = new FileInputStream(ifcFile);) {

				System.out.println("ifcFile: " + ifcFile);
				IfcOpenShellModel model = IFCGeometry.ifcOpenShellEngine_singlethon.openModel(ifcFileInputStream);
				System.out.println("IfcOpenShell opens ifc: " + ifcFile.getAbsolutePath());

				model.generateGeneralGeometry();
				return model;
			}
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