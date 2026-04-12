package com.example.codeannotationplugin;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MethodContext {

    private final String language;                  // 编程语言
    private final String filePath;                  // 文件路径
    private final String className;                 // 所属类名
    private final String methodName;                // 方法名
    private final String methodSignature;           // 方法签名
    private final String returnType;                // 返回类型
    private final List<String> parameters;          // 参数列表
    private final List<String> throwsTypes;         // throws 异常类型列表
    private final List<String> annotations;         // 方法注解列表
    private final List<String> imports;             // import 语句列表
    private final String docComment;                // 已有文档注释
    private final String methodText;                // 完整方法源码
    private final String selectedText;              // 当前选中的代码片段
    private final String contextBeforeSnippet;      // 方法前文片段
    private final String contextAfterSnippet;       // 方法后文片段
    private final String previousMethodSignature;   // 前一个方法签名
    private final String previousMethodText;        // 前一个方法源码
    private final String nextMethodSignature;       // 后一个方法签名
    private final String nextMethodText;            // 后一个方法源码
    private final String classSnippet;              // 当前类源码片段
    private final boolean hasSelection;             // 是否存在选区
    private final int selectionStartOffset;         // 选区起始偏移量
    private final int selectionEndOffset;           // 选区结束偏移量
    private final int selectionStartLine;           // 选区起始行号
    private final int selectionEndLine;             // 选区结束行号
    private final int docCommentStartOffset;        // 文档注释起始偏移量
    private final int docCommentEndOffset;          // 文档注释结束偏移量
    private final int methodStartOffset;            // 方法起始偏移量
    private final int methodEndOffset;              // 方法结束偏移量

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
            @NotNull List<String> imports,
            @NotNull String docComment,
            @NotNull String methodText,
            @NotNull String selectedText,
            @NotNull String contextBeforeSnippet,
            @NotNull String contextAfterSnippet,
            @NotNull String previousMethodSignature,
            @NotNull String previousMethodText,
            @NotNull String nextMethodSignature,
            @NotNull String nextMethodText,
            @NotNull String classSnippet,
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
