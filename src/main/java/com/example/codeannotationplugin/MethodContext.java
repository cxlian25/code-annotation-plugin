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
    private final List<String> imports;
    private final String docComment;
    private final String methodText;
    private final String selectedText;
    private final String contextBeforeSnippet;
    private final String contextAfterSnippet;
    private final String previousMethodSignature;
    private final String previousMethodText;
    private final String nextMethodSignature;
    private final String nextMethodText;
    private final String classSnippet;
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
            @NotNull String language,                // 编程语言
            @NotNull String filePath,                // 文件路径
            @NotNull String className,               // 所属类名
            @NotNull String methodName,              // 方法名
            @NotNull String methodSignature,         // 方法签名
            @NotNull String returnType,              // 返回类型
            @NotNull List<String> parameters,        // 参数列表
            @NotNull List<String> throwsTypes,       // throws 异常类型列表
            @NotNull List<String> annotations,       // 方法注解列表
            @NotNull List<String> imports,           // import 语句列表
            @NotNull String docComment,              // 已有文档注释
            @NotNull String methodText,              // 完整方法源码
            @NotNull String selectedText,            // 当前选中的代码片段
            @NotNull String contextBeforeSnippet,    // 方法前文片段
            @NotNull String contextAfterSnippet,     // 方法后文片段
            @NotNull String previousMethodSignature, // 前一个方法签名
            @NotNull String previousMethodText,      // 前一个方法源码
            @NotNull String nextMethodSignature,     // 后一个方法签名
            @NotNull String nextMethodText,          // 后一个方法源码
            @NotNull String classSnippet,            // 当前类源码片段
            boolean hasSelection,                    // 是否存在选区
            int selectionStartOffset,                // 选区起始偏移量
            int selectionEndOffset,                  // 选区结束偏移量
            int selectionStartLine,                  // 选区起始行号
            int selectionEndLine,                    // 选区结束行号
            int docCommentStartOffset,               // 文档注释起始偏移量
            int docCommentEndOffset,                 // 文档注释结束偏移量
            int methodStartOffset,                   // 方法起始偏移量
            int methodEndOffset                      // 方法结束偏移量
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
        this.imports = Collections.unmodifiableList(new ArrayList<>(imports));
        this.docComment = docComment;
        this.methodText = methodText;
        this.selectedText = selectedText;
        this.contextBeforeSnippet = contextBeforeSnippet;
        this.contextAfterSnippet = contextAfterSnippet;
        this.previousMethodSignature = previousMethodSignature;
        this.previousMethodText = previousMethodText;
        this.nextMethodSignature = nextMethodSignature;
        this.nextMethodText = nextMethodText;
        this.classSnippet = classSnippet;
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

    public List<String> getImports() {
        return imports;
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

    public String getContextBeforeSnippet() {
        return contextBeforeSnippet;
    }

    public String getContextAfterSnippet() {
        return contextAfterSnippet;
    }

    public String getPreviousMethodSignature() {
        return previousMethodSignature;
    }

    public String getPreviousMethodText() {
        return previousMethodText;
    }

    public String getNextMethodSignature() {
        return nextMethodSignature;
    }

    public String getNextMethodText() {
        return nextMethodText;
    }

    public String getClassSnippet() {
        return classSnippet;
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
