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
import javafx.animation.Transition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
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
    /// PCL2 `AniLoop`: the wind-up ends at 600 ms, then the +50 (OutFluent)
    /// and +25 (OutElastic) relative rotations run in parallel for 900 ms.
    /// The sparks fire at the START of the down-swing (600 ms), and the loop
    /// restarts at 1500 ms — not 2400 ms as an earlier HPCL version assumed.
    private static final int STRIKE_AT = WINDUP_DELAY + WINDUP_TIME;
    private static final int LOOP_TIME = STRIKE_AT + CHOP_TIME;

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
    /// PCL2 `AniLoop` is ONE animation group: the relative-angle rotations
    /// and the two spark paths are all driven by the same clock. A single
    /// Transition keeps sparks exactly in phase with the strike and avoids
    /// allocating a Timeline per frame.
    private final Transition loop;
    private final Timeline errorTimeline;
    private final Timeline recoverTimeline;
    /// PCL2 `ErrorAnimation` fades ColorBrush3 -> ColorBrushRedLight over
    /// 300 ms instead of swapping the colour instantly.
    private final DoubleProperty errorMix = new SimpleDoubleProperty(0);

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
        recoverTimeline = buildRecoverAnimation();
        errorMix.addListener((obs, old, value) ->
                applyInk(NORMAL_INK.interpolate(ERROR_INK, Math.max(0, Math.min(1, value.doubleValue())))));

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

    /// PCL2 `MyLoading.AniLoop`, reproduced from
    /// `Controls_MyLoading.xaml.vb:182-205`:
    ///   AaRotateTransform(-20 - Angle, 350, 250, InBack/Weak)  -> 55 to -20
    ///   AaRotateTransform(+50, 900, After=True, OutFluent)      -> -20 to 30
    ///   AaRotateTransform(+25, 900, OutElastic/Weak)            -> +25 layered
    ///   AaCode(sparks at margin 7/14,41) at 600 ms
    ///   AaOpacity(-1, 100, 50) / AaX(±5, 180) / AaY(-6, 180)
    ///   AaCode(restart) at 1500 ms
    /// A single Transition reproduces the *sum* of the two parallel relative
    /// rotations per frame; separate KeyFrames cannot express that overlay.
    private Transition buildLoop() {
        return new SwingTransition();
    }

    private final class SwingTransition extends Transition {
        SwingTransition() {
            setCycleDuration(Duration.millis(LOOP_TIME));
            setInterpolator(Interpolator.LINEAR);
            setCycleCount(Animation.INDEFINITE);
        }

        @Override
        protected void interpolate(double fraction) {
            double t = fraction * LOOP_TIME;
            double angle;
            if (t < WINDUP_DELAY) {
                angle = REST_ANGLE;
            } else if (t < STRIKE_AT) {
                double p = (t - WINDUP_DELAY) / WINDUP_TIME;
                angle = REST_ANGLE + (-20 - REST_ANGLE)
                        * PCLTheme.IN_BACK_WEAK.interpolate(0, 1, p);
            } else {
                double p = Math.min(1, (t - STRIKE_AT) / CHOP_TIME);
                angle = -20
                        + 50 * PCLTheme.OUT_MIDDLE.interpolate(0, 1, p)
                        + 25 * PCLTheme.ELASTIC_WEAK.interpolate(0, 1, p);
            }
            pickaxeRotate.setAngle(angle);
            updateSparks(t);
        }
    }

    /// Sparks appear at the start of the down-swing (600 ms), hold 50 ms,
    /// fade over 100 ms and drift ±5 / -6 px over 180 ms with OutFluent.
    private void updateSparks(double t) {
        if (t < STRIKE_AT) {
            hideSparks();
            return;
        }
        double st = t - STRIKE_AT;
        double opacity = st <= 50 ? 1 : 1 - clamp01((st - 50) / 100);
        sparkLeft.setOpacity(opacity);
        sparkRight.setOpacity(opacity);
        double move = PCLTheme.OUT_MIDDLE.interpolate(0, 1, clamp01(st / 180));
        sparkLeft.setTranslateX(-5 * move);
        sparkRight.setTranslateX(5 * move);
        sparkLeft.setTranslateY(-6 * move);
        sparkRight.setTranslateY(-6 * move);
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    /// PCL `ErrorAnimation`: waits 400 ms if the pickaxe has not landed yet,
    /// then animates ColorBrush3 -> ColorBrushRedLight over 300 ms, fades the
    /// cross in over 100 ms and pops it with OutBack over 400 ms.
    private Timeline buildErrorAnimation() {
        return new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(errorMix, 0, Interpolator.LINEAR),
                        new KeyValue(errorCross.opacityProperty(), 0, Interpolator.LINEAR),
                        new KeyValue(errorCross.scaleXProperty(), 0.6, Interpolator.LINEAR),
                        new KeyValue(errorCross.scaleYProperty(), 0.6, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(400),
                        new KeyValue(errorMix, 0, Interpolator.LINEAR),
                        new KeyValue(errorCross.opacityProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(500),
                        new KeyValue(errorCross.opacityProperty(), 1, PCLTheme.OUT_WEAK)),
                new KeyFrame(Duration.millis(700),
                        new KeyValue(errorMix, 1, PCLTheme.OUT_WEAK)),
                new KeyFrame(Duration.millis(800),
                        new KeyValue(errorCross.scaleXProperty(), 1, PCLTheme.BACK_MIDDLE),
                        new KeyValue(errorCross.scaleYProperty(), 1, PCLTheme.BACK_MIDDLE))
        );
    }

    /// PCL `ErrorAnimation(isError:=False)`: fade the cross out, shrink it
    /// back to 0.5 and restore ColorBrush3.
    private Timeline buildRecoverAnimation() {
        return new Timeline(
                new KeyFrame(Duration.millis(100),
                        new KeyValue(errorCross.opacityProperty(), 0, PCLTheme.OUT_WEAK)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(errorCross.scaleXProperty(), 0.6, PCLTheme.OUT_MIDDLE),
                        new KeyValue(errorCross.scaleYProperty(), 0.6, PCLTheme.OUT_MIDDLE)),
                new KeyFrame(Duration.millis(300),
                        new KeyValue(errorMix, 0, PCLTheme.OUT_WEAK))
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
        boolean wasError = state == State.ERROR;
        state = State.RUN;
        errorTimeline.stop();
        if (wasError && AnimationUtils.isAnimationEnabled()) {
            // PCL2 plays a recovery animation instead of snapping back.
            recoverTimeline.playFromStart();
        } else {
            recoverTimeline.stop();
            errorMix.set(0);
            applyInk(NORMAL_INK);
        }
        errorCross.setOpacity(0);
        errorCross.setScaleX(0.6);
        errorCross.setScaleY(0.6);
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
        recoverTimeline.stop();
        errorMix.set(0);
        errorCross.setOpacity(0);
        applyInk(NORMAL_INK);
        park();
        refreshText();
    }

    /// PCL `LoadingState = Error`: cross + red foreground + error caption,
    /// with the 400 ms wait and 300 ms colour fade from the VB source.
    public void setError(String message) {
        state = State.ERROR;
        if (message != null && !message.isEmpty()) textError = message;
        loop.stop();
        recoverTimeline.stop();
        hideSparks();
        pickaxeRotate.setAngle(REST_ANGLE);
        refreshText();
        errorCross.setOpacity(0);
        errorCross.setScaleX(0.6);
        errorCross.setScaleY(0.6);
        if (AnimationUtils.isAnimationEnabled()) {
            errorMix.set(0);
            errorTimeline.playFromStart();
        } else {
            errorMix.set(1);
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
