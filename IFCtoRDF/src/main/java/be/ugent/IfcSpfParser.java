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

class IfcSpfParser {
    private static final Logger LOG = LoggerFactory.getLogger(RDFWriter.class);

    private final InputStream inputStream;
    private int idCounter = 0;
    private final Map<Long, IFCVO> linemap = new HashMap<>();
    private final Map<Long, Long> listOfDuplicateLineEntries = new HashMap<>();


    IfcSpfParser(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    void readModel() {
        try {
        	// Fix by JO 2024: finally is deprecated
            try (DataInputStream in = new DataInputStream(inputStream);
                    BufferedReader br = new BufferedReader(new InputStreamReader(in))){
                String strLine;
                while ((strLine = br.readLine()) != null) {
                    if (!strLine.isEmpty()) {
                        if (strLine.charAt(0) == '#') {
                            StringBuilder sb = new StringBuilder();
                            String stmp = strLine;
                            sb.append(stmp.trim());
                            while (!stmp.contains(";")) {
                                stmp = br.readLine();
                                if (stmp == null)
                                    break;
                                sb.append(stmp.trim());
                            }
                            // the whole IFC gets parsed, and everything ends up
                            // as IFCVO objects in the Map<Long, IFCVO> linemap
                            // variable
                            parseIfcLineStatement(sb.substring(1));
                        }
                    }
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
                    continue;
                } else if (Character.isDigit(ch))
                    sb.append(ch);
                    break;
                case 1: // (
                if (ch == '(') {
                    ifcvo.setName(sb.toString());
                    sb.setLength(0);
                    state++;
                    continue;
                } else if (ch == ';') {
                    ifcvo.setName(sb.toString());
                    sb.setLength(0);
                    state = Integer.MAX_VALUE;
                } else if (!Character.isWhitespace(ch))
                    sb.append(ch);
                    break;
                case 2: // (... line started and doing (...
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
                        state = Integer.MAX_VALUE; // line is done
                        continue;
                    }
					if (!sb.toString().trim().isEmpty())
					    current.add(sb.toString().trim());
					sb.setLength(0);
					clCount--;
					current = listStack.pop();
                } else if (ch == ',') {
                    if (!sb.toString().trim().isEmpty())
                        current.add(sb.toString().trim());
                    current.add(Character.valueOf(ch));

                    sb.setLength(0);
                } else {
                    sb.append(ch);
                }
                    break;
                case 3: // (...
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
        linemap.put(ifcvo.getLineNum(), ifcvo);
        idCounter++;
    }

    //JO 2023  performance optimization
    void resolveDuplicates()  {
        Map<String, IFCVO> listOfUniqueResources = new HashMap<>();
        List<Long> entriesToRemove = new ArrayList<>();
        for (Long key : linemap.keySet()) {
            IFCVO vo = linemap.get(key);
            String t = vo.getFullLineAfterNum();
            if (!listOfUniqueResources.containsKey(t))
                listOfUniqueResources.put(t, vo);
            else {
                // found duplicate
                entriesToRemove.add(key);
                listOfDuplicateLineEntries.put(vo.getLineNum(), listOfUniqueResources.get(t).getLineNum());
            }
        }
        //JO 2024: removed for performance
        //LOG.info("MESSAGE: found and removed " + listOfDuplicateLineEntries.size() + " duplicates!");
        for (Long x : entriesToRemove) {
            linemap.remove(x);
        }
    }

    boolean mapEntries()  {
        //JO 2023  performance optimization
        for (Long key : linemap.keySet()) {
            IFCVO vo = linemap.get(key);

            // mapping properties to IFCVOs
            for (int i = 0; i < vo.getObjectList().size(); i++) {
                Object o = vo.getObjectList().get(i);
                if (o instanceof Character c) {
                    if (c != ',') {
                        LOG.error("*ERROR 15*: We found a character that is not a comma. That should not be possible");
                    }
                } else if (o instanceof String s) {
                    if (s.isEmpty())
                        continue;
                    if (s.charAt(0) == '#') {
                        Object or;
                        if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
                            or = linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
                        else
                            or = linemap.get(toLong(s.substring(1)));

                        if (or == null) {
                            LOG.error("*ERROR 6*: Reference to non-existing line number in line: #" + vo.getLineNum() + "=" + vo.getFullLineAfterNum());
                            continue;
                            //return true; // JO 2022-05
                        }
                        vo.getObjectList().set(i, or);
                    }
                } else if (o instanceof LinkedList) {
                    @SuppressWarnings("unchecked")
                    LinkedList<Object> tmpList = (LinkedList<Object>) o;

                    for (int j = 0; j < tmpList.size(); j++) {
                        Object o1 = tmpList.get(j);
                        if (o instanceof Character c) {
                            if (c != ',') {
                                LOG.error("*ERROR 16*: We found a character that is not a comma. " + "That should not be possible!");
                            }
                        } else if (o1 instanceof String s) {
                            if (s.isEmpty())
                                continue;
                            if (s.charAt(0) == '#') {
                                Object or;
                                if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
                                    or = linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
                                else
                                    or = linemap.get(toLong(s.substring(1)));
                                if (or == null) {
                                	//Non-existing line number can ne caused because of the geometry removal.
                                	//System.out.println("\"JO P3\" *ERROR 7*: Reference "+s.substring(1)+" to non-existing line number in line: #" + vo.getLineNum() + " - " + vo.getFullLineAfterNum());
                                    //LOG.error("*ERROR 7*: Reference "+s.substring(1)+" to non-existing line number in line: #" + vo.getLineNum() + " - " + vo.getFullLineAfterNum());
                                    tmpList.set(j, "-");
                                    continue;
                                    //return true; // JO 2022-05
                                }
								tmpList.set(j, or);
                            } else {
                                // list/set of values
                                tmpList.set(j, s);
                            }
                        } else if (o1 instanceof LinkedList) {
                            @SuppressWarnings("unchecked")
                            LinkedList<Object> tmp2List = (LinkedList<Object>) o1;
                            for (int j2 = 0; j2 < tmp2List.size(); j2++) {
                                Object o2 = tmp2List.get(j2);
                                if (o2 instanceof String s) {
                                    if (s.isEmpty())
                                        continue;
                                    if (s.charAt(0) == '#') {
                                        Object or;
                                        if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
                                            or = linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
                                        else
                                            or = linemap.get(toLong(s.substring(1)));
                                        if (or == null) {
                                            LOG.error("*ERROR 8*: Reference to non-existing line number in line: #" + vo.getLineNum() + " - " + vo.getFullLineAfterNum());
                                            tmp2List.set(j2, "-");
                                            continue;
                                            //return true; // JO 2022-05
                                        }
										tmp2List.set(j2, or);
                                    }
                                }
                            }
                            tmpList.set(j, tmp2List);
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
        return linemap;
    }
}
