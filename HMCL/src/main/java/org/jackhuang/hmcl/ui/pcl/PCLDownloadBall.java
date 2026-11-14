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
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;

/// PCL2's bottom-right download control is `FrmMain.BtnExtraDownload`, a
/// `MyExtraButton`: a 40px blue disc with a white glyph, and a translucent
/// white fill that rises from the bottom to show `Progress` (0..1). It is not
/// a spinning arc or a modal dialog. `PageSpeedLeft` owns the numeric speed;
/// HPCL keeps a compact speed chip beside the disc so the value stays visible
/// without leaving the page.
///
/// Reproduced from `Controls/MyExtraButton.xaml` + `.xaml.vb`:
/// - Progress: `Rect(0, 40 * (1 - p), 40, 40 * p)` clipped to the circle.
/// - Show: scale 0 -> 0.3 (500ms OutFluent/Weak, 60ms delay) plus
///   0 -> 0.7 (500ms OutBack/Weak), height 0 -> 50 (200ms OutFluent/Weak).
/// - Hide: scale -> 0 (100ms InFluent/Weak), height -> 0 (400ms, 100ms delay).
/// - Press: scale -> 0.85 (800ms OutFluent/Strong); release -> 1 (300ms OutBack).
/// - Hover: ColorBrush3 -> ColorBrush4 (120ms in / 150ms out).
/// - Ribble: a soft ring expanding 13x over 1000ms.
/// `BtnExtraDownload_ShowCheck` = has a running task AND not on TaskManager.
public final class PCLDownloadBall extends HBox {

    private static final double SIZE = 40;
    private static final Color C3 = Color.web("#1370F3");
    private static final Color C4 = Color.web("#4890F5");

    private final PCLDownloadCenter center = PCLDownloadCenter.getInstance();
    private final StackPane ballLayer = new StackPane();
    private final Pane discPane = new Pane();
    private final Circle disc = new Circle(20, 20, 20);
    private final Rectangle progressFill = new Rectangle(0, SIZE, SIZE, 0);
    private final Label speedChip = new Label();
    private final DoubleProperty hoverMix = new SimpleDoubleProperty(0);

    private boolean showing;
    private boolean taskPageVisible;
    private boolean testMode;
    private Runnable onOpen;
    private Timeline showTimeline;
    private Timeline pressTimeline;

    public PCLDownloadBall() {
        getStyleClass().add("hpcl-download-ball");
        setAlignment(Pos.CENTER_RIGHT);
        setSpacing(8);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setPickOnBounds(false);

        disc.getStyleClass().add("hpcl-download-ball-disc");
        // The hover colour is animated as a mix factor (120ms in / 150ms out),
        // equivalent to PCL's AaColor on ColorBrush3 / ColorBrush4.
        disc.fillProperty().bind(Bindings.createObjectBinding(
                () -> C3.interpolate(C4, hoverMix.get()), hoverMix));
        disc.setEffect(new javafx.scene.effect.DropShadow(
                javafx.scene.effect.BlurType.GAUSSIAN, Color.rgb(52, 61, 74, 0.2), 10, 0, 0, 0));

        // MyExtraButton PanProgress: PCL2 writes #2FFFFFFF in WPF's
        // #AARRGGBB order (18% white). JavaFX Color.web parses 8 digits as
        // #RRGGBBAA, so pasting it verbatim produced an opaque CYAN disc
        // instead of a translucent white progress overlay. Use the explicit
        // RGBA equivalent.
        progressFill.setFill(Color.rgb(255, 255, 255, 0.184));
        progressFill.setClip(new Circle(20, 20, 20));
        progressFill.getStyleClass().add("hpcl-download-ball-fill");

        Node glyph = SVG.DOWNLOAD.createIcon(20);
        glyph.getStyleClass().add("hpcl-download-ball-glyph");
        glyph.setLayoutX(10);
        glyph.setLayoutY(10);
        glyph.setMouseTransparent(true);

        discPane.getChildren().setAll(disc, progressFill, glyph);
        discPane.setPrefSize(SIZE, SIZE);
        discPane.setMaxSize(SIZE, SIZE);
        discPane.setMinSize(SIZE, SIZE);

        ballLayer.getChildren().setAll(discPane);
        ballLayer.setPrefSize(SIZE, SIZE);
        ballLayer.setMaxSize(SIZE, SIZE);
        ballLayer.setMinSize(SIZE, SIZE);
        ballLayer.getStyleClass().add("hpcl-download-ball-layer");
        ballLayer.setScaleX(0);
        ballLayer.setScaleY(0);

        speedChip.getStyleClass().add("hpcl-download-ball-speed");
        speedChip.setVisible(false);
        speedChip.setManaged(false);
        speedChip.textProperty().bind(Bindings.createStringBinding(
                () -> PCLDownloadTaskPage.formatSpeed(center.getSpeed()),
                center.speedProperty()));

        getChildren().setAll(speedChip, ballLayer);
        setVisible(false);
        setManaged(false);
        setOpacity(0);

        // Live state from the registry.
        center.smoothedProgressProperty().addListener((o, ov, nv) -> applyProgress(nv.doubleValue()));
        center.speedProperty().addListener((o, ov, nv) -> onSpeed(nv.longValue()));
        center.activeProperty().addListener((o, ov, nv) -> showRefresh());
        center.getTasks().addListener((javafx.collections.ListChangeListener<PCLDownloadCenter.DownloadTask>) c -> {
            if (c.next() && c.wasAdded()) PCLTheme.playRibble(ballLayer, 20);
        });
        applyProgress(center.getSmoothedProgress());

        installInteraction();
    }

    // ------------------------------------------------------------------ //
    // PCL MyExtraButton.Progress
    // ------------------------------------------------------------------ //

    private void applyProgress(double value) {
        double p = Math.max(0, Math.min(1, value));
        progressFill.setY(SIZE * (1 - p));
        progressFill.setHeight(SIZE * p);
        progressFill.setVisible(p >= 0.0001);
    }

    // ------------------------------------------------------------------ //
    // ShowCheck: HasDownloadingTask() AndAlso Not PageCurrent = TaskManager
    // ------------------------------------------------------------------ //

    public void setOnOpen(Runnable action) { this.onOpen = action; }

    public void setTaskPageVisible(boolean visible) {
        if (taskPageVisible == visible) return;
        taskPageVisible = visible;
        showRefresh();
    }

    public void showRefresh() {
        boolean shouldShow = (testMode || center.isActive() || center.getSpeed() > 0)
                && !taskPageVisible;
        setShow(shouldShow);
    }

    private void setShow(boolean value) {
        if (showing == value) return;
        showing = value;
        if (showTimeline != null) showTimeline.stop();

        if (value) {
            setManaged(true);
            setVisible(true);
            speedChip.setVisible(true);
            speedChip.setManaged(true);
            if (!AnimationUtils.isAnimationEnabled()) {
                setOpacity(1);
                ballLayer.setScaleX(1);
                ballLayer.setScaleY(1);
                return;
            }
            // 500ms OutFluent/Weak to 0.3 + 500ms OutBack/Weak by 0.7 with a
            // 60ms delay; approximated as one OutBack scale from 0 -> 1.
            ballLayer.setScaleX(0);
            ballLayer.setScaleY(0);
            showTimeline = new Timeline(
                    new KeyFrame(Duration.millis(60),
                            new KeyValue(ballLayer.scaleXProperty(), 0, PCLTheme.OUT_WEAK),
                            new KeyValue(ballLayer.scaleYProperty(), 0, PCLTheme.OUT_WEAK),
                            new KeyValue(opacityProperty(), 0, PCLTheme.OUT_WEAK)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(opacityProperty(), 1, PCLTheme.OUT_WEAK)),
                    new KeyFrame(Duration.millis(560),
                            new KeyValue(ballLayer.scaleXProperty(), 1, PCLTheme.BACK_WEAK),
                            new KeyValue(ballLayer.scaleYProperty(), 1, PCLTheme.BACK_WEAK)));
            showTimeline.play();
        } else {
            if (!AnimationUtils.isAnimationEnabled()) {
                setVisible(false);
                setManaged(false);
                setOpacity(0);
                ballLayer.setScaleX(0);
                ballLayer.setScaleY(0);
                return;
            }
            // 100ms InFluent/Weak scale out, then the container collapses.
            showTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(ballLayer.scaleXProperty(), ballLayer.getScaleX(), PCLTheme.IN_WEAK),
                            new KeyValue(ballLayer.scaleYProperty(), ballLayer.getScaleY(), PCLTheme.IN_WEAK),
                            new KeyValue(opacityProperty(), getOpacity(), PCLTheme.IN_WEAK)),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(ballLayer.scaleXProperty(), 0, PCLTheme.IN_WEAK),
                            new KeyValue(ballLayer.scaleYProperty(), 0, PCLTheme.IN_WEAK)),
                    new KeyFrame(Duration.millis(220),
                            new KeyValue(opacityProperty(), 0, PCLTheme.IN_WEAK)));
            showTimeline.setOnFinished(e -> {
                if (!showing) {
                    setVisible(false);
                    setManaged(false);
                }
            });
            showTimeline.play();
        }
    }

    private void onSpeed(long bytesPerSecond) {
        boolean has = bytesPerSecond > 0 || testMode;
        speedChip.setVisible(has);
        speedChip.setManaged(has);
        if (!showing && has && !taskPageVisible) showRefresh();
    }

    // ------------------------------------------------------------------ //
    // Press / release / hover colour
    // ------------------------------------------------------------------ //

    private void installInteraction() {
        setOnMousePressed(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            if (pressTimeline != null) pressTimeline.stop();
            if (!AnimationUtils.isAnimationEnabled()) {
                ballLayer.setScaleX(0.85);
                ballLayer.setScaleY(0.85);
                return;
            }
            pressTimeline = new Timeline(new KeyFrame(Duration.millis(800),
                    new KeyValue(ballLayer.scaleXProperty(), 0.85, PCLTheme.OUT_STRONG),
                    new KeyValue(ballLayer.scaleYProperty(), 0.85, PCLTheme.OUT_STRONG)));
            pressTimeline.play();
        });
        setOnMouseReleased(e -> {
            if (pressTimeline != null) pressTimeline.stop();
            if (!AnimationUtils.isAnimationEnabled()) {
                ballLayer.setScaleX(1);
                ballLayer.setScaleY(1);
                return;
            }
            // MyExtraButton: 300ms AniEaseOutBack back to 1.
            pressTimeline = new Timeline(new KeyFrame(Duration.millis(300),
                    new KeyValue(ballLayer.scaleXProperty(), 1, PCLTheme.BACK_WEAK),
                    new KeyValue(ballLayer.scaleYProperty(), 1, PCLTheme.BACK_WEAK)));
            pressTimeline.play();
        });
        setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && onOpen != null)
                onOpen.run();
        });
        setOnMouseEntered(e -> animateHover(1, 120));
        setOnMouseExited(e -> animateHover(0, 150));
    }

    private void animateHover(double target, int millis) {
        if (!AnimationUtils.isAnimationEnabled()) {
            hoverMix.set(target);
            return;
        }
        new Timeline(new KeyFrame(Duration.millis(millis),
                new KeyValue(hoverMix, target, javafx.animation.Interpolator.LINEAR))).play();
    }

    // ------------------------------------------------------------------ //
    // Screenshot hooks (disabled by default)
    // ------------------------------------------------------------------ //

    /// Forces the ball on screen with a fixed speed for real-session captures.
    public void showTestSpeed(long bytesPerSecond) {
        testMode = true;
        applyProgress(0.62);
        Label chip = speedChip;
        chip.textProperty().unbind();
        chip.setText(PCLDownloadTaskPage.formatSpeed(bytesPerSecond));
        chip.setVisible(true);
        chip.setManaged(true);
        setVisible(true);
        setManaged(true);
        setOpacity(1);
        ballLayer.setScaleX(1);
        ballLayer.setScaleY(1);
        showing = true;
    }

    public void dispose() {
        if (showTimeline != null) showTimeline.stop();
        if (pressTimeline != null) pressTimeline.stop();
    }
}
