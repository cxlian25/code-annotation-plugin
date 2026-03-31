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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

public class CodeAnnotationToolWindowPanel {

    private static final String GENERATE_ENDPOINT_PATH = "/api/v1/annotation/generate";
    private static final String GENERATE_INTENT = "generate_comment";
    private static final String UPDATE_INTENT = "update_comment";

    private final Project project;
    private final BackendClient backendClient;
    private final String backendBaseUrl;

    private final JPanel rootPanel;
    private final JTextArea outputArea;

    @Nullable
    private PendingComment pendingComment;

    public CodeAnnotationToolWindowPanel(Project project) {
        this.project = project;
        this.backendClient = new BackendClient();
        this.backendBaseUrl = PluginConfig.getBackendBaseUrl();

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
        JButton updateButton = new JButton("更新注释");

        buttonPanel.add(generateButton);
        buttonPanel.add(updateButton);

        generateButton.addActionListener(e -> runGenerateFlow(GENERATE_INTENT));
        updateButton.addActionListener(e -> runGenerateFlow(UPDATE_INTENT));

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
        appendOutput("2) 点击“生成注释”或“更新注释”。");
    }

    public JPanel getContent() {
        return rootPanel;
    }

    private void runGenerateFlow(@NotNull String intent) {
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
                String requestJson = RequestPayloadBuilder.buildGenerateRequest(context, intent);
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
        if (pendingComment.rangeMarker.isValid()) {
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
        int insertOffset = Math.max(0, Math.min(context.getMethodStartOffset(), document.getTextLength()));
        String previewText = toPreviewCommentText(generatedComment);
        if (previewText.isBlank()) {
            appendOutputLater("[错误] 生成注释为空，未执行插入。");
            return;
        }

        document.insertString(insertOffset, previewText);
        int endOffset = insertOffset + previewText.length();
        RangeMarker marker = document.createRangeMarker(insertOffset, endOffset);
        marker.setGreedyToLeft(true);
        marker.setGreedyToRight(true);

        RangeHighlighter highlighter = addPreviewHighlighter(editor, marker);
        Balloon balloon = showDecisionBalloon(editor, marker);
        pendingComment = new PendingComment(editor, marker, highlighter, balloon);
    }

    @NotNull
    private String toPreviewCommentText(@NotNull String generatedComment) {
        String content = generatedComment.trim();
        if (content.isEmpty()) {
            return "";
        }

        boolean isAlreadyComment = content.startsWith("//") || content.startsWith("/*");
        String text;
        if (isAlreadyComment) {
            text = content;
        } else {
            String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
            String[] lines = normalized.split("\n", -1);
            StringBuilder builder = new StringBuilder();
            builder.append("/**\n");
            for (String line : lines) {
                if (line.isBlank()) {
                    builder.append(" *\n");
                } else {
                    builder.append(" * ").append(line).append("\n");
                }
            }
            builder.append(" */");
            text = builder.toString();
        }

        if (!text.endsWith("\n")) {
            text += "\n";
        }
        if (!text.endsWith("\n\n")) {
            text += "\n";
        }
        return text;
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

        clearPendingVisuals(current);
        pendingComment = null;
        appendOutputLater("已保留注释。");
    }

    private void discardPendingComment() {
        PendingComment current = pendingComment;
        if (current == null) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(project, "删除临时注释", null, () -> {
            if (current.rangeMarker.isValid()) {
                Document document = current.editor.getDocument();
                int start = current.rangeMarker.getStartOffset();
                int end = current.rangeMarker.getEndOffset();
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
        if (pending.highlighter != null) {
            pending.editor.getMarkupModel().removeHighlighter(pending.highlighter);
        }
        if (pending.balloon != null && !pending.balloon.isDisposed()) {
            pending.balloon.hide();
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

    private static final class PendingComment {
        private final Editor editor;
        private final RangeMarker rangeMarker;
        @Nullable
        private final RangeHighlighter highlighter;
        @Nullable
        private final Balloon balloon;

        private PendingComment(
                @NotNull Editor editor,
                @NotNull RangeMarker rangeMarker,
                @Nullable RangeHighlighter highlighter,
                @Nullable Balloon balloon
        ) {
            this.editor = editor;
            this.rangeMarker = rangeMarker;
            this.highlighter = highlighter;
            this.balloon = balloon;
        }
    }
}