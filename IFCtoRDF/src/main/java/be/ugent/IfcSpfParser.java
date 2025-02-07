package be.ugent;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;


/*
Copyright (c) 2014, 2025 Jyrki Oraskari, RWTH AAáchen University (oraskarii [at] ip.rwth-aachen [dot] de)
Copyright (c) 2014 Pieter Pauwels, Ghent University (modifications - pipauwel [dot] pauwels [at] ugent [dot] be / pipauwel [at] gmail [dot] com)
Copyright (c) 2016 Lewis John McGibbney, Apache (mavenized - lewismc [at] apache [dot] org)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

class IfcSpfParser {
	private static final Logger LOG = LoggerFactory.getLogger(RDFWriter.class);

	private final InputStream inputStream;
	private int idCounter = 0;
	private final Map<Long, IFCVO> linenum_linemap = new HashMap<>();
	private final Map<Long, Long> listOfDuplicateLineEntries = new HashMap<>();

	/**
	 * Constructs an IfcSpfParser with the specified input stream.
	 *
	 * @param inputStream the input stream to read the IFC data from
	 */
	IfcSpfParser(InputStream inputStream) {
		this.inputStream = inputStream;
	}

	/**
	 * Reads the IFC model from the input stream and parses it into IFCVO objects.
	 * The parsed objects are stored in the linemap.
	 */
	void readModel() {
		try (DataInputStream in = new DataInputStream(inputStream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
			String strLine;
			StringBuilder sb = new StringBuilder();
			while ((strLine = br.readLine()) != null) {
				if (!strLine.isEmpty() && strLine.charAt(0) == '#') {
					sb.setLength(0);
					String stmp = strLine;
					sb.append(stmp.trim());
					while (!stmp.contains(";")) {
						stmp = br.readLine();
						if (stmp == null)
							break;
						sb.append(stmp.trim());
					}
					parseIfcLineStatement(sb.substring(1));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseIfcLineStatement(String line) {
		IFCVO ifcvo = new IFCVO();
		ifcvo.setFullLineAfterNum(line.substring(line.indexOf('=') + 1));
		int state = 0;
		StringBuilder sb = new StringBuilder();
		int clCount = 0;
		LinkedList<Object> current = (LinkedList<Object>) ifcvo.getObjectList();
		Stack<LinkedList<Object>> listStack = new Stack<>();
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			switch (state) {
			case 0:
				if (ch == '=') {
					ifcvo.setLineNum(toLong(sb.toString()));
					sb.setLength(0);
					state++;
				} else if (Character.isDigit(ch)) {
					sb.append(ch);
				}
				break;
			case 1:
				if (ch == '(') {
					ifcvo.setName(sb.toString());
					sb.setLength(0);
					state++;
				} else if (ch == ';') {
					ifcvo.setName(sb.toString());
					sb.setLength(0);
					state = Integer.MAX_VALUE;
				} else if (!Character.isWhitespace(ch)) {
					sb.append(ch);
				}
				break;
			case 2:
				if (ch == '\'') {
					state++;
				}
				if (ch == '(') {
					listStack.push(current);
					LinkedList<Object> tmp = new LinkedList<>();
					if (!sb.toString().trim().isEmpty())
						current.add(sb.toString().trim());
					sb.setLength(0);
					current.add(tmp);
					current = tmp;
					clCount++;
				} else if (ch == ')') {
					if (clCount == 0) {
						if (!sb.toString().trim().isEmpty())
							current.add(sb.toString().trim());
						sb.setLength(0);
						state = Integer.MAX_VALUE;
					} else {
						if (!sb.toString().trim().isEmpty())
							current.add(sb.toString().trim());
						sb.setLength(0);
						clCount--;
						current = listStack.pop();
					}
				} else if (ch == ',') {
					if (!sb.toString().trim().isEmpty())
						current.add(sb.toString().trim());
					current.add(Character.valueOf(ch));
					sb.setLength(0);
				} else {
					sb.append(ch);
				}
				break;
			case 3:
				if (ch == '\'') {
					state--;
				} else {
					sb.append(ch);
				}
				break;
			default:
				// Do nothing
			}
		}
		linenum_linemap.put(ifcvo.getLineNum(), ifcvo);
		idCounter++;
	}

	void resolveDuplicates() {
		Map<String, IFCVO> listOfUniqueResources = new HashMap<>();
		List<Long> entriesToRemove = new ArrayList<>();
		for (Long key : linenum_linemap.keySet()) {
			IFCVO vo = linenum_linemap.get(key);
			String t = vo.getFullLineAfterNum();
			if (!listOfUniqueResources.containsKey(t)) {
				listOfUniqueResources.put(t, vo);
			} else {
				entriesToRemove.add(key);
				listOfDuplicateLineEntries.put(vo.getLineNum(), listOfUniqueResources.get(t).getLineNum());
			}
		}
		for (Long x : entriesToRemove) {
			linenum_linemap.remove(x);
		}
	}

	boolean mapEntries() {
		for (IFCVO ifcLine : linenum_linemap.values()) {

			for (int inx = 0; inx < ifcLine.getObjectList().size(); inx++) {
				Object ifc_lineslot = ifcLine.getObjectList().get(inx);
				if (ifc_lineslot instanceof Character c) {
					if (c != ',') {
						LOG.error("*ERROR 15*: We found a character that is not a comma. That should not be possible");
					}
				} else if (ifc_lineslot instanceof String s) {
					if (s.isEmpty())
						continue;
					if (s.charAt(0) == '#') {
						Object ifcvo4reference;
						if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
							ifcvo4reference = linenum_linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
						else
							ifcvo4reference = linenum_linemap.get(toLong(s.substring(1)));

						if (ifcvo4reference == null) {
							LOG.error("*ERROR 6*: Reference to non-existing line number in line: #" + ifcLine.getLineNum()
									+ "=" + ifcLine.getFullLineAfterNum());
							continue;
						}
						ifcLine.getObjectList().set(inx, ifcvo4reference);  // Replaces the #num ref with a IFC STEP line object 
					}
				} else if (ifc_lineslot instanceof LinkedList) {
					@SuppressWarnings("unchecked")
					LinkedList<Object> objectList_level1 = (LinkedList<Object>) ifc_lineslot;

					for (int objlist_inx_level1 = 0; objlist_inx_level1 < objectList_level1.size(); objlist_inx_level1++) {
						Object element_of_list_level1 = objectList_level1.get(objlist_inx_level1);
						if (element_of_list_level1 instanceof String s) {
							if (s.isEmpty())
								continue;
							if (s.charAt(0) == '#') {
								Object ifcvo4reference;
								if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
									ifcvo4reference = linenum_linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
								else
									ifcvo4reference = linenum_linemap.get(toLong(s.substring(1)));
								if (ifcvo4reference == null) {
									objectList_level1.set(objlist_inx_level1, "-");
									continue;
								}
								objectList_level1.set(objlist_inx_level1, ifcvo4reference);
							} else {
								objectList_level1.set(objlist_inx_level1, s);
							}
						} else if (element_of_list_level1 instanceof LinkedList) {
							@SuppressWarnings("unchecked")
							LinkedList<Object> objectList_level2 = (LinkedList<Object>) element_of_list_level1;
							for (int objlist_inx_level2 = 0; objlist_inx_level2 < objectList_level2.size(); objlist_inx_level2++) {
								Object element_of_list_level2 = objectList_level2.get(objlist_inx_level2);
								if (element_of_list_level2 instanceof String s) {
									if (s.isEmpty())
										continue;
									if (s.charAt(0) == '#') {
										Object ifcvo4reference;
										if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
											ifcvo4reference = linenum_linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
										else
											ifcvo4reference = linenum_linemap.get(toLong(s.substring(1)));
										if (ifcvo4reference == null) {
											LOG.error("*ERROR 8*: Reference to non-existing line number in line: #"
													+ ifcLine.getLineNum() + " - " + ifcLine.getFullLineAfterNum());
											objectList_level2.set(objlist_inx_level2, "-");
											continue;
										}
										objectList_level2.set(objlist_inx_level2, ifcvo4reference);
									}
								}
							}
							objectList_level1.set(objlist_inx_level1, objectList_level2);
						}
					}
				}
			}
		}
		return true;
	}

	private static Long toLong(String txt) {
		try {
			return Long.valueOf(txt);
		} catch (NumberFormatException e) {
			return Long.MIN_VALUE;
		}
	}

	public int getIdCounter() {
		return idCounter;
	}

	public Map<Long, IFCVO> getLinemap() {
		return linenum_linemap;
	}
}