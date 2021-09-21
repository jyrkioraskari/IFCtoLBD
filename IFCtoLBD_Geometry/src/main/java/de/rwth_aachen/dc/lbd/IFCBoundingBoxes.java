package de.rwth_aachen.dc.lbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

import org.bimserver.geometry.Matrix;
import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.ifcopenshell.IfcGeomServerClientEntity;
import org.ifcopenshell.IfcOpenShellEngine;
import org.ifcopenshell.IfcOpenShellEntityInstance;
import org.ifcopenshell.IfcOpenShellModel;

import de.rwth_aachen.dc.OperatingSystemCopyOf_IfcGeomServer;

public class IFCBoundingBoxes {

	private IfcOpenShellModel renderEngineModel=null;

	public IFCBoundingBoxes(File ifcFile) throws DeserializeException, IOException, RenderEngineException {
		this.renderEngineModel = getRenderEngineModel(ifcFile);

		 ExecutorService executor = Executors.newCachedThreadPool();
	        Callable<IfcOpenShellModel> task = new Callable<IfcOpenShellModel>() {
	           public IfcOpenShellModel call() {
	              return  getRenderEngineModel(ifcFile);
	           }
	        };
	        Future<IfcOpenShellModel> future = executor.submit(task);
	        try {
	            // Should be done in 4 minutes
	            this.renderEngineModel  = future.get(4, TimeUnit.MINUTES); 
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

		if(renderEngineModel==null)
		    return null; 
		IfcOpenShellEntityInstance renderEngineInstance;
		renderEngineInstance = renderEngineModel.getInstanceFromGUID(guid);

		if (renderEngineInstance == null) {
			return null;
		}

		try {
	        IfcGeomServerClientEntity geometry = renderEngineInstance.generateGeometry();
	        if (geometry != null && geometry.getIndices().length > 0) {
	            boundingBox = new BoundingBox();
	            double[] tranformationMatrix = new double[16];
	            Matrix.setIdentityM(tranformationMatrix, 0);
	            if (renderEngineInstance.getTransformationMatrix() != null) {
	                tranformationMatrix = renderEngineInstance.getTransformationMatrix();
	            }
	            for (int i = 0; i < geometry.getIndices().length; i++) {
	                Point3d p=processExtends(tranformationMatrix, geometry.getPositions(), geometry.getIndices()[i] * 3);
	                boundingBox.add(p);
	            }
	        }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
		return boundingBox;
	}

	private IfcOpenShellModel getRenderEngineModel(File ifcFile)  {
	    try {
	        String ifcGeomServerLocation = OperatingSystemCopyOf_IfcGeomServer.getIfcGeomServer();
	        System.out.println("ifcGeomServerLocation: " + ifcGeomServerLocation);
	        Path ifcGeomServerLocationPath = Paths.get(ifcGeomServerLocation);
	        IfcOpenShellEngine ifcOpenShellEngine = new IfcOpenShellEngine(ifcGeomServerLocationPath, false, false);
	        System.out.println("init");
	        ifcOpenShellEngine.init();
	        System.out.println("init done");
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

    private Point3d processExtends(double[] transformationMatrix, float[] ds, int index) {
        double x = ds[index];
        double y = ds[index + 1];
        double z = ds[index + 2];

        double[] result = new double[4];
        Matrix.multiplyMV(result, 0, transformationMatrix, 0, new double[] { x, y, z, 1 }, 0);
        
        Point3d point = new Point3d(result[0], result[1], result[2]);
              
        return point;

    }
    
    public void close()
    {
        if(this.renderEngineModel!=null)
        {
            try {
                this.renderEngineModel.close();
            } catch (RenderEngineException e) {
                // Just do it
            }
        }
    }

}