package com.buildingsmart.tech.ifcowl;

import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Map;

import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;
/*
 *  Copyright (c)2020 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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
public class ConvertToXML2 {

	public static void main(String[] args) throws IOException {
		File dir = new File("src/main/resources");
		if (dir.isDirectory()) {
			for (String f : dir.list()) {
				if (f.endsWith(".ser")) {
					File t = new File("src/main/resources/"+f);
					if (t.getName().startsWith("ent")) {
						System.out.println(f);
						InputStream fis = new FileInputStream(t);
						System.out.println("fis: "+fis);
						ObjectInputStream ois = new ObjectInputStream(fis);

						Map<String, EntityVO> ent = null;
						try {
							ent = (Map<String, EntityVO>) ois.readObject();
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						} finally {
							ois.close();
						}
						
						
						//XStream xstream = new XStream();
						 XMLEncoder e = new XMLEncoder(
		                          new BufferedOutputStream(
		                              new FileOutputStream("src/main/resources/"+f+".xml")));
		                 e.writeObject(ent);
		                 e.close();
					}
					if (t.getName().startsWith("typ")) {
						System.out.println(f);
						InputStream fis = new FileInputStream(t);
						        
						ObjectInputStream ois = new ObjectInputStream(fis);
				            Map<String, TypeVO> typ = null;
				            try {
				                typ = (Map<String, TypeVO>) ois.readObject();
				            } catch (ClassNotFoundException e) {
				                e.printStackTrace();
				            } finally {
				                ois.close();
				            }
				            XMLEncoder e = new XMLEncoder(
			                          new BufferedOutputStream(
			                              new FileOutputStream("src/main/resources/"+f+".xml")));
			                 e.writeObject(typ);
			                 e.close();
					}
				}
			}
		}

	}

}
