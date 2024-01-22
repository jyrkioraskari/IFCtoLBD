package com.buildingsmart.tech.ifcowl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

import com.buildingsmart.tech.ifcowl.vo.AttributeVO;
import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.InverseVO;
import com.buildingsmart.tech.ifcowl.vo.NamedIndividualVO;
import com.buildingsmart.tech.ifcowl.vo.PrimaryTypeVO;
import com.buildingsmart.tech.ifcowl.vo.PropertyVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;

import fi.ni.rdf.Namespace;

/*
 * ExpressReader reads EXPRESS file11 specification of the IFC files and creates 
 * an internal representation of it.
 * 
 * The usage:
 * ExpressReader er = new ExpressReader(InputStream schemaInputStream);
 * 
 *  - readAndBuild() - parses the file and builds up all data required to write an OWL file or convert an IFC file to RDF
 *  - getEntities() - gives map of Entities in IFC
 *  - getTypes()    - gives map of Types in IFC
 */

/*
 * Copyright 2016, 2024 Pieter Pauwels, Ghent University; Jyrki Oraskari, Aalto University; Lewis John McGibbney, Apache
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

public class ExpressReader {

	private static final Map<String, String> formattedClassNameCache = new HashMap<>();
	private Map<String, EntityVO> entities = new HashMap<>();
	private Map<String, TypeVO> types = new HashMap<>();
	private List<NamedIndividualVO> enumIndividuals = new ArrayList<>();
	private Map<String, AttributeVO> attributes = new HashMap<>();
	private Map<String, PropertyVO> properties = new HashMap<>();
	private Map<String, Set<String>> siblings = new HashMap<>();

	private InputStream schemaInputStream;
	
	// STATE_MACHINE FOR PARSING EXPRESS FILES
	private static final int INIT_STATE = 0;
	private static final int TYPE_STATE = 1;
	private static final int TYPE_SWITCH = 101;
	private static final int TYPE_SELECT = 102;
	private static final int TYPE_ENUMERATION = 103;
	private static final int TYPE_ENUMERATION_OF = 104;
	private static final int TYPE_LIST = 105;
	private static final int TYPE_ARRAY = 106;

	private static final int ENTITY_STATE = 2;
	private static final int ENTITY_READY = 201;
	private static final int ENTITY_SUBTYPE_STATE = 3;
	private static final int ENTITY_SUBTYPE_OF_STATE = 4;
	private static final int ENTITY_UNIQUE = 50;
	private static final int ENTITY_UNIQUE_TYPE = 51;
	private static final int ENTITY_WHERE = 7;
	private static final int ENTITY_DERIVE = 8;
	private static final int ENTITY_SUPERTYPE = 90;
	private static final int ENTITY_SUPERTYPE_OF_ONEOF = 91;
	private static final int ENTITY_NAME_STATE = 11;
	private static final int ENTITY_INVERSE_STATE = 111;
	private static final int ENTITY_INVERSE_SET_OF = 112;
	private static final int ENTITY_INVERSE_FOR = 113;

	private int state = INIT_STATE;
	private EntityVO current_entity = null;
	private String tmp_inverse_name;
	private String tmp_inverse_classnamerange;
	private String tmp_inverse_inverseprop;
	private int tmp_inverse_mincard = 0;  //default value according to EXPRESS spec	
	private int tmp_inverse_maxcard = -1;  //default value according to EXPRESS spec	

	private String tmp_entity_name;
	private String tmp_entity_type;
	private TypeVO current_type;
	private Set<String> current_sibling_set;

	private boolean is_set = false;
	private boolean is_array = false;
	private boolean is_list = false;
	private int tmp_mincard = 0; //default value according to EXPRESS spec	
	private int tmp_maxcard = -1; //default value according to EXPRESS spec
	private boolean is_optional = false;

	private boolean is_listoflist = false;
	private int tmp_listoflist_mincard = 0;  //default value according to EXPRESS spec
	private int tmp_listoflist_maxcard = -1; //default value according to EXPRESS spec

	public ExpressReader(InputStream schemaInputStream) {
		this.schemaInputStream = schemaInputStream;
		Namespace.IFC = "https://standards.buildingsmart.org/";
	}

	public void readAndBuild(){		
		try {			
			this.readSpec();
			this.buildExpressStructure();
			this.generateNamedIndividualsWithoutRenaming();

			this.rearrangeAttributesWithFullRenaming();
			this.rearrangeProperties();
			this.addInverses();
			this.interpretSelects();
			System.out.println("Ended reading the EXPRESS file and building internals");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		// args should be: IFC2X3_Final, IFC2X3_TC1, IFC4 or IFC4_ADD1, nothing
		// else is accepted here
		if (args.length != 2)
			System.out
			.println("Usage: java ExpressReader expressSchemaname pathToOutputFile \nExample: java ExpressReader IFC2X3_TC1 outputfile.ttl \nNote: only 'IFC2X3_Final', 'IFC2X3_TC1', 'IFC4_ADD1', 'IFC4_ADD2', 'IFC4_ADD2_TC1', 'IFC4', 'IFC4x1', and 'IFC4x3_RC1' are accepted options");
		else {
			String in = args[0];
			if (in.equalsIgnoreCase("IFC2X3_Final")
					|| in.equalsIgnoreCase("IFC2X3_TC1")
					|| in.equalsIgnoreCase("IFC4_ADD1")
					|| in.equalsIgnoreCase("IFC4_ADD2")
					|| in.equalsIgnoreCase("IFC4_ADD2_TC1")
					|| in.equalsIgnoreCase("IFC4x1")
					|| in.equalsIgnoreCase("IFC4")
					|| in.equalsIgnoreCase("IFC4x3_RC1")) {
				try (InputStream instr = ExpressReader.class   // JO 2024
						.getResourceAsStream("/" + in + ".exp")) {
					ExpressReader er = new ExpressReader(instr);
					
					String inAlt = in;
					if (in.equalsIgnoreCase("IFC2X3_Final"))
						inAlt = "IFC2X3/FINAL/";
					if (in.equalsIgnoreCase("IFC2X3_TC1"))
						inAlt = "IFC2X3/TC1/";
					if (in.equalsIgnoreCase("IFC4_ADD1"))
						inAlt = "IFC4/ADD1/";
					if (in.equalsIgnoreCase("IFC4_ADD2"))
						inAlt = "IFC4/ADD2/";
					if (in.equalsIgnoreCase("IFC4_ADD2_TC1"))
						inAlt = "IFC4/ADD2_TC1/";
					if (in.equalsIgnoreCase("IFC4x1"))
						inAlt = "IFC4_1/";
					if (in.equalsIgnoreCase("IFC4"))
						inAlt = "IFC4/FINAL/";
					if (in.equalsIgnoreCase("IFC4x3_RC1"))
						inAlt = "IFC4_3/RC1/";
					
					Namespace.IFC = "https://standards.buildingsmart.org/IFC/DEV/"
							+ inAlt + "OWL";
					er.readAndBuild();

					er.outputEntitiesAndTypes(args[1], in);
					er.outputEntityPropertyList(args[1], in);

					OWLWriter ow = new OWLWriter(in, er.entities, er.types,
							er.getSiblings(), er.getEnumIndividuals(),
							er.getProperties());
					if(!args[1].endsWith(".ttl"))
						ow.outputOWL(args[1]+".ttl");
					else
						ow.outputOWL(args[1]);
					System.out
					.println("Ended converting the EXPRESS schema into corresponding OWL file");
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else
				System.out
				.println("Usage: java ExpressReader expressSchemaname pathToOutputFile \nExample: java ExpressReader IFC2X3_TC1 outputfile.ttl \nNote: only 'IFC2X3_Final', 'IFC2X3_TC1', 'IFC4_ADD1', 'IFC4_ADD2', 'IFC4_ADD2_TC1', 'IFC4', 'IFC4x1', and 'IFC4x3_RC1' are accepted options");
		}
	}

	private void generateNamedIndividualsWithoutRenaming() {
		for (Map.Entry<String, TypeVO> entry : this.types.entrySet()) {
			TypeVO vo = entry.getValue();
			for (int n = 0; n < vo.getEnumEntities().size(); n++) {
				getEnumIndividuals().add(new NamedIndividualVO(vo.getName(), vo
						.getEnumEntities().get(n), vo.getEnumEntities().get(n)));
			}
		}
	}

	private void rearrangeAttributesWithFullRenaming() {

		Iterator<Entry<String, EntityVO>> iter = this.entities.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, EntityVO> pairs = iter.next();
			EntityVO evo = pairs.getValue();

			for (int n = 0; n < evo.getAttributes().size(); n++) {
				AttributeVO attr = evo.getAttributes().get(n);
				attr.setDomain(evo);				
				attr.setOriginalName(attr.getName());
				attr.setName(attr.getName() + "_" + evo.getName()); //this used to be "_of_"
			}

			for (int n = 0; n < evo.getInverses().size(); n++) {
				InverseVO inv = evo.getInverses().get(n);
				PropertyVO prop = new PropertyVO();
				inv.setAssociatedProperty(prop);

				prop.setName(formatProperty(inv.getName(), false));
				prop.setDomain(evo);
				prop.setRange(inv.getClassRange());
				prop.setSet(inv.isSet());		

				prop.setMinCardinality(inv.getMinCard());
				prop.setMaxCardinality(inv.getMaxCard());
				prop.setOriginalName(prop.getName());
				prop.setName(prop.getName() + "_" + evo.getName()); //this used to be "_of_"

				if(inv.getClassRange().equalsIgnoreCase("NUMBER") || inv.getClassRange().equalsIgnoreCase("REAL") || 
						inv.getClassRange().equalsIgnoreCase("INTEGER") || inv.getClassRange().equalsIgnoreCase("LOGICAL") || 
						inv.getClassRange().equalsIgnoreCase("BOOLEAN") || inv.getClassRange().equalsIgnoreCase("STRING") || 
						inv.getClassRange().equalsIgnoreCase("BINARY")){
					prop.setRangeNS("expr");
				}
				else {
					prop.setRangeNS("ifc");
				}

				getProperties().put(prop.getName(), prop);
			}			
		}
	}

	private void rearrangeProperties() {
		Iterator<Entry<String, EntityVO>> it = entities.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, EntityVO> pairs = it.next();
			EntityVO evo = pairs.getValue();
			for (int n = 0; n < evo.getAttributes().size(); n++) {
				AttributeVO attr = evo.getAttributes().get(n);

				TypeVO type = attr.getType();
				String type_primaryType = attr.getType().getPrimarytype();
				String type_name = attr.getType().getName();

				PropertyVO prop = new PropertyVO();
				prop.setName(attr.getName());
				prop.setOriginalName(attr.getOriginalName());
				prop.setDomain(attr.getDomain());
				prop.setArray(attr.isArray());
				prop.setSet(attr.isSet());
				prop.setList(attr.isList());
				prop.setListOfList(attr.isListOfList());
				prop.setRange(type_name);
				prop.setMinCardinality(attr.getMinCard());
				prop.setMaxCardinality(attr.getMaxCard());
				prop.setMinCardinalityListOfList(attr.getMinCardListOfList());
				prop.setMaxCardinalityListOfList(attr.getMaxCardListOfList());
				prop.setOptional(attr.isOptional());

				if(type_name.startsWith("Ifc")){
					prop.setRangeNS("ifc");
					attr.setRangeNS("ifc");
				}
				else {
					prop.setRangeNS("expr");
					attr.setRangeNS("expr");
				}

				if ("enumeration".equalsIgnoreCase(type_primaryType) || 
						PrimaryTypeVO.getPrimaryTypeVO(type_primaryType) != null)
					prop.setType(PropertyVO.propertyType.TYPEVO);
				else if ("select".equalsIgnoreCase(type_primaryType)) {
					prop.setType(PropertyVO.propertyType.SELECT);
					prop.setSelectEntities(type.getSelectEntities());
				} else if ("class".equalsIgnoreCase(type_primaryType))
					prop.setType(PropertyVO.propertyType.ENTITYVO);
				else {
					prop.setType(PropertyVO.propertyType.TYPEVO);
				}
				getProperties().put(prop.getName(), prop);
			}
		}
	}

	private void addInverses() {
		Iterator<Entry<String, EntityVO>> iter = entities.entrySet().iterator();
		ArrayList<PropertyVO> listOfAddedObjectProperties = new ArrayList<>();
		while (iter.hasNext()) {
			Entry<String, EntityVO> pairs = iter.next();
			EntityVO evo = pairs.getValue();
			for (int n = 0; n < evo.getInverses().size(); n++) {

				InverseVO inv = evo.getInverses().get(n);
				PropertyVO prop = inv.getAssociatedProperty();	
				PropertyVO inverseOfInv = getProperties().get(inv
						.getInverseOfProperty());

				if (inverseOfInv == null) {
					inverseOfInv = getProperties().get(inv.getInverseOfProperty()
							+ "_" + prop.getRange());
				}	

				if(!listOfAddedObjectProperties.contains(inverseOfInv) && inverseOfInv!=null){	
					listOfAddedObjectProperties.add(inverseOfInv);
					prop.setInverseProp(inverseOfInv);
					inverseOfInv.setInverseProp(prop);

					if(inverseOfInv.isList() || inverseOfInv.isListOfList() || inverseOfInv.isArray()){
						//Property needs to be deleted again to counter inconsistencies in the eventual OWL ontology
						getProperties().remove(prop.getName());
						inverseOfInv.setInverseProp(null);
						listOfAddedObjectProperties.remove(inverseOfInv);
					}
				}
				else{
					PropertyVO origprop = inverseOfInv;
					if(origprop!=null){
						PropertyVO originv = null;
						   if(inverseOfInv!=null)  // JO 2024
							   originv=inverseOfInv.getInverseProperty();
						if(originv!=null){
							System.out.println("removing property 2 from property list: " + originv.getName());
							if(getProperties().remove(originv.getName())==null){
								System.out.println("could not remove property 2 from list: " + originv.getName());
								getProperties().remove(originv.getOriginalName());
							}
							System.out.println("removing property 2 from property list: " + origprop.getName());
							originv.setInverseProp(null);
							System.out.println("removed inverses of property: " + originv.getName());
						}	
						else{
							System.out.println("removing property 3: " + origprop.getName());	
							origprop.setInverseProp(null);
						}

						System.out.println("removing property 4: " + prop.getName());	
						if(getProperties().remove(prop.getName())==null)
							System.out.println("could not remove property 4 from list: " + prop.getName());
						if(inverseOfInv!=null)  // JO 2024
						{
						  inverseOfInv.setInverseProp(null);
						  System.out.println("removed inverses of property: " + inverseOfInv.getName());
						}
					}	
					else{
						System.out.println("removing property 5: " + prop.getName());
						getProperties().remove(prop.getName());
					}
				}
			}
		}
	}

	private void interpretSelects() {
		Iterator<Entry<String, TypeVO>> iter = types.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, TypeVO> pairs = iter.next();
			TypeVO parent = pairs.getValue();
			for (int n = 0; n < parent.getSelectEntities().size(); n++) {
				String entString = parent.getSelectEntities().get(n);

				TypeVO type = TypeVO.getTypeVO(entString);
				if (type != null && !type.getPrimarytype().equalsIgnoreCase("CLASS")){
					type.addParentSelectType(parent);
				}

				else {
					EntityVO ent = EntityVO.getEntityVO(entString);
					if (ent != null){
						ent.addParentSelectType(parent);
					}
					else {
						PrimaryTypeVO ptype = PrimaryTypeVO
								.getPrimaryTypeVO(entString);
						if (ptype != null){
							System.out.println("Warning: PTYPE is part of select : " + parent.getName());
							ptype.addParentSelectType(parent);
						}
						else{
							System.out.println("Warning: Something is part of select that is not a PType, Entity, or Type: " + parent.getName());
						}
					}
				}
			}
		}
	}

	// CONVERTING
	private void readSpec() {
		try {
			
			try (BufferedReader br = new BufferedReader(new InputStreamReader(schemaInputStream));){
				String strLine;
				while ((strLine = br.readLine()) != null) {
					if (strLine.length() > 0) {
						parse_level(strLine);
					}
				}
			} 
		} catch (FileNotFoundException fe) {
			System.err.println("The IFC Express file is missing.");
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void buildExpressStructure() throws IOException {
		generateDerivedAttributeList();
		generateDerivedInverseList();
	}

	private void outputEntitiesAndTypes(String filePathNoExt, String schemaName) {
		String filePath = "";
		if(filePathNoExt.lastIndexOf(File.separatorChar) != -1)
			filePath = filePathNoExt.substring(0, filePathNoExt.lastIndexOf(File.separatorChar))+File.separatorChar;
		System.out.println("writing output to : " + filePath+"ent"+schemaName+".ser and " + filePath+"typ"+schemaName+".ser");

		FileOutputStream fos = null;
		try {
			//JO 2024
			fos = new FileOutputStream(filePath+"ent"+schemaName+".ser");

			try (ObjectOutputStream oos1 = new ObjectOutputStream(fos)) {
				oos1.writeObject(this.entities);
			}
			fos = new FileOutputStream(filePath+"typ"+schemaName+".ser");

			try (ObjectOutputStream oos2 = new ObjectOutputStream(fos)) {
				oos2.writeObject(this.types);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				if(fos!=null)
				  fos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}	

	private void outputEntityPropertyList(String filePathNoExt, String schemaName){
		String filePath = "";
		if(filePathNoExt.lastIndexOf(File.separatorChar) != -1)
			filePath = filePathNoExt.substring(0, filePathNoExt.lastIndexOf(File.separatorChar))+File.separatorChar;
		System.out.println("writing output to : " + filePath+"proplist"+schemaName+".csv and " + filePath+"proplist"+schemaName+".csv");
		try {
			File file = new File(filePath+"proplist"+schemaName+".csv");
			FileWriter fw = new FileWriter(file);
			try (BufferedWriter bw = new BufferedWriter(fw)) { // JO 2024
				Iterator<Entry<String, EntityVO>> it = this.entities.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, EntityVO> pairs = it.next();
					EntityVO evo = pairs.getValue();
					List<AttributeVO> attrs = evo.getDerivedAttributeList();
					for(AttributeVO attr : attrs){
						String setorlist = "ENTITY";
						if(attr.isSet())
							setorlist = "SET";
						else if (attr.isArray())
							setorlist = "ARRAY";
						else if (attr.isListOfList())
							setorlist = "LISTOFLIST";
						else if (attr.isList())
							setorlist = "LIST";

						bw.write(evo.getName() + "," + attr.getOriginalName() + "," + attr.getName() +  "," + setorlist + "\r\n");		
					}
					bw.flush();
				}
				bw.close();
			}	

			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// WRITING ATTRIBUTE AND INVERSE STRUCTURE
	private void generateDerivedAttributeList() throws IOException {
		Iterator<Entry<String, EntityVO>> it = entities.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, EntityVO> pairs = it.next();
			EntityVO evo = pairs.getValue();
			addAttributeEntries(evo, evo);
		}
	}

	private void addAttributeEntries(EntityVO evo, EntityVO top)
			throws IOException {
		if (evo.getSuperclass() != null) {
			EntityVO sup = entities.get(formatClassName(evo.getSuperclass()));
			if (sup != null)
				addAttributeEntries(sup, top);
		}

		for (int n = 0; n < evo.getAttributes().size(); n++) {
			attributes.put(top.getName() + "#"
					+ evo.getAttributes().get(n).getName(), evo.getAttributes()
					.get(n));
			top.getDerivedAttributeList().add(evo.getAttributes().get(n));
		}
	}

	private void generateDerivedInverseList() throws IOException {
		Iterator<Entry<String, EntityVO>> it = entities.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, EntityVO> pairs = it.next();
			EntityVO evo = pairs.getValue();
			addInverseEntries(evo, evo);
		}
	}

	private void addInverseEntries(EntityVO evo, EntityVO top)
			throws IOException {
		if (evo.getSuperclass() != null) {
			EntityVO sup = entities.get(evo.getSuperclass());
			if (sup != null)
				addInverseEntries(sup, top);
		}

		for (int n = 0; n < evo.getInverses().size(); n++) {
			AttributeVO avo = attributes.get(evo.getInverses().get(n)
					.getClassRange()
					+ "#" + evo.getInverses().get(n).getInverseOfProperty());
			boolean unique = false;
			if (avo != null) {
				InverseVO ivo = evo.getInverses().get(n);
				if (ivo.getMaxCard() == 1)
					if (!avo.isSet())
						avo.setOne2One(true);
				if (avo.isUnique())
					unique = true;
				avo.setReversePointer(true);
				avo.setPointsFrom(evo.getInverses().get(n));
			}
			evo.getInverses().get(n).setUnique(unique);
			top.getDerivedInverseList().add(evo.getInverses().get(n));
		}
	}

	// FORMATTING and BUILDING
	public static String formatClassName(String unformatted) {
		if (unformatted == null) {
			return null;
		}
		String formatted = formattedClassNameCache.get(unformatted);
		if (formatted == null) {
			formatted = filterExtras(unformatted).toUpperCase();
			formattedClassNameCache.put(unformatted, formatted);
		}
		return formatted;
	}

	public static String formatProperty(String s, boolean isList) {
		if (s == null)
			return null;

		if (isList)
			return s + "_List";
		return s;
	}

	@SuppressWarnings("unused")
	private void stateMachine(String txt) {

		switch (state) {
		case INIT_STATE:
			if ("TYPE".equalsIgnoreCase(txt))
				state = TYPE_STATE;
			if ("ENTITY".equalsIgnoreCase(txt))
				state = ENTITY_NAME_STATE;
			if ("FUNCTION".equalsIgnoreCase(txt)) {
				break;
			}
			if ("RULE".equalsIgnoreCase(txt)) {
				break;
			}
			if ("END_SCHEMA;".equalsIgnoreCase(txt))
				break;
			break;

			// 1. TYPE
		case TYPE_STATE:
			if (txt.endsWith("=")) {
				state = TYPE_SWITCH;
			} else {
				String txtT = formatClassName(txt);
				TypeVO t = types.get(txtT);
				if (t == null) {
					current_type = new TypeVO(txt);
					types.put(txtT, current_type);
				}
			}
			break;

		case TYPE_SWITCH:
			if ("SELECT".equalsIgnoreCase(txt)) {
				state = TYPE_SELECT;
				current_type.setPrimarytype(formatClassName(txt));
			} else if ("ENUMERATION".equalsIgnoreCase(txt)) {
				state = TYPE_ENUMERATION;
			} else if (isAllUpper(txt.substring(0, txt.length() - 1))) {
				if (txt.startsWith("ARRAY"))
					state = TYPE_ARRAY;
				else if (txt.startsWith("SET")
						|| txt.startsWith("LIST"))
					state = TYPE_LIST;
				else {
					// primarytypes like REAL/INTEGER/STRING/...
					if(formatClassName(txt).equalsIgnoreCase("NUMBER") || formatClassName(txt).equalsIgnoreCase("REAL") || 
							formatClassName(txt).equalsIgnoreCase("INTEGER") || formatClassName(txt).equalsIgnoreCase("LOGICAL") || 
							formatClassName(txt).equalsIgnoreCase("BOOLEAN") || formatClassName(txt).equalsIgnoreCase("STRING") || 
							formatClassName(txt).equalsIgnoreCase("BINARY"))
						new PrimaryTypeVO(formatClassName(txt));
					this.state = INIT_STATE;
				}
				txt = formatClassName(txt);
			} else {
				// references to TypeVOs
				if (txt.endsWith(";"))
					txt = txt.substring(0, txt.length() - 1);
				this.state = INIT_STATE;
			}
			this.current_type.setPrimarytype(txt);
			break;

		case TYPE_ARRAY:
			if (!txt.endsWith(";")) {
				if (this.current_type != null)
					this.current_type.setPrimarytype(this.current_type.getPrimarytype()
							+ " " + txt);
			} else {
				if (this.current_type != null)
					this.current_type.setPrimarytype(this.current_type.getPrimarytype()
							+ " " + txt);
				this.state = INIT_STATE;
			}
			break;

		case TYPE_LIST:
			if (!txt.endsWith(";")) {
				if (this.current_type != null)
					this.current_type.setPrimarytype(this.current_type.getPrimarytype()
							+ " " + txt);
			} else {
				if (this.current_type != null)
					this.current_type.setPrimarytype(this.current_type.getPrimarytype()
							+ " " + txt);
				this.state = INIT_STATE;
			}
			break;

		case TYPE_SELECT:
			if (txt.endsWith(";")) {
				String txtT = filterExtras(txt);
				if (this.current_type != null)
					this.current_type.getSelectEntities().add(txtT);
				this.state = INIT_STATE;
			} else {
				String txtT = filterExtras(txt);
				if (this.current_type != null)
					this.current_type.getSelectEntities().add(txtT);
			}
			break;

		case TYPE_ENUMERATION:
			if ("OF".equals(txt)) {
				this.state = TYPE_ENUMERATION_OF;
			}
			break;

		case TYPE_ENUMERATION_OF:
			if (txt.endsWith(";")) {
				String txtT = formatClassName(txt);
				if (this.current_type != null)
					this.current_type.getEnumEntities().add(txtT);
				this.state = INIT_STATE;
			} else {
				String txtT = formatClassName(txt);
				if (this.current_type != null)
					this.current_type.getEnumEntities().add(txtT);
			}
			break;

			// 2. ENTITY
		case ENTITY_NAME_STATE:
			// replaces all non-letter characters with nothing
			String orgName = txt;
			if (orgName.endsWith(";"))
				orgName = orgName.substring(0, orgName.length() - 1);
			String entityName = ExpressReader.formatClassName(orgName);
			this.current_entity = this.entities.get(entityName);
			if (this.current_entity == null) {
				this.current_entity = new EntityVO(orgName);
				this.entities.put(entityName, this.current_entity);
			}
			this.state = ENTITY_STATE;
			break;

		case ENTITY_STATE:
			this.is_array = false;
			this.is_set = false;
			this.is_list = false;
			this.is_optional = false;
			this.tmp_mincard = 0;
			this.tmp_maxcard = -1;
			this.is_listoflist = false;
			this.tmp_listoflist_mincard = 0;
			this.tmp_listoflist_maxcard = -1;

			if (txt.equalsIgnoreCase("SUBTYPE")) {
				this.state = ENTITY_SUBTYPE_STATE;
			} else if (txt.equalsIgnoreCase("SUPERTYPE")) {
				this.state = ENTITY_SUPERTYPE;
			} else if (txt.equalsIgnoreCase("ABSTRACT")) {
				this.current_entity.setAbstractSuperclass(true);
				this.state = ENTITY_SUPERTYPE;
			} else if (txt.equalsIgnoreCase("INVERSE")) {
				this.state = ENTITY_INVERSE_STATE;
			} else if (txt.equalsIgnoreCase("UNIQUE")) {
				this.state = ENTITY_UNIQUE;
			} else if (txt.equalsIgnoreCase("WHERE")) {
				state = ENTITY_WHERE;
			} else if (txt.equalsIgnoreCase("DERIVE")) {
				state = ENTITY_DERIVE;
			} else if (txt.equalsIgnoreCase("END_ENTITY;")) {
				state = INIT_STATE;
			} else {
				if (is_listoflist == true)
					tmp_entity_name = ExpressReader.formatProperty(
							ExpressReader.formatProperty(txt, true), true);
				else if (is_list == true && is_set == false)
					tmp_entity_name = ExpressReader.formatProperty(txt, true);
				else
					tmp_entity_name = ExpressReader.formatProperty(txt, false);
				state = ENTITY_READY;
			}
			break;

			// 2.1 PROPERTIES
		case ENTITY_READY:			
			if (txt.equalsIgnoreCase("END_ENTITY;")) {
				state = INIT_STATE;
			} else if (txt.equalsIgnoreCase("OPTIONAL")) {
				is_optional = true;
			} else if (txt.equalsIgnoreCase("ARRAY")) {
				is_array = true;
			} else if (txt.equalsIgnoreCase("SET")) {
				is_set = true;
			} else if (txt.equalsIgnoreCase("LIST")) {
				if (is_listoflist == true) {
					System.out
					.println("WARNING: LIST of LIST of LIST property found in EXPRESS for : "
							+ tmp_entity_name
							+ " - this is currently not supported by the converter!!");
				}
				if (is_list == true)
					is_listoflist = true;
				is_list = true;
			} else if (txt.endsWith("]") && txt.startsWith("[")) {
				// //[3:4] or similar parsed
				String[] tempCards = txt.split(":");
				String mincard = txt.split(":")[0].substring(1);
				String maxcard = txt.split(":")[1].substring(0,
						tempCards[1].length() - 1);
				if (is_listoflist == true) {
					if (!mincard.equalsIgnoreCase("?"))
						tmp_listoflist_mincard = Integer.parseInt(mincard);
					if (!maxcard.equalsIgnoreCase("?"))
						tmp_listoflist_maxcard = Integer.parseInt(maxcard);
				} else {
					if (!mincard.equalsIgnoreCase("?"))
						tmp_mincard = Integer.parseInt(mincard);
					if (!maxcard.equalsIgnoreCase("?"))
						tmp_maxcard = Integer.parseInt(maxcard);
				}
			} else if (txt.equalsIgnoreCase("SUBTYPE")) {
				state = ENTITY_SUBTYPE_STATE;
			} else if (txt.contains(";")) {
				tmp_entity_type = ExpressReader.formatClassName(txt.substring(
						0, txt.length() - 1));

				String txt_filtered = filter_PTypeExtras(txt);
				if(txt_filtered.equalsIgnoreCase("NUMBER") || txt_filtered.equalsIgnoreCase("REAL") || 
						txt_filtered.equalsIgnoreCase("INTEGER") || txt_filtered.equalsIgnoreCase("LOGICAL") || 
						txt_filtered.equalsIgnoreCase("BOOLEAN") || txt_filtered.equalsIgnoreCase("STRING") || 
						txt_filtered.equalsIgnoreCase("BINARY")){
					// primarytypes like REAL/INTEGER/STRING/...
					System.out.println("Filtering : " + txt + " -> " + txt_filtered);
					new PrimaryTypeVO(formatClassName(txt_filtered));
					TypeVO type = types.get(txt_filtered);

					if (type == null) {
						type = new TypeVO(txt_filtered,
								"CLASS");
					}
					current_entity.getAttributes().add(
							new AttributeVO(tmp_entity_name, type, is_array, is_set, is_list,
									is_listoflist, tmp_mincard, tmp_maxcard,
									tmp_listoflist_mincard, tmp_listoflist_maxcard,
									is_optional));
					state = ENTITY_STATE;
				}
				else{
					TypeVO type = types.get(tmp_entity_type);

					if (type == null) {
						type = new TypeVO(txt.substring(0, txt.length() - 1),
								"CLASS");
					}
					current_entity.getAttributes().add(
							new AttributeVO(tmp_entity_name, type, is_array, is_set, is_list,
									is_listoflist, tmp_mincard, tmp_maxcard,
									tmp_listoflist_mincard, tmp_listoflist_maxcard,
									is_optional));
					state = ENTITY_STATE;
				}
			}
			break;

			// 2.2 SUBTYPE
		case ENTITY_SUBTYPE_STATE:
			if (txt.equalsIgnoreCase("OF"))
				state = ENTITY_SUBTYPE_OF_STATE;
			else
				state = ENTITY_STATE;
			break;

		case ENTITY_SUBTYPE_OF_STATE:
			current_entity.setSuperclass(filterExtras(txt));

			state = ENTITY_STATE;
			break;

			// 2.3 SUPERTYPE
		case ENTITY_SUPERTYPE:
			if (txt.equalsIgnoreCase("END_ENTITY;")) {
				state = INIT_STATE;
			} else if (txt.equalsIgnoreCase("SUBTYPE")) {
				state = ENTITY_SUBTYPE_STATE;
			} else if (txt.equalsIgnoreCase("(ONEOF")) {
				state = ENTITY_SUPERTYPE_OF_ONEOF;
				current_sibling_set = new HashSet<String>();
			} else {
				if (txt.contains(";"))
					state = ENTITY_STATE;
			}
			break;

		case ENTITY_SUPERTYPE_OF_ONEOF:
			if (txt.equalsIgnoreCase("END_ENTITY;")) {
				state = INIT_STATE;
			} else if (txt.equalsIgnoreCase("SUBTYPE")) {
				state = ENTITY_SUBTYPE_STATE;
			} else {
				if (txt.contains(";")) {
					current_entity.setSubClassList(current_sibling_set);
					state = ENTITY_STATE;
				}
				if (txt.contains(")")) {
					current_entity.setSubClassList(current_sibling_set);
					state = ENTITY_STATE;
				}
				String sibstr = filterExtras(txt);
				current_sibling_set.add(sibstr);
				Set<String> s = this.getSiblings().get(sibstr);
				if (s != null)
					System.err.println("DUPLICATE: " + sibstr);
				else
					this.getSiblings().put(sibstr, current_sibling_set);
			}
			break;

			// 2.4 INVERSE
		case ENTITY_INVERSE_STATE:
			is_set = false;
			tmp_inverse_mincard = 0;
			tmp_inverse_maxcard = -1;
			if (txt.equalsIgnoreCase("WHERE")) {
				state = ENTITY_WHERE;
			} else if (txt.equalsIgnoreCase("END_ENTITY;")) {
				state = INIT_STATE;
			} else if (txt.equalsIgnoreCase("SUBTYPE")) {
				state = ENTITY_SUBTYPE_STATE;
			} else if (txt.equalsIgnoreCase(":")) {
				// the name of the inverse attribute
				state = ENTITY_INVERSE_SET_OF;
			} else
				tmp_inverse_name = ExpressReader.formatProperty(txt, false);
			break;

		case ENTITY_INVERSE_SET_OF:
			if (txt.equalsIgnoreCase("END_ENTITY;")) {
				state = INIT_STATE;
			} else if (txt.equalsIgnoreCase("SUBTYPE")) {
				state = ENTITY_SUBTYPE_STATE;
			} else if (txt.equalsIgnoreCase("SET")) {
				is_set = true;
			} else if (txt.equalsIgnoreCase("FOR")) {
				state = ENTITY_INVERSE_FOR;
			} else {
				if (txt.startsWith("[") && txt.endsWith("]")) {
					String[] tempCards = txt.split(":");
					String mincard = txt.split(":")[0].substring(1);
					String maxcard = txt.split(":")[1].substring(0,
							tempCards[1].length() - 1);
					if (!mincard.equalsIgnoreCase("?"))
						tmp_inverse_mincard = Integer.parseInt(mincard);
					if (!maxcard.equalsIgnoreCase("?"))
						tmp_inverse_maxcard = Integer.parseInt(maxcard);
				}
				tmp_inverse_classnamerange = txt;
			}
			break;

		case ENTITY_INVERSE_FOR:
			if (txt.equalsIgnoreCase("END_ENTITY;")) {
				state = INIT_STATE;
			} else if (txt.equalsIgnoreCase("SUBTYPE")) {
				state = ENTITY_SUBTYPE_STATE;
			} else if (txt.contains(";")) {
				tmp_inverse_inverseprop = txt.substring(0, txt.length() - 1);
				current_entity.getInverses().add(
						new InverseVO(tmp_inverse_name,
								tmp_inverse_classnamerange,
								tmp_inverse_inverseprop, is_set,
								tmp_inverse_mincard, tmp_inverse_maxcard));
				state = ENTITY_INVERSE_STATE;
			}
			break;

			// 2.5 UNIQUE RESTRICTIONS
		case ENTITY_UNIQUE:
			if (":".equals(txt))
				state = ENTITY_UNIQUE_TYPE;
			else if ("END_ENTITY;".equalsIgnoreCase(txt) || "WHERE".equalsIgnoreCase(txt)) {
				state = INIT_STATE;
			} else if ("SUBTYPE".equalsIgnoreCase(txt)) {
				state = ENTITY_SUBTYPE_STATE;
			}
			break;

		case ENTITY_UNIQUE_TYPE:
			if ("END_ENTITY;".equalsIgnoreCase(txt)) {
				state = INIT_STATE;
			} else if ("WHERE".equalsIgnoreCase(txt)) {
				state = INIT_STATE;
			} else if ("SUBTYPE".equalsIgnoreCase(txt)) {
				state = ENTITY_SUBTYPE_STATE;
			} else {
				if (!txt.contains(",")) {
					String uniqueAttribute = txt
							.substring(0, txt.length() - 1);

					for (int j = 0; j < current_entity.getAttributes().size(); j++) {
						AttributeVO ao = current_entity.getAttributes().get(j);
						if (ao.getName().equals(uniqueAttribute)) {
							ao.setUnique(true);
						}
					}
				}
				state = ENTITY_UNIQUE;
			}
			break;

			// 2.6 UNHANLDED WHERE AND DERIVE LINES
		case ENTITY_WHERE:
			// not parsed
			if ("END_ENTITY;".equalsIgnoreCase(txt)) {
				state = INIT_STATE;
			} else if ("SUBTYPE".equalsIgnoreCase(txt)) {
				state = ENTITY_SUBTYPE_STATE;
			}
			break;

		case ENTITY_DERIVE:
			// not parsed
			if ("END_ENTITY;".equalsIgnoreCase(txt)) {
				state = INIT_STATE;
			} else if ("SUBTYPE".equalsIgnoreCase(txt)) {
				state = ENTITY_SUBTYPE_STATE;
			} else if ("INVERSE".equalsIgnoreCase(txt)) {
				state = ENTITY_INVERSE_STATE;
			}
			break;

		default:
			// Do nothing
		}
	}

	static public String filterExtras(String txt) {
		StringBuilder sb = new StringBuilder();
		for (int n = 0; n < txt.length(); n++) {
			char ch = txt.charAt(n);
			switch (ch) {
			case '(':
				break;
			case ';':
				break;
			case ',':
				break;
			case ')':
				break;
			default:
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	static public String filter_PTypeExtras(String txt) {
		StringBuilder sb = new StringBuilder();
		for (int n = 0; n < txt.length(); n++) {
			char ch = txt.charAt(n);
			switch (ch) {
			case '0':
				break;
			case '1':
				break;
			case '2':
				break;
			case '3':
				break;
			case '4':
				break;
			case '5':
				break;
			case '6':
				break;
			case '7':
				break;
			case '8':
				break;
			case '9':
				break;
			case '(':
				break;
			case ';':
				break;
			case ',':
				break;
			case ')':
				break;
			default:
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	private void parse_level(String txt) {
		StringTokenizer st = new StringTokenizer(txt);
		while (st.hasMoreTokens()) {
			stateMachine(st.nextToken());
		}
	}

	public static boolean isAllUpper(String s) {
		for (char c : s.toCharArray()) {
			if (Character.isLetter(c) && Character.isLowerCase(c)) {
				return false;
			}
		}
		return true;
	}

	// ACCESSORS
	public Map<String, TypeVO> getTypes() {
		return types;
	}

	public void setTypes(Map<String, TypeVO> types) {
		this.types = types;
	}

	public Map<String, EntityVO> getEntities() {
		return entities;
	}

	public void setEntities(Map<String, EntityVO> entities) {
		this.entities = entities;
	}

	public Map<String, Set<String>> getSiblings() {
		return siblings;
	}

	public void setSiblings(Map<String, Set<String>> siblings) {
		this.siblings = siblings;
	}

	public List<NamedIndividualVO> getEnumIndividuals() {
		return enumIndividuals;
	}

	public void setEnumIndividuals(List<NamedIndividualVO> enumIndividuals) {
		this.enumIndividuals = enumIndividuals;
	}

	public Map<String, PropertyVO> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, PropertyVO> properties) {
		this.properties = properties;
	}
}
