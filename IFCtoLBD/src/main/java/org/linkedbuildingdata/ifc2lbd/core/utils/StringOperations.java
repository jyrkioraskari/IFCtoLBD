package org.linkedbuildingdata.ifc2lbd.core.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class StringOperations {
	/**
	 * Converts a string into the CamelCase notation described in:
	 * https://en.wikipedia.org/wiki/Camel_case
	 * 
	 * @param txt a string to be formatetd
	 * @return a Camel Case formatted string
	 */
	static public String toCamelCase(String txt) {
		if (txt.toUpperCase().equals(txt))
			try {
				return URLEncoder.encode(txt.replace(" ", "_"), StandardCharsets.UTF_8.toString());
			} catch (UnsupportedEncodingException e) {
				return txt.replace(" ", "_");
			}

		txt = org.apache.commons.lang3.StringUtils.stripAccents(txt);
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

		try {
			return URLEncoder.encode(ret.toString(), StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return ret.toString();
	}

	/**
	 * Converts a CamelCase string into space separate words.
	 * https://en.wikipedia.org/wiki/Camel_case
	 * 
	 * @param txt a string to be formatetd
	 * @return an un-"CamelCased" formatted string
	 */
	static public String toUnCamelCase(final String txt) {
		if (txt == null)
			return null;

		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < txt.length(); i++) {
			char c = txt.charAt(i);
			if (i > 0 && Character.isUpperCase(c)) {
				ret.append(" ");
				ret.append(Character.toLowerCase(c));
			} else if (c == '_')
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
	 * @return filtered content
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

	static public String handleUnicode(String line) {
		try {
			line = line.replace("\\\\", "\\");

			// UTF-8 fix for French double encoding
			line = line.replace("\\X\\0D", " ");
			line = line.replace("\\X\\0A", "");
			line = line.replace("\\X\\22", "\"");
			line = line.replace("\\X\\23", "#");
			line = line.replace("\\X\\26", "&");
			line = line.replace("\\X\\3A", ":");
			line = line.replace("\\X\\3D", "=");
			line = line.replace("\\X\\3F", "?");

			// For Scandinavian letters
			line = line.replace("\\X\\C5", "Å");
			line = line.replace("\\X\\C4", "Ä");
			line = line.replace("\\X\\D6", "Ö");
			line = line.replace("\\X\\E5", "å");
			line = line.replace("\\X\\E4", "ä");
			line = line.replace("\\X\\F6", "ö");

			// For Norwegian and Danish letters
			line = line.replace("\\X\\C6", "Æ");
			line = line.replace("\\X\\D8", "Ø");
			line = line.replace("\\X\\E6", "æ");
			line = line.replace("\\X\\F8", "ø");

			// For French letters
			line = line.replace("\\X\\C0", "À");
			line = line.replace("\\X\\C7", "Ç");
			line = line.replace("\\X\\C8", "È");
			line = line.replace("\\X\\C9", "É");
			line = line.replace("\\X\\CA", "Ê");
			line = line.replace("\\X\\CB", "Ë");
			line = line.replace("\\X\\CC", "Ì");
			line = line.replace("\\X\\CE", "Î");
			line = line.replace("\\X\\CF", "Ï");
			line = line.replace("\\X\\D4", "Ô");
			line = line.replace("\\X\\D9", "Ù");
			line = line.replace("\\X\\DB", "Û");
			line = line.replace("\\X\\E0", "à");
			line = line.replace("\\X\\E7", "ç");
			line = line.replace("\\X\\E8", "è");
			line = line.replace("\\X\\E9", "é");
			line = line.replace("\\X\\EA", "ê");
			line = line.replace("\\X\\EB", "ë");
			line = line.replace("\\X\\EC", "ì");
			line = line.replace("\\X\\EE", "î");
			line = line.replace("\\X\\EF", "ï");
			line = line.replace("\\X\\F4", "ô");
			line = line.replace("\\X\\F9", "ù");
			line = line.replace("\\X\\FB", "û");
			line = line.replace("\\X\\FC", "ü");

			line = line.replace("\\X2\\00A0\\X0\\", " ");
			line = line.replace("\\X2\\00B0\\X0\\", "Â°");
			// LATIN letters
			line = line.replace("\\X2\\00C0\\X0\\", "Ã€");
			line = line.replace("\\X2\\00C1\\X0\\", "Ã�");
			line = line.replace("\\X2\\00C2\\X0\\", "Ã‚");
			line = line.replace("\\X2\\00C3\\X0\\", "Ãƒ");
			line = line.replace("\\X2\\00C4\\X0\\", "Ã„");
			line = line.replace("\\X2\\00C5\\X0\\", "Ã…");
			line = line.replace("\\X2\\00C6\\X0\\", "Ã†");
			line = line.replace("\\X2\\00C7\\X0\\", "Ã‡");
			line = line.replace("\\X2\\00C8\\X0\\", "Ãˆ");
			line = line.replace("\\X2\\00C9\\X0\\", "Ã‰");
			line = line.replace("\\X2\\00CA\\X0\\", "ÃŠ");
			line = line.replace("\\X2\\00CB\\X0\\", "Ã‹");
			line = line.replace("\\X2\\00CC\\X0\\", "ÃŒ");
			line = line.replace("\\X2\\00CD\\X0\\", "Ã�");
			line = line.replace("\\X2\\00CE\\X0\\", "ÃŽ");
			line = line.replace("\\X2\\00CF\\X0\\", "Ã�");

			line = line.replace("\\X2\\00D0\\X0\\", "Ã�");
			line = line.replace("\\X2\\00D1\\X0\\", "Ã‘");
			line = line.replace("\\X2\\00D2\\X0\\", "Ã’");
			line = line.replace("\\X2\\00D3\\X0\\", "Ã“");
			line = line.replace("\\X2\\00D4\\X0\\", "Ã”");
			line = line.replace("\\X2\\00D5\\X0\\", "Ã•");
			line = line.replace("\\X2\\00D6\\X0\\", "Ã–");
			line = line.replace("\\X2\\00D7\\X0\\", "Ã—");
			line = line.replace("\\X2\\00D8\\X0\\", "Ã˜");
			line = line.replace("\\X2\\00D9\\X0\\", "Ã™");
			line = line.replace("\\X2\\00DA\\X0\\", "Ãš");
			line = line.replace("\\X2\\00DB\\X0\\", "Ã›");
			line = line.replace("\\X2\\00DC\\X0\\", "Ãœ");
			line = line.replace("\\X2\\00DD\\X0\\", "Ã�");
			line = line.replace("\\X2\\00DE\\X0\\", "Ãž");
			line = line.replace("\\X2\\00DF\\X0\\", "ÃŸ");

			line = line.replace("\\X2\\00E0\\X0\\", "Ã ");
			line = line.replace("\\X2\\00E1\\X0\\", "Ã¡");
			line = line.replace("\\X2\\00E2\\X0\\", "Ã¢");
			line = line.replace("\\X2\\00E3\\X0\\", "Ã£");
			line = line.replace("\\X2\\00E4\\X0\\", "Ã¤");
			line = line.replace("\\X2\\00E5\\X0\\", "Ã¥");
			line = line.replace("\\X2\\00E6\\X0\\", "Ã¦");
			line = line.replace("\\X2\\00E7\\X0\\", "Ã§");
			line = line.replace("\\X2\\00E8\\X0\\", "Ã¨");
			line = line.replace("\\X2\\00E9\\X0\\", "Ã©");
			line = line.replace("\\X2\\00EA\\X0\\", "Ãª");
			line = line.replace("\\X2\\00EB\\X0\\", "Ãª");
			line = line.replace("\\X2\\00EC\\X0\\", "Ã¬");
			line = line.replace("\\X2\\00ED\\X0\\", "Ã­");
			line = line.replace("\\X2\\00EE\\X0\\", "Ã®");
			line = line.replace("\\X2\\00EF\\X0\\", "Ã¯");

			line = line.replace("\\X2\\00F0\\X0\\", "Ã°");
			line = line.replace("\\X2\\00F1\\X0\\", "Ã±");
			line = line.replace("\\X2\\00F2\\X0\\", "Ã²");
			line = line.replace("\\X2\\00F3\\X0\\", "Ã³");
			line = line.replace("\\X2\\00F4\\X0\\", "Ã´");
			line = line.replace("\\X2\\00F5\\X0\\", "Ãµ");
			line = line.replace("\\X2\\00F6\\X0\\", "Ã¶");
			line = line.replace("\\X2\\00F7\\X0\\", "Ã·");
			line = line.replace("\\X2\\00F8\\X0\\", "Ã¸");
			line = line.replace("\\X2\\00F9\\X0\\", "Ã¹");
			line = line.replace("\\X2\\00FA\\X0\\", "Ãº");
			line = line.replace("\\X2\\00FB\\X0\\", "Ã»");
			line = line.replace("\\X2\\00FC\\X0\\", "Ã¼");
			line = line.replace("\\X2\\00FD\\X0\\", "Ã½");
			line = line.replace("\\X2\\00FE\\X0\\", "Ã¾");
			line = line.replace("\\X2\\00FF\\X0\\", "Ã¿");

			line = unIFCUnicode(line); // multi-character decode
			// line = line.replace("\\", "\\\\");
			// line = line.replace("\\\\\"", "\\\"");

		} catch (Exception e) {
			// Just catch it
			e.printStackTrace();
		}
		return line;
	}

	static public String unIFCUnicode(String txt) {
		StringBuilder sb = new StringBuilder();
		StringBuilder su4 = new StringBuilder();
		int state = 0;
		for (char ch : txt.toCharArray()) {
			switch (state) {
			default:
			case 0:
				if (ch == '\\')
					state = 1;
				else
					sb.append(ch);
				break;
			case 1:
				if (ch == 'X' || ch == 'x')
					state = 2;
				else
					state = 0;
				break;
			case 2:
				if (ch == '2' || ch == '4')
					state = 3;
				else
					state = 0;
				break;
			case 3:
				if (ch == '\\')
					state = 4;
				else
					state = 0;
				break;

			case 4:
				if (ch == '\\')
					state = 5;
				else {
					su4.append(ch);
					if (su4.length() > 3) {
						sb.append("\\u");
						sb.append(su4);
						su4.setLength(0);
					}
				}
				break;
			case 5:
				if (ch == '\'') {
					sb.append("'");
					state = 0;
				}
				if (ch == '/' || ch == '\\')
					state = 0;
				break;
			}
		}

		try {
			return decodeUnicode(sb.toString());
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("org was: " + txt);
			System.err.println("was: " + sb.toString());
			return sb.toString();// StringEscapeUtils.unescapeJava(sb.toString()); // For some reasons this
									// blocks
		}
	}

	public static String decodeUnicode(String str) {
		Pattern pattern = Pattern.compile("\\\\u(\\p{XDigit}{4})");
		Matcher matcher = pattern.matcher(str);
		StringBuffer decodedString = new StringBuffer();

		while (matcher.find()) {
			String unicodeChar = matcher.group(1);
			char decodedChar = (char) Integer.parseInt(unicodeChar, 16);
			matcher.appendReplacement(decodedString, Character.toString(decodedChar));
		}
		matcher.appendTail(decodedString);

		return decodedString.toString();
	}
}
