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
package org.jackhuang.hmcl.ui.pcl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/// PCL2 `ModLoader.vb` keeps every download in `LoaderTaskbar`, a shared list
/// read by three consumers: the bottom-right `BtnExtraDownload` progress ball,
/// `PageSpeedLeft` (aggregate statistics) and `PageSpeedRight` (per-task cards).
/// HPCL has no such list, so HMCL's task executors used to render into an
/// isolated modal pane. This class is the equivalent registry: it observes a
/// [TaskExecutor] through a [TaskListener] (exactly like HMCL's TaskListPane),
/// and exposes JavaFX observable state consumed by every PCL download view.
///
/// PCL2 timing reproduced here: a 300 ms watcher (`PageSpeedLeft.WatcherInterval`)
/// recomputes the aggregate, and `LoaderTaskbarProgressRefresh` smooths the
/// displayed progress with `p = p * 0.9 + raw * 0.1` (snapping when the raw
/// value is 0/1/regressing).
///
/// Progress correctness (HPCL): PCL2 derives a loader's progress from real
/// byte/file counters, never from Java task names. HMCL's executor emits a lot
/// of internal MINOR tasks (`CountTask`, `getCaller()`-named glue) whose
/// progress jumps 0 -> 1 immediately; averaging those made the UI show 100 %
/// while files were still downloading. We therefore
///   1. keep internal tasks out of the visible rows and aggregate them into a
///      single friendly "下载文件" row whose progress is finished/total;
///   2. clamp every active loader to 99 % — 100 % is only reachable from
///      `onStop(success = true)`, mirroring PCL2's `LoadState.Finished`;
///   3. treat an empty task list as 0 %, not 100 %.
public final class PCLDownloadCenter {

    private static final PCLDownloadCenter INSTANCE = new PCLDownloadCenter();

    public static PCLDownloadCenter getInstance() {
        return INSTANCE;
    }

    /// Mirrors PCL2 `LoadState` as far as the download UI needs it.
    public enum Status { WAITING, RUNNING, FINISHED, FAILED, CANCELLED }

    /// One row of a PCL2 task card (`PageSpeedLeft.TaskRefresh`): a 50px status
    /// glyph plus the sub-task name. Waiting = "···", running = "45%",
    /// finished = a check mark, failed = a cross.
    public static final class SubTask {
        private final StringProperty name = new SimpleStringProperty("");
        private final DoubleProperty progress = new SimpleDoubleProperty(0);
        private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.WAITING);
        private Task<?> source;

        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }
        public double getProgress() { return progress.get(); }
        public DoubleProperty progressProperty() { return progress; }
        public Status getStatus() { return status.get(); }
        public ObjectProperty<Status> statusProperty() { return status; }
        public ReadOnlyObjectProperty<Status> readOnlyStatusProperty() { return status; }

        void bind(Task<?> task) {
            this.source = task;
            name.set(task.getName() == null ? "" : task.getName());
            progress.bind(task.progressProperty());
            status.set(Status.RUNNING);
        }

        void mark(Status value) {
            progress.unbind();
            if (value == Status.FINISHED) progress.set(1);
            status.set(value);
        }

        /// Aggregate row: name/progress are driven by the owning DownloadTask,
        /// not by a single Task's progress property.
        void setAggregated(String displayName, double value, Status valueStatus) {
            progress.set(Math.max(0, Math.min(1, value)));
            name.set(displayName);
            status.set(valueStatus);
        }

        Task<?> getSource() { return source; }
    }

    /// A single `LoaderTaskbar` entry, rendered as one `MyCard` on
    /// `PageSpeedRight`. Successful/cancelled entries are removed after a short
    /// grace period; failed ones stay until the user closes the card.
    public static final class DownloadTask {
        private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper("");
        private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(0);
        private final ReadOnlyObjectWrapper<Status> status =
                new ReadOnlyObjectWrapper<>(Status.WAITING);
        private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");
        private final ObservableList<SubTask> subtasks = FXCollections.observableArrayList();
        private final Map<Task<?>, SubTask> nodes = new IdentityHashMap<>();
        private final Runnable cancelAction;

        /// Internal MINOR/glue tasks are not shown as rows; they are counted
        /// and presented through one aggregated "下载文件" row instead.
        private int internalTotal;
        private int internalFinished;
        private SubTask aggregateRow;

        DownloadTask(String title, Runnable cancelAction) {
            this.title.set(title);
            this.cancelAction = cancelAction;
        }

        public String getTitle() { return title.get(); }
        public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
        public double getProgress() { return progress.get(); }
        public ReadOnlyDoubleProperty progressProperty() { return progress.getReadOnlyProperty(); }
        public Status getStatus() { return status.get(); }
        public ReadOnlyObjectProperty<Status> statusProperty() { return status.getReadOnlyProperty(); }
        public String getError() { return error.get(); }
        public ReadOnlyStringProperty errorProperty() { return error.getReadOnlyProperty(); }
        public ObservableList<SubTask> getSubtasks() { return subtasks; }

        public boolean isActive() {
            Status s = status.get();
            return s == Status.WAITING || s == Status.RUNNING;
        }

        public int getInternalRemain() {
            return Math.max(0, internalTotal - internalFinished);
        }

        public void setTitle(String value) { title.set(value == null ? "" : value); }
        public void cancel() {
            if (cancelAction != null) cancelAction.run();
        }

        void setStatus(Status value) { status.set(value); }
        void setError(String value) { error.set(value == null ? "" : value); }

        /// PCL2 `LoaderTaskbarProgressGet`: average of the visible rows, but an
        /// active loader can never legitimately read 100 % — only `onStop(true)`
        /// (LoadState.Finished) may do that. Clamp to 0.99 until then.
        void recompute() {
            if (status.get() == Status.FINISHED) {
                progress.set(1);
                return;
            }
            if (subtasks.isEmpty()) {
                progress.set(0);
                return;
            }
            double sum = 0;
            for (SubTask t : subtasks) {
                double p = t.getProgress();
                if (Double.isNaN(p) || p < 0) p = 0;
                sum += Math.min(1, p);
            }
            double raw = sum / subtasks.size();
            progress.set(Math.min(0.99, raw));
        }

        SubTask nodeFor(Task<?> task) { return nodes.get(task); }

        void addNode(Task<?> task) {
            if (nodes.containsKey(task)) return;
            String display = PCLDownloadCenter.displayName(task);
            if (display == null) {
                // Internal MINOR/glue task: count it, refresh the aggregate row.
                internalTotal++;
                nodes.put(task, aggregateRow());
                refreshAggregate();
                recompute();
                return;
            }
            SubTask node = new SubTask();
            node.bind(task);
            nodes.put(task, node);
            subtasks.add(node);
            // PCL2 keeps every row visible; cap HPCL's card so a 300-file game
            // install does not grow an unbounded VBox.
            while (subtasks.size() > MAX_VISIBLE_ROWS) {
                SubTask removed = subtasks.remove(0);
                if (removed == aggregateRow) aggregateRow = null;
            }
            recompute();
        }

        private SubTask aggregateRow() {
            if (aggregateRow == null) {
                aggregateRow = new SubTask();
                subtasks.add(0, aggregateRow);   // PCL2 shows the file row on top
            }
            return aggregateRow;
        }

        private void refreshAggregate() {
            if (aggregateRow == null) return;
            double value = internalTotal == 0 ? 0 : (double) internalFinished / internalTotal;
            boolean done = internalTotal > 0 && internalFinished >= internalTotal;
            // Never let the aggregate row read 100 % while the loader is active.
            if (done && status.get() != Status.FINISHED) value = Math.min(value, 0.99);
            aggregateRow.setAggregated("下载文件 (" + internalFinished + "/" + internalTotal + ")",
                    value, done && status.get() == Status.FINISHED ? Status.FINISHED : Status.RUNNING);
        }

        void finishNode(Task<?> task, Status value) {
            SubTask node = nodes.get(task);
            if (node == aggregateRow && aggregateRow != null) {
                if (value == Status.FINISHED || value == Status.FAILED || value == Status.CANCELLED)
                    internalFinished++;
                refreshAggregate();
                recompute();
                return;
            }
            if (node != null) node.mark(value);
            recompute();
        }

        /// Called once `onStop(success = true)` fires: only now is 100 % real.
        void markCompleted() {
            internalFinished = internalTotal;
            refreshAggregate();
            for (SubTask s : subtasks) {
                if (s.getStatus() == Status.RUNNING || s.getStatus() == Status.WAITING)
                    s.mark(Status.FINISHED);
            }
            progress.set(1);
        }

        private static final int MAX_VISIBLE_ROWS = 40;
    }

    private final ObservableList<DownloadTask> tasks = FXCollections.observableArrayList();
    private final ReadOnlyDoubleWrapper smoothedProgress = new ReadOnlyDoubleWrapper(0);
    private final ReadOnlyLongWrapper speed = new ReadOnlyLongWrapper(0);
    private final ReadOnlyIntegerWrapper fileRemain = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper threadCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyBooleanWrapper active = new ReadOnlyBooleanWrapper(false);
    private final Timeline watcher;
    @SuppressWarnings("FieldCanBeLocal")
    private final Consumer<FetchTask.SpeedEvent> speedHandler;

    private PCLDownloadCenter() {
        // PCL2 `NetManager.Speed` is a global throughput counter updated once
        // per second by FetchTask's timer. The ball and PageSpeedLeft both read
        // that single value.
        speedHandler = FetchTask.SPEED_EVENT.registerWeak(event ->
                Platform.runLater(() -> speed.set(Math.max(0, event.getSpeed()))));

        // PageSpeedLeft.Watcher: DispatcherTimer with a 300 ms interval.
        watcher = new Timeline(new KeyFrame(Duration.millis(300), e -> refresh()));
        watcher.setCycleCount(Timeline.INDEFINITE);
        watcher.play();
    }

    public ObservableList<DownloadTask> getTasks() { return tasks; }

    public double getSmoothedProgress() { return smoothedProgress.get(); }
    public ReadOnlyDoubleProperty smoothedProgressProperty() { return smoothedProgress.getReadOnlyProperty(); }
    public long getSpeed() { return speed.get(); }
    public ReadOnlyLongProperty speedProperty() { return speed.getReadOnlyProperty(); }
    public int getFileRemain() { return fileRemain.get(); }
    public ReadOnlyIntegerProperty fileRemainProperty() { return fileRemain.getReadOnlyProperty(); }
    public int getThreadCount() { return threadCount.get(); }
    public ReadOnlyIntegerProperty threadCountProperty() { return threadCount.getReadOnlyProperty(); }
    public boolean isActive() { return active.get(); }
    public ReadOnlyBooleanProperty activeProperty() { return active.getReadOnlyProperty(); }

    /// PCL2 `NetTaskThreadLimit` is the download executor's concurrency.
    public int getThreadLimit() {
        return Math.max(1, FetchTask.getDownloadExecutorConcurrency());
    }

    /// Turns HMCL's task names into PCL2-style readable rows.
    /// Returns null for internal plumbing tasks (`Task$CountTask`,
    /// `StackWalker`-generated `Class.method(File.java:NN)` glue), which are
    /// aggregated into the "下载文件" row instead of being printed verbatim.
    static String displayName(Task<?> task) {
        String name = task.getName();
        if (name == null || name.isEmpty()) return null;
        // getName() falls back to getClass().getName() when no name was set.
        if (name.equals(task.getClass().getName())) return null;
        if (name.startsWith("org.jackhuang.")) return null;
        // StackWalker caller strings look like "com.foo.Bar.baz(Bar.java:42)".
        if (name.contains(".java:") || (name.indexOf('(') > 0 && name.indexOf('.') > 0)) return null;
        if (name.contains("$") || name.endsWith("Task")) return null;
        return name;
    }

    /// Registers a task executor. Called on the FX thread from
    /// `TaskExecutorDialogPane#setExecutor`; the listener callbacks arrive on
    /// worker threads, so every mutation is marshalled with Platform.runLater.
    public DownloadTask register(String title, TaskExecutor executor, Runnable cancelAction) {
        final DownloadTask task = new DownloadTask(title == null ? "" : title, cancelAction);
        if (Platform.isFxApplicationThread()) {
            tasks.add(task);
            smoothedProgress.set(0);
        } else Platform.runLater(() -> {
            tasks.add(task);
            smoothedProgress.set(0);
        });

        if (executor != null) {
            executor.addTaskListener(new TaskListener() {
                @Override
                public void onRunning(Task<?> t) {
                    Platform.runLater(() -> task.addNode(t));
                }

                @Override
                public void onFinished(Task<?> t) {
                    Platform.runLater(() -> task.finishNode(t, Status.FINISHED));
                }

                @Override
                public void onFailed(Task<?> t, Throwable throwable) {
                    Platform.runLater(() -> {
                        task.finishNode(t, Status.FAILED);
                        if (throwable != null) task.setError(throwable.getLocalizedMessage());
                    });
                }

                @Override
                public void onStop(boolean success, TaskExecutor exec) {
                    Platform.runLater(() -> {
                        Status s = success ? Status.FINISHED
                                : (exec.isCancelled() ? Status.CANCELLED : Status.FAILED);
                        task.setStatus(s);
                        if (!success && task.getError().isEmpty() && exec.getException() != null)
                            task.setError(exec.getException().getLocalizedMessage());
                        // 100 % is only legitimate once the executor truly stopped.
                        if (success) task.markCompleted();
                        else task.recompute();
                        refresh();
                        if (s != Status.FAILED) scheduleRemoval(task);
                    });
                }
            });
        }
        if (Platform.isFxApplicationThread()) refresh();
        else Platform.runLater(this::refresh);
        return task;
    }

    /// PCL2 destroys a card with `AniDispose` when its loader finishes. Keep
    /// the finished state readable for a moment, then drop it.
    private void scheduleRemoval(DownloadTask task) {
        Timeline hold = new Timeline(new KeyFrame(Duration.millis(2200), e -> remove(task)));
        hold.play();
    }

    public void remove(DownloadTask task) {
        if (task == null) return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> remove(task));
            return;
        }
        tasks.remove(task);
        refresh();
    }

    /// `LoaderTaskbarProgressRefresh` + `PageSpeedLeft.Watcher`.
    private void refresh() {
        double raw = rawProgress();
        double shown = smoothedProgress.get();
        // Never snap to 100 % while any loader is still active.
        boolean anyActive = tasks.stream().anyMatch(DownloadTask::isActive);
        if (raw >= 1 && anyActive) raw = 0.99;
        if (raw <= 0 || shown > raw) smoothedProgress.set(raw);
        else if (raw >= 1 && !anyActive) smoothedProgress.set(raw);
        else smoothedProgress.set(shown * 0.9 + raw * 0.1);

        int remaining = 0;
        int running = 0;
        boolean activeNow = false;
        for (DownloadTask t : tasks) {
            if (t.isActive()) activeNow = true;
            remaining += t.getInternalRemain();
            for (SubTask s : t.getSubtasks()) {
                if (s.getStatus() == Status.RUNNING || s.getStatus() == Status.WAITING) remaining++;
                if (s.getStatus() == Status.RUNNING) running++;
            }
        }
        fileRemain.set(remaining);
        threadCount.set(running);
        active.set(activeNow);
    }

    /// PCL2 `LoaderTaskbarProgressGet`: average progress of every loader,
    /// clamped to [0, 1]. An empty list reads as 0 % (there is nothing done),
    /// and active loaders are clamped to 99 % by their own recompute().
    private double rawProgress() {
        if (tasks.isEmpty()) return 0;
        double sum = 0;
        for (DownloadTask t : tasks) sum += Math.max(0, Math.min(1, t.getProgress()));
        return sum / tasks.size();
    }

    /// Used only by the opt-in screenshot hook: fabricates a task with a few
    /// sub-tasks so the real page can be captured without a network download.
    public DownloadTask createDemoTask(String title, String... subtaskNames) {
        final DownloadTask[] holder = new DownloadTask[1];
        DownloadTask task = new DownloadTask(title, () -> remove(holder[0]));
        holder[0] = task;
        tasks.add(task);
        for (String name : subtaskNames) {
            SubTask s = new SubTask();
            s.nameProperty().set(name);
            s.statusProperty().set(Status.WAITING);
            task.getSubtasks().add(s);
        }
        task.setStatus(Status.RUNNING);
        refresh();
        return task;
    }

    /// Advances a demo task, again only for the screenshot hook.
    public void tickDemo(DownloadTask task, double delta) {
        if (task == null || !tasks.contains(task)) return;
        for (SubTask s : task.getSubtasks()) {
            if (s.getStatus() == Status.RUNNING) {
                double p = Math.min(1, s.getProgress() + delta);
                s.progressProperty().set(p);
                if (p >= 1) s.statusProperty().set(Status.FINISHED);
                break;
            } else if (s.getStatus() == Status.WAITING) {
                s.statusProperty().set(Status.RUNNING);
                s.progressProperty().set(delta);
                break;
            }
        }
        task.recompute();
        refresh();
    }
}
