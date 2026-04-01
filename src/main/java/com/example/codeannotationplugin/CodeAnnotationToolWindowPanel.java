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
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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

    @Nullable
    private PendingComment pendingComment;
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
        JLabel backendLabel = new JLabel("后端地址：" + backendBaseUrl);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        headerPanel.add(backendLabel);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        JButton generateButton = new JButton("生成注释");
        JButton detailLevelButton = new JButton(buildDetailLevelButtonText());

        buttonPanel.add(generateButton);
        buttonPanel.add(detailLevelButton);

        generateButton.addActionListener(e -> runGenerateFlow());
        detailLevelButton.addActionListener(e -> toggleCommentDetailLevel(detailLevelButton));

        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.add(headerPanel);
        topContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        topContainer.add(buttonPanel);

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
        appendOutput("2) 点击“代码风格”切换简洁/详细，再点击“生成注释”。");
    }

    public JPanel getContent() {
        return rootPanel;
    }

    @NotNull
    private String buildDetailLevelButtonText() {
        return "代码风格：" + selectedDetailLevel.getDisplayText();
    }

    private void toggleCommentDetailLevel(@NotNull JButton detailLevelButton) {
        selectedDetailLevel = selectedDetailLevel == CommentDetailLevel.CONCISE
                ? CommentDetailLevel.DETAILED
                : CommentDetailLevel.CONCISE;
        detailLevelButton.setText(buildDetailLevelButtonText());
        appendOutput("已切换代码风格：" + selectedDetailLevel.getDisplayText());
    }

    private void runGenerateFlow() {
        if (hasActivePendingComment()) {
            appendOutputLater("[提示] 当前有一条临时注释，请先点击“保留”或“放弃”。");
            return;
        }

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

    private boolean hasActivePendingComment() {
        if (pendingComment == null) {
            return false;
        }
        if (pendingComment.commentRangeMarker.isValid()) {
            return true;
        }
        clearPendingVisuals(pendingComment);
        pendingComment = null;
        return false;
    }

    private void handleGeneratedCommentResponse(@NotNull BackendClient.HttpResult response, @NotNull MethodContext context) {
        String generatedComment = JsonUtil.extractTopLevelStringField(response.getBody(), "generatedComment");
        if (generatedComment == null || generatedComment.isBlank()) {
            appendOutputLater("[错误] 响应中未找到 generatedComment 字段。");
            return;
        }

        replaceOutputLater(generatedComment);
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
            return;
        }

        int replacedLength = Math.max(0, replaceEndOffset - replaceStartOffset);
        if (replacedLength > 0) {
            document.replaceString(replaceStartOffset, replaceEndOffset, previewText);
        } else {
            document.insertString(insertOffset, previewText);
        }

        int commentEndOffset = insertOffset + previewText.length();
        RangeMarker commentMarker = document.createRangeMarker(insertOffset, commentEndOffset);
        commentMarker.setGreedyToLeft(true);
        commentMarker.setGreedyToRight(true);

        int delta = previewText.length() - replacedLength;
        int methodStartAfterInsert = context.getMethodStartOffset() + delta;
        int methodEndAfterInsert = context.getMethodEndOffset() + delta;
        methodStartAfterInsert = Math.max(0, Math.min(methodStartAfterInsert, document.getTextLength()));
        methodEndAfterInsert = Math.max(methodStartAfterInsert, Math.min(methodEndAfterInsert, document.getTextLength()));

        RangeMarker methodMarker = document.createRangeMarker(methodStartAfterInsert, methodEndAfterInsert);
        methodMarker.setGreedyToLeft(true);
        methodMarker.setGreedyToRight(true);

        RangeHighlighter highlighter = addPreviewHighlighter(editor, commentMarker);
        Balloon balloon = showDecisionBalloon(editor, commentMarker);

        PendingComment newPendingComment = new PendingComment(
                editor,
                commentMarker,
                methodMarker,
                highlighter,
                balloon,
                context.getMethodSignature(),
                context.getMethodText(),
                context.getMethodName()
        );
        registerHoverRestore(newPendingComment);
        pendingComment = newPendingComment;
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
        if (pendingComment != pending) {
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
            if (pendingComment != pending || !pending.commentRangeMarker.isValid()) {
                return;
            }
            if (pending.balloon != null && !pending.balloon.isDisposed()) {
                pending.balloon.hide();
            }
            pending.balloon = showDecisionBalloon(pending.editor, pending.commentRangeMarker);
        });
    }

    private void scheduleBalloonHideOnLeave(@NotNull PendingComment pending) {
        cancelBalloonHideTimer(pending);
        Timer timer = new Timer(BALLOON_HIDE_DELAY_MS, e -> {
            if (pendingComment != pending) {
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
    private Balloon showDecisionBalloon(@NotNull Editor editor, @NotNull RangeMarker marker) {
        if (!marker.isValid()) {
            return null;
        }

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setBorder(BorderFactory.createLineBorder(new JBColor(new Color(160, 200, 160), new Color(90, 120, 90))));

        JButton keepButton = new JButton("保留");
        JButton discardButton = new JButton("放弃");

        keepButton.addActionListener(e -> keepPendingComment());
        discardButton.addActionListener(e -> discardPendingComment());

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

    private void keepPendingComment() {
        PendingComment current = pendingComment;
        if (current == null) {
            return;
        }

        methodChangeMonitor.watchMethod(
                current.editor,
                current.methodRangeMarker,
                current.baselineSignature,
                current.baselineMethodText,
                current.methodName
        );

        clearPendingVisuals(current);
        pendingComment = null;
        appendOutputLater("已保留注释，并开始监控方法变更。");
    }

    private void discardPendingComment() {
        PendingComment current = pendingComment;
        if (current == null) {
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
            pendingComment = null;
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
