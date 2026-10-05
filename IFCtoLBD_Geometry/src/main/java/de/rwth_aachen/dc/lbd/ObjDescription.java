package de.rwth_aachen.dc.lbd;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;

import javax.vecmath.Point3d;

import org.apache.commons.lang3.tuple.ImmutableTriple;



/*
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
public class ObjDescription {
	
	List<Point3d> vertices = new ArrayList<>();
	List<ImmutableTriple<Integer, Integer, Integer>> faces = new ArrayList<>();
	double[] meshVertices;
	int[] meshFaces;
	boolean meshFacesOneBased;
	private String encoded;

    public ObjDescription() {
    }

	static ObjDescription fromMesh(double[] vertices, int[] faces, boolean oneBasedFaces) {
		ObjDescription description = new ObjDescription();
		description.meshVertices = vertices;
		description.meshFaces = faces;
		description.meshFacesOneBased = oneBasedFaces;
		return description;
	}

    public void addVertex(Point3d point) {
		materializeMesh();
    	this.vertices.add(point);
		this.encoded = null;
    }
    
    public void addFace(ImmutableTriple<Integer, Integer, Integer> face) {
		materializeMesh();
    	this.faces.add(face);
		this.encoded = null;
     }

	private void materializeMesh() {
		if (this.meshVertices == null || this.meshFaces == null) {
			return;
		}
		for (int i = 0; i + 2 < this.meshVertices.length; i += 3) {
			this.vertices.add(new Point3d(this.meshVertices[i], this.meshVertices[i + 1], this.meshVertices[i + 2]));
		}
		int indexOffset = this.meshFacesOneBased ? 0 : 1;
		for (int i = 0; i + 2 < this.meshFaces.length; i += 3) {
			this.faces.add(new ImmutableTriple<>(this.meshFaces[i] + indexOffset,
					this.meshFaces[i + 1] + indexOffset, this.meshFaces[i + 2] + indexOffset));
		}
		this.meshVertices = null;
		this.meshFaces = null;
	}

    @Override
    public String toString() {
		if (this.encoded != null) {
			return this.encoded;
		}
    	
    	StringBuilder sb = new StringBuilder();
    	
    	
		if (this.meshVertices != null && this.meshFaces != null) {
			for (int i = 0; i + 2 < this.meshVertices.length; i += 3) {
				sb.append("v ").append(this.meshVertices[i]).append(' ').append(this.meshVertices[i + 1]).append(' ')
						.append(this.meshVertices[i + 2]).append('\n');
			}
			int indexOffset = this.meshFacesOneBased ? 0 : 1;
			for (int i = 0; i + 2 < this.meshFaces.length; i += 3) {
				sb.append("f ").append(this.meshFaces[i] + indexOffset).append(' ')
						.append(this.meshFaces[i + 1] + indexOffset).append(' ')
						.append(this.meshFaces[i + 2] + indexOffset).append('\n');
			}
		} else {
			for (Point3d vertex : this.vertices) {
				sb.append("v ").append(vertex.x).append(' ').append(vertex.y).append(' ').append(vertex.z).append('\n');
			}
			for (ImmutableTriple<Integer, Integer, Integer> face : this.faces) {
				sb.append("f ").append(face.left).append(' ').append(face.middle).append(' ').append(face.right)
						.append('\n');
			}
		}
		String content=sb.toString();
		//System.out.println("content"+content);
		//System.out.println("org "+content.length());
		this.encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
		
		//System.out.println("encoded "+encodedString.length());
        return this.encoded;
    }
}
