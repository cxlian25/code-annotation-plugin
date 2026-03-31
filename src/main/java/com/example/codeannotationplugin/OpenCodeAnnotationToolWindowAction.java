package com.example.codeannotationplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class OpenCodeAnnotationToolWindowAction extends AnAction {

    private static final String TOOL_WINDOW_ID = "代码注释";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() == null) {
            return;
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(e.getProject()).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.activate(null, true);
        }
    }
}
