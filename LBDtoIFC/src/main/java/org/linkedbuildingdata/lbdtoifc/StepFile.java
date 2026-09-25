package org.linkedbuildingdata.lbdtoifc;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class StepFile {
    private final List<String> entities = new ArrayList<>();

    int add(String entity, String... arguments) {
        entities.add(entity.toUpperCase(Locale.ROOT) + "(" + String.join(",", arguments) + ")");
        return entities.size();
    }

    static String ref(int id) {
        return "#" + id;
    }

    static String refs(List<Integer> ids) {
        return "(" + ids.stream().map(StepFile::ref).reduce((a, b) -> a + "," + b).orElse("") + ")";
    }

    static String string(String value) {
        if (value == null) {
            return "$";
        }
        StringBuilder encoded = new StringBuilder("'");
        StringBuilder unicode = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint >= 32 && codePoint <= 126 && codePoint != '\\') {
                flushUnicode(encoded, unicode);
                if (codePoint == '\'') {
                    encoded.append("''");
                } else {
                    encoded.append((char) codePoint);
                }
            } else {
                if (codePoint <= 0xffff) {
                    unicode.append(String.format(Locale.ROOT, "%04X", codePoint));
                } else {
                    char[] surrogatePair = Character.toChars(codePoint);
                    unicode.append(String.format(Locale.ROOT, "%04X%04X",
                            (int) surrogatePair[0], (int) surrogatePair[1]));
                }
            }
        }
        flushUnicode(encoded, unicode);
        return encoded.append('\'').toString();
    }

    private static void flushUnicode(StringBuilder target, StringBuilder unicode) {
        if (!unicode.isEmpty()) {
            target.append("\\X2\\").append(unicode).append("\\X0\\");
            unicode.setLength(0);
        }
    }

    void write(Writer writer, String sourceName) throws IOException {
        String date = LocalDate.now().toString();
        writer.write("ISO-10303-21;\nHEADER;\n");
        writer.write("FILE_DESCRIPTION(('ViewDefinition [ReferenceView]'),'2;1');\n");
        writer.write("FILE_NAME(" + string(sourceName) + "," + string(date)
                + ",('LBDtoIFC'),(''),'LBDtoIFC 0.1.0','LBDtoIFC','');\n");
        writer.write("FILE_SCHEMA(('IFC4'));\nENDSEC;\nDATA;\n");
        for (int index = 0; index < entities.size(); index++) {
            writer.write("#" + (index + 1) + "=" + entities.get(index) + ";\n");
        }
        writer.write("ENDSEC;\nEND-ISO-10303-21;\n");
    }
}
