package com.example.codeannotationplugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonUtil {

    private JsonUtil() {
    }

    @NotNull
    public static String escape(@NotNull String input) {
        StringBuilder builder = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
            }
        }
        return builder.toString();
    }

    @Nullable
    public static String extractTopLevelStringField(@NotNull String json, @NotNull String fieldName) {
        String keyToken = "\"" + fieldName + "\"";
        int searchFrom = 0;
        while (true) {
            int keyIndex = json.indexOf(keyToken, searchFrom);
            if (keyIndex < 0) {
                return null;
            }

            int colonIndex = findNextNonWhitespace(json, keyIndex + keyToken.length());
            if (colonIndex < 0 || json.charAt(colonIndex) != ':') {
                searchFrom = keyIndex + keyToken.length();
                continue;
            }

            int valueStart = findNextNonWhitespace(json, colonIndex + 1);
            if (valueStart < 0 || json.charAt(valueStart) != '"') {
                searchFrom = keyIndex + keyToken.length();
                continue;
            }

            return parseJsonStringValue(json, valueStart + 1);
        }
    }

    private static int findNextNonWhitespace(@NotNull String text, int fromIndex) {
        for (int i = fromIndex; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static String parseJsonStringValue(@NotNull String json, int contentStart) {
        StringBuilder builder = new StringBuilder();
        for (int i = contentStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return builder.toString();
            }
            if (ch != '\\') {
                builder.append(ch);
                continue;
            }

            if (i + 1 >= json.length()) {
                return null;
            }
            char escaped = json.charAt(++i);
            switch (escaped) {
                case '"':
                    builder.append('"');
                    break;
                case '\\':
                    builder.append('\\');
                    break;
                case '/':
                    builder.append('/');
                    break;
                case 'b':
                    builder.append('\b');
                    break;
                case 'f':
                    builder.append('\f');
                    break;
                case 'n':
                    builder.append('\n');
                    break;
                case 'r':
                    builder.append('\r');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                case 'u':
                    if (i + 4 >= json.length()) {
                        return null;
                    }
                    String hex = json.substring(i + 1, i + 5);
                    try {
                        builder.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                    i += 4;
                    break;
                default:
                    return null;
            }
        }
        return null;
    }
}