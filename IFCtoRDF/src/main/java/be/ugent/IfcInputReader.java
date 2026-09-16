/*
Copyright (c) 2026 Jyrki Oraskari

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
package be.ugent;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.buildingsmart.tech.ifcowl.ExpressReader;
import com.buildingsmart.tech.ifcowl.vo.AttributeVO;
import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Adapts the supported IFC serializations to the converter's IFC-SPF input.
 *
 * <p>The mature RDF writer understands IFC-SPF and the loaded EXPRESS metadata.
 * XML and JSON inputs are therefore normalized to an in-memory SPF stream instead
 * of maintaining three separate RDF conversion implementations.</p>
 */
final class IfcInputReader {
    /** Serialization determined from the first meaningful character in the file. */
    enum Format { SPF, XML, JSON }

    private IfcInputReader() { }

    /**
     * Detects the serialization from content rather than the filename extension.
     * This also permits generic {@code .xml} and {@code .json} filenames and files
     * prefixed by a UTF-8 byte-order mark.
     */
    static Format detect(Path path) throws IOException {
        if (isGitLfsPointer(path)) {
            throw new IOException("Git LFS payload is not available for IFC input: " + path
                    + ". Fetch the LFS object or use an available IFC/JSON or IFC/XML variant.");
        }
        try (InputStream input = Files.newInputStream(path)) {
            int value = input.read();
            if (value == 0xef) {
                int second = input.read();
                int third = input.read();
                if (second != 0xbb || third != 0xbf) return Format.SPF;
                value = input.read();
            }
            while (value != -1 && Character.isWhitespace(value)) value = input.read();
            if (value == '<') return Format.XML;
            if (value == '{' || value == '[') return Format.JSON;
            return Format.SPF;
        }
    }

    /** Returns true when the working-tree file contains only a Git LFS pointer. */
    static boolean isGitLfsPointer(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 1024) return false;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return "version https://git-lfs.github.com/spec/v1".equals(reader.readLine());
        }
    }

    /**
     * Reads the schema identifier carried by a structured input.
     *
     * @return the declared/inferred schema, or {@code null} for IFC-SPF, whose
     *         header is parsed by {@link IfcSpfReader}
     */
    static String schema(Path path) throws IOException {
        return switch (detect(path)) {
            case SPF -> null;
            case JSON -> jsonSchema(path);
            case XML -> xmlSchema(path);
        };
    }

    /**
     * Opens an SPF stream for the common RDF writer. Native SPF is streamed from
     * disk; XML and JSON are first mapped using the selected EXPRESS schema.
     */
    static InputStream openSpf(Path path, Map<String, EntityVO> entities, Map<String, TypeVO> types, String schema)
            throws IOException {
        return switch (detect(path)) {
            case SPF -> Files.newInputStream(path);
            case JSON -> new ByteArrayInputStream(new JsonToSpf(entities, types).convert(path, schema));
            case XML -> new ByteArrayInputStream(new XmlToSpf(entities, types).convert(path, schema));
        };
    }

    private static String jsonSchema(Path path) throws IOException {
        try (var reader = Json.createReader(openUtf8Reader(path))) {
            JsonValue root = reader.readValue();
            if (root instanceof JsonObject object) {
                String schema = string(object, "schemaIdentifier");
                if (schema == null) schema = string(object, "schema");
                if (schema != null) return schema;
            }
        }
        // Legacy IFC.JAVA tree JSON omitted a header and was emitted as IFC4 ADD2.
        return "IFC4_ADD2";
    }

    /** Removes a decoded UTF-8 BOM because JSON-P treats it as JSON content. */
    private static Reader openUtf8Reader(Path path) throws IOException {
        PushbackReader reader = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 1);
        int first = reader.read();
        if (first != -1 && first != '\ufeff') reader.unread(first);
        return reader;
    }

    private static String xmlSchema(Path path) throws IOException {
        Document document = parseXml(path);
        Element root = document.getDocumentElement();
        for (String candidate : new String[] { root.getNamespaceURI(), root.getAttribute("schemaIdentifier"),
                root.getAttribute("express"), root.getAttribute("configuration") }) {
            String schema = schemaToken(candidate);
            if (schema != null) return schema;
        }
        // Legacy IFC.JAVA tree XML omitted a header and was emitted as IFC4 ADD2.
        return "IFC4_ADD2";
    }

    private static String schemaToken(String text) {
        if (text == null) return null;
        String upper = text.toUpperCase(Locale.ROOT).replace('-', '_');
        for (String candidate : List.of("IFC4X3_RC1", "IFC4X3", "IFC4X1", "IFC4_ADD2", "IFC4_ADD1",
                "IFC2X3_TC1", "IFC2X3_FINAL", "IFC4")) {
            if (upper.contains(candidate)) return candidate;
        }
        return null;
    }

    private static Document parseXml(Path path) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Structured IFC is self-contained. Disable external entities and
            // schemas both for predictable offline conversion and XXE protection.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(path.toFile());
        } catch (Exception e) {
            throw new IOException("Cannot parse IFC/XML file: " + path, e);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonValue value = object.get(key);
        return value instanceof JsonString text ? text.getString() : null;
    }

    /** Shared schema-aware renderer used by the XML and JSON tree walkers. */
    private abstract static class ToSpf<T> {
        final Map<String, EntityVO> entities;
        final Map<String, TypeVO> types;
        final Map<String, Long> namedIds = new LinkedHashMap<>();
        final IdentityHashMap<T, Long> objectIds = new IdentityHashMap<>();
        final List<T> definitions = new ArrayList<>();
        long nextId = 1;

        ToSpf(Map<String, EntityVO> entities, Map<String, TypeVO> types) {
            this.entities = entities;
            this.types = types;
        }

        abstract String type(T value);
        abstract String identity(T value);
        abstract boolean isReference(T value);
        abstract Object property(T value, String name);
        abstract String scalar(Object value, TypeVO expectedType) throws IOException;
        abstract Object normalizeGlobalId(Object value);

        boolean isEntity(T value) { return entity(type(value)) != null; }

        EntityVO entity(String name) {
            return name == null ? null : entities.get(ExpressReader.formatClassName(name));
        }

        TypeVO typeInfo(String name) {
            return name == null ? null : types.get(ExpressReader.formatClassName(name));
        }

        long register(T value) {
            Long old = objectIds.get(value);
            if (old != null) return old;
            String identity = identity(value);
            // References may encounter the same logical entity through distinct
            // XML/JSON objects, so stable IFC identifiers take precedence over
            // Java object identity.
            if (identity != null && namedIds.containsKey(identity)) {
                long id = namedIds.get(identity);
                objectIds.put(value, id);
                return id;
            }
            long id = nextId++;
            objectIds.put(value, id);
            if (identity != null) namedIds.put(identity, id);
            definitions.add(value);
            return id;
        }

        byte[] render(String schema) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            String header = """
                    ISO-10303-21;
                    HEADER;
                    FILE_DESCRIPTION(('ViewDefinition [ReferenceView]'),'2;1');
                    FILE_NAME('structured-input.ifc','',(),(),'IFCtoRDF','IFCtoRDF','');
                    FILE_SCHEMA(('%s'));
                    ENDSEC;
                    DATA;
                    """.formatted(spfSchema(schema));
            output.write(header.getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < definitions.size(); i++) {
                T value = definitions.get(i);
                long id = objectIds.get(value);
                EntityVO entity = entity(type(value));
                if (entity == null || isReference(value)) continue;
                StringBuilder line = new StringBuilder().append('#').append(id).append('=').append(entity.getName().toUpperCase(Locale.ROOT))
                        .append('(');
                // EXPRESS metadata supplies canonical attribute order; object key
                // or element order in the source serialization is not significant.
                List<AttributeVO> attributes = entity.getDerivedAttributeList();
                for (int n = 0; n < attributes.size(); n++) {
                    if (n > 0) line.append(',');
                    AttributeVO attribute = attributes.get(n);
                    Object raw = property(value, attribute.getOriginalName());
                    if (raw == null) raw = property(value, attribute.getName());
                    if (isGlobalId(attribute)) raw = normalizeGlobalId(raw);
                    line.append(raw == null ? "$" : scalar(raw, attribute.getType()));
                }
                line.append(");\n");
                output.write(line.toString().getBytes(StandardCharsets.UTF_8));
            }
            output.write("ENDSEC;\nEND-ISO-10303-21;\n".getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        }

        private static String spfSchema(String schema) {
            String upper = schema == null ? "IFC4" : schema.toUpperCase(Locale.ROOT);
            if (upper.startsWith("IFC2X3")) return "IFC2X3";
            if (upper.equals("IFC4_ADD1") || upper.equals("IFC4_ADD2")) return "IFC4";
            return upper;
        }

        String quote(String value) { return "'" + value.replace("'", "''") + "'"; }

        private boolean isGlobalId(AttributeVO attribute) {
            return "GlobalId".equalsIgnoreCase(attribute.getOriginalName())
                    || "GlobalId".equalsIgnoreCase(attribute.getName());
        }

        boolean enumType(TypeVO value) {
            if (value == null) return false;
            if (!value.getEnumEntities().isEmpty()) return true;
            TypeVO parent = typeInfo(value.getPrimarytype());
            return parent != value && enumType(parent);
        }

        boolean stringType(TypeVO value) {
            if (value == null) return false;
            String primary = value.getPrimarytype();
            if (primary == null) return false;
            if (primary.toUpperCase(Locale.ROOT).contains("STRING")) return true;
            TypeVO parent = typeInfo(primary);
            return parent != value && stringType(parent);
        }
    }

    private static final class JsonToSpf extends ToSpf<JsonObject> {
        private final IdentityHashMap<JsonObject, String> inferredTypes = new IdentityHashMap<>();

        JsonToSpf(Map<String, EntityVO> entities, Map<String, TypeVO> types) { super(entities, types); }

        byte[] convert(Path path, String schema) throws IOException {
            try (var reader = Json.createReader(openUtf8Reader(path))) {
                JsonValue root = reader.readValue();
                JsonValue data = root instanceof JsonObject object && object.containsKey("data") ? object.get("data") : root;
                scan(data);
                return render(schema);
            }
        }

        private void scan(JsonValue value) {
            // Register every definition before rendering so forward references can
            // be resolved regardless of their position in the JSON document.
            if (value instanceof JsonArray array) {
                array.forEach(this::scan);
            } else if (value instanceof JsonObject object) {
                if (isEntity(object) && !isReference(object)) register(object);
                object.values().forEach(this::scan);
            }
        }

        @Override String type(JsonObject value) {
            String explicit = string(value, "type");
            return explicit != null ? explicit : inferredTypes.get(value);
        }
        @Override String identity(JsonObject value) {
            String result = string(value, "globalId");
            return result != null ? result : string(value, "id");
        }
        @Override boolean isReference(JsonObject value) { return value.containsKey("ref"); }
        @Override Object property(JsonObject value, String name) {
            if (name == null) return null;
            for (Map.Entry<String, JsonValue> entry : value.entrySet())
                if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
            return null;
        }

        @Override Object normalizeGlobalId(Object raw) {
            if (raw instanceof JsonString value) return Json.createValue(compressUuid(value.getString()));
            return raw;
        }

        @Override String scalar(Object raw, TypeVO expectedType) throws IOException {
            JsonValue value = (JsonValue) raw;
            return switch (value.getValueType()) {
                case NULL -> "$";
                case TRUE -> ".T.";
                case FALSE -> ".F.";
                case NUMBER -> ((JsonNumber) value).toString();
                case STRING -> stringValue(((JsonString) value).getString(), expectedType);
                case ARRAY -> array((JsonArray) value, expectedType);
                case OBJECT -> object((JsonObject) value, expectedType);
            };
        }

        private String stringValue(String value, TypeVO expectedType) {
            if (expectedType != null && entity(expectedType.getName()) != null && namedIds.containsKey(value))
                return "#" + namedIds.get(value);
            return enumType(expectedType) ? "." + value.toUpperCase(Locale.ROOT) + "." : quote(value);
        }

        private String array(JsonArray array, TypeVO expectedType) throws IOException {
            List<String> values = new ArrayList<>();
            for (JsonValue value : array) values.add(scalar(value, expectedType));
            return "(" + String.join(",", values) + ")";
        }

        private String object(JsonObject object, TypeVO expectedType) throws IOException {
            String reference = string(object, "ref");
            if (reference != null) {
                Long id = namedIds.get(reference);
                if (id == null) throw new IOException("IFC/JSON reference has no target: " + reference);
                return "#" + id;
            }
            // IFC.JAVA represents the rows of schema-level list-of-list values
            // (notably IfcCartesianPointList3D.CoordList) as convenience entity
            // objects. In SPF these rows must remain inline aggregates, not
            // references to IfcCartesianPoint instances.
            if (isEntity(object) && expectedType != null && entity(expectedType.getName()) == null) {
                JsonValue coordinates = object.get("coordinates");
                if (coordinates != null) return scalar(coordinates, expectedType);
            }
            if (isEntity(object)) return "#" + register(object);
            String objectType = type(object);
            if (objectType == null && expectedType != null && entity(expectedType.getName()) != null) {
                inferredTypes.put(object, expectedType.getName());
                return "#" + register(object);
            }
            // Some IFC/JSON writers wrap aggregate attributes in a named
            // property object (for example {"units":[...]}), without a type.
            // The attribute metadata already tells us how to serialize it.
            if (objectType == null && object.size() == 1)
                return scalar(object.values().iterator().next(), expectedType);
            TypeVO wrappedType = typeInfo(objectType);
            JsonValue wrapped = object.get("value");
            // IFC/JSON represents an unset optional typed value by retaining its
            // type object while omitting the value member; SPF uses '$' for this.
            if (wrapped == null && wrappedType != null) return "$";
            if (wrapped != null && wrappedType != null) {
                String serialized = scalar(wrapped, wrappedType);
                // A value whose declared JSON type is already the EXPRESS
                // attribute/aggregate element type is written directly. Typed
                // parameters are only required when selecting one alternative
                // from an EXPRESS SELECT.
                if (expectedType != null && wrappedType.getName().equalsIgnoreCase(expectedType.getName()))
                    return serialized;
                return wrappedType.getName().toUpperCase(Locale.ROOT) + "(" + serialized + ")";
            }
            throw new IOException("Unsupported IFC/JSON value object: " + object);
        }
    }

    private static final class XmlToSpf extends ToSpf<Element> {
        // Some IFC/XML serializers wrap an entity in an attribute-named element
        // rather than writing xsi:type. The expected EXPRESS type supplies it.
        private final IdentityHashMap<Element, String> inferredTypes = new IdentityHashMap<>();

        XmlToSpf(Map<String, EntityVO> entities, Map<String, TypeVO> types) { super(entities, types); }

        byte[] convert(Path path, String schema) throws IOException {
            Document document = parseXml(path);
            scan(document.getDocumentElement());
            return render(schema);
        }

        private void scan(Element element) {
            // As with JSON, perform a registration pass before resolving refs.
            if (isEntity(element) && !isReference(element)) register(element);
            for (Element child : children(element)) scan(child);
        }

        @Override String type(Element value) {
            String inferred = inferredTypes.get(value);
            if (inferred != null) return inferred;
            String type = attribute(value, "type");
            if (type != null) return type;
            String local = value.getLocalName();
            return local != null ? local : value.getTagName().replaceFirst("^.*:", "");
        }
        @Override String identity(Element value) {
            String id = attribute(value, "globalId");
            return id != null ? id : attribute(value, "id");
        }
        @Override boolean isReference(Element value) { return attribute(value, "ref") != null; }

        @Override Object property(Element value, String name) {
            if (name == null) return null;
            String attribute = attribute(value, name);
            if (attribute != null) return attribute;
            for (Element child : children(value)) {
                String local = child.getLocalName() != null ? child.getLocalName() : child.getTagName().replaceFirst("^.*:", "");
                if (local.equalsIgnoreCase(name)) return child;
            }
            return null;
        }

        @Override Object normalizeGlobalId(Object raw) {
            if (raw instanceof String value) return compressUuid(value);
            return raw;
        }

        @Override String scalar(Object raw, TypeVO expectedType) throws IOException {
            if (raw instanceof String string) {
                if (expectedType != null && entity(expectedType.getName()) != null && namedIds.containsKey(string))
                    return "#" + namedIds.get(string);
                return enumType(expectedType) ? "." + string.toUpperCase(Locale.ROOT) + "." : quote(string);
            }
            Element element = (Element) raw;
            if ("true".equalsIgnoreCase(attribute(element, "nil"))) return "$";
            String reference = attribute(element, "ref");
            if (reference == null && children(element).isEmpty()) {
                String text = element.getTextContent().trim();
                if (namedIds.containsKey(text)) reference = text;
            }
            if (reference != null && !reference.isBlank()) {
                Long id = namedIds.get(reference);
                if (id == null) throw new IOException("IFC/XML reference has no target: " + reference);
                return "#" + id;
            }
            if (isEntity(element)) return "#" + register(element);
            List<Element> children = children(element);
            EntityVO expectedEntity = expectedType == null ? null : entity(expectedType.getName());
            if (expectedEntity != null && !children.isEmpty() && !allEntityValues(children)) {
                inferredTypes.put(element, expectedEntity.getName());
                return "#" + register(element);
            }
            if (children.size() > 1) {
                List<String> values = new ArrayList<>();
                for (Element child : children) values.add(scalar(child, expectedType));
                return "(" + String.join(",", values) + ")";
            }
            if (children.size() == 1) return scalar(children.get(0), expectedType);
            String value = attribute(element, "value");
            if (value == null) value = element.getTextContent().trim();
            TypeVO wrappedType = typeInfo(type(element));
            if (wrappedType != null) return wrappedType.getName().toUpperCase(Locale.ROOT) + "(" + primitive(value, wrappedType) + ")";
            return primitive(value, expectedType);
        }

        private boolean allEntityValues(List<Element> values) {
            for (Element value : values) {
                if (!isEntity(value)) return false;
            }
            return true;
        }

        private String primitive(String value, TypeVO expectedType) {
            if (value.equalsIgnoreCase("true")) return ".T.";
            if (value.equalsIgnoreCase("false")) return ".F.";
            if (enumType(expectedType)) return "." + value.toUpperCase(Locale.ROOT) + ".";
            if (!stringType(expectedType) && value.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][-+]?\\d+)?")) return value;
            return quote(value);
        }

        private static String attribute(Element element, String name) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node item = attributes.item(i);
                String local = item.getLocalName() != null ? item.getLocalName() : item.getNodeName().replaceFirst("^.*:", "");
                if (local.equalsIgnoreCase(name)) return item.getNodeValue();
            }
            return null;
        }

        private static List<Element> children(Element element) {
            List<Element> result = new ArrayList<>();
            NodeList nodes = element.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) if (nodes.item(i) instanceof Element child) result.add(child);
            return result;
        }
    }

    private static String compressUuid(String value) {
        // IFC/JSON and IFC/XML may use ordinary UUIDs, while IFC-SPF represents
        // IfcGloballyUniqueId with buildingSMART's 22-character base-64 alphabet.
        String hex = value.replace("-", "");
        if (!hex.matches("(?i)[0-9a-f]{32}")) return value;
        BigInteger number = new BigInteger(hex, 16);
        char[] alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$".toCharArray();
        char[] result = new char[22];
        BigInteger radix = BigInteger.valueOf(64);
        for (int i = result.length - 1; i >= 0; i--) {
            BigInteger[] part = number.divideAndRemainder(radix);
            result[i] = alphabet[part[1].intValue()];
            number = part[0];
        }
        return new String(result);
    }
}
