/*
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
package be.ugent;

/** Decodes the Unicode escape sequences defined for strings in IFC-SPF (ISO 10303-21). */
final class IfcStepStringDecoder {
	private IfcStepStringDecoder() { }

	static String decode(String value) {
		int firstEscape = value.indexOf('\\');
		if (firstEscape < 0) return value;

		StringBuilder decoded = new StringBuilder(value.length());
		decoded.append(value, 0, firstEscape);
		for (int i = firstEscape; i < value.length();) {
			if (value.charAt(i) != '\\' || i + 1 >= value.length()) {
				decoded.append(value.charAt(i++));
				continue;
			}

			if (value.charAt(i + 1) == '\\') {
				decoded.append('\\');
				i += 2;
				continue;
			}

			if (i + 4 < value.length() && value.charAt(i + 3) == '\\'
					&& Character.toUpperCase(value.charAt(i + 1)) == 'X'
					&& (value.charAt(i + 2) == '2' || value.charAt(i + 2) == '4')) {
				int unitWidth = value.charAt(i + 2) == '2' ? 4 : 8;
				int dataStart = i + 4;
				int terminator = value.indexOf("\\X0\\", dataStart);
				if (terminator < 0) terminator = value.indexOf("\\x0\\", dataStart);
				int digits = terminator < 0 ? value.length() - dataStart : terminator - dataStart;
				if (digits > 0 && digits % unitWidth == 0 && isHex(value, dataStart, dataStart + digits)) {
					for (int offset = dataStart; offset < dataStart + digits; offset += unitWidth) {
						long codePoint = Long.parseLong(value.substring(offset, offset + unitWidth), 16);
						if (unitWidth == 4) decoded.append((char) codePoint);
						else if (codePoint <= Character.MAX_CODE_POINT)
							decoded.appendCodePoint((int) codePoint);
						else {
							decoded.append(value, i, offset + unitWidth);
							return decoded.append(value.substring(offset + unitWidth)).toString();
						}
					}
					i = terminator < 0 ? value.length() : terminator + 4;
					continue;
				}
			}

			decoded.append(value.charAt(i++));
		}
		return decoded.toString();
	}

	private static boolean isHex(String value, int start, int end) {
		for (int i = start; i < end; i++)
			if (Character.digit(value.charAt(i), 16) < 0) return false;
		return true;
	}
}
