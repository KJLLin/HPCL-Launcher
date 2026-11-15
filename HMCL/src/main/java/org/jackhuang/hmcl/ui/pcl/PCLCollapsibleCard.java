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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// PCL2 `Controls/MyCard.vb` port of the `CanSwap` / `IsSwapped` behaviour.
///
/// A `MyCard` is a white 5px-radius panel with a bold 13px heading at (15,12)
/// and a chevron at the top-right. Clicking the heading toggles `IsSwapped`:
/// the card collapses to `SwapedHeight = 40` and the chevron rotates from
/// 180deg to 0 over 250ms with `AniEaseOutFluent(ExtraStrong)`.
///
/// `StartHeightAnimation` uses a 150ms OutFluent(ExtraStrong) ease for short
/// distances (<= 800px), otherwise a constant-velocity segment followed by a
/// deceleration segment. This port animates the body's `maxHeight` (which
/// drives the VBox's layout height) with an equivalent two-phase curve; the
/// body is clipped so its content does not overflow while collapsing.
///
/// PCL2 uses this heavily on `PageDownloadInstall.xaml.vb` (the Forge /
/// Fabric / OptiFine cards) and on `PageLaunchRight.xaml` for the log card.
@NotNullByDefault
public class PCLCollapsibleCard extends VBox {

    /// PCL `MyCard.SwapedHeight`.
    public static final double SWAPPED_HEIGHT = 40;
    /// PCL heading occupies (15,12) -> (..,28); the swap hit area is
    /// `SwapedHeight - 6 = 34`, so the whole heading row is clickable.
    private static final double HEADER_HEIGHT = 28;
    private static final Insets EXPANDED_PADDING = new Insets(12, 15, 13, 15);
    /// 12 (top) + 28 (heading) + 0 (bottom) = 40, exactly `SwapedHeight`.
    private static final Insets SWAPPED_PADDING = new Insets(12, 15, 0, 15);

    private final StackPane titleBar = new StackPane();
    private final HBox titleAccessories = new HBox(8);
    private final VBox body = new VBox(4);
    private final Node arrow;
    private final Label titleLabel;

    private boolean swapped;
    private boolean heightAnimating;
    private double naturalHeight = -1;
    private @Nullable Timeline heightAnimation;
    private @Nullable Timeline arrowAnimation;

    /// Builds an expanded, collapsible `MyCard`.
    ///
    /// @param title   the `MyCard.Title` heading.
    /// @param content the `SwapControl` children.
    public PCLCollapsibleCard(String title, Node... content) {
        getStyleClass().addAll("hpcl-flow-card", "hpcl-collapsible-card");
        setMaxWidth(Double.MAX_VALUE);
        setPadding(EXPANDED_PADDING);
        setSpacing(0);

        titleLabel = PCLTheme.createCardTitle(title);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(titleLabel, Pos.CENTER_LEFT);

        arrow = SVG.ARROW_DROP_DOWN.createIcon(10);
        arrow.getStyleClass().add("hpcl-collapsible-arrow");
        // PCL `MainSwap` starts at RotateTransform(180) for an expanded card.
        arrow.setRotate(180);
        arrow.setMouseTransparent(true);
        StackPane arrowHost = new StackPane(arrow);
        titleAccessories.setAlignment(Pos.CENTER_RIGHT);
        titleAccessories.getChildren().setAll(arrowHost);
        StackPane.setAlignment(titleAccessories, Pos.CENTER_RIGHT);

        titleBar.getChildren().setAll(titleLabel, titleAccessories);
        titleBar.setMinHeight(HEADER_HEIGHT);
        titleBar.setPrefHeight(HEADER_HEIGHT);
        titleBar.setMaxHeight(HEADER_HEIGHT);
        titleBar.getStyleClass().add("hpcl-collapsible-title-bar");
        titleBar.setCursor(Cursor.HAND);
        // PCL checks `Mouse.GetPosition(Me).Y <= SwapedHeight - 6`; the whole
        // header row (28px) is inside that threshold.
        titleBar.setOnMouseClicked(e -> setSwapped(!swapped));

        body.getStyleClass().add("hpcl-flow-card-body");
        body.getChildren().setAll(content);
        body.setMinHeight(0);
        body.setMaxHeight(Double.MAX_VALUE);
        // PCL collapses SwapControl to Visibility.Collapsed after the height
        // animation. Clip the body so content never paints outside the card.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(body.widthProperty());
        clip.heightProperty().bind(body.heightProperty());
        body.setClip(clip);
        body.heightProperty().addListener((obs, oldValue, newValue) -> {
            if (!heightAnimating && !swapped && newValue.doubleValue() > 0) {
                naturalHeight = newValue.doubleValue();
            }
        });

        getChildren().setAll(titleBar, body);
    }

    /// The card body, so callers can append live content (e.g. launch logs).
    public VBox getBody() {
        return body;
    }

    /// The heading label, for callers that add a header action.
    public Label getTitleLabel() {
        return titleLabel;
    }

    /// Puts a small control (e.g. PCL's tip-refresh `MyIconButton`) to the
    /// left of the chevron. Its clicks are consumed so pressing it does not
    /// also toggle the card, matching PCL's `PanClick` separation.
    public void setTitleAccessory(Node node) {
        node.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> event.consume());
        Node arrowHost = titleAccessories.getChildren().isEmpty()
                ? null : titleAccessories.getChildren().get(titleAccessories.getChildren().size() - 1);
        if (arrowHost == null) titleAccessories.getChildren().setAll(node);
        else titleAccessories.getChildren().setAll(node, arrowHost);
    }

    /// PCL `MyCard.IsSwapped`.
    public boolean isSwapped() {
        return swapped;
    }

    /// Sets the collapsed state with PCL's height + chevron animation.
    public void setSwapped(boolean value) {
        setSwapped(value, true);
    }

    /// Sets the collapsed state. `animate = false` is PCL's `Loaded` path,
    /// which applies the collapsed height without a height animation.
    public void setSwapped(boolean value, boolean animate) {
        if (swapped == value) return;
        swapped = value;
        if (!value) {
            body.setVisible(true);
            body.setManaged(true);
            setPadding(EXPANDED_PADDING);
        } else {
            setPadding(SWAPPED_PADDING);
        }
        ensureNaturalHeight();
        animateHeight(value, animate);
        animateArrow(value, animate);
    }

    private void ensureNaturalHeight() {
        if (naturalHeight <= 0) {
            double h = body.getHeight();
            if (h <= 0) h = body.prefHeight(body.getWidth() > 0 ? body.getWidth() : 400);
            naturalHeight = Math.max(h, 20);
        }
    }

    private void animateHeight(boolean collapse, boolean animate) {
        if (heightAnimation != null) heightAnimation.stop();
        double from = collapse ? naturalHeight : 0;
        double to = collapse ? 0 : naturalHeight;
        body.setMaxHeight(from);
        if (!animate || !AnimationUtils.isAnimationEnabled()) {
            body.setMaxHeight(to);
            finishHeight(collapse);
            return;
        }
        double distance = Math.abs(to - from);
        heightAnimating = true;
        Timeline timeline;
        if (distance <= 800) {
            // PCL: AaHeight(Delta, 150, , AniEaseOutFluent(ExtraStrong)).
            timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(body.maxHeightProperty(), from, PCLTheme.OUT_EXTRA_STRONG)),
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(body.maxHeightProperty(), to, PCLTheme.OUT_EXTRA_STRONG)));
        } else {
            // PCL: a constant-velocity segment then a deceleration segment.
            double mid = from + (to - from) * 0.6;
            timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(body.maxHeightProperty(), from, javafx.animation.Interpolator.LINEAR)),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(body.maxHeightProperty(), mid, javafx.animation.Interpolator.LINEAR)),
                    new KeyFrame(Duration.millis(300),
                            new KeyValue(body.maxHeightProperty(), to, PCLTheme.OUT_EXTRA_STRONG)));
        }
        timeline.setOnFinished(e -> finishHeight(collapse));
        heightAnimation = timeline;
        timeline.play();
    }

    private void finishHeight(boolean collapse) {
        heightAnimating = false;
        if (collapse) {
            // PCL: SwapControl.Visibility = Collapsed once the height is 40.
            body.setVisible(false);
            body.setManaged(false);
        } else {
            body.setMaxHeight(Double.MAX_VALUE);
        }
    }

    private void animateArrow(boolean collapse, boolean animate) {
        if (arrowAnimation != null) arrowAnimation.stop();
        double target = collapse ? 0 : 180;
        if (!animate || !AnimationUtils.isAnimationEnabled()) {
            arrow.setRotate(target);
            return;
        }
        // PCL: AaRotateTransform(..., 250, , AniEaseOutFluent(ExtraStrong)).
        arrowAnimation = new Timeline(new KeyFrame(Duration.millis(250),
                new KeyValue(arrow.rotateProperty(), target, PCLTheme.OUT_EXTRA_STRONG)));
        arrowAnimation.play();
    }
}
