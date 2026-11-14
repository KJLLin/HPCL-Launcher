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

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.HashMap;
import java.util.Map;

/// PCL2 `PageType.TaskManager`: `PageSpeedLeft` (four aggregate statistics on
/// a 200px white sidebar) + `PageSpeedRight` (a scrolling stack of `MyCard`s,
/// one per `LoaderTaskbar` entry, with a 50px status column and 26px rows).
///
/// Every value is live: the registry is [PCLDownloadCenter], fed by the very
/// same [org.jackhuang.hmcl.task.TaskListener] events HMCL's TaskListPane uses.
public final class PCLDownloadTaskPage extends BorderPane implements DecoratorPage {

    private final PCLDownloadCenter center = PCLDownloadCenter.getInstance();
    private final VBox cardFlow = new VBox(15);
    private final VBox emptyCard = new VBox(10);
    private final Map<PCLDownloadCenter.DownloadTask, TaskCard> cards = new HashMap<>();
    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle("下载管理"));

    public PCLDownloadTaskPage() {
        getStyleClass().add("hpcl-download-task-page");
        setLeft(createStatisticsPane());
        setCenter(createTaskPane());

        center.getTasks().addListener((ListChangeListener<PCLDownloadCenter.DownloadTask>) c -> {
            while (c.next()) {
                for (PCLDownloadCenter.DownloadTask t : c.getRemoved()) removeCard(t);
                for (PCLDownloadCenter.DownloadTask t : c.getAddedSubList()) addCard(t);
            }
            updateEmpty();
        });
        for (PCLDownloadCenter.DownloadTask t : center.getTasks()) addCard(t);
        updateEmpty();
    }

    // ------------------------------------------------------------------ //
    // PageSpeedLeft: 总进度 / 下载速度 / 剩余文件 / 剩余线程
    // ------------------------------------------------------------------ //

    private Region createStatisticsPane() {
        VBox box = new VBox();
        box.getStyleClass().add("hpcl-task-stats");
        box.setPrefWidth(200);
        box.setMinWidth(200);
        box.setMaxWidth(200);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12, 0, 12, 0));

        Region top = spacer(1);
        Region gap1 = spacer(0.6);
        Region gap2 = spacer(0.6);
        Region gap3 = spacer(0.6);
        Region bottom = spacer(1);

        Label progress = statValue();
        progress.textProperty().bind(Bindings.createStringBinding(
                () -> formatPercent(center.getSmoothedProgress()),
                center.smoothedProgressProperty()));
        Label speed = statValue();
        speed.textProperty().bind(Bindings.createStringBinding(
                () -> formatSpeed(center.getSpeed()), center.speedProperty()));
        Label files = statValue();
        files.textProperty().bind(Bindings.createStringBinding(
                () -> Integer.toString(center.getFileRemain()),
                center.fileRemainProperty()));
        Label threads = statValue();
        threads.textProperty().bind(Bindings.createStringBinding(
                () -> center.getThreadCount() + " / " + center.getThreadLimit(),
                center.threadCountProperty()));

        box.getChildren().setAll(
                top,
                statTitle("总进度"), statSplit(), progress,
                gap1,
                statTitle("下载速度"), statSplit(), speed,
                gap2,
                statTitle("剩余文件"), statSplit(), files,
                gap3,
                statTitle("剩余线程"), statSplit(), threads,
                bottom);
        for (Region r : new Region[]{top, gap1, gap2, gap3, bottom})
            VBox.setVgrow(r, Priority.ALWAYS);
        // PCL uses 1* / 0.6* / 0.6* / 0.6* / 1* rows: make the outer spacers
        // slightly taller than the inner ones.
        top.setPrefHeight(20);
        bottom.setPrefHeight(20);
        return box;
    }

    private static Region spacer(double weight) {
        Region r = new Region();
        r.setPrefHeight(weight * 18);
        return r;
    }

    private static Label statTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hpcl-task-stat-title");
        return label;
    }

    /// PCL `PageSpeedLeft` "Split": a 2px line that fades to the background
    /// at both ends, with the theme colour through the middle.
    private static Region statSplit() {
        Region line = new Region();
        line.getStyleClass().add("hpcl-task-stat-split");
        line.setPrefHeight(2);
        line.setMaxWidth(150);
        return line;
    }

    private static Label statValue() {
        Label label = new Label("00.00 %");
        label.getStyleClass().add("hpcl-task-stat-value");
        return label;
    }

    /// PCL2: `Math.Floor(p*100) & "." & Math.Floor(frac*100).EnsureLength("0",2)`
    private static String formatPercent(double p) {
        double v = Math.max(0, Math.min(1, p)) * 100;
        if (v > 99.9999) return "100 %";
        int whole = (int) Math.floor(v);
        int frac = (int) Math.floor((v - whole) * 100);
        return String.format("%02d.%02d %%", whole, frac);
    }

    /// PCL `StringUtils.FormatByteSize` + "/s".
    public static String formatSpeed(long bytesPerSecond) {
        double v = Math.max(0, bytesPerSecond);
        if (v < 1024) return (long) v + " B/s";
        if (v < 1024 * 1024) return String.format("%.1f KB/s", v / 1024);
        if (v < 1024L * 1024 * 1024) return String.format("%.1f MB/s", v / (1024 * 1024));
        return String.format("%.2f GB/s", v / (1024L * 1024 * 1024));
    }

    // ------------------------------------------------------------------ //
    // PageSpeedRight: PanMain card flow
    // ------------------------------------------------------------------ //

    private Region createTaskPane() {
        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().add("hpcl-task-content");

        cardFlow.getStyleClass().add("hpcl-task-card-flow");
        cardFlow.setPadding(new Insets(25, 25, 10, 25));
        cardFlow.setAlignment(Pos.TOP_CENTER);

        buildEmptyCard();

        ScrollPane scroll = new ScrollPane(cardFlow);
        scroll.getStyleClass().addAll("hpcl-card-flow-scroll", "hpcl-task-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        wrapper.getChildren().setAll(scroll);
        return wrapper;
    }

    private void buildEmptyCard() {
        emptyCard.getStyleClass().addAll("hpcl-flow-card", "hpcl-task-empty");
        emptyCard.setAlignment(Pos.CENTER);
        emptyCard.setPadding(new Insets(34, 20, 34, 20));

        Node glyph = SVG.DOWNLOAD.createIcon(34);
        glyph.getStyleClass().add("hpcl-task-empty-icon");
        Label title = new Label("当前没有下载任务");
        title.getStyleClass().add("hpcl-task-empty-title");
        Label hint = new Label("下载游戏、整合包或资源时，进度会实时显示在这里");
        hint.getStyleClass().add("hpcl-task-empty-hint");
        hint.setWrapText(true);

        Button go = new Button("前往下载页");
        go.getStyleClass().add("hpcl-task-empty-button");
        go.setOnAction(e -> {
            Controllers.getDownloadPage().showGameDownloads();
            Controllers.navigate(Controllers.getDownloadPage());
        });
        emptyCard.getChildren().setAll(glyph, title, hint, go);
        cardFlow.getChildren().add(emptyCard);
    }

    private void updateEmpty() {
        boolean empty = center.getTasks().isEmpty();
        emptyCard.setVisible(empty);
        emptyCard.setManaged(empty);
    }

    private void addCard(PCLDownloadCenter.DownloadTask task) {
        if (cards.containsKey(task)) return;
        TaskCard card = new TaskCard(task);
        cards.put(task, card);
        // PCL2 inserts new cards at index 0 (newest on top).
        cardFlow.getChildren().add(0, card);
        PCLTheme.playEntrance(card);
        updateEmpty();
    }

    private void removeCard(PCLDownloadCenter.DownloadTask task) {
        TaskCard card = cards.remove(task);
        if (card == null) return;
        PCLTheme.playCardDispose(card, () -> {
            cardFlow.getChildren().remove(card);
            updateEmpty();
        });
    }

    // ------------------------------------------------------------------ //
    // One MyCard per LoaderTaskbar entry
    // ------------------------------------------------------------------ //

    private static final class TaskCard extends VBox {
        private final PCLDownloadCenter.DownloadTask task;
        private final VBox rows = new VBox();
        private final Map<PCLDownloadCenter.SubTask, Node> rowNodes = new HashMap<>();
        private final Label percent = new Label();
        private final Rectangle fill = new Rectangle(0, 4);
        private final StackPane track = new StackPane();
        private final Label errorLabel = new Label();

        TaskCard(PCLDownloadCenter.DownloadTask task) {
            this.task = task;
            getStyleClass().addAll("hpcl-flow-card", "hpcl-task-card");
            setSpacing(0);

            // PCL MyCard heading at (15,12), body inset from y=34.
            Label name = new Label();
            name.textProperty().bind(task.titleProperty());
            name.getStyleClass().add("hpcl-task-card-title");

            percent.getStyleClass().add("hpcl-task-card-percent");
            percent.textProperty().bind(Bindings.createStringBinding(
                    () -> (int) Math.floor(Math.max(0, Math.min(1, task.getProgress())) * 100) + " %",
                    task.progressProperty()));

            Button cancel = new Button();
            cancel.getStyleClass().add("hpcl-task-card-cancel");
            Node closeIcon = SVG.CLOSE.createIcon(12);
            closeIcon.getStyleClass().add("hpcl-task-card-cancel-icon");
            cancel.setGraphic(closeIcon);
            cancel.setTooltip(null);
            cancel.setOnAction(e -> task.cancel());

            Region spring = new Region();
            HBox.setHgrow(spring, Priority.ALWAYS);
            HBox heading = new HBox(8, name, spring, percent, cancel);
            heading.setAlignment(Pos.CENTER_LEFT);

            // A thin progress strip (HPCL affordance) under the heading. PCL
            // itself shows the aggregate on the ball and the left sidebar.
            fill.getStyleClass().add("hpcl-task-card-fill");
            fill.widthProperty().bind(track.widthProperty()
                    .multiply(Bindings.createDoubleBinding(
                            () -> Math.max(0, Math.min(1, task.getProgress())),
                            task.progressProperty())));
            track.getStyleClass().add("hpcl-task-card-track");
            track.getChildren().setAll(fill);
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
                ClipboardContent content = new ClipboardContent();
                content.putString(errorLabel.getText());
                Clipboard.getSystemClipboard().setContent(content);
            });

            getChildren().setAll(heading, track, rows, errorLabel);

            for (PCLDownloadCenter.SubTask s : task.getSubtasks()) addRow(s);
            task.getSubtasks().addListener((ListChangeListener<PCLDownloadCenter.SubTask>) c -> {
                while (c.next()) {
                    for (PCLDownloadCenter.SubTask s : c.getRemoved()) {
                        Node n = rowNodes.remove(s);
                        if (n != null) rows.getChildren().remove(n);
                    }
                    for (PCLDownloadCenter.SubTask s : c.getAddedSubList()) addRow(s);
                }
                refreshSummary();
            });
            task.statusProperty().addListener((o, ov, nv) -> applyStatus(nv));
            task.errorProperty().addListener((o, ov, nv) -> showError(nv));
            applyStatus(task.getStatus());
            showError(task.getError());
            refreshSummary();
        }

        private void addRow(PCLDownloadCenter.SubTask s) {
            Node row = new SubTaskRow(s);
            rowNodes.put(s, row);
            rows.getChildren().add(row);
        }

        /// PCL caps the card at a readable height; collapse the long tail into
        /// a single summary line so a 300-file install stays usable.
        private void refreshSummary() {
            rows.getChildren().removeIf(n -> "hpcl-task-row-summary"
                    .equals(n.getProperties().get("hpcl.row.kind")));
            int total = task.getSubtasks().size();
            if (total <= MAX_ROWS) return;
            int shown = 0;
            for (Node n : rows.getChildren()) {
                if (n instanceof SubTaskRow) shown++;
            }
            if (shown <= MAX_ROWS) return;
            // Keep the first MAX_ROWS rows, drop the rest from the view.
            int kept = 0;
            var it = rows.getChildren().iterator();
            while (it.hasNext()) {
                Node n = it.next();
                if (n instanceof SubTaskRow) {
                    kept++;
                    if (kept > MAX_ROWS) it.remove();
                }
            }
            Label summary = new Label("…… 等共 " + total + " 个文件");
            summary.getStyleClass().add("hpcl-task-row-summary");
            summary.getProperties().put("hpcl.row.kind", "hpcl-task-row-summary");
            rows.getChildren().add(summary);
        }

        private static final int MAX_ROWS = 10;

        private void applyStatus(PCLDownloadCenter.Status status) {
            getStyleClass().removeAll("hpcl-task-card-failed", "hpcl-task-card-done");
            if (status == PCLDownloadCenter.Status.FAILED)
                getStyleClass().add("hpcl-task-card-failed");
            else if (status == PCLDownloadCenter.Status.FINISHED
                    || status == PCLDownloadCenter.Status.CANCELLED)
                getStyleClass().add("hpcl-task-card-done");
        }

        private void showError(String text) {
            if (text == null || text.isEmpty()) {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
                return;
            }
            errorLabel.setText("下载失败（点击复制）：" + text);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    /// A 26px PCL row: 50px status glyph + sub-task name.
    private static final class SubTaskRow extends HBox {
        private final PCLDownloadCenter.SubTask sub;
        private final Label waiting = new Label("•••");
        private final Label running = new Label("0%");
        private final Node done;
        private final Node failed;
        private final StackPane glyphBox = new StackPane();

        SubTaskRow(PCLDownloadCenter.SubTask sub) {
            this.sub = sub;
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
            name.textProperty().bind(sub.nameProperty());
            name.getStyleClass().add("hpcl-task-row-name");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);

            getChildren().setAll(glyphBox, name);

            sub.progressProperty().addListener((o, ov, nv) -> update());
            sub.statusProperty().addListener((o, ov, nv) -> update());
            update();
        }

        private void update() {
            PCLDownloadCenter.Status s = sub.getStatus();
            waiting.setVisible(s == PCLDownloadCenter.Status.WAITING);
            running.setVisible(s == PCLDownloadCenter.Status.RUNNING);
            done.setVisible(s == PCLDownloadCenter.Status.FINISHED);
            failed.setVisible(s == PCLDownloadCenter.Status.FAILED
                    || s == PCLDownloadCenter.Status.CANCELLED);
            int p = (int) Math.floor(Math.max(0, Math.min(1, sub.getProgress())) * 100);
            running.setText(p + "%");
        }
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
        // Stats are bound to live properties; nothing polling to re-run.
    }

    @Override
    public void onDecoratorPageNavigating(org.jackhuang.hmcl.ui.construct.Navigator.NavigationEvent event) {
        // The page paints its own PCL surfaces; skip HMCL's content-background.
    }
}
