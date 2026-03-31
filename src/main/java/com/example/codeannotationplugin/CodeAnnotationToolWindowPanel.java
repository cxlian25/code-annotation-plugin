package com.example.codeannotationplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
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

    public CodeAnnotationToolWindowPanel(Project project) {
        this.project = project;
        this.backendClient = new BackendClient();
        this.backendBaseUrl = PluginConfig.getBackendBaseUrl();

        this.rootPanel = new JPanel(new BorderLayout());
        this.rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("Code Annotation Assistant");
        JLabel subtitleLabel = new JLabel("Project: " + project.getName());
        JLabel backendLabel = new JLabel("Backend: " + backendBaseUrl);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        headerPanel.add(backendLabel);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        JButton generateButton = new JButton("Generate Annotation");
        JButton updateButton = new JButton("Update Annotation");

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
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Output"));

        rootPanel.add(topContainer, BorderLayout.NORTH);
        rootPanel.add(outputScrollPane, BorderLayout.CENTER);

        appendOutput("Panel initialized.");
        appendOutput("1) Place the caret inside a Java method or select code.");
        appendOutput("2) Click Generate Annotation or Update Annotation.");
    }

    public JPanel getContent() {
        return rootPanel;
    }

    private void runGenerateFlow(@NotNull String intent) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Code Annotation Request", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Extracting code context...");

                MethodContextExtractor.ExtractionResult extractionResult =
                        ReadAction.compute(() -> MethodContextExtractor.extract(project));

                if (!extractionResult.isSuccess() || extractionResult.getMethodContext() == null) {
                    String errorMessage = extractionResult.getErrorMessage() == null
                            ? "Unknown context extraction error."
                            : extractionResult.getErrorMessage();
                    appendOutputLater("[Error] " + errorMessage);
                    return;
                }

                MethodContext context = extractionResult.getMethodContext();
                appendOutputLater("[Context] " + summarizeContext(context));

                String requestJson = RequestPayloadBuilder.buildGenerateRequest(context, intent);
//                System.out.println(requestJson);
                String endpoint = backendBaseUrl + GENERATE_ENDPOINT_PATH;

                appendOutputLater("[HTTP] POST " + endpoint);
                appendOutputLater("[HTTP] Request body size: " + requestJson.length() + " chars");

                try {
                    indicator.setText("Sending request to backend...");
                    BackendClient.HttpResult response = backendClient.postJson(endpoint, requestJson);

                    appendOutputLater("[HTTP] Status: " + response.getStatusCode());
                    appendOutputLater("[HTTP] Response:\n" + shorten(response.getBody(), 4000));
                } catch (HttpTimeoutException timeoutException) {
                    appendOutputLater("[Error] Request timeout: " + timeoutException.getMessage());
                } catch (ConnectException connectException) {
                    appendOutputLater("[Error] Cannot connect to backend. Please start the API service first.");
                } catch (IOException ioException) {
                    appendOutputLater("[Error] IO exception: " + ioException.getMessage());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    appendOutputLater("[Error] Request interrupted.");
                } catch (Exception exception) {
                    appendOutputLater("[Error] Unexpected exception: " + exception.getMessage());
                }
            }
        });
    }

    @NotNull
    private String summarizeContext(@NotNull MethodContext context) {
        return "method=" + context.getMethodName() +
                ", class=" + context.getClassName() +
                ", lines=" + context.getSelectionStartLine() + "-" + context.getSelectionEndLine() +
                ", hasSelection=" + context.hasSelection();
    }

    private void appendOutput(@NotNull String line) {
        if (outputArea.getText().isEmpty()) {
            outputArea.setText(line);
        } else {
            outputArea.append("\n" + line);
        }
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void appendOutputLater(@NotNull String line) {
        ApplicationManager.getApplication().invokeLater(() -> appendOutput(line));
    }

    @NotNull
    private String shorten(@NotNull String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n...(truncated)";
    }
}