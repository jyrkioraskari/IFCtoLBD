
package org.linkedbuildingdata.ifc2lbd;

import java.io.File;
import java.util.List;

import org.apache.jena.sys.JenaSystem;
import org.linkedbuildingdata.ifc2lbd.core.utils.FileUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/*
 *  Copyright (c) 2022 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

@Command(name = "IFCtoLBDConverter_CLI", mixinStandardHelpOptions = true, version = "IFCtoLBDConverter_CLI")
public class IFCtoLBDConverter_CLI implements Runnable {


	@Override
	public void run() {
		
	}

	/*
    * @param ifc_filename
    *            The absolute path for the IFC file that will be converted
    * @param uriBase
    *            The URI base for all the elemenents that will be created
    * @param target_file
    *            The main file name for the output. If there are many, they
    *            will be sharing the same beginning
    * @param props_level
    *            The levels described in
    *            https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
    */
    
    @Option(names = { "-be", "--hasBuildingElements" },
    description = "The Building Elements will be created in the output.")
    private boolean[] hasBuildingElements = new boolean[0];


    @Option(names = { "-bsbm", "--hasSeparateBuildingElementsModel" },
    description = "The Building elements will have a separate file.")
    private boolean[] hasSeparateBuildingElementsModel = new boolean[0];

    @Option(names = { "-bp", "--hasBuildingProperties" },
    description = "The properties will ne added into the output.")
    private boolean[] hasBuildingProperties = new boolean[0];

    @Option(names = { "-pm", "--hasSeparatePropertiesModel" },
    description = "The properties will be written in a separate file.")
    private boolean[] hasSeparatePropertiesModel = new boolean[0];

    @Option(names = { "-b", "--hasPropertiesBlankNodes" },
    description = "Blank nodes are used.")
    private boolean[] hasPropertiesBlankNodes = new boolean[0];

    @Option(names = { "-gl", "--hasGeolocation" },
    description = "Geolocation, i.e., the latitude and longitude are added.")
    private boolean[] hasGeolocation = new boolean[0];

	
    public static void main(String[] args) {
        JenaSystem.init();
        if (args.length > 3) {
            int level=2;
            try {
                level=Integer.parseInt(args[3]);
            } catch (Exception e) {
                System.out.println("OPT level was not a number: Example: ");
                return;
            }
            System.out.println("Base URI: "+args[0]);
            System.out.println("Selected IFC File: "+args[1]);
            System.out.println("Targer TTL File: "+args[2]);
            System.out.println("OPM Level: "+level);
            new IFCtoLBDConverter(args[1], args[0], args[2], level, true, false, true, false, false, true);
        } 
        else
        if (args.length > 2) {
            System.out.println("Base URI: "+args[0]);
            System.out.println("Selected IFC File: "+args[1]);
            System.out.println("Targer TTL File: "+args[2]);
            System.out.println("OPM Level: "+2);
            new IFCtoLBDConverter(args[1], args[0], args[2], 2, true, false, true, false, false, true);
        } else if (args.length == 1) {
            // directory upload
            final List<String> inputFiles;
            final List<String> outputFiles;
            inputFiles = FileUtils.listFiles(args[0]);
            outputFiles = null;

            for (int i = 0; i < inputFiles.size(); ++i) {
                final String inputFile = inputFiles.get(i);
                String outputFile;
                if (inputFile.endsWith(".ifc")) {
                    if (outputFiles == null) {
                        outputFile = inputFile.substring(0, inputFile.length() - 4) + ".ttl";
                    } else {
                        outputFile = outputFiles.get(i);
                    }

                    outputFile = outputFile.replaceAll(args[0], args[0] + "\\___out\\");
                    String copyFile = inputFile.replaceAll(args[0], args[0] + "\\___done\\");

                    // move file to output directory
                    System.out.println("--------- converting: " + inputFile);
                    new IFCtoLBDConverter(inputFile, "https://dot.ugent.be/IFCtoLBDset#", outputFile, 0, true, false, true, false, false, false);

                    // move original file to output directory
                    File afile = new File(inputFile);
                    afile.renameTo(new File(copyFile));
                    System.out.println("--------- done ");
                }
            }
        } else
        {
            System.out.println("Usage:");
            System.out.println("IFCtoLBDConverter ifc_filename base_uri targer_file ");
            System.out.println("With an OPM level :");
            System.out.println("IFCtoLBDConverter ifc_filename base_uri targer_file level");
            System.out.println("Example: java -jar IFCtoLBD_Java_15.jar  http://lbd.example.com/ c:\\IFC\\Duplex_A_20110505.ifc c:\\IFC\\Duplex_A_20110505.ttl");
        }
    }


}
