package de.rwth_aachen.dc.lbd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

/*
 *   
 *  Copyright (c) 2023, 2024 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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
	private IfcOpenShellGeometryIteratorModel geometryIteratorModel = null;
	private final File ifcFile;
	private Map<String, String> stepEntities = null;

	public IFCGeometry(File ifcFile) {
		this.ifcFile = ifcFile;

		if (Boolean.parseBoolean(System.getProperty("ifctolbd.ifcopenshell.iterator", "true"))) {
			try {
				this.geometryIteratorModel = new IfcOpenShellGeometryIteratorModel(ifcFile);
				return;
			} catch (Exception e) {
				System.err.println("IfcOpenShell geometry iterator unavailable, falling back to IfcGeomServer: "
						+ e.getMessage());
			}
		}

		ExecutorService executor = Executors.newCachedThreadPool();
		Callable<IfcOpenShellModel> task = new Callable<>() {
			public IfcOpenShellModel call() {
				IFCGeometry.this.renderEngineModel = getRenderEngineModel(ifcFile);
				return IFCGeometry.this.renderEngineModel;
			}
		};
		Future<IfcOpenShellModel> future = executor.submit(task);
		try {
			// Should be done in 4 minutes
			this.renderEngineModel = future.get(4, TimeUnit.MINUTES);
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

	public BoundingBox getBoundingBox(String guid) {
		if (this.geometryIteratorModel != null) {
			return this.geometryIteratorModel.getBoundingBox(guid);
		}
		BoundingBox boundingBox = null;

		if (this.renderEngineModel == null)
			return null;
		IfcOpenShellEntityInstance renderEngineInstance;
		renderEngineInstance = this.renderEngineModel.getInstanceFromGUID(guid);

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

	private static Point3d processExtends(double[] transformationMatrix, ByteBuffer byteBuffer) {
		double x = byteBuffer.getDouble();
		double y = byteBuffer.getDouble();
		double z = byteBuffer.getDouble();

		double[] result = new double[4];
		Matrix.multiplyMV(result, 0, transformationMatrix, 0, new double[] { x, y, z, 1 }, 0);

		Point3d point = new Point3d(result[0], result[1], result[2]);
		return point;

	}

	public ObjDescription getOBJ(String guid) {
		if (this.geometryIteratorModel != null) {
			return this.geometryIteratorModel.getOBJ(guid);
		}
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
				ver = ver.position(0);
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

	public MTLDescription getMTL(String guid) {
		if (this.geometryIteratorModel != null) {
			return this.geometryIteratorModel.getMTL(guid);
		}
		MTLDescription mtl_desc = null;

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
				Optional<MTLDescription.MTLMaterial> visualStyleMaterial = Optional.empty();
				try {
					visualStyleMaterial = getVisualStyleMaterial(guid);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (visualStyleMaterial.isPresent()) {
					mtl_desc = new MTLDescription();
					mtl_desc.addMaterial(visualStyleMaterial.get());
					return mtl_desc;
				}
				mtl_desc = new MTLDescription();
				List<Integer> usedMaterialIndices = getUsedMaterialIndicesByFaceCount(geometry);
				Map<Integer, MTLDescription.MTLMaterial> materialsByIndex = new HashMap<>();
				ByteBuffer materials = geometry.getMaterials().duplicate().order(ByteOrder.nativeOrder());
				materials = materials.position(0);
				int materialIndex = 0;
				while (materials.remaining() >= Float.BYTES * 4) {
					MTLDescription.MTLMaterial material = processMTLMaterial(materials, materialIndex);
					materialsByIndex.put(materialIndex, material);
					materialIndex++;
				}
				for (Integer usedMaterialIndex : usedMaterialIndices) {
					MTLDescription.MTLMaterial material = materialsByIndex.get(usedMaterialIndex);
					if (material != null) {
						mtl_desc.addMaterial(material);
					}
				}
				if (usedMaterialIndices.isEmpty()) {
					for (int i = 0; i < materialIndex; i++) {
						MTLDescription.MTLMaterial material = materialsByIndex.get(i);
						if (material != null) {
							mtl_desc.addMaterial(material);
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return mtl_desc;
	}

	public String getWireframeWKT(String guid) {
		if (this.geometryIteratorModel != null) {
			return this.geometryIteratorModel.getWireframeWKT(guid);
		}
		return WireframeWKT.fromObj(getOBJ(guid));
	}

	private Optional<MTLDescription.MTLMaterial> getVisualStyleMaterial(String guid) throws IOException {
		Map<String, String> entities = getStepEntities();
		Optional<String> productDefinitionShapeId = findProductDefinitionShapeId(entities, guid);
		if (productDefinitionShapeId.isEmpty()) {
			return Optional.empty();
		}

		Set<String> geometryItemIds = findGeometryItemIds(entities, productDefinitionShapeId.get());
		for (String styledItem : entities.values()) {
			if (!isEntityType(styledItem, "IFCSTYLEDITEM")) {
				continue;
			}
			List<String> styledItemArgs = splitEntityArguments(styledItem);
			if (styledItemArgs.size() < 2 || !geometryItemIds.contains(stripReference(styledItemArgs.get(0)))) {
				continue;
			}
			Optional<double[]> color = findColorFromStyleAssignments(entities, extractReferences(styledItemArgs.get(1)));
			if (color.isPresent()) {
				double[] specular = new double[] { 0.0, 0.0, 0.0 };
				return Optional.of(new MTLDescription.MTLMaterial("material_0", color.get(), color.get(), specular, 1.0));
			}
		}
		return Optional.empty();
	}

	private Map<String, String> getStepEntities() throws IOException {
		if (this.stepEntities == null) {
			this.stepEntities = readStepEntities(this.ifcFile.toPath());
		}
		return this.stepEntities;
	}

	private static Map<String, String> readStepEntities(Path ifcPath) throws IOException {
		Map<String, String> entities = new HashMap<>();
		StringBuilder entity = new StringBuilder();
		for (String line : Files.readAllLines(ifcPath, StandardCharsets.UTF_8)) {
			String trimmed = line.trim();
			if (!trimmed.startsWith("#") && entity.length() == 0) {
				continue;
			}
			entity.append(trimmed);
			if (trimmed.endsWith(";")) {
				String entityText = entity.toString();
				int equalsIndex = entityText.indexOf('=');
				if (equalsIndex > 1) {
					entities.put(entityText.substring(1, equalsIndex).trim(), entityText);
				}
				entity.setLength(0);
			}
		}
		return entities;
	}

	private static Optional<String> findProductDefinitionShapeId(Map<String, String> entities, String guid) {
		String guidToken = "'" + guid + "'";
		for (String entity : entities.values()) {
			if (!entity.contains(guidToken)) {
				continue;
			}
			List<String> args = splitEntityArguments(entity);
			if (args.size() > 6) {
				String representationId = stripReference(args.get(6));
				if (!representationId.isEmpty()) {
					return Optional.of(representationId);
				}
			}
		}
		return Optional.empty();
	}

	private static Set<String> findGeometryItemIds(Map<String, String> entities, String productDefinitionShapeId) {
		String productDefinitionShape = entities.get(productDefinitionShapeId);
		if (productDefinitionShape == null) {
			return Collections.emptySet();
		}
		List<String> productDefinitionShapeArgs = splitEntityArguments(productDefinitionShape);
		if (productDefinitionShapeArgs.size() < 3) {
			return Collections.emptySet();
		}

		Set<String> geometryItemIds = new HashSet<>();
		for (String representationId : extractReferences(productDefinitionShapeArgs.get(2))) {
			String representation = entities.get(representationId);
			if (representation == null) {
				continue;
			}
			List<String> representationArgs = splitEntityArguments(representation);
			if (representationArgs.size() >= 4) {
				geometryItemIds.addAll(extractReferences(representationArgs.get(3)));
			}
		}
		return geometryItemIds;
	}

	private static Optional<double[]> findColorFromStyleAssignments(Map<String, String> entities,
			Collection<String> styleAssignmentIds) {
		for (String styleAssignmentId : styleAssignmentIds) {
			String styleAssignment = entities.get(styleAssignmentId);
			if (styleAssignment == null) {
				continue;
			}
			List<String> styleAssignmentArgs = splitEntityArguments(styleAssignment);
			List<String> surfaceStyleIds = isEntityType(styleAssignment, "IFCPRESENTATIONSTYLEASSIGNMENT")
					&& !styleAssignmentArgs.isEmpty() ? extractReferences(styleAssignmentArgs.get(0))
							: List.of(styleAssignmentId);
			for (String surfaceStyleId : surfaceStyleIds) {
				Optional<double[]> color = findColorFromSurfaceStyle(entities, surfaceStyleId);
				if (color.isPresent()) {
					return color;
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<double[]> findColorFromSurfaceStyle(Map<String, String> entities, String surfaceStyleId) {
		String surfaceStyle = entities.get(surfaceStyleId);
		if (surfaceStyle == null || !isEntityType(surfaceStyle, "IFCSURFACESTYLE")) {
			return Optional.empty();
		}
		List<String> surfaceStyleArgs = splitEntityArguments(surfaceStyle);
		if (surfaceStyleArgs.size() < 3) {
			return Optional.empty();
		}
		for (String renderingId : extractReferences(surfaceStyleArgs.get(2))) {
			String rendering = entities.get(renderingId);
			if (rendering == null || (!isEntityType(rendering, "IFCSURFACESTYLERENDERING")
					&& !isEntityType(rendering, "IFCSURFACESTYLESHADING"))) {
				continue;
			}
			List<String> renderingArgs = splitEntityArguments(rendering);
			if (!renderingArgs.isEmpty()) {
				Optional<double[]> color = readColor(entities, stripReference(renderingArgs.get(0)));
				if (color.isPresent()) {
					return color;
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<double[]> readColor(Map<String, String> entities, String colorId) {
		String color = entities.get(colorId);
		if (color == null || !isEntityType(color, "IFCCOLOURRGB")) {
			return Optional.empty();
		}
		List<String> colorArgs = splitEntityArguments(color);
		if (colorArgs.size() < 4) {
			return Optional.empty();
		}
		return Optional.of(new double[] { clampColor(Double.parseDouble(colorArgs.get(1))),
				clampColor(Double.parseDouble(colorArgs.get(2))), clampColor(Double.parseDouble(colorArgs.get(3))) });
	}

	private static boolean isEntityType(String entity, String type) {
		int equalsIndex = entity.indexOf('=');
		if (equalsIndex < 0) {
			return false;
		}
		return entity.regionMatches(true, equalsIndex + 1, type, 0, type.length());
	}

	private static List<String> splitEntityArguments(String entity) {
		int start = entity.indexOf('(');
		int end = entity.lastIndexOf(')');
		if (start < 0 || end < start) {
			return List.of();
		}
		return splitTopLevel(entity.substring(start + 1, end));
	}

	private static List<String> splitTopLevel(String text) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		boolean inString = false;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\'') {
				inString = !inString;
			} else if (!inString) {
				if (c == '(') {
					depth++;
				} else if (c == ')') {
					depth--;
				} else if (c == ',' && depth == 0) {
					parts.add(text.substring(start, i).trim());
					start = i + 1;
				}
			}
		}
		parts.add(text.substring(start).trim());
		return parts;
	}

	private static List<String> extractReferences(String text) {
		List<String> references = new ArrayList<>();
		int index = 0;
		while (index < text.length()) {
			int hashIndex = text.indexOf('#', index);
			if (hashIndex < 0) {
				break;
			}
			int end = hashIndex + 1;
			while (end < text.length() && Character.isDigit(text.charAt(end))) {
				end++;
			}
			if (end > hashIndex + 1) {
				references.add(text.substring(hashIndex + 1, end));
			}
			index = end;
		}
		return references;
	}

	private static String stripReference(String text) {
		String trimmed = text.trim();
		return trimmed.startsWith("#") ? trimmed.substring(1) : "";
	}

	private static List<Integer> getUsedMaterialIndicesByFaceCount(RenderEngineGeometry geometry) {
		Map<Integer, Integer> useCounts = new HashMap<>();
		ByteBuffer materialIndices = geometry.getMaterialIndices();
		if (materialIndices == null) {
			return List.of();
		}

		ByteBuffer indices = materialIndices.duplicate().order(ByteOrder.nativeOrder());
		indices = indices.position(0);
		while (indices.remaining() >= Integer.BYTES) {
			int materialIndex = indices.getInt();
			if (materialIndex >= 0) {
				useCounts.merge(materialIndex, 1, Integer::sum);
			}
		}
		List<Integer> usedMaterialIndices = new ArrayList<>(useCounts.keySet());
		usedMaterialIndices.sort((left, right) -> {
			int countComparison = Integer.compare(useCounts.get(right), useCounts.get(left));
			if (countComparison != 0) {
				return countComparison;
			}
			return Integer.compare(left, right);
		});
		return usedMaterialIndices;
	}

	private static MTLDescription.MTLMaterial processMTLMaterial(ByteBuffer byteBuffer, int materialIndex) {
		double r = clampColor(byteBuffer.getFloat());
		double g = clampColor(byteBuffer.getFloat());
		double b = clampColor(byteBuffer.getFloat());
		double alpha = clampColor(byteBuffer.getFloat());

		double[] color = new double[] { r, g, b };
		double[] specular = new double[] { 0.0, 0.0, 0.0 };
		return new MTLDescription.MTLMaterial("material_" + materialIndex, color, color, specular, alpha);
	}

	private static double clampColor(double value) {
		if (Double.isNaN(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static Point3d processVertex(double[] transformationMatrix, ByteBuffer byteBuffer) {
		double x = byteBuffer.getDouble();
		double y = byteBuffer.getDouble();
		double z = byteBuffer.getDouble();

		double[] result = new double[4];
		Matrix.multiplyMV(result, 0, transformationMatrix, 0, new double[] { x, y, z, 1 }, 0);

		Point3d point = new Point3d(result[0], result[1], result[2]);
		//point = new Point3d(x, y, z);
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

	public static IfcOpenShellEngine ifcOpenShellEngine_singlethon = null;

	private static IfcOpenShellModel getRenderEngineModel(File ifcFile) {
		try {
			String ifcGeomServerLocation = OperatingSystemCopyOf_IfcGeomServer.getIfcGeomServer();
			System.out.println("ifcGeomServerLocation: " + ifcGeomServerLocation);
			Path ifcGeomServerLocationPath = Paths.get(ifcGeomServerLocation);

			if (IFCGeometry.ifcOpenShellEngine_singlethon == null) {
				IFCGeometry.ifcOpenShellEngine_singlethon = new IfcOpenShellEngine(ifcGeomServerLocationPath, false,
						false);
				IFCGeometry.ifcOpenShellEngine_singlethon.init();
			}
			// JO 2024
			try (FileInputStream ifcFileInputStream = new FileInputStream(ifcFile);) {
				//System.out.println("ifcFile: " + ifcFile);
				System.out.println("ifcOpenShell  open model");
				IfcOpenShellModel model = IFCGeometry.ifcOpenShellEngine_singlethon.openModel(ifcFileInputStream);
				//System.out.println("IfcOpenShell opens ifc: " + ifcFile.getAbsolutePath());
				System.out.println("ifcOpenShell  geometry start");
				model.generateGeneralGeometry();
				System.out.println("ifcOpenShell  geometry ends");

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
