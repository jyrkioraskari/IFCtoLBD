package org.lbd.ifc2lbd.utils;

public class StringOperations {
	/**
	 * Converts a string into the CamelCase notation described in:
	 * https://en.wikipedia.org/wiki/Camel_case
	 *  
	 * @param txt
	 * @return
	 */
	static public String toCamelCase(final String txt) {
		if (txt == null)
			return null;

		StringBuilder ret = new StringBuilder();

		boolean first = true;
		for (final String word : txt.split(" ")) {
			if (!word.isEmpty()) {
				if (first) {
					ret.append(filterCharaters(word.substring(0, 1).toLowerCase()));
					first = false;
				} else
					ret.append(filterCharaters(word.substring(0, 1).toUpperCase()));
				ret.append(filterCharaters(word.substring(1)));
			}
		}

		return ret.toString();
	}

	/**
	 * Converts a CamelCase string into space separate words.
	 * https://en.wikipedia.org/wiki/Camel_case
	 *  
	 * @param txt
	 * @return
	 */
	static public String toUnCamelCase(final String txt) {
		if (txt == null)
			return null;

		StringBuilder ret = new StringBuilder();
		for(int i=0;i<txt.length();i++)
		{
			char c=txt.charAt(i);
			if(i>0 && Character.isUpperCase(c))
			{
				ret.append(" ");
				ret.append(Character.toLowerCase(c));
			}
			else
			if(c=='_')
				ret.append(" ");
			else
			  ret.append(c);
		}
		return ret.toString();	
	}
	
	
	/**
	 * Removes all characters other than letters from a string
	 * 
	 * @param txt A text string
	 * @return
	 */
	static private String filterCharaters(String txt) {
		StringBuilder ret = new StringBuilder();
		for (byte cb : txt.getBytes()) {
			char c = (char) cb;
			if (Character.isAlphabetic(c))
				ret.append(c);
		}
		return ret.toString();
	}



}
