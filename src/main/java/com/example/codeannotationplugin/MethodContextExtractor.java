package com.example.codeannotationplugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class MethodContextExtractor {
    private static final int CONTEXT_CHAR_WINDOW = 1200;
    private static final int METHOD_SNIPPET_MAX_CHARS = 2500;
    private static final int CLASS_SNIPPET_MAX_CHARS = 5000;

    private MethodContextExtractor() {
    }

    public static ExtractionResult extract(@NotNull Project project) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return ExtractionResult.failure("No active editor found.");
        }

        Document document = editor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile == null) {
            return ExtractionResult.failure("Cannot resolve PSI file from the current editor.");
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        boolean hasSelection = selectionModel.hasSelection();
        int caretOffset = editor.getCaretModel().getOffset();
        int selectionStart = hasSelection ? selectionModel.getSelectionStart() : caretOffset;
        int selectionEnd = hasSelection ? selectionModel.getSelectionEnd() : caretOffset;

        PsiMethod method = findTargetMethod(psiFile, selectionStart, selectionEnd, caretOffset);
        if (method == null) {
            return ExtractionResult.failure("No Java method found in selection/caret scope.");
        }

        TextRange methodRange = method.getTextRange();
        int methodStartOffset = clampOffset(document, methodRange.getStartOffset());
        int methodEndOffset = clampOffsetInclusive(document, methodRange.getEndOffset());

        int normalizedStart;
        int normalizedEnd;

        if (hasSelection) {
            normalizedStart = Math.min(selectionStart, selectionEnd);
            normalizedEnd = Math.max(selectionStart, selectionEnd);
        } else {
            normalizedStart = methodStartOffset;
            normalizedEnd = methodEndOffset;
        }

        normalizedStart = clampOffset(document, normalizedStart);
        normalizedEnd = clampOffsetInclusive(document, normalizedEnd);

        String selectedText;
        if (hasSelection) {
            selectedText = selectionModel.getSelectedText();
            if (selectedText == null || selectedText.isBlank()) {
                selectedText = safeSubstring(document, normalizedStart, normalizedEnd);
            }
        } else {
            selectedText = method.getText();
        }

        int startLine = toLineNumber(document, normalizedStart);
        int endLine = toLineNumber(document, Math.max(normalizedStart, normalizedEnd - 1));

        MethodContext context = buildMethodContext(
                document,
                psiFile,
                method,
                selectedText,
                hasSelection,
                normalizedStart,
                normalizedEnd,
                startLine,
                endLine
        );

        return ExtractionResult.success(context);
    }

    public static BatchExtractionResult extractAllInCurrentFile(@NotNull Project project) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return BatchExtractionResult.failure("No active editor found.");
        }

        Document document = editor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile == null) {
            return BatchExtractionResult.failure("Cannot resolve PSI file from the current editor.");
        }

        Collection<PsiMethod> allMethods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class);
        if (allMethods.isEmpty()) {
            return BatchExtractionResult.failure("No Java methods found in current file.");
        }

        List<PsiMethod> sortedMethods = new ArrayList<>(allMethods);
        sortedMethods.sort(Comparator.comparingInt(method -> method.getTextRange().getStartOffset()));

        List<MethodContext> contexts = new ArrayList<>(sortedMethods.size());
        for (PsiMethod method : sortedMethods) {
            TextRange methodRange = method.getTextRange();
            int methodStartOffset = clampOffset(document, methodRange.getStartOffset());
            int methodEndOffset = clampOffsetInclusive(document, methodRange.getEndOffset());
            int startLine = toLineNumber(document, methodStartOffset);
            int endLine = toLineNumber(document, Math.max(methodStartOffset, methodEndOffset - 1));

            MethodContext context = buildMethodContext(
                    document,
                    psiFile,
                    method,
                    method.getText(),
                    false,
                    methodStartOffset,
                    methodEndOffset,
                    startLine,
                    endLine
            );
            contexts.add(context);
        }

        return BatchExtractionResult.success(contexts);
    }

    @NotNull
    private static MethodContext buildMethodContext(
            @NotNull Document document,
            @NotNull PsiFile psiFile,
            @NotNull PsiMethod method,
            @NotNull String selectedText,
            boolean hasSelection,
            int normalizedStart,
            int normalizedEnd,
            int startLine,
            int endLine
    ) {

        String filePath = psiFile.getVirtualFile() == null ? psiFile.getName() : psiFile.getVirtualFile().getPath();
        String language = psiFile.getLanguage().getID();

        PsiClass containingClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
        String className = containingClass == null ? "<TopLevel>" : containingClass.getQualifiedName();
        if (className == null || className.isBlank()) {
            className = containingClass == null ? "<TopLevel>" : containingClass.getName();
        }

        String returnType;
        PsiType methodReturnType = method.getReturnType();
        if (method.isConstructor()) {
            returnType = "constructor";
        } else if (methodReturnType == null) {
            returnType = "void";
        } else {
            returnType = methodReturnType.getPresentableText();
        }

        List<String> parameters = new ArrayList<>();
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            parameters.add(parameter.getType().getPresentableText() + " " + parameter.getName());
        }

        List<String> throwsTypes = new ArrayList<>();
        for (PsiClassType classType : method.getThrowsList().getReferencedTypes()) {
            throwsTypes.add(classType.getPresentableText());
        }

        List<String> annotations = new ArrayList<>();
        for (PsiAnnotation annotation : method.getModifierList().getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            annotations.add(qualifiedName == null ? annotation.getText() : qualifiedName);
        }
        List<String> imports = extractImports(psiFile);

        PsiDocComment methodDocComment = method.getDocComment();
        String docComment = methodDocComment == null ? "" : methodDocComment.getText();
        int docCommentStartOffset = -1;
        int docCommentEndOffset = -1;
        if (methodDocComment != null) {
            TextRange docCommentRange = methodDocComment.getTextRange();
            docCommentStartOffset = clampOffsetInclusive(document, docCommentRange.getStartOffset());
            docCommentEndOffset = clampOffsetInclusive(document, docCommentRange.getEndOffset());
        }
        String methodSignature = buildMethodSignature(method, returnType);
        TextRange methodRange = method.getTextRange();
        int methodStartOffset = clampOffset(document, methodRange.getStartOffset());
        int methodEndOffset = clampOffsetInclusive(document, methodRange.getEndOffset());
        int beforeStartOffset = Math.max(0, methodStartOffset - CONTEXT_CHAR_WINDOW);
        int afterEndOffset = Math.min(document.getTextLength(), methodEndOffset + CONTEXT_CHAR_WINDOW);
        String contextBeforeSnippet = safeSubstring(document, beforeStartOffset, methodStartOffset);
        String contextAfterSnippet = safeSubstring(document, methodEndOffset, afterEndOffset);
        MethodNeighborContext neighborContext = extractMethodNeighborContext(containingClass, method);
        String classSnippet = containingClass == null ? "" : truncateText(containingClass.getText(), CLASS_SNIPPET_MAX_CHARS);

        return new MethodContext(
                language,
                filePath,
                className,
                method.getName(),
                methodSignature,
                returnType,
                parameters,
                throwsTypes,
                annotations,
                imports,
                docComment,
                method.getText(),
                selectedText,
                contextBeforeSnippet,
                contextAfterSnippet,
                neighborContext.previousMethodSignature,
                neighborContext.previousMethodText,
                neighborContext.nextMethodSignature,
                neighborContext.nextMethodText,
                classSnippet,
                hasSelection,
                normalizedStart,
                normalizedEnd,
                startLine,
                endLine,
                docCommentStartOffset,
                docCommentEndOffset,
                methodStartOffset,
                methodEndOffset
        );
    }

    @Nullable
    private static PsiMethod findTargetMethod(@NotNull PsiFile psiFile, int selectionStart, int selectionEnd, int caretOffset) {
        if (selectionEnd > selectionStart) {
            PsiMethod methodFromSelection = findMethodFromSelection(psiFile, selectionStart, selectionEnd);
            if (methodFromSelection != null) {
                return methodFromSelection;
            }
        }

        return findMethodAtOffset(psiFile, caretOffset);
    }

    @Nullable
    private static PsiMethod findMethodFromSelection(@NotNull PsiFile psiFile, int selectionStart, int selectionEnd) {
        int safeStart = Math.max(0, selectionStart);
        int safeEnd = Math.max(safeStart, selectionEnd);

        PsiElement startElement = psiFile.findElementAt(safeStart);
        PsiElement endElement = psiFile.findElementAt(Math.max(0, safeEnd - 1));

        PsiMethod startMethod = PsiTreeUtil.getParentOfType(startElement, PsiMethod.class, false);
        PsiMethod endMethod = PsiTreeUtil.getParentOfType(endElement, PsiMethod.class, false);

        if (startMethod != null && startMethod == endMethod) {
            return startMethod;
        }

        Collection<PsiMethod> allMethods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class);
        List<PsiMethod> candidates = new ArrayList<>();
        for (PsiMethod method : allMethods) {
            TextRange range = method.getTextRange();
            boolean intersects = range.getEndOffset() > safeStart && range.getStartOffset() < safeEnd;
            if (intersects) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingInt(m -> m.getTextRange().getLength()));
        return candidates.get(0);
    }

    @Nullable
    private static PsiMethod findMethodAtOffset(@NotNull PsiFile psiFile, int offset) {
        int textLength = psiFile.getTextLength();
        if (textLength == 0) {
            return null;
        }

        int safeOffset = Math.max(0, Math.min(offset, textLength - 1));
        PsiElement element = psiFile.findElementAt(safeOffset);
        return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    }

    private static int clampOffset(@NotNull Document document, int offset) {
        int textLength = document.getTextLength();
        if (textLength == 0) {
            return 0;
        }
        return Math.max(0, Math.min(offset, textLength - 1));
    }

    private static int clampOffsetInclusive(@NotNull Document document, int offset) {
        int textLength = document.getTextLength();
        return Math.max(0, Math.min(offset, textLength));
    }

    private static int toLineNumber(@NotNull Document document, int offset) {
        int textLength = document.getTextLength();
        if (textLength == 0) {
            return 1;
        }
        int safeOffset = Math.max(0, Math.min(offset, textLength - 1));
        return document.getLineNumber(safeOffset) + 1;
    }

    @NotNull
    private static String safeSubstring(@NotNull Document document, int startOffset, int endOffset) {
        int start = Math.max(0, Math.min(startOffset, document.getTextLength()));
        int end = Math.max(start, Math.min(endOffset, document.getTextLength()));
        return document.getText(new TextRange(start, end));
    }

    @NotNull
    private static List<String> extractImports(@NotNull PsiFile psiFile) {
        if (!(psiFile instanceof PsiJavaFile javaFile)) {
            return List.of();
        }

        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return List.of();
        }

        List<String> imports = new ArrayList<>();
        for (PsiImportStatementBase statement : importList.getAllImportStatements()) {
            String text = statement.getText();
            if (text != null && !text.isBlank()) {
                imports.add(text);
            }
        }
        return imports;
    }

    @NotNull
    private static MethodNeighborContext extractMethodNeighborContext(@Nullable PsiClass containingClass, @NotNull PsiMethod currentMethod) {
        if (containingClass == null) {
            return MethodNeighborContext.empty();
        }

        PsiMethod[] methods = containingClass.getMethods();
        if (methods.length == 0) {
            return MethodNeighborContext.empty();
        }

        int currentIndex = -1;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i] == currentMethod) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex < 0) {
            return MethodNeighborContext.empty();
        }

        String previousMethodSignature = "";
        String previousMethodText = "";
        if (currentIndex > 0) {
            PsiMethod previousMethod = methods[currentIndex - 1];
            previousMethodSignature = buildMethodSignature(previousMethod, resolveReturnTypeText(previousMethod));
            previousMethodText = truncateText(previousMethod.getText(), METHOD_SNIPPET_MAX_CHARS);
        }

        String nextMethodSignature = "";
        String nextMethodText = "";
        if (currentIndex < methods.length - 1) {
            PsiMethod nextMethod = methods[currentIndex + 1];
            nextMethodSignature = buildMethodSignature(nextMethod, resolveReturnTypeText(nextMethod));
            nextMethodText = truncateText(nextMethod.getText(), METHOD_SNIPPET_MAX_CHARS);
        }

        return new MethodNeighborContext(
                previousMethodSignature,
                previousMethodText,
                nextMethodSignature,
                nextMethodText
        );
    }

    @NotNull
    private static String resolveReturnTypeText(@NotNull PsiMethod method) {
        if (method.isConstructor()) {
            return "constructor";
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return "void";
        }
        return returnType.getPresentableText();
    }

    @NotNull
    private static String truncateText(@NotNull String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int safeMax = Math.max(0, maxChars);
        return text.substring(0, safeMax) + "\n... [truncated]";
    }

    @NotNull
    private static String buildMethodSignature(@NotNull PsiMethod method, @NotNull String returnType) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getName()).append('(');
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameter.getType().getPresentableText())
                    .append(' ')
                    .append(parameter.getName());
        }
        builder.append(") : ").append(returnType);
        return builder.toString();
    }

    private static final class MethodNeighborContext {
        private final String previousMethodSignature;
        private final String previousMethodText;
        private final String nextMethodSignature;
        private final String nextMethodText;

        private MethodNeighborContext(
                @NotNull String previousMethodSignature,
                @NotNull String previousMethodText,
                @NotNull String nextMethodSignature,
                @NotNull String nextMethodText
        ) {
            this.previousMethodSignature = previousMethodSignature;
            this.previousMethodText = previousMethodText;
            this.nextMethodSignature = nextMethodSignature;
            this.nextMethodText = nextMethodText;
        }

        @NotNull
        private static MethodNeighborContext empty() {
            return new MethodNeighborContext("", "", "", "");
        }
    }

    public static final class ExtractionResult {

        private final MethodContext methodContext;
        private final String errorMessage;

        private ExtractionResult(@Nullable MethodContext methodContext, @Nullable String errorMessage) {
            this.methodContext = methodContext;
            this.errorMessage = errorMessage;
        }

        public static ExtractionResult success(@NotNull MethodContext methodContext) {
            return new ExtractionResult(methodContext, null);
        }

        public static ExtractionResult failure(@NotNull String errorMessage) {
            return new ExtractionResult(null, errorMessage);
        }

        public boolean isSuccess() {
            return methodContext != null;
        }

        @Nullable
        public MethodContext getMethodContext() {
            return methodContext;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class BatchExtractionResult {

        private final List<MethodContext> methodContexts;
        private final String errorMessage;

        private BatchExtractionResult(@Nullable List<MethodContext> methodContexts, @Nullable String errorMessage) {
            this.methodContexts = methodContexts == null ? null : List.copyOf(methodContexts);
            this.errorMessage = errorMessage;
        }

        public static BatchExtractionResult success(@NotNull List<MethodContext> methodContexts) {
            return new BatchExtractionResult(methodContexts, null);
        }

        public static BatchExtractionResult failure(@NotNull String errorMessage) {
            return new BatchExtractionResult(null, errorMessage);
        }

        public boolean isSuccess() {
            return methodContexts != null;
        }

        @Nullable
        public List<MethodContext> getMethodContexts() {
            return methodContexts;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
