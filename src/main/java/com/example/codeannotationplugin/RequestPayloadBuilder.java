package com.example.codeannotationplugin;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RequestPayloadBuilder {

    private RequestPayloadBuilder() {
    }

    @NotNull
    public static String buildGenerateRequest(@NotNull MethodContext context, @NotNull String commentDetailLevel) {
        String targetCode = resolveTargetCode(context);
        String contextText = buildContextText(context);

        StringBuilder builder = new StringBuilder();
        builder.append('{');
        appendStringField(builder, "targetCode", targetCode);
        builder.append(',');
        appendStringField(builder, "context", contextText);
        builder.append(',');
        appendArrayField(builder, "imports", context.getImports());
        builder.append(',');
        appendStringField(builder, "contextBeforeSnippet", context.getContextBeforeSnippet());
        builder.append(',');
        appendStringField(builder, "contextAfterSnippet", context.getContextAfterSnippet());
        builder.append(',');
        appendStringField(builder, "previousMethodSignature", context.getPreviousMethodSignature());
        builder.append(',');
        appendStringField(builder, "previousMethodText", context.getPreviousMethodText());
        builder.append(',');
        appendStringField(builder, "nextMethodSignature", context.getNextMethodSignature());
        builder.append(',');
        appendStringField(builder, "nextMethodText", context.getNextMethodText());
        builder.append(',');
        appendStringField(builder, "classSnippet", context.getClassSnippet());
        builder.append(',');
        appendStringField(builder, "commentDetailLevel", commentDetailLevel);
        builder.append('}');
        return builder.toString();
    }

    @NotNull
    private static String resolveTargetCode(@NotNull MethodContext context) {
        if (context.hasSelection() && !context.getSelectedText().isBlank()) {
            return context.getSelectedText();
        }
        return context.getMethodText();
    }

    @NotNull
    private static String buildContextText(@NotNull MethodContext context) {
        StringBuilder builder = new StringBuilder(512);
        appendLine(builder, "language", context.getLanguage());
        appendLine(builder, "filePath", context.getFilePath());
        appendLine(builder, "className", context.getClassName());
        appendLine(builder, "methodName", context.getMethodName());
        appendLine(builder, "methodSignature", context.getMethodSignature());
        appendLine(builder, "returnType", context.getReturnType());
        appendListLine(builder, "parameters", context.getParameters());
        appendListLine(builder, "throwsTypes", context.getThrowsTypes());
        appendListLine(builder, "annotations", context.getAnnotations());
        appendListLine(builder, "imports", context.getImports());

        if (context.hasSelection()) {
            appendLine(builder, "selectionLines", context.getSelectionStartLine() + "-" + context.getSelectionEndLine());
            appendLine(builder, "selectedText", context.getSelectedText());
            appendLine(builder, "enclosingMethod", context.getMethodText());
        }

        appendOptionalLine(builder, "contextBeforeSnippet", context.getContextBeforeSnippet());
        appendOptionalLine(builder, "contextAfterSnippet", context.getContextAfterSnippet());
        appendOptionalLine(builder, "previousMethodSignature", context.getPreviousMethodSignature());
        appendOptionalLine(builder, "previousMethodText", context.getPreviousMethodText());
        appendOptionalLine(builder, "nextMethodSignature", context.getNextMethodSignature());
        appendOptionalLine(builder, "nextMethodText", context.getNextMethodText());
        appendOptionalLine(builder, "classSnippet", context.getClassSnippet());

        if (!context.getDocComment().isBlank()) {
            appendLine(builder, "existingDocComment", context.getDocComment());
        }

        return builder.toString();
    }

    private static void appendLine(@NotNull StringBuilder builder, @NotNull String key, @NotNull String value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(key).append(": ").append(value);
    }

    private static void appendListLine(@NotNull StringBuilder builder, @NotNull String key, @NotNull List<String> values) {
        appendLine(builder, key, String.join(" | ", values));
    }

    private static void appendOptionalLine(@NotNull StringBuilder builder, @NotNull String key, @NotNull String value) {
        if (!value.isBlank()) {
            appendLine(builder, key, value);
        }
    }

    private static void appendStringField(@NotNull StringBuilder builder, @NotNull String key, @NotNull String value) {
        builder.append('"').append(JsonUtil.escape(key)).append('"')
                .append(':')
                .append('"').append(JsonUtil.escape(value)).append('"');
    }

    private static void appendArrayField(@NotNull StringBuilder builder, @NotNull String key, @NotNull List<String> values) {
        builder.append('"').append(JsonUtil.escape(key)).append('"')
                .append(':')
                .append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(JsonUtil.escape(values.get(i))).append('"');
        }
        builder.append(']');
    }
}
