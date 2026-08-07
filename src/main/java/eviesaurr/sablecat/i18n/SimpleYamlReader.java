package eviesaurr.sablecat.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

public final class SimpleYamlReader {

    private SimpleYamlReader() {}

    public static Map<String, Object> read(Reader reader) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        Deque<Map<String, Object>> mapStack = new ArrayDeque<>();
        Deque<Integer> indentStack = new ArrayDeque<>();
        mapStack.push(root);
        indentStack.push(-1);

        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            line = stripComment(line);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            int indent = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') indent++;
                else break;
            }

            while (!indentStack.isEmpty() && indentStack.peek() >= indent) {
                indentStack.pop();
                mapStack.pop();
            }

            int colonIdx = findColon(trimmed);
            if (colonIdx < 0) continue;

            String key = trimmed.substring(0, colonIdx).trim();
            String value = trimmed.substring(colonIdx + 1).trim();

            if (value.isEmpty()) {
                Map<String, Object> child = new LinkedHashMap<>();
                mapStack.peek().put(key, child);
                mapStack.push(child);
                indentStack.push(indent);
            } else {
                mapStack.peek().put(key, unquote(value));
            }
        }

        return root;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> flatten(Map<String, Object> nested) {
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", nested, flat);
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flatten(key, (Map<String, Object>) entry.getValue(), result);
            } else {
                result.put(key, String.valueOf(entry.getValue()));
            }
        }
    }

    private static int findColon(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':') return i;
            if (c == '"' || c == '\'') {
                // 跳过引号内的内容
                char quote = c;
                i++;
                while (i < s.length() && s.charAt(i) != quote) i++;
            }
        }
        return -1;
    }

    private static String unquote(String s) {
        if ((s.startsWith("\"") && s.endsWith("\"")) ||
            (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String stripComment(String line) {
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quoteChar) inQuote = false;
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quoteChar = c;
                } else if (c == '#') {
                    return line.substring(0, i);
                }
            }
        }
        return line;
    }
}
