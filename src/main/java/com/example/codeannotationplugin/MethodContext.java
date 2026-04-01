package com.example.codeannotationplugin;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MethodContext {

    private final String language;
    private final String filePath;
    private final String className;
    private final String methodName;
    private final String methodSignature;
    private final String returnType;
    private final List<String> parameters;
    private final List<String> throwsTypes;
    private final List<String> annotations;
    private final String docComment;
    private final String methodText;
    private final String selectedText;
    private final boolean hasSelection;
    private final int selectionStartOffset;
    private final int selectionEndOffset;
    private final int selectionStartLine;
    private final int selectionEndLine;
    private final int docCommentStartOffset;
    private final int docCommentEndOffset;
    private final int methodStartOffset;
    private final int methodEndOffset;

    public MethodContext(
            @NotNull String language,
            @NotNull String filePath,
            @NotNull String className,
            @NotNull String methodName,
            @NotNull String methodSignature,
            @NotNull String returnType,
            @NotNull List<String> parameters,
            @NotNull List<String> throwsTypes,
            @NotNull List<String> annotations,
            @NotNull String docComment,
            @NotNull String methodText,
            @NotNull String selectedText,
            boolean hasSelection,
            int selectionStartOffset,
            int selectionEndOffset,
            int selectionStartLine,
            int selectionEndLine,
            int docCommentStartOffset,
            int docCommentEndOffset,
            int methodStartOffset,
            int methodEndOffset
    ) {
        this.language = language;
        this.filePath = filePath;
        this.className = className;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
        this.returnType = returnType;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.throwsTypes = Collections.unmodifiableList(new ArrayList<>(throwsTypes));
        this.annotations = Collections.unmodifiableList(new ArrayList<>(annotations));
        this.docComment = docComment;
        this.methodText = methodText;
        this.selectedText = selectedText;
        this.hasSelection = hasSelection;
        this.selectionStartOffset = selectionStartOffset;
        this.selectionEndOffset = selectionEndOffset;
        this.selectionStartLine = selectionStartLine;
        this.selectionEndLine = selectionEndLine;
        this.docCommentStartOffset = docCommentStartOffset;
        this.docCommentEndOffset = docCommentEndOffset;
        this.methodStartOffset = methodStartOffset;
        this.methodEndOffset = methodEndOffset;
    }

    public String getLanguage() {
        return language;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public List<String> getThrowsTypes() {
        return throwsTypes;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public String getDocComment() {
        return docComment;
    }

    public String getMethodText() {
        return methodText;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public boolean hasSelection() {
        return hasSelection;
    }

    public int getSelectionStartOffset() {
        return selectionStartOffset;
    }

    public int getSelectionEndOffset() {
        return selectionEndOffset;
    }

    public int getSelectionStartLine() {
        return selectionStartLine;
    }

    public int getSelectionEndLine() {
        return selectionEndLine;
    }

    public int getDocCommentStartOffset() {
        return docCommentStartOffset;
    }

    public int getDocCommentEndOffset() {
        return docCommentEndOffset;
    }

    public boolean hasDocCommentRange() {
        return docCommentStartOffset >= 0 && docCommentEndOffset > docCommentStartOffset;
    }

    public int getMethodStartOffset() {
        return methodStartOffset;
    }

    public int getMethodEndOffset() {
        return methodEndOffset;
    }
}
