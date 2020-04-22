/*******************************************************************************
 * Copyright (C) 2017 Chi Zhang
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package nl.tue.ddss.convert;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import com.buildingsmart.tech.ifcowl.vo.IFCVO;


/**
 * The class to parse IFC header data in IFC SPF file to generate Header object
 * @author Chi
 *
 */
public class HeaderParser {
	
	
	
	public static Header parseHeader(String filePath) throws IOException{
		Header header=new Header();
		try {
			InputStream in=new FileInputStream(filePath);
			header=parseHeader(in);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return header;
	}
	
	public static Header parseHeader(InputStream inputStream) throws IOException {
    	InputStreamReader reader=new InputStreamReader(inputStream);
        return parseHeader(reader);
    }
	
	
	public static Header parseHeader(Reader reader) throws IOException{
		Header header=new Header();
    	HeaderParser headerParser=new HeaderParser();    
		BufferedReader br = new BufferedReader(reader);
            String strLine;
            try {             
                while ((strLine=br.readLine()) != null) {
                	try{
                	  if(strLine.trim().equals("ISO-10303-21;")||strLine.trim().equals("HEADER;")){
                		  continue;
                	  }
                      StringBuffer sb = new StringBuffer();
                      if (strLine.startsWith("FILE_DESCRIPTION")) {
                      String stmp = strLine;
                      sb.append(stmp.trim());
                      while (!stmp.endsWith(");")) {
                          stmp = br.readLine();
                          if (stmp == null)
                              break;
                          sb.append(stmp.trim());
                      }
                      strLine=sb.toString();
                    
                            headerParser.parseFileDescription(strLine, header);
                    }
                    else if (strLine.startsWith("FILE_NAME")){
                    	 String stmp = strLine;
                         sb.append(stmp.trim());
                         while (!stmp.endsWith(");")) {
                             stmp = br.readLine();
                             if (stmp == null)
                                 break;
                             sb.append(stmp.trim());
                         }
                         strLine=sb.toString();
                    	headerParser.parseFileName(strLine,header);
                    }
                    else if(strLine.startsWith("FILE_SCHEMA")){
                    	 String stmp = strLine;
                         sb.append(stmp.trim());
                         while (!stmp.endsWith(");")) {
                             stmp = br.readLine();
                             if (stmp == null)
                                 break;
                             sb.append(stmp.trim());
                         }
                         strLine=sb.toString();
                    	headerParser.parseFileSchema(strLine,header);
                    }
                    else{
                    	if (strLine.trim().equals("ENDSEC;")){
                    		break;
                    	}
                    }
                	}catch(IfcHeaderFormatException e){
                		e.printStackTrace();
                	}
                }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return header;
	}
	

	

	private void parseFileDescription(String strLine, Header header) throws IfcHeaderFormatException {
		IFCVO fileName=parseHeaderLineStatement(strLine);
		List<Object> objects=fileName.getObjectList();
		if(objects!=null&&objects.size()==2){
			header.setDescription(listStringValue(objects.get(0)));
			header.setImplementation_level(stringValue(objects.get(1)));
		}
		else {
			throw new IfcHeaderFormatException("FILE_DESCRIPTION does not have exactly 2 attributes: "+strLine);
		}		
	}
	
	   private void parseFileName(String strLine, Header header) throws IfcHeaderFormatException {
			IFCVO fileName=parseHeaderLineStatement(strLine);
			List<Object> objects=fileName.getObjectList();
			if(objects!=null&&objects.size()==7){
				header.setName(stringValue(objects.get(0)));
				header.setTime_stamp(stringValue(objects.get(1)));
				header.setAuthor(listStringValue(objects.get(2)));
				header.setOrganization(listStringValue(objects.get(3)));
				header.setPreprocessor_version(stringValue(objects.get(4)));
				header.setOriginating_system(stringValue(objects.get(5)));
				header.setAuthorization(stringValue(objects.get(6)));
			}
			else {
				throw new IfcHeaderFormatException("FILE_NAME does not have exactly 7 attributes: "+strLine);
			}
		}
	    
		private void parseFileSchema(String strLine, Header header) throws IfcHeaderFormatException {
			IFCVO fileName=parseHeaderLineStatement(strLine);
			List<Object> objects=fileName.getObjectList();
			if(objects!=null&&objects.size()==1){
				header.setSchema_identifiers(listStringValue(objects.get(0)));
			}
			else {
			}		
		}
	
	@SuppressWarnings({ "unchecked" })
	private List<String> listStringValue(Object object) throws IfcHeaderFormatException{
		LinkedList<String> list=new LinkedList<String>();
		if (object instanceof LinkedList<?>){
			if(((LinkedList<Object>)object).size()>0){
			for (Object o:(LinkedList<Object>)object){
				list.add(stringValue(o));
			}
			return list;
			}
			else{
				return null;
			}
		}
		else if(object.equals("$")){
			return null;
		}
		else{
			list.add((String)object);
			return list;
		}
	}
	
	private String stringValue(Object object) throws IfcHeaderFormatException{
        if (object instanceof String){
			if (object.equals("$")){
				return null;
			}
			else{
				return ((String) object).substring(1);
			}
		}
		else{
			throw new IfcHeaderFormatException("String value needed (not list)");
		}
	}

	private IFCVO parseHeaderLineStatement(String line) {    	
        IFCVO ifcvo = new IFCVO();
        int state = 0;
        StringBuffer sb = new StringBuffer();
        int clCount = 0;
        LinkedList<Object> current = (LinkedList<Object>) ifcvo.getObjectList();
        boolean comment=false;
        Stack<LinkedList<Object>> listStack = new Stack<LinkedList<Object>>();
        for (int i = 0; i < line.length()-1; i++) {
            char ch = line.charAt(i);
            char next=line.charAt(i+1);
            if(ch=='/' && next=='*'){
            	comment =true;
            } 
            if (ch=='*'&&next=='/'&&comment==true){
            	comment=false;
            	i=i+2;
            	continue;
            }
            if(comment==false){
            switch (state) {
                case 0: // (
                if (ch == '(') {                   
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
                case 1: // (... line started and doing (...
                if (ch == '\'') {
                    state++;
                }
                if (ch == '(') {
                    listStack.push(current);
                    LinkedList<Object> tmp = new LinkedList<Object>();
                    if (sb.toString().trim().length() > 0)
                        current.add(sb.toString().trim());
                    sb.setLength(0);
                    current.add(tmp);
                    current = tmp;
                    clCount++;
                    // sb.append(ch);
                } else if (ch == ')') {
                    if (clCount == 0) {
                        if (sb.toString().trim().length() > 0)
                            current.add(sb.toString().trim());
                        sb.setLength(0);
                        state = Integer.MAX_VALUE; // line is done
                        continue;
                    } else {
                        if (sb.toString().trim().length() > 0)
                            current.add(sb.toString().trim());
                        sb.setLength(0);
                        clCount--;
                        current = listStack.pop();
                    }
                } else if (ch == ',') {
                    if (sb.toString().trim().length() > 0)
                        current.add(sb.toString().trim());
                   // current.add(Character.valueOf(ch));

                    sb.setLength(0);
                } else {
                    sb.append(ch);
                }
                    break;
                case 2: // (...
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
        }
        return ifcvo;
    }

}
