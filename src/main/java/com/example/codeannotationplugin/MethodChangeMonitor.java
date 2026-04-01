package com.example.codeannotationplugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MethodChangeMonitor {

    private static final String NOTIFICATION_GROUP = "Code Annotation";

    private final Project project;
    private final Alarm alarm;
    private final List<WatchedMethod> watchedMethods = new ArrayList<>();
    private final Map<Document, DocumentListener> listeners = new HashMap<>();

    public MethodChangeMonitor(@NotNull Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    }

    public void watchMethod(
            @NotNull Editor editor,
            @NotNull RangeMarker methodRangeMarker,
            @NotNull String baselineSignature,
            @NotNull String baselineMethodText,
            @NotNull String methodName
    ) {
        cleanupInvalidWatches();
        if (!methodRangeMarker.isValid()) {
            return;
        }

        Document document = editor.getDocument();
        removeDuplicateWatch(document, methodRangeMarker, methodName);

        WatchedMethod watched = new WatchedMethod(
                document,
                methodRangeMarker,
                baselineSignature.trim(),
                normalizeLogicBody(baselineMethodText),
                methodName
        );
        watchedMethods.add(watched);
        ensureDocumentListener(document);
    }

    private void removeDuplicateWatch(@NotNull Document document, @NotNull RangeMarker marker, @NotNull String methodName) {
        Iterator<WatchedMethod> iterator = watchedMethods.iterator();
        while (iterator.hasNext()) {
            WatchedMethod watched = iterator.next();
            if (watched.document != document) {
                continue;
            }
            if (!watched.methodName.equals(methodName)) {
                continue;
            }
            if (!watched.methodRangeMarker.isValid()) {
                iterator.remove();
                continue;
            }
            int oldStart = watched.methodRangeMarker.getStartOffset();
            int oldEnd = watched.methodRangeMarker.getEndOffset();
            int newStart = marker.getStartOffset();
            int newEnd = marker.getEndOffset();
            boolean overlaps = oldEnd > newStart && oldStart < newEnd;
            if (overlaps) {
                iterator.remove();
            }
        }
    }

    private void ensureDocumentListener(@NotNull Document document) {
        if (listeners.containsKey(document)) {
            return;
        }

        DocumentListener listener = new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (!hasWatchedMethodsForDocument(document)) {
                    return;
                }
                alarm.cancelAllRequests();
                alarm.addRequest(MethodChangeMonitor.this::evaluateWatchedMethods, 500);
            }
        };

        document.addDocumentListener(listener, project);
        listeners.put(document, listener);
    }

    private boolean hasWatchedMethodsForDocument(@NotNull Document document) {
        for (WatchedMethod watched : watchedMethods) {
            if (watched.document == document && watched.methodRangeMarker.isValid() && !watched.notified) {
                return true;
            }
        }
        return false;
    }

    private void evaluateWatchedMethods() {
        cleanupInvalidWatches();

        for (WatchedMethod watched : watchedMethods) {
            if (watched.notified || !watched.methodRangeMarker.isValid()) {
                continue;
            }

            String currentMethodText = readMethodText(watched);
            if (currentMethodText == null || currentMethodText.isBlank()) {
                continue;
            }

            String currentSignature = extractMethodSignature(currentMethodText);
            String currentLogic = normalizeLogicBody(currentMethodText);

            boolean signatureChanged = !currentSignature.trim().equals(watched.baselineSignature);
            boolean logicChanged = isMajorLogicChange(watched.baselineLogicNormalized, currentLogic);
            if (!signatureChanged && !logicChanged) {
                continue;
            }

            watched.notified = true;
            String reason = signatureChanged
                    ? "函数签名已变化"
                    : "函数逻辑发生重大变化";

            Notifications.Bus.notify(
                    new Notification(
                            NOTIFICATION_GROUP,
                            "注释可能已过期",
                            "检测到方法 " + watched.methodName + " 的" + reason + "，建议手动更新注释。",
                            NotificationType.WARNING
                    ),
                    project
            );
        }
    }

    @Nullable
    private String readMethodText(@NotNull WatchedMethod watched) {
        return ReadAction.compute(() -> {
            if (!watched.methodRangeMarker.isValid()) {
                return null;
            }
            int start = watched.methodRangeMarker.getStartOffset();
            int end = watched.methodRangeMarker.getEndOffset();
            if (start < 0 || end < start || end > watched.document.getTextLength()) {
                return null;
            }
            return watched.document.getText(new TextRange(start, end));
        });
    }

    @NotNull
    private String extractMethodSignature(@NotNull String methodText) {
        String normalized = methodText.replace("\r\n", "\n").replace("\r", "\n");
        int braceIndex = normalized.indexOf('{');
        if (braceIndex <= 0) {
            return normalized.trim();
        }
        return normalized.substring(0, braceIndex).trim();
    }

    @NotNull
    private String normalizeLogicBody(@NotNull String methodText) {
        String normalized = methodText.replace("\r\n", "\n").replace("\r", "\n");

        int firstBrace = normalized.indexOf('{');
        int lastBrace = normalized.lastIndexOf('}');
        String body;
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            body = normalized.substring(firstBrace + 1, lastBrace);
        } else {
            body = normalized;
        }

        String noBlockComments = body.replaceAll("(?s)/\\*.*?\\*/", " ");
        String noLineComments = noBlockComments.replaceAll("(?m)//.*$", " ");
        return noLineComments.replaceAll("\\s+", "").trim();
    }

    private boolean isMajorLogicChange(@NotNull String baseline, @NotNull String current) {
        if (baseline.equals(current)) {
            return false;
        }
        if (baseline.isEmpty() || current.isEmpty()) {
            return true;
        }

        int maxLen = Math.max(baseline.length(), current.length());
        int lenDiff = Math.abs(baseline.length() - current.length());
        double lenChangeRatio = (double) lenDiff / (double) maxLen;
        if (lenChangeRatio >= 0.30) {
            return true;
        }

        double similarity = tokenJaccardSimilarity(baseline, current);
        return similarity < 0.65;
    }

    private double tokenJaccardSimilarity(@NotNull String left, @NotNull String right) {
        Set<String> leftTokens = tokenize(left);
        Set<String> rightTokens = tokenize(right);

        if (leftTokens.isEmpty() && rightTokens.isEmpty()) {
            return 1.0;
        }

        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);

        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);

        return union.isEmpty() ? 1.0 : ((double) intersection.size() / (double) union.size());
    }

    @NotNull
    private Set<String> tokenize(@NotNull String text) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private void cleanupInvalidWatches() {
        watchedMethods.removeIf(w -> !w.methodRangeMarker.isValid());
    }

    private static final class WatchedMethod {
        private final Document document;
        private final RangeMarker methodRangeMarker;
        private final String baselineSignature;
        private final String baselineLogicNormalized;
        private final String methodName;
        private boolean notified;

        private WatchedMethod(
                @NotNull Document document,
                @NotNull RangeMarker methodRangeMarker,
                @NotNull String baselineSignature,
                @NotNull String baselineLogicNormalized,
                @NotNull String methodName
        ) {
            this.document = document;
            this.methodRangeMarker = methodRangeMarker;
            this.baselineSignature = baselineSignature;
            this.baselineLogicNormalized = baselineLogicNormalized;
            this.methodName = methodName;
            this.notified = false;
        }
    }
}