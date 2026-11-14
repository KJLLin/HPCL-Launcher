/*
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// PCL2-style game launch progress page.
///
/// Replaces HMCL's `TaskExecutorDialogPane` / `TaskListPane` launch list.
/// The left pane reproduces `PageLaunchLeft.PanLaunching` (pickaxe loader,
/// skewed title, 4px gradient strip, `PanLaunchingInfo` four-row grid,
/// `PanLaunchingHint`), while the right pane reproduces the `MyCard` task
/// list from `PageSpeedRight.xaml`: 50px status column + 26px rows with
/// waiting `•••` / running `NN%` / done `✓` / failed `✕` glyphs.
///
/// A real [TaskExecutor] is bridged through [TaskListener], the same source
/// HMCL's TaskListPane uses, so every launch dependency, login step and the
/// final process hand-off appears live with PCL2 state animations.
public final class PCLLaunchProgressPage extends BorderPane implements DecoratorPage {

    public enum Phase { RUNNING, FINISHED, FAILED, CANCELLED }

    /// One row of the PCL2 task card.
    public static final class LaunchStep {
        private final StringProperty name = new SimpleStringProperty("");
        private final DoubleProperty progress = new SimpleDoubleProperty(0);
        private final ObjectProperty<StepState> state = new SimpleObjectProperty<>(StepState.WAITING);
        private Task<?> source;

        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }
        public double getProgress() { return progress.get(); }
        public DoubleProperty progressProperty() { return progress; }
        public StepState getState() { return state.get(); }
        public ObjectProperty<StepState> stateProperty() { return state; }
    }

    public enum StepState { WAITING, RUNNING, FINISHED, FAILED }

    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle("正在启动游戏"));
    private final ObservableList<LaunchStep> steps = FXCollections.observableArrayList();
    private final Map<LaunchStep, Node> rowNodes = new HashMap<>();
    private final Map<Task<?>, LaunchStep> byTask = new IdentityHashMap<>();

    private final PCLLoading loader = new PCLLoading();
    private final Label versionName = new Label();
    private final Label stageValue = new Label();
    private final Label methodValue = new Label();
    private final Label progressValue = new Label("00.00 %");
    private final Label speedValue = new Label("0 B/s");
    private final Label speedCaption = new Label("下载速度");
    private final Label hintText = new Label();
    private final StackPane hintCard = new StackPane();
    private final Rectangle progressFill = new Rectangle(0, 4);
    private final StackPane progressTrack = new StackPane();
    private final VBox rows = new VBox();
    private final Label cardPercent = new Label("0 %");
    private final Label cardTitle = new Label();
    private final Label errorLabel = new Label();
    private final VBox card = new VBox();
    private final Button cancelButton = new Button(i18n("button.cancel"));

    private TaskExecutor executor;
    private TaskListener listener;
    private Runnable cancelAction;
    private Phase phase = Phase.RUNNING;
    private final ReadOnlyDoubleWrapper smoothedProgress = new ReadOnlyDoubleWrapper(0);
    private final ReadOnlyStringWrapper currentStage = new ReadOnlyStringWrapper("");
    private Timeline watcher;
    private double rawProgress;
    /// Strong reference so the weak SPEED_EVENT registration stays alive.
    private final java.util.function.Consumer<FetchTask.SpeedEvent> speedHandler;

    public PCLLaunchProgressPage() {
        speedHandler = event -> Platform.runLater(() -> setSpeed(event.getSpeed()));
        FetchTask.SPEED_EVENT.registerWeak(speedHandler);
        getStyleClass().add("hpcl-launch-progress-page");
        setLeft(createLaunchingPane());
        setCenter(createTaskPane());

        loader.setHasAnimation(true);
        loader.setText("");
        cardTitle.setText("启动任务");

        // PCL2 `LoaderTaskbarProgressRefresh`: p = p * 0.9 + raw * 0.1.
        watcher = new Timeline(new KeyFrame(Duration.millis(300), e -> tick()));
        watcher.setCycleCount(Animation.INDEFINITE);

        steps.addListener((ListChangeListener<LaunchStep>) c -> {
            while (c.next()) {
                for (LaunchStep s : c.getRemoved()) {
                    Node n = rowNodes.remove(s);
                    if (n != null) rows.getChildren().remove(n);
                }
                for (LaunchStep s : c.getAddedSubList()) addRow(s);
            }
        });
    }

    // ------------------------------------------------------------------ //
    // Left: PageLaunchLeft.PanLaunching
    // ------------------------------------------------------------------ //

    private Region createLaunchingPane() {
        VBox box = new VBox();
        box.getStyleClass().add("hpcl-launching-panel");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24, 20, 22, 20));
        box.setPrefWidth(300);
        box.setMinWidth(300);
        box.setMaxWidth(300);

        title.getStyleClass().add("hpcl-launching-title");
        applySkew(title);
        versionName.getStyleClass().add("hpcl-launching-name");
        applySkew(versionName);

        progressTrack.getStyleClass().addAll("hpcl-launching-bar", "hpcl-launching-bar-track");
        progressTrack.setMinHeight(4);
        progressTrack.setPrefHeight(4);
        progressTrack.setMaxHeight(4);
        progressTrack.setMaxWidth(Double.MAX_VALUE);
        // Same construction as PCLDownloadTaskPage: an -fx-fill Rectangle
        // whose width is bound to the track. Using a Region with a
        // -fx-background-color class shrinks the strip to a dark stub.
        progressFill.getStyleClass().add("hpcl-launch-progress-fill");
        progressFill.setHeight(4);
        progressFill.widthProperty().bind(progressTrack.widthProperty()
                .multiply(Bindings.createDoubleBinding(
                        () -> Math.max(0, Math.min(1, smoothedProgress.get())),
                        smoothedProgress.getReadOnlyProperty())));
        progressTrack.getChildren().setAll(progressFill);
        progressTrack.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(progressFill, Pos.CENTER_LEFT);

        GridPane info = new GridPane();
        info.getStyleClass().add("hpcl-launching-info");
        info.setAlignment(Pos.CENTER);
        info.setHgap(15);
        info.setVgap(5);
        info.setMaxWidth(Double.MAX_VALUE);
        stageValue.getStyleClass().add("hpcl-launching-info-value");
        methodValue.getStyleClass().add("hpcl-launching-info-value");
        progressValue.getStyleClass().add("hpcl-launching-info-value");
        speedValue.getStyleClass().add("hpcl-launching-info-value");
        Label stageCaption = infoCaption("当前步骤");
        Label methodCaption = infoCaption("登录方式");
        Label progressCaption = infoCaption("启动进度");
        info.add(stageCaption, 0, 0);
        info.add(stageValue, 1, 0);
        info.add(methodCaption, 0, 1);
        info.add(methodValue, 1, 1);
        info.add(progressCaption, 0, 2);
        info.add(progressValue, 1, 2);
        info.add(speedCaption, 0, 3);
        info.add(speedValue, 1, 3);
        for (Label l : new Label[]{stageCaption, methodCaption, progressCaption, speedCaption})
            GridPane.setHalignment(l, HPos.RIGHT);
        speedCaption.setVisible(false);
        speedValue.setVisible(false);

        hintText.getStyleClass().add("hpcl-launching-hint-text");
        hintText.setWrapText(true);
        hintText.setMaxWidth(230);
        Label hintCaption = new Label("你知道吗");
        hintCaption.getStyleClass().add("hpcl-launching-hint-caption");
        VBox hintBody = new VBox(4, hintCaption, hintText);
        hintBody.setAlignment(Pos.CENTER);
        hintCard.getStyleClass().add("hpcl-launching-hint");
        hintCard.getChildren().setAll(hintBody);
        hintCard.setVisible(false);
        hintCard.setManaged(false);
        hintCard.setOpacity(0);
        hintText.setText(randomHint());

        cancelButton.getStyleClass().add("hpcl-launching-cancel");
        cancelButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setOnAction(e -> cancel());

        box.getChildren().setAll(loader, title, versionName, progressTrack, info, hintCard, cancelButton);
        VBox.setMargin(progressTrack, new Insets(12, 8, 0, 8));
        VBox.setMargin(info, new Insets(2, 8, 0, 8));
        VBox.setMargin(cancelButton, new Insets(16, 8, 0, 8));
        return box;
    }

    private final Label title = new Label(i18n("hpcl.launching.title"));

    private static void applySkew(Label label) {
        label.getTransforms().add(new javafx.scene.transform.Shear(-0.0524, 0));
    }

    private static Label infoCaption(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hpcl-launching-info-caption");
        return l;
    }

    // ------------------------------------------------------------------ //
    // Right: PageSpeedRight.PanMain MyCard task list
    // ------------------------------------------------------------------ //

    private Region createTaskPane() {
        ScrollPane scroll = new ScrollPane();
        scroll.getStyleClass().add("hpcl-launch-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox flow = new VBox(15);
        flow.getStyleClass().add("hpcl-launch-flow");

        card.getStyleClass().addAll("hpcl-flow-card", "hpcl-launch-card");
        card.setSpacing(0);

        cardTitle.getStyleClass().add("hpcl-task-card-title");
        cardPercent.getStyleClass().add("hpcl-task-card-percent");
        cardPercent.textProperty().bind(Bindings.createStringBinding(
                () -> (int) Math.floor(Math.max(0, Math.min(1, smoothedProgress.get())) * 100) + " %",
                smoothedProgress.getReadOnlyProperty()));

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        HBox heading = new HBox(8, cardTitle, spring, cardPercent);
        heading.setAlignment(Pos.CENTER_LEFT);

        Rectangle fill = new Rectangle(0, 4);
        fill.getStyleClass().add("hpcl-task-card-fill");
        fill.widthProperty().bind(progressTrack.widthProperty()
                .multiply(Bindings.createDoubleBinding(
                        () -> Math.max(0, Math.min(1, smoothedProgress.get())),
                        smoothedProgress.getReadOnlyProperty())));
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("hpcl-task-card-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMaxHeight(4);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        rows.getStyleClass().add("hpcl-task-rows");
        rows.setPadding(new Insets(2, 0, 0, 0));

        errorLabel.getStyleClass().add("hpcl-task-card-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setOnMouseClicked(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(errorLabel.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });

        card.getChildren().setAll(heading, track, rows, errorLabel);
        flow.getChildren().setAll(card);
        scroll.setContent(flow);
        return scroll;
    }

    private void addRow(LaunchStep step) {
        StepRow row = new StepRow(step);
        rowNodes.put(step, row);
        rows.getChildren().add(row);
        if (AnimationUtils.isAnimationEnabled()) {
            row.setOpacity(0);
            row.setTranslateY(-6);
            new Timeline(new KeyFrame(Duration.millis(180),
                    new KeyValue(row.opacityProperty(), 1, PCLTheme.OUT_WEAK),
                    new KeyValue(row.translateYProperty(), 0, PCLTheme.OUT_MIDDLE))).play();
        }
    }

    /// A 26px PCL row: 50px status glyph + sub-task name.
    private static final class StepRow extends HBox {
        private final LaunchStep step;
        private final Label waiting = new Label("•••");
        private final Label running = new Label("0%");
        private final Node done;
        private final Node failed;
        private final StackPane glyphBox = new StackPane();

        StepRow(LaunchStep step) {
            this.step = step;
            getStyleClass().add("hpcl-task-row");
            setAlignment(Pos.CENTER_LEFT);
            setMinHeight(26);
            setPrefHeight(26);
            setMaxHeight(26);

            done = SVG.CHECK.createIcon(15);
            done.getStyleClass().add("hpcl-task-glyph-done");
            failed = SVG.CLOSE.createIcon(13);
            failed.getStyleClass().add("hpcl-task-glyph-failed");
            waiting.getStyleClass().add("hpcl-task-glyph-waiting");
            running.getStyleClass().add("hpcl-task-glyph-running");

            glyphBox.getStyleClass().add("hpcl-task-glyph-box");
            glyphBox.setMinWidth(50);
            glyphBox.setPrefWidth(50);
            glyphBox.setMaxWidth(50);
            glyphBox.setAlignment(Pos.CENTER_LEFT);
            glyphBox.getChildren().setAll(waiting, running, done, failed);

            Label name = new Label();
            name.textProperty().bind(step.nameProperty());
            name.getStyleClass().add("hpcl-task-row-name");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            getChildren().setAll(glyphBox, name);

            step.progressProperty().addListener((o, ov, nv) -> update());
            step.stateProperty().addListener((o, ov, nv) -> update());
            update();
        }

        private void update() {
            StepState s = step.getState();
            waiting.setVisible(s == StepState.WAITING);
            running.setVisible(s == StepState.RUNNING);
            done.setVisible(s == StepState.FINISHED);
            failed.setVisible(s == StepState.FAILED);
            int p = (int) Math.floor(Math.max(0, Math.min(1, step.getProgress())) * 100);
            running.setText(p + "%");
        }
    }

    // ------------------------------------------------------------------ //
    // Real TaskExecutor bridge
    // ------------------------------------------------------------------ //

    /// Binds a real launch executor. Every `MAJOR` task becomes a PCL2 row.
    /// Kept for the screenshot demo hook; real launches use the overload that
    /// receives the [Account] so the "登录方式" row reflects the true account.
    public void bind(TaskExecutor executor, String version, Runnable cancelAction) {
        bind(executor, version, null, cancelAction);
    }

    /// Resolves the PCL2 "登录方式" caption from the actual account type.
    /// Offline accounts must NOT be reported as Microsoft accounts.
    public static String loginMethodName(Account account) {
        if (account == null) return i18n("account.methods");
        if (account instanceof OfflineAccount) return i18n("account.methods.offline");
        if (account instanceof AuthlibInjectorAccount) return i18n("account.methods.authlib_injector");
        if (account instanceof MicrosoftAccount) return i18n("account.methods.microsoft");
        // Mojang / Yggdrasil and any future factory: use the factory's own
        // localised login-type name rather than hard-coding Microsoft.
        try {
            return Accounts.getLocalizedLoginTypeName(Accounts.getAccountFactory(account));
        } catch (RuntimeException ignored) {
            return i18n("account.methods");
        }
    }

    public void bind(TaskExecutor executor, String version, Account account, Runnable cancelAction) {
        unbind();
        this.executor = executor;
        this.cancelAction = cancelAction;
        versionName.setText(version == null ? "" : version);
        methodValue.setText(loginMethodName(account));
        stageValue.setText(i18n("message.doing"));
        phase = Phase.RUNNING;
        rawProgress = 0;
        smoothedProgress.set(0);
        card.getStyleClass().removeAll("hpcl-launch-card-done", "hpcl-launch-card-failed");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        hintCard.setVisible(false);
        hintCard.setManaged(false);
        // start() resets the state to RUN (MyLoading.LoadingState.Run).
        loader.start();

        if (executor != null) {
            listener = new TaskListener() {
                @Override
                public void onReady(Task<?> task) {
                    addStep(task);
                }

                @Override
                public void onRunning(Task<?> task) {
                    Platform.runLater(() -> {
                        LaunchStep step = addStep(task);
                        if (step != null) step.stateProperty().set(StepState.RUNNING);
                        String name = task.getName();
                        if (name != null && !name.startsWith("org.jackhuang.hmcl") && !name.contains("$"))
                            currentStage.set(name);
                    });
                }

                @Override
                public void onFinished(Task<?> task) {
                    Platform.runLater(() -> {
                        LaunchStep step = byTask.get(task);
                        if (step != null) {
                            step.progressProperty().set(1);
                            step.stateProperty().set(StepState.FINISHED);
                        }
                    });
                }

                @Override
                public void onFailed(Task<?> task, Throwable throwable) {
                    Platform.runLater(() -> {
                        LaunchStep step = byTask.get(task);
                        if (step != null) step.stateProperty().set(StepState.FAILED);
                    });
                }

                @Override
                public void onStop(boolean success, TaskExecutor exec) {
                    Platform.runLater(() -> finish(success, exec));
                }
            };
            executor.addTaskListener(listener);
        }
        watcher.playFromStart();
    }

    private LaunchStep addStep(Task<?> task) {
        String name = task.getName();
        if (name == null || name.isEmpty() || !task.getSignificance().shouldShow())
            return null;
        // Task.allOf / compose wrappers keep their Java class name as the
        // default name; PCL2's LoaderTaskbar only shows named loaders.
        if (name.startsWith("org.jackhuang.hmcl") || name.contains("$")
                || name.indexOf('.') > 0 && name.matches("[a-zA-Z0-9_.]+"))
            return null;
        LaunchStep existing = byTask.get(task);
        if (existing != null) return existing;
        LaunchStep step = new LaunchStep();
        step.nameProperty().set(name);
        step.source = task;
        byTask.put(task, step);
        if (Platform.isFxApplicationThread()) steps.add(step);
        else Platform.runLater(() -> steps.add(step));
        return step;
    }

    /// PCL2 `PageSpeedLeft.Watcher` + `LoaderTaskbarProgressRefresh`.
    private void tick() {
        if (phase != Phase.RUNNING) return;
        double sum = 0;
        int count = 0;
        for (LaunchStep s : steps) {
            if (s.source != null) {
                double p = s.source.progressProperty().get();
                if (p >= 0) s.progressProperty().set(Math.max(0, Math.min(1, p)));
            }
            sum += s.getProgress();
            count++;
        }
        rawProgress = count == 0 ? 0 : sum / count;
        double shown = smoothedProgress.get();
        if (rawProgress <= 0 || rawProgress >= 1 || shown > rawProgress)
            smoothedProgress.set(rawProgress);
        else smoothedProgress.set(shown * 0.9 + rawProgress * 0.1);

        double pct = smoothedProgress.get() * 100;
        progressValue.setText(String.format("%05.2f %%", pct));
        if (!currentStage.get().isEmpty()) stageValue.setText(currentStage.get());
    }

    private void finish(boolean success, TaskExecutor exec) {
        if (phase != Phase.RUNNING) return;
        Exception ex = exec == null ? null : exec.getException();
        boolean cancelled = ex instanceof CancellationException
                || (exec != null && exec.isCancelled());
        phase = cancelled ? Phase.CANCELLED : (success ? Phase.FINISHED : Phase.FAILED);
        watcher.stop();
        smoothedProgress.set(success ? 1 : smoothedProgress.get());
        progressValue.setText(String.format("%05.2f %%", smoothedProgress.get() * 100));

        for (LaunchStep s : steps) {
            if (s.source != null && s.source.progressProperty().get() < 0)
                s.progressProperty().set(1);
            if (success && s.getState() != StepState.FAILED)
                s.stateProperty().set(StepState.FINISHED);
        }

        loader.stop();
        if (success) {
            card.getStyleClass().add("hpcl-launch-card-done");
            stageValue.setText("启动完成");
            // PCL `PanLaunchingHint` fades in once the game has launched.
            hintCard.setVisible(true);
            hintCard.setManaged(true);
            if (AnimationUtils.isAnimationEnabled()) {
                hintCard.setOpacity(0);
                new Timeline(new KeyFrame(Duration.millis(220),
                        new KeyValue(hintCard.opacityProperty(), 1, PCLTheme.OUT_WEAK))).play();
            } else hintCard.setOpacity(1);
            cancelButton.setText("返回");
            cancelButton.setOnAction(e -> goBack());
        } else {
            loader.setError(ex == null ? "" : ex.getLocalizedMessage());
            card.getStyleClass().add("hpcl-launch-card-failed");
            stageValue.setText(cancelled ? i18n("message.cancelled") : i18n("launch.failed"));
            if (ex != null) {
                errorLabel.setText("启动失败（点击复制）：" + ex.getLocalizedMessage());
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
            cancelButton.setText("返回");
            cancelButton.setOnAction(e -> goBack());
        }
    }

    private void goBack() {
        org.jackhuang.hmcl.ui.Controllers.backToHome();
    }

    public void cancel() {
        if (phase != Phase.RUNNING) { goBack(); return; }
        if (cancelAction != null) cancelAction.run();
    }

    public void setSpeed(long bytesPerSecond) {
        boolean show = phase == Phase.RUNNING && bytesPerSecond > 0;
        speedCaption.setVisible(show);
        speedCaption.setManaged(show);
        speedValue.setVisible(show);
        speedValue.setManaged(show);
        if (show) speedValue.setText(PCLDownloadTaskPage.formatSpeed(bytesPerSecond));
    }

    /// Called when the launcher is closed / a new launch begins.
    public void unbind() {
        watcher.stop();
        this.executor = null;
        this.listener = null;
        this.cancelAction = null;
        byTask.clear();
        if (Platform.isFxApplicationThread()) steps.clear();
        else Platform.runLater(steps::clear);
    }

    public Phase getPhase() { return phase; }

    private static String randomHint() {
        String[] hints = {
                "首次启动需要下载游戏依赖，之后启动会快很多。",
                "在设置中调整内存分配，可以让大型整合包更流畅。",
                "点击右下角的下载悬浮球可以随时查看当前任务进度。",
                "启动失败时，点击错误信息即可复制详细日志。",
                "PCL2 风格界面按开源实现复刻，游戏内核仍完全兼容 HMCL。"
        };
        return hints[Math.abs((int) System.currentTimeMillis()) % hints.length];
    }

    // ------------------------------------------------------------------ //
    // DecoratorPage
    // ------------------------------------------------------------------ //

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
    }

    @Override
    public void onDecoratorPageNavigating(Navigator.NavigationEvent event) {
        // PCL pages paint their own surfaces.
    }

    @Override
    public void closePage() {
        unbind();
    }
}
