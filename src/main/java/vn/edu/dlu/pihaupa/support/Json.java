package vn.edu.dlu.pihaupa.support;

import java.util.*;

/** Small JSON encoder: all user-provided strings are escaped at the boundary. */
public final class Json {
    private Json() {}
    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
                }
            }
            return out.append('"').toString();
        }
        if (value instanceof Number n) return Double.isFinite(n.doubleValue()) ? n.toString() : "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner out = new StringJoiner(",", "{", "}");
            map.forEach((k,v) -> out.add(encode(k.toString()) + ":" + encode(v)));
            return out.toString();
        }
        if (value instanceof Iterable<?> list) {
            StringJoiner out = new StringJoiner(",", "[", "]");
            list.forEach(v -> out.add(encode(v)));
            return out.toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }
}
