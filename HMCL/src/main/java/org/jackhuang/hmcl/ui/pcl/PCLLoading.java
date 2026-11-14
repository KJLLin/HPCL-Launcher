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

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;

/// PCL2 `Controls/MyLoading.xaml` + `.xaml.vb`.
///
/// This is the pickaxe loader that PCL2 shows in four places: the launch
/// overlay (`PageLaunchLeft.LoadLaunching`), the "loading version list" card
/// on the install page (`PageDownloadInstall.PanLoad`, with ShowProgress),
/// the link page (`PageLinkMain.Load`) and the generic right-page loader
/// (`MyPageRight.PageLoaderInit`).
///
/// Reproduced from the source:
/// - Foreground is `ColorBrush3` (theme blue), not grey; on error it animates
///   to `ColorBrushRedLight` and back.
/// - The swing is a relative-angle loop: 55deg -> -20deg (350ms InBack/Weak,
///   250ms delay) -> 30deg (900ms OutFluent) -> 55deg (900ms OutElastic/Weak),
///   then restarts. Two sparks fly out when the pickaxe lands.
/// - `ShowProgress` appends "- NN%" to the caption; `HasAnimation=False`
///   parks the pickaxe; `State=Error` reveals a cross and swaps the caption
///   for the error text; clicking the control asks the owner to retry.
public final class PCLLoading extends VBox {

    /// State mirror of `MyLoading.MyLoadingState`.
    public enum State { RUN, STOP, ERROR }

    /// PCL `PathPickaxe.Data` verbatim. 1024x1024 design box, stroked (not filled).
    private static final String PICKAXE_PATH =
            "M 963.6 858.2 410.816 305.504 C 508.116 213.304 609.204 196.8 711.104 128.6 " +
            "837.11367 49.573762 879.34045 50.334062 751.5 49.5 611.3 52 471.8 96.2 353.3 182.4 " +
            "309.8 155.7 252.1 161.2 214.5 198.9 176.9 236.6 171.3 294.2 198 337.7 " +
            "111.8 456.3 67.6 595.8 65.1 735.9 63.315254 883.82034 65.077966 837.29308 144.2 695.488 " +
            "212.4 593.588 228.888 492.4 321.088 395.2 L 873.9 948 " +
            "c 0.60001 0.59999 1.6 0.6 2.2 0 l 87.5 -87.5 c 0.6 -0.7 0.6 -1.6 0 -2.3 z";

    /// PCL `PathLeft` / `PathRight`: a tiny wedge-shaped spark.
    private static final String SPARK_PATH = "M0,0L2,0L1,4";
    /// PCL `PathError`: the cross shown in the error state.
    private static final String ERROR_PATH =
            "M2,0 L0,2 8,10 0,18 2,20 10,12 18,20 20,18 12,10 20,2 18,0 10,8 2,0Z";

    /// Resting angle of the pickaxe (`RotateTransform Angle="55"`).
    private static final double REST_ANGLE = 55;
    private static final int WINDUP_DELAY = 250;
    private static final int WINDUP_TIME = 350;
    private static final int CHOP_TIME = 900;
    private static final int SETTLE_TIME = 900;
    private static final int LOOP_TIME = WINDUP_DELAY + WINDUP_TIME + CHOP_TIME + SETTLE_TIME;

    private static final double ICON_WIDTH = 60;
    private static final double ICON_HEIGHT = 47;
    private static final double PICKAXE_HEIGHT = 35;

    private static final Color NORMAL_INK = Color.web(PCLTheme.C3);
    private static final Color ERROR_INK = Color.web("#E45C58");

    private final Pane iconBox = new Pane();
    private final Rotate pickaxeRotate = new Rotate(REST_ANGLE);
    private final SVGPath pickaxe = new SVGPath();
    private final SVGPath sparkLeft;
    private final SVGPath sparkRight;
    private final SVGPath errorCross = new SVGPath();
    private final Rectangle ground = new Rectangle(25, 2, NORMAL_INK);
    private final Label textLabel = new Label();
    /// PCL2 `AniLoop` is ONE animation group: the three relative-angle
    /// RotateTransforms and the two spark paths are all driven by the same
    /// clock. Using a single Timeline keeps the sparks exactly in phase with
    /// the strike (the old code ran a separate 780 ms INDEFINITE Timeline
    /// against a 2400 ms swing, so sparks fired ~3x per swing).
    private final Timeline loop;
    private final Timeline errorTimeline;

    private State state = State.STOP;
    private String text = "加载中";
    private String textError = "加载失败";
    private boolean showProgress;
    private double progress;
    private boolean hasAnimation = true;
    private Runnable onRetry;
    private Color foreground = NORMAL_INK;

    public PCLLoading() {
        this("加载中");
    }

    public PCLLoading(String text) {
        getStyleClass().add("hpcl-loading");
        setAlignment(Pos.CENTER);
        setSpacing(6);
        this.text = text == null ? "" : text;

        iconBox.setMinSize(ICON_WIDTH, ICON_HEIGHT);
        iconBox.setPrefSize(ICON_WIDTH, ICON_HEIGHT);
        iconBox.setMaxSize(ICON_WIDTH, ICON_HEIGHT);

        pickaxe.setContent(PICKAXE_PATH);
        pickaxe.setFill(null);
        pickaxe.setStroke(NORMAL_INK);
        pickaxe.setStrokeLineCap(StrokeLineCap.ROUND);
        pickaxe.setStrokeLineJoin(StrokeLineJoin.ROUND);
        Bounds pb = pickaxe.getLayoutBounds();
        double pickaxeScale = PICKAXE_HEIGHT / Math.max(1, pb.getHeight());
        pickaxe.getTransforms().add(new Scale(pickaxeScale, pickaxeScale, 0, 0));
        pickaxe.setTranslateX(-pb.getMinX() * pickaxeScale);
        pickaxe.setTranslateY(-pb.getMinY() * pickaxeScale);
        pickaxe.setStrokeWidth(2 / pickaxeScale);

        Pane pickaxeGroup = new Pane(pickaxe);
        pickaxeGroup.setLayoutX(10);
        pickaxeGroup.setLayoutY(6);
        pickaxeGroup.setPrefSize(PICKAXE_HEIGHT, PICKAXE_HEIGHT);
        pickaxeRotate.setPivotX(20);
        pickaxeRotate.setPivotY(24);
        pickaxeGroup.getTransforms().add(pickaxeRotate);

        // PCL2 PathLeft/PathRight carry RotateTransform -45 / +45 deg with
        // RenderTransformOrigin 0.5,0.5, so the two wedges splay outwards.
        sparkLeft = createSpark(-45);
        sparkLeft.setLayoutX(7);
        sparkLeft.setLayoutY(41);
        sparkRight = createSpark(45);
        sparkRight.setLayoutX(14);
        sparkRight.setLayoutY(41);

        errorCross.setContent(ERROR_PATH);
        errorCross.setFill(ERROR_INK);
        Bounds eb = errorCross.getLayoutBounds();
        double es = 17.0 / Math.max(1, Math.max(eb.getWidth(), eb.getHeight()));
        errorCross.getTransforms().add(new Scale(es * 0.6, es * 0.6, 0, 0));
        errorCross.setTranslateX(4 - eb.getMinX() * es * 0.6);
        errorCross.setTranslateY(23 - eb.getMinY() * es * 0.6);
        errorCross.setOpacity(0);
        errorCross.setScaleX(0.6);
        errorCross.setScaleY(0.6);
        errorCross.setMouseTransparent(true);

        ground.setLayoutX(0);
        ground.setLayoutY(45);

        iconBox.getChildren().setAll(ground, sparkLeft, sparkRight, errorCross, pickaxeGroup);

        textLabel.getStyleClass().add("hpcl-loading-text");
        textLabel.setTextFill(NORMAL_INK);
        refreshText();

        setPadding(new Insets(10, 12, 12, 12));
        getChildren().setAll(iconBox, textLabel);

        loop = buildLoop();
        errorTimeline = buildErrorAnimation();

        setOnMouseClicked(e -> {
            if (state == State.ERROR && onRetry != null) onRetry.run();
        });
        // A loader parked on screen must not keep animating.
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) stop();
        });
    }

    private static SVGPath createSpark(double angle) {
        SVGPath spark = new SVGPath();
        spark.setContent(SPARK_PATH);
        spark.setFill(NORMAL_INK);
        Bounds b = spark.getLayoutBounds();
        double s = 5.0 / Math.max(1, b.getHeight());
        // PCL2 renders the wedge around its own centre (RenderTransformOrigin
        // 0.5,0.5) with a +/-45 deg RotateTransform, then scales it uniformly.
        // JavaFX applies transforms in order, so scale first, then rotate
        // about the wedge's centre in the scaled coordinate space.
        Scale scale = new Scale(s, s, 0, 0);
        Rotate rotate = new Rotate(angle, b.getWidth() * s / 2, b.getHeight() * s / 2);
        spark.getTransforms().addAll(scale, rotate);
        spark.setTranslateX(-b.getMinX() * s);
        spark.setTranslateY(-b.getMinY() * s);
        spark.setOpacity(0);
        spark.setMouseTransparent(true);
        return spark;
    }

    /// PCL2 `MyLoading.AniLoop`, verbatim timeline:
    ///   AaRotateTransform(PathPickaxe, -20-Angle, 350, 250, InBack/Weak)
    ///   AaRotateTransform(PathPickaxe, +50, 900, , OutFluent, After=True)
    ///   AaRotateTransform(PathPickaxe, +25, 900, , OutElastic/Weak, After=True)
    ///   AaCode(show sparks at margin 7/14,41)
    ///   AaOpacity(sparks, -1, 100, 50) ; AaX(-5/+5, 180, OutFluent) ; AaY(-6, 180)
    /// Starting angle is 55, so the sequence is 55 -> -20 -> 30 -> 55, with
    /// the sparks firing at the END of the chop (1500 ms = 250+350+900),
    /// i.e. when the pickaxe actually hits the ground line — not at the
    /// beginning of the swing. Everything shares ONE Timeline so phase is exact.
    private Timeline buildLoop() {
        int strikeAt = WINDUP_DELAY + WINDUP_TIME + CHOP_TIME;   // 1500 ms
        Timeline timeline = new Timeline(
                // --- pickaxe swing ---
                new KeyFrame(Duration.ZERO,
                        new KeyValue(pickaxeRotate.angleProperty(), REST_ANGLE, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(WINDUP_DELAY),
                        new KeyValue(pickaxeRotate.angleProperty(), REST_ANGLE, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(WINDUP_DELAY + WINDUP_TIME),
                        new KeyValue(pickaxeRotate.angleProperty(), -20, PCLTheme.IN_BACK_WEAK)),
                new KeyFrame(Duration.millis(strikeAt),
                        new KeyValue(pickaxeRotate.angleProperty(), 30, PCLTheme.OUT_MIDDLE)),
                new KeyFrame(Duration.millis(LOOP_TIME),
                        new KeyValue(pickaxeRotate.angleProperty(), REST_ANGLE, PCLTheme.ELASTIC_WEAK)),
                // --- sparks: hidden until the strike ---
                new KeyFrame(Duration.ZERO,
                        new KeyValue(sparkLeft.opacityProperty(), 0, Interpolator.LINEAR),
                        new KeyValue(sparkRight.opacityProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(strikeAt - 1), e -> hideSparks()),
                new KeyFrame(Duration.millis(strikeAt), e -> {
                    sparkLeft.setOpacity(1);
                    sparkRight.setOpacity(1);
                    sparkLeft.setTranslateX(0);
                    sparkRight.setTranslateX(0);
                    sparkLeft.setTranslateY(0);
                    sparkRight.setTranslateY(0);
                }),
                new KeyFrame(Duration.millis(strikeAt + 50),
                        new KeyValue(sparkLeft.opacityProperty(), 1, Interpolator.LINEAR),
                        new KeyValue(sparkRight.opacityProperty(), 1, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(strikeAt + 180),
                        new KeyValue(sparkLeft.opacityProperty(), 0, PCLTheme.OUT_MIDDLE),
                        new KeyValue(sparkRight.opacityProperty(), 0, PCLTheme.OUT_MIDDLE),
                        new KeyValue(sparkLeft.translateXProperty(), -5, PCLTheme.OUT_MIDDLE),
                        new KeyValue(sparkRight.translateXProperty(), 5, PCLTheme.OUT_MIDDLE),
                        new KeyValue(sparkLeft.translateYProperty(), -6, PCLTheme.OUT_MIDDLE),
                        new KeyValue(sparkRight.translateYProperty(), -6, PCLTheme.OUT_MIDDLE))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        return timeline;
    }

    /// PCL `ErrorAnimation`: cross fades in after 300ms (plus a 400ms wait if
    /// the swing has not landed yet) and pops with `AniEaseOutBack`.
    private Timeline buildErrorAnimation() {
        return new Timeline(
                new KeyFrame(Duration.millis(300),
                        new KeyValue(errorCross.opacityProperty(), 1, PCLTheme.OUT_WEAK)),
                new KeyFrame(Duration.millis(700),
                        new KeyValue(errorCross.scaleXProperty(), 1, PCLTheme.BACK_MIDDLE),
                        new KeyValue(errorCross.scaleYProperty(), 1, PCLTheme.BACK_MIDDLE))
        );
    }

    private void refreshText() {
        if (state == State.ERROR) {
            textLabel.setText(textError);
        } else if (showProgress) {
            textLabel.setText(text + " - " + (int) Math.floor(Math.max(0, Math.min(1, progress)) * 100) + "%");
        } else {
            textLabel.setText(text);
        }
    }

    private void applyInk(Color color) {
        this.foreground = color;
        pickaxe.setStroke(color);
        ground.setFill(color);
        sparkLeft.setFill(color);
        sparkRight.setFill(color);
        textLabel.setTextFill(color);
    }

    // ==== public API ====================================================

    public void setText(String text) {
        this.text = text == null ? "" : text;
        refreshText();
    }

    public String getText() {
        return text;
    }

    /// PCL `TextError`; the caption shown while [State] is ERROR.
    public void setTextError(String text) {
        this.textError = text == null ? "加载失败" : text;
        refreshText();
    }

    /// PCL `ShowProgress`: append "- NN%" to the caption.
    public void setShowProgress(boolean value) {
        if (showProgress == value) return;
        showProgress = value;
        refreshText();
    }

    public boolean isShowProgress() {
        return showProgress;
    }

    /// PCL `State.ProgressChanged`: redraws the caption when ShowProgress.
    public void setProgress(double value) {
        this.progress = value;
        refreshText();
    }

    public double getProgress() {
        return progress;
    }

    /// PCL `HasAnimation`: when false the pickaxe is parked at 55deg.
    public void setHasAnimation(boolean value) {
        if (hasAnimation == value) return;
        hasAnimation = value;
        if (!value && state == State.RUN) {
            loop.stop();
            pickaxeRotate.setAngle(REST_ANGLE);
            hideSparks();
        } else if (value && state == State.RUN) {
            startLoop();
        }
    }

    public boolean isHasAnimation() {
        return hasAnimation;
    }

    /// PCL `Click`: clicking the failed loader asks the owner to retry.
    public void setOnRetry(Runnable retry) {
        this.onRetry = retry;
    }

    public State getState() {
        return state;
    }

    /// Starts the swing loop (idempotent). Mirrors `LoadingState = Run`.
    public void start() {
        if (state == State.RUN) return;
        state = State.RUN;
        applyInk(NORMAL_INK);
        errorCross.setOpacity(0);
        errorCross.setScaleX(0.6);
        errorCross.setScaleY(0.6);
        errorTimeline.stop();
        pickaxeGroupVisible(true);
        refreshText();
        if (hasAnimation) startLoop();
        else park();
    }

    private void startLoop() {
        if (!AnimationUtils.isAnimationEnabled()) {
            park();
            return;
        }
        pickaxeRotate.setAngle(REST_ANGLE);
        loop.playFromStart();
    }

    private void park() {
        loop.stop();
        pickaxeRotate.setAngle(REST_ANGLE);
        hideSparks();
    }

    /// Stops the swing loop and parks the pickaxe at its resting angle.
    public void stop() {
        if (state == State.STOP) return;
        state = State.STOP;
        loop.stop();
        errorTimeline.stop();
        errorCross.setOpacity(0);
        applyInk(NORMAL_INK);
        park();
        refreshText();
    }

    /// PCL `LoadingState = Error`: cross + red foreground + error caption.
    public void setError(String message) {
        state = State.ERROR;
        if (message != null && !message.isEmpty()) textError = message;
        loop.stop();
        hideSparks();
        pickaxeRotate.setAngle(REST_ANGLE);
        applyInk(ERROR_INK);
        refreshText();
        errorCross.setOpacity(0);
        errorCross.setScaleX(0.6);
        errorCross.setScaleY(0.6);
        if (AnimationUtils.isAnimationEnabled()) errorTimeline.playFromStart();
        else {
            errorCross.setOpacity(1);
            errorCross.setScaleX(1);
            errorCross.setScaleY(1);
        }
    }

    private void hideSparks() {
        sparkLeft.setOpacity(0);
        sparkRight.setOpacity(0);
        sparkLeft.setTranslateX(0);
        sparkRight.setTranslateX(0);
        sparkLeft.setTranslateY(0);
        sparkRight.setTranslateY(0);
    }

    private void pickaxeGroupVisible(boolean visible) {
        pickaxe.setVisible(visible);
    }

    public boolean isRunning() {
        return state == State.RUN;
    }
}
