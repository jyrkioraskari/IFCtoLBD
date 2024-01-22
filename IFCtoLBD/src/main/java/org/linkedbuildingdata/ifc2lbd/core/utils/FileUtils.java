package org.linkedbuildingdata.ifc2lbd.core.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

/*
 *  Copyright (c) 2017 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

public abstract class FileUtils {

    /**
     * Returns a list of all matching files at the source code base. The code
     * base can be the application JAR file of the converter program.
     * 
     * Use: Lists ontology files.
     * 
     * @param dir
     *            the selected subdirectory at the code JAR
     * @param extension
     *            the searched file extension
     * @return List of files found
     */
    public static List<String> getListofFiles(String dir, String extension) {
        List<String> goodFiles = new ArrayList<>();
        //System.out.println("read files /" + dir);

        CodeSource src = IFCtoLBDConverter.class.getProtectionDomain().getCodeSource();
        //System.out.println(src.getLocation());
        try {
            URL jar = src.getLocation();
            try(ZipInputStream zip = new ZipInputStream(jar.openStream());)
            {
            while (true) {
                ZipEntry e = zip.getNextEntry();
                if (e == null)
                    break;
                String name = e.getName();
                if (name.contains("/" + dir)) {
                    if (name.contains("_") && name.endsWith(extension))
                        goodFiles.add(name);
                }
            }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        if (goodFiles.isEmpty())
            goodFiles = listFiles(src.getLocation().getFile() + dir);
        return goodFiles;
    }

    /**
     * 
     * Retuns a list of all files at the directory and in the subdirectories
     * 
     * @param dir
     *            The selected directory
     * @return List of files found
     */
    public static List<String> listFiles(String dir) {
        List<String> goodFiles = new ArrayList<>();

        File folder = new File(dir);
        File[] listOfFiles = folder.listFiles();
        if(listOfFiles==null)
            return goodFiles;
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile())
                goodFiles.add(listOfFiles[i].getAbsolutePath());
            else if (listOfFiles[i].isDirectory())
                goodFiles.addAll(listFiles(listOfFiles[i].getAbsolutePath()));
        }
        return goodFiles;
    }
}
