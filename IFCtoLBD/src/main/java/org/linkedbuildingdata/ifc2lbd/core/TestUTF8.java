package org.linkedbuildingdata.ifc2lbd.core;

import org.apache.commons.lang.StringEscapeUtils;

public class TestUTF8 {

	static private String unIFCUnicode(String txt)
	{
		StringBuilder sb = new StringBuilder();
		StringBuilder su4 = new StringBuilder();
		int state=0;
		for(char ch:txt.toCharArray())
		{
			switch(state)
			{
			case	0:
				    if(ch=='\\' || ch=='/')
				    	state=1;
					break;
			case	1:
			    if(ch=='X' || ch=='x')
			    	state=2;
			    else 
			    	state=0;
				break;
			case	2:
			    if(ch=='2' || ch=='4')
			    	state=3;
			    else 
			    	state=0;
				break;
			case	3:
				if(ch=='\\' || ch=='/')
			    	state=4;
			    else 
			    	state=0;
				break;
				
			case	4:
			    if(ch=='\\' || ch=='/')
			    	state=0;
			    else
			    {
			    	su4.append(ch);
			    	if(su4.length()>3)
			    	{
			    		sb.append("\\u");
			    		sb.append(su4);
			    		su4.setLength(0);
			    	}
			    }
				break;
			}
		}
		System.out.println(sb.toString());
		return StringEscapeUtils.unescapeJava(sb.toString());
	}
	
	public static void main(String[] args) {
		
		String txt="\\X2\\004C00560049002D00350030002D003100300033003400350020002D2206007400200035003000BA0043002C00200075006C006B006F0069006C006D0061006B0061006E0061007600610020006C00E4006D006D0069006E002000740069006C0061002000540049003500300050\\X0\\";


		 System.out.println("StringEscapeUtils.unescapeJava(sJava):\n" + unIFCUnicode( txt));
		
	}

}
