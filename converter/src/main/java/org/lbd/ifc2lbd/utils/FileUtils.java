package org.lbd.ifc2lbd.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.lbd.ifc2lbd.IFCtoLBDConverter;

/*
* The GNU Affero General Public License
* 
* Copyright (c) 2017, 2018, 2019 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

public class FileUtils {


	/**
	 * Returns a list of  all matching files at the source code base. The code base can be the application JAR
	 * file of the converter program. 
	 * 
	 * Use: Lists ontology files. 
	 * 
	 * @param dir    the selected subdirectory at the code JAR
	 * @param extension the searched file extension
	 * @return List of files found
	 */
	public static List<String> getListofFiles(String dir, String extension) {
		List<String> goodFiles = new ArrayList<>();
		System.out.println("read files /" + dir);

		CodeSource src = IFCtoLBDConverter.class.getProtectionDomain().getCodeSource();
		try {
			if (src != null) {
				URL jar = src.getLocation();
				ZipInputStream zip;
				zip = new ZipInputStream(jar.openStream());
				while (true) {
					ZipEntry e = zip.getNextEntry();
					if (e == null)
						break;
					String name = e.getName();
					if (name.startsWith("/" + dir)) {
						if (name.contains("_") && name.endsWith(extension))
							goodFiles.add(name);
					}
				}
			} else {
				System.out.println("No directory");
			}

		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		return goodFiles;
	}

	
	/**
	 * 
	 * Retuns a list of all files at the directory and in the subdirectories
	 * 
	 * @param dir The selected directory
	 * @@return List of files found
	 */
	public static List<String> listFiles(String dir) {
		List<String> goodFiles = new ArrayList<String>();

		File folder = new File(dir);
		File[] listOfFiles = folder.listFiles();

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile())
				goodFiles.add(listOfFiles[i].getAbsolutePath());
			else if (listOfFiles[i].isDirectory())
				goodFiles.addAll(listFiles(listOfFiles[i].getAbsolutePath()));
		}
		return goodFiles;
	}
}
