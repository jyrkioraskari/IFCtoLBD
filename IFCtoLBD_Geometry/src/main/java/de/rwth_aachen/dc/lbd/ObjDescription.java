package de.rwth_aachen.dc.lbd;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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

    public ObjDescription() {
    }

    public void addVertex(Point3d point) {
    	this.vertices.add(point);
    }
    
    public void addFace(ImmutableTriple<Integer, Integer, Integer> face) {
    	this.faces.add(face);
     }

    @Override
    public String toString() {
    	
    	StringBuilder sb = new StringBuilder();
    	
    	
    	for(Point3d v:this.vertices)
    	{
    		sb.append("v "+v.x+" "+v.y+" "+v.z+"\n");
    		
    	}
		    
    	
		for(ImmutableTriple<Integer, Integer, Integer> f:this.faces)
    		sb.append("f "+f.left+" "+f.middle+" "+f.right+"\n");
		String content=sb.toString();
		//System.out.println("content"+content);
		//System.out.println("org "+content.length());
		String encodedString = Base64.getEncoder().encodeToString(content.getBytes());
		
		//System.out.println("encoded "+encodedString.length());
        return encodedString;
    }
}
