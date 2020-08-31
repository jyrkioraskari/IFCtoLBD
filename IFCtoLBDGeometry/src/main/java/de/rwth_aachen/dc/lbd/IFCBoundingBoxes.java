package de.rwth_aachen.dc.lbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.vecmath.Point3d;

import org.bimserver.geometry.Matrix;
import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.ifcopenshell.IfcGeomServerClientEntity;
import org.ifcopenshell.IfcOpenShellEngine;
import org.ifcopenshell.IfcOpenShellEntityInstance;
import org.ifcopenshell.IfcOpenShellModel;

import de.rwth_aachen.dc.OperatingSystemCopyOf_IfcGeomServer;
import nl.tue.ddss.bcf.BoundingBox;

public class IFCBoundingBoxes {

	private final IfcOpenShellModel renderEngineModel;

	public IFCBoundingBoxes(File ifcFile) throws DeserializeException, IOException, RenderEngineException {
		this.renderEngineModel = getRenderEngineModel(ifcFile);

	}

	public BoundingBox getBoundingBox(String guid) {
		BoundingBox boundingBox = null;

		IfcOpenShellEntityInstance renderEngineInstance;
		renderEngineInstance = renderEngineModel.getInstancesByGuid().get(guid);

		if (renderEngineInstance == null) {
			return null;
		}
		IfcGeomServerClientEntity geometry = renderEngineInstance.generateGeometry();
		 if (geometry != null && geometry.getIndices().length > 0) {
				boundingBox = new BoundingBox();
				double[] tranformationMatrix = new double[16];
				if (renderEngineInstance.getTransformationMatrix() != null) {
				    tranformationMatrix = renderEngineInstance.getTransformationMatrix();
				    tranformationMatrix = Matrix.changeOrientation(tranformationMatrix);
				} else {
				    Matrix.setIdentityM(tranformationMatrix, 0);
				}

				float[] vertices=geometry.getPositions();
				for (int i = 0; i < geometry.getPositions().length; i+=3) {
				    processExtends(boundingBox, tranformationMatrix, vertices[i],vertices[i+1],vertices[i+2]);
				}
			    }
		return boundingBox;
	}

	private IfcOpenShellModel getRenderEngineModel(File ifcFile) throws RenderEngineException, IOException {
		String ifcGeomServerLocation = OperatingSystemCopyOf_IfcGeomServer.getIfcGeomServer();
		System.out.println("ifcGeomServerLocation: "+ifcGeomServerLocation);
		Path ifcGeomServerLocationPath = Paths.get(ifcGeomServerLocation);
		IfcOpenShellEngine ifcOpenShellEngine = new IfcOpenShellEngine(ifcGeomServerLocationPath, false, false);
		System.out.println("init");
		ifcOpenShellEngine.init();
		System.out.println("init done");
		FileInputStream ifcFileInputStream = new FileInputStream(ifcFile);

		System.out.println("ifcFile: "+ifcFile);
		IfcOpenShellModel model = ifcOpenShellEngine.openModel(ifcFileInputStream);
		System.out.println("IfcOpenShell opens ifc: " + ifcFile.getAbsolutePath());
		
		model.generateGeneralGeometry();
		return model;
	}

	  private void processExtends(BoundingBox boundingBox, double[] transformationMatrix, double x, double y, double z) {
			double[] result = new double[4];
			Matrix.multiplyMV(result, 0, transformationMatrix, 0, new double[] { x, y, z, 1 }, 0);
			x = result[0];
			y = result[1];
			z = result[2];
			Point3d point = new Point3d(x / 1000, y / 1000, z / 1000);
			boundingBox.add(point);
		    }

}