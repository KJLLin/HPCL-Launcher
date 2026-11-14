/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.pcl.PCLDownloadCenter;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// PCL does not cover the launcher with a modal task-progress dialog. This
/// pane is docked to a non-modal bottom-right task layer instead, so it keeps
/// the failure state (to retain the per-task error details) and exposes an
/// explicit close button.
public class TaskExecutorDialogPane extends BorderPane {
    private TaskExecutor executor;
    private TaskCancellationAction onCancel;
    private boolean failed;
    private boolean cancelled;
    private boolean userClosed;
    /// PCL renders launch progress inside the left-pane PanLaunching, so the
    /// HMCL launcher-steps pane is swallowed instead of being docked.
    private boolean suppressFromTaskLayer;
    /// The PCL download-registry entry mirroring this executor. Launch steps
    /// are suppressed because PanLaunching renders them in the left pane.
    private PCLDownloadCenter.DownloadTask registeredTask;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final Consumer<FetchTask.SpeedEvent> speedEventHandler;

    private final VBox centerBox;
    private final Label lblTitle;
    private final Label lblProgress;
    private final JFXButton btnCancel;
    private final JFXButton btnClose;
    private final TaskListPane taskListPane;

    public TaskExecutorDialogPane(@NotNull TaskCancellationAction cancel) {
        this.getStyleClass().addAll("task-executor-dialog-layout", "hpcl-task-panel");

        FXUtils.setLimitWidth(this, 400);
        FXUtils.setLimitHeight(this, 300);

        centerBox = new VBox();
        this.setCenter(centerBox);
        centerBox.setPadding(new Insets(16));
        {
            lblTitle = new Label();
            lblTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: BOLD;");
            // PCL2's task card title follows the loader name; keep the registry
            // entry in sync when a caller renames the pane after construction.
            lblTitle.textProperty().addListener((o, ov, nv) -> {
                if (registeredTask != null) registeredTask.setTitle(nv);
            });

            taskListPane = new TaskListPane();
            VBox.setVgrow(taskListPane, Priority.ALWAYS);

            centerBox.getChildren().setAll(lblTitle, taskListPane);
        }

        BorderPane bottom = new BorderPane();
        this.setBottom(bottom);
        bottom.setPadding(new Insets(0, 8, 8, 8));
        {
            lblProgress = new Label();
            bottom.setLeft(lblProgress);

            btnCancel = new JFXButton(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");

            btnClose = new JFXButton();
            btnClose.getStyleClass().add("hpcl-task-close");
            btnClose.setFocusTraversable(false);
            Node closeIcon = SVG.CLOSE.createIcon(12);
            closeIcon.getStyleClass().add("hpcl-task-close-icon");
            btnClose.setGraphic(closeIcon);
            btnClose.setOnAction(e -> requestClose());

            HBox buttons = new HBox(6, btnCancel, btnClose);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            bottom.setRight(buttons);
        }

        setCancel(cancel);

        btnCancel.setDisable(onCancel.getCancellationAction() == null);
        btnCancel.setOnAction(e -> {
            if (executor != null)
                executor.cancel();
            onCancel.getCancellationAction().accept(this);
        });

        speedEventHandler = FetchTask.SPEED_EVENT.registerWeak(speedEvent -> {
            String message = I18n.formatSpeed(speedEvent.getSpeed());
            Platform.runLater(() -> lblProgress.setText(message));
        });

        onEscPressed(this, this::requestClose);
    }

    public void setExecutor(TaskExecutor executor) {
        setExecutor(executor, true);
    }

    public void setExecutor(TaskExecutor executor, boolean autoClose) {
        // A pane is reused across launches, so drop the previous registry
        // entry before observing a new executor.
        unregister();
        this.executor = executor;
        this.failed = false;
        this.cancelled = false;
        this.userClosed = false;
        getStyleClass().remove("hpcl-task-panel-failed");

        if (executor != null) {
            // In PCL2 launch mode the progress list is rendered by
            // PCLLaunchProgressPage; skip HMCL's TaskListPane entirely so no
            // HMCL-style progress list is built at all.
            if (!suppressFromTaskLayer) taskListPane.setExecutor(executor);

            if (!suppressFromTaskLayer) {
                registeredTask = PCLDownloadCenter.getInstance().register(
                        getTitle(), executor, () -> executor.cancel());
            }

            if (autoClose)
                executor.addTaskListener(new TaskListener() {
                    @Override
                    public void onStop(boolean success, TaskExecutor executor) {
                        Platform.runLater(() -> {
                            failed = !success;
                            cancelled = !success && executor.getException()
                                    instanceof java.util.concurrent.CancellationException;
                            fireEvent(new DialogCloseEvent());
                        });
                    }
                });
        }
    }

    public StringProperty titleProperty() {
        return lblTitle.textProperty();
    }

    public String getTitle() {
        return lblTitle.getText();
    }

    public void setTitle(String currentState) {
        lblTitle.setText(currentState);
    }

    public void setCancel(TaskCancellationAction onCancel) {
        this.onCancel = onCancel;

        runInFX(() -> btnCancel.setDisable(onCancel == null));
    }

    /// True when the task ended with an error; the task layer keeps the card
    /// on screen so the failed steps remain readable.
    public boolean isFailed() {
        return failed;
    }

    /// True when the user cancelled the task rather than it failing.
    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isUserClosed() {
        return userClosed;
    }

    /// Marks a pane that must not be docked as a task card (launch progress).
    public void setSuppressFromTaskLayer(boolean suppress) {
        this.suppressFromTaskLayer = suppress;
        // Launch steps belong to PanLaunching, not the download queue.
        if (suppress) unregister();
    }

    private void unregister() {
        if (registeredTask != null) {
            PCLDownloadCenter.getInstance().remove(registeredTask);
            registeredTask = null;
        }
    }

    public boolean isSuppressFromTaskLayer() {
        return suppressFromTaskLayer;
    }

    /// Closes the card from the UI (the × button or ESC).
    public void requestClose() {
        userClosed = true;
        unregister();
        fireEvent(new DialogCloseEvent());
    }

    /// Extra status line under the heading, used by the headless verification
    /// hook and harmless in production.
    public void setHint(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("hpcl-task-hint");
        hint.setWrapText(true);
        centerBox.getChildren().removeIf(n -> n.getStyleClass().contains("hpcl-task-hint"));
        centerBox.getChildren().add(1, hint);
    }

    /// Sets the speed / progress caption on the bottom-left of the card.
    public void setProgressText(String text) {
        lblProgress.setText(text);
    }
}
