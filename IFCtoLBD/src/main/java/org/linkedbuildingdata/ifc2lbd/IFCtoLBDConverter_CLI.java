
package org.linkedbuildingdata.ifc2lbd;

import java.io.File;
import java.util.concurrent.Callable;

import org.apache.jena.sys.JenaSystem;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/*
 * 
 *  IFCtoLBD Command Line interface
 *  
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

@Command(name = "IFCtoLBD_CLI", mixinStandardHelpOptions = true, version = "IFCtoLBD_CLI")
public class IFCtoLBDConverter_CLI implements Callable<Integer> {

	/*
   
    * @param uriBase
    *            
    * @param target_file
    *            
    * @param props_level
    *            The levels described in
    *            https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf
    */
	
	@Parameters(index = "0", description = "The absolute path for the IFC file that will be converted.")
	private String ifc_filename;

	@Parameters(index = "1", description = "The URI base for all the elemenents that will be created.")
    private String uriBase;

	@Parameters(index = "2", description = "The main file name for the output. If there are many, they will be sharing the same name beginning.")
    private String target_file;
	
    
	@Parameters(index = "3", description = "The OPM ontology complexity level")
    private int props_level;
	
    @Option(names = { "-be", "--hasBuildingElements" },
    description = "The Building Elements will be created in the output.")
    private boolean[] hasBuildingElements = new boolean[0];


    @Option(names = { "-sbe", "--hasSeparateBuildingElementsModel" },
    description = "The Building elements will have a separate file.")
    private boolean[] hasSeparateBuildingElementsModel = new boolean[0];

    @Option(names = { "-p", "--hasBuildingElementProperties" },
    description = "The properties will ne added into the output.")
    private boolean[] hasBuildingProperties = new boolean[0];

    @Option(names = { "-sp", "--hasSeparatePropertiesModel" },
    description = "The properties will be written in a separate file.")
    private boolean[] hasSeparatePropertiesModel = new boolean[0];

    @Option(names = { "-b", "--hasBlankNodes" },
    description = "Blank nodes are used.")
    private boolean[] hasPropertiesBlankNodes = new boolean[0];

    @Option(names = { "-gl", "--hasGeolocation" },
    description = "Geolocation, i.e., the latitude and longitude are added.")
    private boolean[] hasGeolocation = new boolean[0];

	

	@Override
	public Integer call() throws Exception {
		new IFCtoLBDConverter(ifc_filename, uriBase, target_file, props_level, true, false, true, false, false, true);
		return null;
	}


    public static void main(String[] args) {
        JenaSystem.init();
        int exitCode = new CommandLine(new IFCtoLBDConverter_CLI()).execute(args);
        
                System.exit(exitCode);
    }


}
