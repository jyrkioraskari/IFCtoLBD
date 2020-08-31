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

/******************************************************************************
 * Copyright (C) 2009-2018  BIMserver.org
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

import org.bimserver.geometry.Matrix;
import org.bimserver.plugins.renderengine.RenderEngineException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class IfcGeomServerClientEntity {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private int id;
	private String guid;
	private String name;
	private String type;
	private int parentId;
	private double[] matrix;
	private int repId;
	private float[] positions;
	private float[] normals;
	private int[] indices;
	private float[] colors;
	private int[] materialIndices;
	private ObjectNode extendedData;
	
	public IfcGeomServerClientEntity(int id, String guid, String name,
			String type, int parentId, double[] matrix, int repId,
			float[] positions, float[] normals, int[] indices, float[] colors,
			int[] materialIndices, String messageRemainder) {
		super();
		this.id = id;
		this.guid = guid;
		this.name = name;
		this.type = type;
		this.parentId = parentId;
		this.matrix = Matrix.changeOrientation(matrix);
		this.repId = repId;
		this.positions = positions;
		this.normals = normals;
		this.indices = indices;
		this.colors = colors;
		this.materialIndices = materialIndices;
		
		if (messageRemainder != null && messageRemainder.length() > 0) {
			// un-pad string
			try {
				this.extendedData = OBJECT_MAPPER.readValue(messageRemainder.trim(), ObjectNode.class);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public int getId() {
		return id;
	}

	public String getGuid() {
		return guid;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public int getParentId() {
		return parentId;
	}

	public double[] getMatrix() {
		return matrix;
	}

	public int getRepId() {
		return repId;
	}

	public float[] getPositions() {
		return positions;
	}

	public float[] getNormals() {
		return normals;
	}

	public int[] getIndices() {
		return indices;
	}

	public float[] getColors() {
		return colors;
	}

	public int[] getMaterialIndices() {
		return materialIndices;
	}
	
	public int getNumberOfPrimitives() {
		return indices.length / 3;
	}
	
	public int getNumberOfColors() {
		return colors.length / 4;
	}
	
	public ObjectNode getAllExtendedData() throws RenderEngineException {
		return this.extendedData;
	}
}