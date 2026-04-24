package com.example.codeannotationplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


// 面板
public class CodeAnnotationToolWindowPanel {

    private static final String GENERATE_ENDPOINT_PATH = "/api/v1/annotation/generate";
    private static final int BALLOON_HIDE_DELAY_MS = 350;

    private final Project project;
    private final BackendClient backendClient;
    private final String backendBaseUrl;
    private final MethodChangeMonitor methodChangeMonitor;

    private final JPanel rootPanel;
    private final JTextArea outputArea;

    private final List<PendingComment> pendingComments = new ArrayList<>();
    private CommentDetailLevel selectedDetailLevel = CommentDetailLevel.CONCISE;

    public CodeAnnotationToolWindowPanel(Project project) {
        this.project = project;
        this.backendClient = new BackendClient();
        this.backendBaseUrl = PluginConfig.getBackendBaseUrl();
        this.methodChangeMonitor = new MethodChangeMonitor(project);

        this.rootPanel = new JPanel(new BorderLayout());
        this.rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("代码注释助手");
        JLabel subtitleLabel = new JLabel("项目：" + project.getName());
        JLabel backendLabel = new JLabel("作者：" + "cxlian");

        Icon pluginIcon = IconLoader.getIcon("/META-INF/pluginIcon.svg", CodeAnnotationToolWindowPanel.class);
        JLabel iconLabel = new JLabel(pluginIcon);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        headerPanel.add(backendLabel);

        JPanel headerContainer = new JPanel(new BorderLayout());
        headerContainer.add(iconLabel, BorderLayout.WEST);
        headerContainer.add(headerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        JButton generateButton = new JButton("生成注释");
        JButton detailLevelButton = new JButton(buildDetailLevelButtonText());
        JButton keepAllButton = new JButton("一键保留");
        JButton annotateAllButton = new JButton("一键注释全文件");

        buttonPanel.add(generateButton);
        buttonPanel.add(detailLevelButton);

        generateButton.addActionListener(e -> runGenerateFlow());
        detailLevelButton.addActionListener(e -> toggleCommentDetailLevel(detailLevelButton));
        keepAllButton.addActionListener(e -> keepAllPendingComments());
        annotateAllButton.addActionListener(e -> runGenerateAllFlow());

        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.add(headerContainer);
        topContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        topContainer.add(buttonPanel);
        topContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        JPanel batchButtonPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        batchButtonPanel.add(keepAllButton);
        batchButtonPanel.add(annotateAllButton);
        topContainer.add(batchButtonPanel);

        this.outputArea = new JTextArea();
        this.outputArea.setEditable(false);
        this.outputArea.setLineWrap(true);
        this.outputArea.setWrapStyleWord(true);
        this.outputArea.setRows(16);

        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("输出"));

        rootPanel.add(topContainer, BorderLayout.NORTH);
        rootPanel.add(outputScrollPane, BorderLayout.CENTER);

        appendOutput("面板已初始化。");
        appendOutput("1) 请将光标放在 Java 方法内，或选中一段代码。");
        appendOutput("2) 点击“注释风格”切换简洁/详细，再点击“生成注释”。");
        appendOutput("3) 点击“一键注释全文件”可批量处理当前页面所有方法。");
    }

    public JPanel getContent() {
        return rootPanel;
    }

    @NotNull
    private String buildDetailLevelButtonText() {
        return "注释风格：" + selectedDetailLevel.getDisplayText();
    }

    private void toggleCommentDetailLevel(@NotNull JButton detailLevelButton) {
        selectedDetailLevel = selectedDetailLevel == CommentDetailLevel.CONCISE
                ? CommentDetailLevel.DETAILED
                : CommentDetailLevel.CONCISE;
        detailLevelButton.setText(buildDetailLevelButtonText());
        appendOutput("已切换注释风格：" + selectedDetailLevel.getDisplayText());
    }

    private void runGenerateFlow() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "代码注释请求", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("正在提取代码上下文...");

                MethodContextExtractor.ExtractionResult extractionResult =
                        ReadAction.compute(() -> MethodContextExtractor.extract(project));

                if (!extractionResult.isSuccess() || extractionResult.getMethodContext() == null) {
                    String errorMessage = extractionResult.getErrorMessage() == null
                            ? "未知的上下文提取错误。"
                            : extractionResult.getErrorMessage();
                    appendOutputLater("[错误] " + errorMessage);
                    return;
                }

                MethodContext context = extractionResult.getMethodContext();
                String requestJson = RequestPayloadBuilder.buildGenerateRequest(context, selectedDetailLevel.name());
                String endpoint = backendBaseUrl + GENERATE_ENDPOINT_PATH;

                try {
                    indicator.setText("正在向后端发送请求...");
                    BackendClient.HttpResult response = backendClient.postJson(endpoint, requestJson);
                    handleGeneratedCommentResponse(response, context);
                } catch (HttpTimeoutException timeoutException) {
                    appendOutputLater("[错误] 请求超时: " + timeoutException.getMessage());
                } catch (ConnectException connectException) {
                    appendOutputLater("[错误] 无法连接后端，请先启动本地 API 服务。");
                } catch (IOException ioException) {
                    appendOutputLater("[错误] IO 异常: " + ioException.getMessage());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    appendOutputLater("[错误] 请求被中断。");
                } catch (Exception exception) {
                    appendOutputLater("[错误] 未知异常: " + exception.getMessage());
                }
            }
        });
    }

    private void runGenerateAllFlow() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "全文件一键注释", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("正在提取当前文件方法列表...");

                MethodContextExtractor.BatchExtractionResult extractionResult =
                        ReadAction.compute(() -> MethodContextExtractor.extractAllInCurrentFile(project));
                if (!extractionResult.isSuccess() || extractionResult.getMethodContexts() == null) {
                    String errorMessage = extractionResult.getErrorMessage() == null
                            ? "未知的方法提取错误。"
                            : extractionResult.getErrorMessage();
                    appendOutputLater("[错误] " + errorMessage);
                    return;
                }

                List<MethodContext> allMethods = extractionResult.getMethodContexts();
                if (allMethods.isEmpty()) {
                    appendOutputLater("[提示] 当前文件没有可注释的方法。");
                    return;
                }

                String endpoint = backendBaseUrl + GENERATE_ENDPOINT_PATH;
                List<GeneratedCommentItem> generatedItems = new ArrayList<>();
                List<String> failedMethods = new ArrayList<>();

                for (int i = 0; i < allMethods.size(); i++) {
                    MethodContext context = allMethods.get(i);
                    indicator.setFraction((i + 1d) / allMethods.size());
                    indicator.setText("正在生成注释 (" + (i + 1) + "/" + allMethods.size() + "): " + context.getMethodName());
                    String requestJson = RequestPayloadBuilder.buildGenerateRequest(context, selectedDetailLevel.name());
                    try {
                        BackendClient.HttpResult response = backendClient.postJson(endpoint, requestJson);
                        if (!isSuccessful(response)) {
                            String failure = summarizeBackendFailure(response);
                            failedMethods.add(context.getMethodName() + "(" + failure + ")");
                            appendOutputLater("[错误] " + context.getMethodName() + ": " + failure);
                            continue;
                        }
                        GeneratedAnnotationResult generatedResult = parseGeneratedAnnotationResult(response.getBody());
                        if (generatedResult.generatedComment == null || generatedResult.generatedComment.isBlank()) {
                            String backendMessage = extractBackendMessage(response.getBody());
                            if (backendMessage != null && !backendMessage.isBlank()) {
                                failedMethods.add(context.getMethodName() + "(" + backendMessage + ")");
                                appendOutputLater("[错误] " + context.getMethodName() + ": " + backendMessage);
                            } else {
                                failedMethods.add(context.getMethodName() + "(返回为空)");
                            }
                            continue;
                        }
                        if (generatedResult.llmFallbackUsed) {
                            appendOutputLater("[LLM fallback] " + context.getMethodName() + ": " + buildFallbackSummary(generatedResult));
                        }
                        generatedItems.add(new GeneratedCommentItem(context, generatedResult.generatedComment));
                    } catch (HttpTimeoutException timeoutException) {
                        failedMethods.add(context.getMethodName() + "(超时)");
                    } catch (ConnectException connectException) {
                        failedMethods.add(context.getMethodName() + "(连接失败)");
                        break;
                    } catch (IOException ioException) {
                        failedMethods.add(context.getMethodName() + "(IO异常)");
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        appendOutputLater("[错误] 请求被中断。");
                        return;
                    } catch (Exception exception) {
                        failedMethods.add(context.getMethodName() + "(未知异常)");
                    }
                }

                if (generatedItems.isEmpty()) {
                    appendOutputLater("[错误] 一键注释失败，未生成任何有效注释。");
                    if (!failedMethods.isEmpty()) {
                        appendOutputLater("[失败方法] " + String.join("，", failedMethods));
                    }
                    return;
                }

                ApplicationManager.getApplication().invokeLater(() ->
                        WriteCommandAction.runWriteCommandAction(project, "一键注释全文件", null, () -> {
                            int appliedCount = applyBatchComments(generatedItems);
                            int failedCount = allMethods.size() - appliedCount;
                            appendOutputLater("一键注释完成：成功 " + appliedCount + " 个，失败 " + failedCount + " 个。");
                            if (!failedMethods.isEmpty()) {
                                appendOutputLater("[失败方法] " + String.join("，", failedMethods));
                            }
                        })
                );
            }
        });
    }

    private void handleGeneratedCommentResponse(@NotNull BackendClient.HttpResult response, @NotNull MethodContext context) {
        if (!isSuccessful(response)) {
            appendOutputLater("[错误] " + summarizeBackendFailure(response));
            return;
        }

        GeneratedAnnotationResult generatedResult = parseGeneratedAnnotationResult(response.getBody());
        String generatedComment = generatedResult.generatedComment;
        if (generatedComment == null || generatedComment.isBlank()) {
            String backendMessage = extractBackendMessage(response.getBody());
            if (backendMessage != null && !backendMessage.isBlank()) {
                appendOutputLater("[错误] " + backendMessage);
                return;
            }
            appendOutputLater("[错误] 响应中未找到 generatedComment 字段。");
            return;
        }

        replaceOutputLater(buildOutputBoardText(generatedResult));
        ApplicationManager.getApplication().invokeLater(() ->
                WriteCommandAction.runWriteCommandAction(project, "插入临时注释", null, () -> insertPreviewComment(context, generatedComment))
        );
    }

    private void insertPreviewComment(@NotNull MethodContext context, @NotNull String generatedComment) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            appendOutputLater("[错误] 未找到当前编辑器，无法插入注释。");
            return;
        }

        Document document = editor.getDocument();
        AppliedComment appliedComment = applyCommentToDocument(document, context, generatedComment);
        if (appliedComment == null) {
            return;
        }

        RangeMarker commentMarker = document.createRangeMarker(
                appliedComment.commentStartOffset,
                appliedComment.commentEndOffset
        );
        commentMarker.setGreedyToLeft(true);
        commentMarker.setGreedyToRight(true);

        RangeMarker methodMarker = document.createRangeMarker(
                appliedComment.methodStartOffset,
                appliedComment.methodEndOffset
        );
        methodMarker.setGreedyToLeft(true);
        methodMarker.setGreedyToRight(true);

        RangeHighlighter highlighter = addPreviewHighlighter(editor, commentMarker);

        PendingComment newPendingComment = new PendingComment(
                editor,
                commentMarker,
                methodMarker,
                highlighter,
                null,
                context.getMethodSignature(),
                context.getMethodText(),
                context.getMethodName()
        );
        newPendingComment.balloon = showDecisionBalloon(editor, commentMarker, newPendingComment);
        registerHoverRestore(newPendingComment);
        pendingComments.add(newPendingComment);
    }

    private boolean isSuccessful(@NotNull BackendClient.HttpResult response) {
        int statusCode = response.getStatusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    @NotNull
    private String summarizeBackendFailure(@NotNull BackendClient.HttpResult response) {
        String backendMessage = extractBackendMessage(response.getBody());
        if (backendMessage != null && !backendMessage.isBlank()) {
            return "后端错误 " + response.getStatusCode() + ": " + backendMessage;
        }
        return "后端错误 " + response.getStatusCode() + ": " + previewResponseBody(response.getBody());
    }

    @Nullable
    private String extractBackendMessage(@NotNull String responseBody) {
        return JsonUtil.extractFirstTopLevelStringField(responseBody, "message", "error");
    }

    @NotNull
    private GeneratedAnnotationResult parseGeneratedAnnotationResult(@NotNull String responseBody) {
        String generatedComment = JsonUtil.extractTopLevelStringField(responseBody, "generatedComment");
        String requestedProvider = JsonUtil.extractTopLevelStringField(responseBody, "llmRequestedProvider");
        String actualProvider = JsonUtil.extractTopLevelStringField(responseBody, "llmActualProvider");
        String fallbackReason = JsonUtil.extractTopLevelStringField(responseBody, "llmFallbackReason");
        Boolean fallbackUsed = JsonUtil.extractTopLevelBooleanField(responseBody, "llmFallbackUsed");
        return new GeneratedAnnotationResult(
                generatedComment,
                requestedProvider,
                actualProvider,
                Boolean.TRUE.equals(fallbackUsed),
                fallbackReason
        );
    }

    @NotNull
    private String buildOutputBoardText(@NotNull GeneratedAnnotationResult generatedResult) {
        if (!generatedResult.llmFallbackUsed) {
            return generatedResult.generatedComment == null ? "" : generatedResult.generatedComment;
        }

        StringBuilder builder = new StringBuilder();
        if (generatedResult.generatedComment != null) {
            builder.append(generatedResult.generatedComment.trim());
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("[LLM fallback]\n");
        builder.append("provider: ")
                .append(defaultText(generatedResult.llmRequestedProvider, "unknown"))
                .append(" -> ")
                .append(defaultText(generatedResult.llmActualProvider, "unknown"));
        if (!isBlank(generatedResult.llmFallbackReason)) {
            builder.append("\nreason: ").append(generatedResult.llmFallbackReason.trim());
        }
        return builder.toString();
    }

    @NotNull
    private String buildFallbackSummary(@NotNull GeneratedAnnotationResult generatedResult) {
        StringBuilder builder = new StringBuilder();
        builder.append(defaultText(generatedResult.llmRequestedProvider, "unknown"))
                .append(" -> ")
                .append(defaultText(generatedResult.llmActualProvider, "unknown"));
        if (!isBlank(generatedResult.llmFallbackReason)) {
            builder.append(", reason=").append(generatedResult.llmFallbackReason.trim());
        }
        return builder.toString();
    }

    @NotNull
    private String defaultText(@Nullable String value, @NotNull String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    @NotNull
    private String previewResponseBody(@NotNull String responseBody) {
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "empty response";
        }
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180) + "...";
    }

    private int applyBatchComments(@NotNull List<GeneratedCommentItem> generatedItems) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            appendOutputLater("[错误] 未找到当前编辑器，无法执行一键注释。");
            return 0;
        }

        Document document = editor.getDocument();
        List<GeneratedCommentItem> sortedItems = new ArrayList<>(generatedItems);
        sortedItems.sort(Comparator.comparingInt((GeneratedCommentItem item) -> item.methodContext.getMethodStartOffset()).reversed());

        int appliedCount = 0;
        for (GeneratedCommentItem item : sortedItems) {
            AppliedComment appliedComment = applyCommentToDocument(document, item.methodContext, item.generatedComment);
            if (appliedComment == null) {
                continue;
            }
            RangeMarker methodMarker = document.createRangeMarker(
                    appliedComment.methodStartOffset,
                    appliedComment.methodEndOffset
            );
            methodMarker.setGreedyToLeft(true);
            methodMarker.setGreedyToRight(true);

            methodChangeMonitor.watchMethod(
                    editor,
                    methodMarker,
                    item.methodContext.getMethodSignature(),
                    item.methodContext.getMethodText(),
                    item.methodContext.getMethodName()
            );
            appliedCount++;
        }
        return appliedCount;
    }

    @Nullable
    private AppliedComment applyCommentToDocument(@NotNull Document document, @NotNull MethodContext context, @NotNull String generatedComment) {
        int textLength = document.getTextLength();
        int safeMethodOffset = Math.max(0, Math.min(context.getMethodStartOffset(), Math.max(0, textLength - 1)));
        int methodLine = textLength == 0 ? 0 : document.getLineNumber(safeMethodOffset);
        int methodLineStartOffset = document.getLineStartOffset(methodLine);
        int replaceStartOffset = methodLineStartOffset;
        int replaceEndOffset = methodLineStartOffset;

        if (context.hasDocCommentRange()) {
            int safeDocStart = Math.max(0, Math.min(context.getDocCommentStartOffset(), document.getTextLength()));
            int safeDocEnd = Math.max(safeDocStart, Math.min(context.getDocCommentEndOffset(), document.getTextLength()));
            if (safeDocEnd > safeDocStart) {
                int docAnchorOffset = Math.max(0, Math.min(safeDocStart, Math.max(0, textLength - 1)));
                int docStartLine = textLength == 0 ? 0 : document.getLineNumber(docAnchorOffset);
                replaceStartOffset = document.getLineStartOffset(docStartLine);
                replaceEndOffset = includeTrailingLineBreak(document, safeDocEnd);
            }
        }

        int insertOffset = replaceStartOffset;
        String previewText = toPreviewCommentText(generatedComment, document, methodLineStartOffset);
        if (previewText.isBlank()) {
            appendOutputLater("[错误] 生成注释为空，未执行插入。");
            return null;
        }

        int replacedLength = Math.max(0, replaceEndOffset - replaceStartOffset);
        if (replacedLength > 0) {
            document.replaceString(replaceStartOffset, replaceEndOffset, previewText);
        } else {
            document.insertString(insertOffset, previewText);
        }

        int commentEndOffset = insertOffset + previewText.length();
        int delta = previewText.length() - replacedLength;
        int methodStartAfterInsert = context.getMethodStartOffset() + delta;
        int methodEndAfterInsert = context.getMethodEndOffset() + delta;
        methodStartAfterInsert = Math.max(0, Math.min(methodStartAfterInsert, document.getTextLength()));
        methodEndAfterInsert = Math.max(methodStartAfterInsert, Math.min(methodEndAfterInsert, document.getTextLength()));

        return new AppliedComment(insertOffset, commentEndOffset, methodStartAfterInsert, methodEndAfterInsert);
    }

    @NotNull
    private String toPreviewCommentText(@NotNull String generatedComment, @NotNull Document document, int insertOffset) {
        String content = generatedComment.trim();
        if (content.isEmpty()) {
            return "";
        }

        String indent = detectLineIndent(document, insertOffset);
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);

        StringBuilder builder = new StringBuilder();
        boolean isComment = content.startsWith("//") || content.startsWith("/*");

        if (isComment) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    builder.append(indent);
                } else if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") || line.startsWith("*/")) {
                    builder.append(indent).append(line);
                } else {
                    builder.append(indent).append("// ").append(line);
                }
                if (i < lines.length - 1) {
                    builder.append("\n");
                }
            }
        } else {
            builder.append(indent).append("/**\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    builder.append(indent).append(" *\n");
                } else {
                    builder.append(indent).append(" * ").append(trimmed).append("\n");
                }
            }
            builder.append(indent).append(" */");
        }

        if (!builder.toString().endsWith("\n")) {
            builder.append("\n");
        }
        return builder.toString();
    }

    @NotNull
    private String detectLineIndent(@NotNull Document document, int offset) {
        int textLength = document.getTextLength();
        if (textLength == 0) {
            return "";
        }

        int safeOffset = Math.max(0, Math.min(offset, Math.max(0, textLength - 1)));
        int lineNumber = document.getLineNumber(safeOffset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String lineText = document.getText(new TextRange(lineStart, lineEnd));

        int index = 0;
        while (index < lineText.length()) {
            char ch = lineText.charAt(index);
            if (ch != ' ' && ch != '\t') {
                break;
            }
            index++;
        }
        return lineText.substring(0, index);
    }

    private int includeTrailingLineBreak(@NotNull Document document, int offset) {
        int textLength = document.getTextLength();
        int safeOffset = Math.max(0, Math.min(offset, textLength));
        if (safeOffset < textLength && document.getCharsSequence().charAt(safeOffset) == '\n') {
            return safeOffset + 1;
        }
        return safeOffset;
    }

    private void registerHoverRestore(@NotNull PendingComment pending) {
        MouseMotionListener motionListener = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                tryRestoreDecisionBalloonByHover(pending, event.getPoint());
            }
        };

        MouseListener mouseListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                tryRestoreDecisionBalloonByHover(pending, event.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                pending.isMouseInsidePreview = false;
                scheduleBalloonHideOnLeave(pending);
            }
        };

        pending.editor.getContentComponent().addMouseMotionListener(motionListener);
        pending.editor.getContentComponent().addMouseListener(mouseListener);
        pending.hoverMotionListener = motionListener;
        pending.hoverMouseListener = mouseListener;
    }

    private void tryRestoreDecisionBalloonByHover(@NotNull PendingComment pending, @NotNull Point hoverPoint) {
        if (!pendingComments.contains(pending)) {
            return;
        }
        if (!pending.commentRangeMarker.isValid()) {
            return;
        }

        int hoverOffset = pending.editor.logicalPositionToOffset(
                pending.editor.xyToLogicalPosition(hoverPoint)
        );
        int start = pending.commentRangeMarker.getStartOffset();
        int end = pending.commentRangeMarker.getEndOffset();
        if (hoverOffset < start || hoverOffset > end) {
            pending.isMouseInsidePreview = false;
            scheduleBalloonHideOnLeave(pending);
            return;
        }

        if (pending.isMouseInsidePreview) {
            return;
        }
        pending.isMouseInsidePreview = true;
        cancelBalloonHideTimer(pending);

        ApplicationManager.getApplication().invokeLater(() -> {
            if (!pendingComments.contains(pending) || !pending.commentRangeMarker.isValid()) {
                return;
            }
            if (pending.balloon != null && !pending.balloon.isDisposed()) {
                pending.balloon.hide();
            }
            pending.balloon = showDecisionBalloon(pending.editor, pending.commentRangeMarker, pending);
        });
    }

    private void scheduleBalloonHideOnLeave(@NotNull PendingComment pending) {
        cancelBalloonHideTimer(pending);
        Timer timer = new Timer(BALLOON_HIDE_DELAY_MS, e -> {
            if (!pendingComments.contains(pending)) {
                return;
            }
            if (pending.isMouseInsidePreview) {
                return;
            }
            if (pending.balloon != null && !pending.balloon.isDisposed()) {
                pending.balloon.hide();
            }
        });
        timer.setRepeats(false);
        timer.start();
        pending.hideBalloonTimer = timer;
    }

    private void cancelBalloonHideTimer(@NotNull PendingComment pending) {
        if (pending.hideBalloonTimer != null) {
            pending.hideBalloonTimer.stop();
            pending.hideBalloonTimer = null;
        }
    }

    @Nullable
    private RangeHighlighter addPreviewHighlighter(@NotNull Editor editor, @NotNull RangeMarker marker) {
        if (!marker.isValid()) {
            return null;
        }

        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(new JBColor(new Color(220, 255, 220), new Color(48, 80, 48)));

        MarkupModel markupModel = editor.getMarkupModel();
        return markupModel.addRangeHighlighter(
                marker.getStartOffset(),
                marker.getEndOffset(),
                HighlighterLayer.SELECTION - 1,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
        );
    }

    @Nullable
    private Balloon showDecisionBalloon(@NotNull Editor editor, @NotNull RangeMarker marker, @NotNull PendingComment pending) {
        if (!marker.isValid()) {
            return null;
        }

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setBorder(BorderFactory.createLineBorder(new JBColor(new Color(160, 200, 160), new Color(90, 120, 90))));

        JButton keepButton = new JButton("保留");
        JButton discardButton = new JButton("放弃");

        keepButton.addActionListener(e -> keepPendingComment(pending, true));
        discardButton.addActionListener(e -> discardPendingComment(pending));

        panel.add(keepButton);
        panel.add(discardButton);

        Balloon balloon = JBPopupFactory.getInstance()
                .createBalloonBuilder(panel)
                .setHideOnClickOutside(false)
                .setHideOnFrameResize(false)
                .setHideOnKeyOutside(false)
                .setCloseButtonEnabled(false)
                .setFadeoutTime(0)
                .createBalloon();

        LogicalPosition logicalPosition = editor.offsetToLogicalPosition(marker.getStartOffset());
        Point point = editor.logicalPositionToXY(logicalPosition);
        point.translate(14, -6);
        balloon.show(new RelativePoint(editor.getContentComponent(), point), Balloon.Position.atRight);
        return balloon;
    }

    private void keepAllPendingComments() {
        Editor selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        List<PendingComment> snapshot = new ArrayList<>();
        for (PendingComment pending : pendingComments) {
            if (selectedEditor != null && pending.editor == selectedEditor) {
                snapshot.add(pending);
            }
        }
        if (snapshot.isEmpty()) {
            appendOutputLater("[提示] 当前页面没有待保留的临时注释。");
            return;
        }

        int keptCount = 0;
        for (PendingComment pending : snapshot) {
            if (keepPendingComment(pending, false)) {
                keptCount++;
            }
        }
        appendOutputLater("已一键保留 " + keptCount + " 条临时注释。");
    }

    private boolean keepPendingComment(@NotNull PendingComment current, boolean notify) {
        if (!pendingComments.contains(current)) {
            return false;
        }
        if (!current.methodRangeMarker.isValid()) {
            clearPendingVisuals(current);
            pendingComments.remove(current);
            return false;
        }

        methodChangeMonitor.watchMethod(
                current.editor,
                current.methodRangeMarker,
                current.baselineSignature,
                current.baselineMethodText,
                current.methodName
        );

        clearPendingVisuals(current);
        pendingComments.remove(current);
        if (notify) {
            appendOutputLater("已保留注释，并开始监控方法变更。");
        }
        return true;
    }

    private void discardPendingComment(@NotNull PendingComment current) {
        if (!pendingComments.contains(current)) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(project, "删除临时注释", null, () -> {
            if (current.commentRangeMarker.isValid()) {
                Document document = current.editor.getDocument();
                int start = current.commentRangeMarker.getStartOffset();
                int end = current.commentRangeMarker.getEndOffset();
                if (start >= 0 && end >= start && end <= document.getTextLength()) {
                    document.deleteString(start, end);
                }
            }

            clearPendingVisuals(current);
            pendingComments.remove(current);
            appendOutputLater("已放弃并删除临时注释。");
        }));
    }

    private void clearPendingVisuals(@NotNull PendingComment pending) {
        cancelBalloonHideTimer(pending);
        if (pending.highlighter != null) {
            pending.editor.getMarkupModel().removeHighlighter(pending.highlighter);
        }
        if (pending.balloon != null && !pending.balloon.isDisposed()) {
            pending.balloon.hide();
        }
        if (pending.hoverMotionListener != null) {
            pending.editor.getContentComponent().removeMouseMotionListener(pending.hoverMotionListener);
            pending.hoverMotionListener = null;
        }
        if (pending.hoverMouseListener != null) {
            pending.editor.getContentComponent().removeMouseListener(pending.hoverMouseListener);
            pending.hoverMouseListener = null;
        }
    }

    private void appendOutput(@NotNull String line) {
        if (outputArea.getText().isEmpty()) {
            outputArea.setText(line);
        } else {
            outputArea.append("\n" + line);
        }
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void replaceOutputLater(@NotNull String text) {
        ApplicationManager.getApplication().invokeLater(() -> {
            outputArea.setText(text);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private void appendOutputLater(@NotNull String line) {
        ApplicationManager.getApplication().invokeLater(() -> appendOutput(line));
    }

    private enum CommentDetailLevel {
        CONCISE("简洁"),
        DETAILED("详细");

        private final String displayText;

        CommentDetailLevel(@NotNull String displayText) {
            this.displayText = displayText;
        }

        @NotNull
        public String getDisplayText() {
            return displayText;
        }
    }

    private static final class GeneratedCommentItem {
        private final MethodContext methodContext;
        private final String generatedComment;

        private GeneratedCommentItem(@NotNull MethodContext methodContext, @NotNull String generatedComment) {
            this.methodContext = methodContext;
            this.generatedComment = generatedComment;
        }
    }

    private static final class GeneratedAnnotationResult {
        @Nullable
        private final String generatedComment;
        @Nullable
        private final String llmRequestedProvider;
        @Nullable
        private final String llmActualProvider;
        private final boolean llmFallbackUsed;
        @Nullable
        private final String llmFallbackReason;

        private GeneratedAnnotationResult(
                @Nullable String generatedComment,
                @Nullable String llmRequestedProvider,
                @Nullable String llmActualProvider,
                boolean llmFallbackUsed,
                @Nullable String llmFallbackReason
        ) {
            this.generatedComment = generatedComment;
            this.llmRequestedProvider = llmRequestedProvider;
            this.llmActualProvider = llmActualProvider;
            this.llmFallbackUsed = llmFallbackUsed;
            this.llmFallbackReason = llmFallbackReason;
        }
    }

    private static final class AppliedComment {
        private final int commentStartOffset;
        private final int commentEndOffset;
        private final int methodStartOffset;
        private final int methodEndOffset;

        private AppliedComment(int commentStartOffset, int commentEndOffset, int methodStartOffset, int methodEndOffset) {
            this.commentStartOffset = commentStartOffset;
            this.commentEndOffset = commentEndOffset;
            this.methodStartOffset = methodStartOffset;
            this.methodEndOffset = methodEndOffset;
        }
    }

    private static final class PendingComment {
        private final Editor editor;
        private final RangeMarker commentRangeMarker;
        private final RangeMarker methodRangeMarker;
        @Nullable
        private final RangeHighlighter highlighter;
        @Nullable
        private Balloon balloon;
        @Nullable
        private MouseMotionListener hoverMotionListener;
        @Nullable
        private MouseListener hoverMouseListener;
        @Nullable
        private Timer hideBalloonTimer;
        private boolean isMouseInsidePreview;
        private final String baselineSignature;
        private final String baselineMethodText;
        private final String methodName;

        private PendingComment(
                @NotNull Editor editor,
                @NotNull RangeMarker commentRangeMarker,
                @NotNull RangeMarker methodRangeMarker,
                @Nullable RangeHighlighter highlighter,
                @Nullable Balloon balloon,
                @NotNull String baselineSignature,
                @NotNull String baselineMethodText,
                @NotNull String methodName
        ) {
            this.editor = editor;
            this.commentRangeMarker = commentRangeMarker;
            this.methodRangeMarker = methodRangeMarker;
            this.highlighter = highlighter;
            this.balloon = balloon;
            this.hideBalloonTimer = null;
            this.isMouseInsidePreview = false;
            this.baselineSignature = baselineSignature;
            this.baselineMethodText = baselineMethodText;
            this.methodName = methodName;
        }
    }
}
