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
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;

/// PCL2 `MyPageRight.PageLoaderInit` / `PageDownloadInstall.PanLoad`.
///
/// PCL2 shows a centred white `MyCard` with a `MyLoading` pickaxe whenever a
/// right-hand page has a background loader. It waits 400ms before entering,
/// holds at least 400ms, then fades out while the content enters. The install
/// page uses the same card with `ShowProgress=True` ("正在加载 - NN%").
///
/// This is a reusable HPCL counterpart: [show] / [hide] play PCL's entrance
/// and exit, [setProgress] drives the caption, and [showError] keeps the card
/// on screen with the retry affordance (`MyLoading` click to reload).
public final class PCLPageLoader extends StackPane {

    private final VBox card = new VBox();
    private final PCLLoading loading;
    private Timeline current;
    private Runnable onRetry;

    public PCLPageLoader(String text, boolean showProgress) {
        getStyleClass().add("hpcl-page-loader");
        setAlignment(Pos.CENTER);
        setPickOnBounds(false);
        // As a child of a StackPane this layer is stretched to the whole pane;
        // that is fine (it is transparent), but keep a reference so the card
        // itself is never stretched: it is fixed pref-size and centred.
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        loading = new PCLLoading(text);
        loading.setShowProgress(showProgress);
        loading.setOnRetry(() -> {
            if (onRetry != null) onRetry.run();
        });

        // PCL `PanLoad`: a centred MyCard with the loader inset 20/20/20/17.
        card.getStyleClass().addAll("hpcl-flow-card", "hpcl-page-loader-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20, 20, 17, 20));
        card.getChildren().setAll(loading);
        // Prevent StackPane from stretching the white card across the pane.
        card.setMinWidth(220);
        card.setPrefWidth(240);
        card.setMaxWidth(280);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.CENTER);

        getChildren().setAll(card);
        setVisible(false);
        setManaged(false);
        setOpacity(0);
    }

    public void setOnRetry(Runnable retry) {
        this.onRetry = retry;
    }

    public void setText(String text) {
        loading.setText(text);
    }

    public void setProgress(double value) {
        loading.setProgress(value);
    }

    public PCLLoading getLoading() {
        return loading;
    }

    /// PCL `PageLoaderState` -> LoaderEnter: card fades in and slides down
    /// from -16px, pickaxe starts swinging.
    public void show() {
        if (current != null) current.stop();
        setManaged(true);
        setVisible(true);
        loading.start();
        if (!AnimationUtils.isAnimationEnabled()) {
            setOpacity(1);
            card.setTranslateY(0);
            return;
        }
        card.setTranslateY(-16);
        current = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(opacityProperty(), 0, PCLTheme.OUT_WEAK),
                        new KeyValue(card.translateYProperty(), -16, PCLTheme.OUT_MIDDLE)),
                new KeyFrame(Duration.millis(100),
                        new KeyValue(opacityProperty(), 1, PCLTheme.OUT_WEAK)),
                new KeyFrame(Duration.millis(250),
                        new KeyValue(card.translateYProperty(), -11, PCLTheme.OUT_MIDDLE)),
                new KeyFrame(Duration.millis(350),
                        new KeyValue(card.translateYProperty(), 0, PCLTheme.BACK_MIDDLE)));
        current.play();
    }

    /// PCL `TriggerExitAnimation`: 70ms fade, -6px slide, then the content
    /// container enters. `onFinished` is where the owner reveals its content.
    public void hide(Runnable onFinished) {
        if (current != null) current.stop();
        if (!AnimationUtils.isAnimationEnabled()) {
            finish(onFinished);
            return;
        }
        current = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(opacityProperty(), 1, PCLTheme.IN_WEAK),
                        new KeyValue(card.translateYProperty(), 0, PCLTheme.IN_WEAK)),
                new KeyFrame(Duration.millis(70),
                        new KeyValue(opacityProperty(), 0, PCLTheme.IN_WEAK),
                        new KeyValue(card.translateYProperty(), -6, PCLTheme.IN_WEAK)));
        current.setOnFinished(e -> finish(onFinished));
        current.play();
    }

    private void finish(Runnable onFinished) {
        setVisible(false);
        setManaged(false);
        setOpacity(0);
        loading.stop();
        if (onFinished != null) onFinished.run();
    }

    /// PCL `MyLoading` error state: keep the card and show a clickable cross.
    public void showError(String message, Runnable retry) {
        onRetry = retry;
        loading.setError(message);
    }

    /// Plays PCL's staggered entrance on a freshly built content container.
    public static void reveal(Node content) {
        if (content == null) return;
        if (content instanceof javafx.scene.layout.Pane pane) {
            PCLTheme.playEntrance(new java.util.ArrayList<>(pane.getChildren()));
        } else {
            PCLTheme.playEntrance(content);
        }
    }
}
