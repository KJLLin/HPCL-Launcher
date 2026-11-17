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
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/// PCL2 `Controls/MyExtraButton.xaml` + `.xaml.vb` port: a 40px floating disc
/// anchored in the bottom-right `StackPanel` of `FormMain.xaml`.
///
/// PCL2 declares five of them (`BtnExtraBack`, `BtnExtraDownload`,
/// `BtnExtraApril`, `BtnExtraShutdown`, `BtnExtraMusic`), all collapsed by
/// default. Each carries a `ShowCheck` delegate and is revealed by
/// `ShowRefresh()` whenever the relevant state changes (scroll position,
/// running tasks, launched game, ...). This class is the generic building
/// block for the additional balls; `PCLDownloadBall` remains the specialised
/// task-progress variant.
///
/// Reproduced behaviour:
/// - `PanColor` = ColorBrush3 disc with a 10px / 0.2 drop shadow, glyph in
///   ColorBrush8 (`Controls_MyExtraButton.xaml`).
/// - `Show`: scale 0 -> 1 over 500ms with `AniEaseOutBack(Weak)` after a 60ms
///   delay, opacity in 200ms; hide scales to 0 in 100ms `AniEaseInFluent` and
///   collapses after 220ms. `IsHitTestVisible` flips immediately.
/// - Press: `PanScale` shrinks to 0.85 over 800ms `AniEaseOutFluent(Strong)`,
///   release springs back to 1 in 300ms `AniEaseOutBack`, leave in 500ms.
/// - Hover colour: ColorBrush3 -> ColorBrush4 in 120ms, back in 150ms.
/// - `Ribble()`: a translucent ring expands ~13x over 1000ms and fades out.
@NotNullByDefault
public class PCLExtraButton extends StackPane {

    /// PCL2 `MyExtraButton Width="40"`; `PanScale Margin="0,10,0,0"` only
    /// offsets the visual inside the 50px animated slot.
    private static final double SIZE = 40;
    private static final Color C3 = Color.web("#1370F3");
    private static final Color C4 = Color.web("#4890F5");
    private static final Color C8 = Color.web("#EAF2FE");

    private final StackPane panScale = new StackPane();
    private final StackPane panClick = new StackPane();
    private final Circle disc = new Circle(SIZE / 2, SIZE / 2, SIZE / 2);
    private final DoubleProperty hoverMix = new SimpleDoubleProperty(0);

    private boolean showing;
    private boolean ribbleOnShow;
    private @Nullable Supplier<Boolean> showCheck;
    private @Nullable Runnable onClick;
    private @Nullable Timeline showTimeline;
    private @Nullable Timeline pressTimeline;
    /// Reused for every hover transition so rapid pointer moves do not stack
    /// several Timelines writing the same hoverMix property.
    private @Nullable Timeline hoverTimeline;

    /// Builds one extra button.
    ///
    /// @param svgPath  a 24x24 SVG path string (PCL2 `Logo` geometry).
    /// @param logoScale PCL2 `LogoScale` applied to the glyph.
    /// @param tooltip  PCL2 `ToolTip`, e.g. "返回顶部".
    /// @param onClick  PCL2 `Click` event handler.
    public PCLExtraButton(String svgPath, double logoScale, @Nullable String tooltip,
                          @Nullable Runnable onClick) {
        this.onClick = onClick;
        getStyleClass().add("hpcl-extra-button");
        setPrefSize(SIZE, SIZE);
        setMinSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        setPickOnBounds(false);
        // `IsHitTestVisible = False` while hidden: a shrinking ball must never
        // intercept a click aimed at the page underneath.
        setMouseTransparent(true);

        disc.fillProperty().bind(Bindings.createObjectBinding(
                () -> C3.interpolate(C4, hoverMix.get()), hoverMix));
        disc.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(52, 61, 74, 0.2),
                10, 0, 0, 0));
        disc.getStyleClass().add("hpcl-extra-button-disc");

        SVGPath glyph = new SVGPath();
        glyph.setContent(svgPath);
        glyph.setFill(C8);
        glyph.setScaleX(logoScale);
        glyph.setScaleY(logoScale);
        glyph.getStyleClass().add("hpcl-extra-button-glyph");
        glyph.setMouseTransparent(true);
        // PCL2 `Path Margin="12"` inside a 40px ball -> 16x16 glyph. The
        // source paths use a 24x24 grid, so scale down to fit.
        glyph.setScaleX(glyph.getScaleX() * (16.0 / 24.0));
        glyph.setScaleY(glyph.getScaleY() * (16.0 / 24.0));

        panScale.getChildren().setAll(disc, glyph);
        panScale.setPrefSize(SIZE, SIZE);
        panScale.setMinSize(SIZE, SIZE);
        panScale.setMaxSize(SIZE, SIZE);
        panScale.setScaleX(0);
        panScale.setScaleY(0);
        panScale.setMouseTransparent(true);

        // `PanClick` is the round hit target. PCL2 gives it a fully
        // transparent semi-transparent brush and keeps it on top of the
        // layout; a 40px square is visually indistinguishable from the disc.
        panClick.setPrefSize(SIZE, SIZE);
        panClick.setMinSize(SIZE, SIZE);
        panClick.setMaxSize(SIZE, SIZE);
        panClick.setPickOnBounds(true);
        panClick.getStyleClass().add("hpcl-extra-button-click");

        getChildren().setAll(panScale, panClick);
        setVisible(false);
        setManaged(false);
        setOpacity(0);

        if (tooltip != null && !tooltip.isEmpty()) {
            Tooltip.install(panClick, new Tooltip(tooltip));
        }
        installInteraction();
    }

    /// PCL2 `ShowCheck`: a delegate queried by `ShowRefresh()`.
    public void setShowCheck(@Nullable Supplier<Boolean> check) {
        this.showCheck = check;
        showRefresh();
    }

    /// PCL2 `Ribble()` fires once when a ball appears to draw attention
    /// (`Modules_ModMain.vb:1038`, `PageInstanceOverall.xaml.vb:271`).
    /// Off by default: a scroll-driven ball must not ripple on every tick.
    public void setRibbleOnShow(boolean value) {
        this.ribbleOnShow = value;
    }

    /// Binds `ShowRefresh()` to an observable (e.g. `PCLScrollViewer`).
    public void bindShowCheck(ObservableValue<? extends Boolean> observable) {
        observable.addListener((obs, oldValue, newValue) -> showRefresh());
    }

    /// PCL2 `ShowRefresh()`: re-evaluates `ShowCheck` and animates the change.
    public void showRefresh() {
        Supplier<Boolean> check = showCheck;
        setShow(check != null && Boolean.TRUE.equals(check.get()));
    }

    /// Directly sets the PCL2 `Show` property (used by verification hooks).
    public void setShow(boolean value) {
        if (showing == value) return;
        boolean appearing = value;
        showing = value;
        if (showTimeline != null) showTimeline.stop();

        if (appearing && ribbleOnShow && AnimationUtils.isAnimationEnabled()) {
            PCLTheme.playRibble(panScale, SIZE / 2);
        }

        if (value) {
            setMouseTransparent(false);
            setManaged(true);
            setVisible(true);
            if (!AnimationUtils.isAnimationEnabled()) {
                setOpacity(1);
                panScale.setScaleX(1);
                panScale.setScaleY(1);
                return;
            }
            panScale.setScaleX(0);
            panScale.setScaleY(0);
            // PCL2 runs two scale animations in parallel (0->0.3 OutFluent and
            // 0->0.7 OutBack) plus a 200ms height grow. One OutBack to 1 is the
            // exact sum of the two curves at every point.
            showTimeline = new Timeline(
                    new KeyFrame(Duration.millis(60),
                            new KeyValue(panScale.scaleXProperty(), 0, PCLTheme.OUT_WEAK),
                            new KeyValue(panScale.scaleYProperty(), 0, PCLTheme.OUT_WEAK),
                            new KeyValue(opacityProperty(), 0, PCLTheme.OUT_WEAK)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(opacityProperty(), 1, PCLTheme.OUT_WEAK)),
                    new KeyFrame(Duration.millis(560),
                            new KeyValue(panScale.scaleXProperty(), 1, PCLTheme.BACK_WEAK),
                            new KeyValue(panScale.scaleYProperty(), 1, PCLTheme.BACK_WEAK)));
            showTimeline.play();
        } else {
            setMouseTransparent(true);
            if (!AnimationUtils.isAnimationEnabled()) {
                setVisible(false);
                setManaged(false);
                setOpacity(0);
                panScale.setScaleX(0);
                panScale.setScaleY(0);
                return;
            }
            showTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(panScale.scaleXProperty(), panScale.getScaleX(), PCLTheme.IN_WEAK),
                            new KeyValue(panScale.scaleYProperty(), panScale.getScaleY(), PCLTheme.IN_WEAK),
                            new KeyValue(opacityProperty(), getOpacity(), PCLTheme.IN_WEAK)),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(panScale.scaleXProperty(), 0, PCLTheme.IN_WEAK),
                            new KeyValue(panScale.scaleYProperty(), 0, PCLTheme.IN_WEAK)),
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

    /// PCL2 `Ribble()`: a soft expanding ring used to draw attention to a ball.
    /// Delegates to [PCLTheme#playRibble], which centres the ring in a
    /// StackPane; the local `Circle(radius, fill)` version was off-centre
    /// (its centre defaults to (0,0), not the 20,20 disc centre) and was
    /// never called. PCL2 fires it after `LoaderTaskbarAdd` and on the April
    /// Fools ball; HPCL fires it from [PCLDownloadBall] when a task arrives.
    public void ribble() {
        if (!isVisible()) return;
        PCLTheme.playRibble(panScale, SIZE / 2);
    }

    private void installInteraction() {
        panClick.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (pressTimeline != null) pressTimeline.stop();
            double target = 0.85;
            if (!AnimationUtils.isAnimationEnabled()) {
                panScale.setScaleX(target);
                panScale.setScaleY(target);
                return;
            }
            pressTimeline = new Timeline(new KeyFrame(Duration.millis(800),
                    new KeyValue(panScale.scaleXProperty(), target, PCLTheme.OUT_STRONG),
                    new KeyValue(panScale.scaleYProperty(), target, PCLTheme.OUT_STRONG)));
            pressTimeline.play();
        });
        panClick.setOnMouseReleased(e -> {
            if (pressTimeline != null) pressTimeline.stop();
            springBack(300, PCLTheme.BACK_WEAK);
        });
        panClick.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onClick != null) onClick.run();
        });
        panClick.setOnMouseEntered(e -> animateHover(1, 120));
        panClick.setOnMouseExited(e -> {
            if (pressTimeline != null) pressTimeline.stop();
            springBack(500, PCLTheme.OUT_WEAK);
            animateHover(0, 150);
        });
    }

    private void springBack(int millis, javafx.animation.Interpolator interpolator) {
        if (!AnimationUtils.isAnimationEnabled()) {
            panScale.setScaleX(1);
            panScale.setScaleY(1);
            return;
        }
        pressTimeline = new Timeline(new KeyFrame(Duration.millis(millis),
                new KeyValue(panScale.scaleXProperty(), 1, interpolator),
                new KeyValue(panScale.scaleYProperty(), 1, interpolator)));
        pressTimeline.play();
    }

    private void animateHover(double target, int millis) {
        if (!AnimationUtils.isAnimationEnabled()) {
            hoverMix.set(target);
            return;
        }
        if (hoverTimeline != null) hoverTimeline.stop();
        hoverTimeline = new Timeline(new KeyFrame(Duration.millis(millis),
                new KeyValue(hoverMix, target, javafx.animation.Interpolator.LINEAR)));
        hoverTimeline.play();
    }

    /// Stops running animations when the window closes.
    public void dispose() {
        if (showTimeline != null) showTimeline.stop();
        if (pressTimeline != null) pressTimeline.stop();
        if (hoverTimeline != null) hoverTimeline.stop();
    }
}
