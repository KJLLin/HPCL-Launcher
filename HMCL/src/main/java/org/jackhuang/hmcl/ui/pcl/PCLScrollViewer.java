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
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/// PCL2 `Controls/MyScrollViewer.vb` port.
///
/// WPF's ScrollViewer jumps a fixed number of lines per wheel notch. PCL2
/// replaces that with `PerformVerticalOffsetDelta(-e.Delta)`: it accumulates a
/// `RealOffset`, clamps it to `[0, ExtentHeight - ViewportHeight]`, and eases
/// toward it over 300ms with `AniEaseOutFluent(6)`. Rapid wheel input extends
/// the target instead of stacking independent animations, which is why PCL2
/// feels "attached" to the wheel rather than floating with friction.
///
/// PCL2 also calls `FrmMain.BtnExtraBack.ShowRefresh()` from `ScrollChanged`
/// and `IsVisibleChanged`. This class mirrors that with a global
/// [PCLScrollViewer#scrolledProperty] driven by the currently active pane, so
/// the "back to top" extra button can appear exactly when PCL2 shows it.
///
/// HMCL previously used `FXUtils.smoothScrolling` (MaterialFX's multi-order
/// friction model). That model is a velocity simulation and behaves quite
/// differently: it keeps gliding after the wheel stops and is hard to stop at
/// a precise position. PCL2's target-based easing is used instead.
@NotNullByDefault
public final class PCLScrollViewer {

    /// WPF reports 120 logical units per wheel notch; JavaFX on X11 reports
    /// roughly 40 pixels. Scale so one notch moves ~120px like PCL2.
    private static final double WHEEL_TO_PCL = 3.0;
    /// `AniEaseOutFluent(6)` over 300ms (`PerformVerticalOffsetDelta`).
    private static final int EASE_MS = 300;
    /// PCL2 shows `BtnExtraBack` as soon as the page is scrolled away from the
    /// top; a small threshold avoids flicker from sub-pixel layout noise.
    private static final double SHOW_THRESHOLD = 48;
    /// Style marker so [PCLScrollViewer#activate] can find a page's scroll pane.
    private static final String MARK = "hpcl-scroll-viewer";
    /// The page's main card flow. A page may contain nested scroll panes
    /// (list views, dialog text), so activation prefers this one.
    private static final String PRIMARY_MARK = "hpcl-scroll-viewer-primary";
    private static final String CONTROLLER_KEY = "hpcl.scroll.controller";
    /// Opt-in diagnostics for headless verification runs.
    private static final boolean DEBUG = Boolean.getBoolean("hpcl.scroll.debug");

    private static final BooleanProperty scrolled = new SimpleBooleanProperty(false);
    private static @Nullable ScrollPane active = null;
    private static @Nullable Node pendingPage;
    private static final List<WeakReference<ScrollPane>> installed = new ArrayList<>();

    private PCLScrollViewer() {
    }

    /// Whether the active PCL2-style scroll pane is scrolled away from the top.
    public static ReadOnlyBooleanProperty scrolledProperty() {
        return scrolled;
    }

    /// Whether the active pane is scrolled far enough to show "back to top".
    public static boolean isScrolled() {
        return scrolled.get();
    }

    /// Installs PCL2's wheel easing on [pane]. Idempotent: a pane is only ever
    /// wrapped once. Replaces `FXUtils.smoothScrolling` for PCL card flows.
    public static void install(ScrollPane pane) {
        if (pane.getProperties().containsKey(CONTROLLER_KEY)) return;
        if (!pane.getStyleClass().contains(MARK)) pane.getStyleClass().add(MARK);
        Controller controller = new Controller(pane);
        pane.getProperties().put(CONTROLLER_KEY, controller);
        installed.add(new WeakReference<>(pane));
        if (DEBUG) System.err.println("[PCLScroll] install " + pane.getStyleClass());

        // `ScrollChanged` keeps RealOffset in sync when the user drags the
        // scrollbar or code calls setVvalue directly.
        pane.vvalueProperty().addListener((obs, oldValue, newValue) -> controller.onExternalVvalue());
        pane.vvalueProperty().addListener((obs, oldValue, newValue) -> {
            if (DEBUG) System.err.println("[PCLScroll] vvalue " + oldValue + " -> " + newValue);
        });

        // PCL2 `PreviewMouseWheel` is a tunnel event handled before the
        // built-in ScrollViewer. In JavaFX a wheel event can be consumed by
        // another global handler before it reaches a filter on the ScrollPane
        // or its content (whose parent is the skin's viewport). Register the
        // filter on the Scene, which is always first in the capture chain,
        // and route it to the currently active pane by walking the target's
        // parent chain.
        pane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            Object marker = scene.getProperties().get("hpcl.scroll.wheel");
            if (marker != null) return;
            scene.getProperties().put("hpcl.scroll.wheel", Boolean.TRUE);
            // One global filter per Scene; it resolves the owning pane from
            // the event target, so every installed page keeps working.
            scene.addEventFilter(ScrollEvent.SCROLL, PCLScrollViewer::sceneWheel);
        });
    }

    /// Routes a wheel event to the active (or event-owning) controller.
    /// PCL2 only scrolls the currently visible `MyScrollViewer`.
    private static void sceneWheel(ScrollEvent event) {
        if (DEBUG) {
            StringBuilder chain = new StringBuilder();
            Node n = event.getTarget() instanceof Node t ? t : null;
            while (n != null && chain.length() < 400) {
                chain.append(n.getClass().getSimpleName()).append('(')
                        .append(String.join(".", n.getStyleClass())).append(")>");
                n = n.getParent();
            }
            System.err.println("[PCLScroll] sceneWheel dy=" + event.getDeltaY()
                    + " active=" + (active != null)
                    + " belongs=" + (active != null && belongsTo(active, event))
                    + " chain=" + chain);
        }
        ScrollPane pane = active;
        if (pane == null || !belongsTo(pane, event)) {
            pane = null;
            for (WeakReference<ScrollPane> ref : installed) {
                ScrollPane candidate = ref.get();
                if (candidate != null && candidate.isVisible() && belongsTo(candidate, event)) {
                    pane = candidate;
                    break;
                }
            }
        }
        if (pane == null) return;
        Controller controller = controllerOf(pane);
        if (controller == null) return;
        boolean ignored = shouldIgnore(event);
        if (DEBUG) System.err.println("[PCLScroll] wheel dy=" + event.getDeltaY()
                + " ignored=" + ignored + " max=" + controller.maxOffset());
        if (ignored) return;
        event.consume();
        // PCL2 passes -e.Delta; negative JavaFX deltaY means "scroll down".
        controller.scrollBy(-event.getDeltaY() * WHEEL_TO_PCL);
    }

    /// Whether the event target lives inside this pane's content subtree.
    private static boolean belongsTo(ScrollPane pane, ScrollEvent event) {
        Node content = pane.getContent();
        if (content == null) return false;
        Node node = event.getTarget() instanceof Node target ? target : null;
        while (node != null) {
            if (node == content || node == pane) return true;
            node = node.getParent();
        }
        return false;
    }

    /// Installs PCL2 wheel easing and marks the pane as the page's main card
    /// flow, so [PCLScrollViewer#activate] picks it over nested list scrolls.
    public static void installPrimary(ScrollPane pane) {
        install(pane);
        if (!pane.getStyleClass().contains(PRIMARY_MARK)) pane.getStyleClass().add(PRIMARY_MARK);
    }

    /// Makes the first installed scroll pane inside [page] the active one.
    /// PCL2 refreshes `BtnExtraBack` on `IsVisibleChanged`, which happens on
    /// every page change; the navigator calls this from `onNavigated`.
    public static void activate(@Nullable Node page) {
        pendingPage = page;
        ScrollPane found = find(page, 0, true);
        if (found == null) found = find(page, 0, false);
        if (DEBUG) System.err.println("[PCLScroll] activate " + (page == null ? "null" : page.getClass().getSimpleName())
                + " found=" + (found != null));
        active = found;
        Controller controller = found == null ? null : controllerOf(found);
        if (controller != null) controller.syncFromVvalue();
        refresh();

        // Some PCL-style pages build their scroll pane one layout pass after
        // navigation (`DownloadPage`, `SettingsPage`). PCL2 refreshes
        // `BtnExtraBack` on `IsVisibleChanged`, so retry once after layout.
        if (found == null && page != null) {
            PauseTransition retry = new PauseTransition(Duration.millis(450));
            retry.setOnFinished(e -> {
                if (pendingPage != page) return;
                ScrollPane late = find(page, 0, true);
                if (late == null) late = find(page, 0, false);
                if (late == null) return;
                active = late;
                Controller lateController = controllerOf(late);
                if (lateController != null) lateController.syncFromVvalue();
                refresh();
                if (DEBUG) System.err.println("[PCLScroll] activate(retry) found=true");
            });
            retry.play();
        }
    }

    /// Clears the active pane (e.g. a page with no scrollable card flow).
    public static void deactivate() {
        active = null;
        pendingPage = null;
        refresh();
    }

    /// PCL2 `PanBack.ScrollToHome()` triggered by `BtnExtraBack`. Uses the same
    /// 300ms OutFluent(6) curve as wheel scrolling, so returning to the top
    /// matches the feel of scrolling down.
    public static void scrollToHome() {
        ScrollPane pane = active;
        if (pane == null) return;
        Controller controller = controllerOf(pane);
        if (controller != null) controller.animateTo(0);
    }

    /// Recomputes the global "scrolled" flag for the extra buttons.
    public static void refresh() {
        ScrollPane pane = active;
        Controller controller = pane == null ? null : controllerOf(pane);
        boolean value = controller != null && controller.realOffset > SHOW_THRESHOLD
                && pane.isVisible() && pane.getScene() != null;
        if (scrolled.get() != value) scrolled.set(value);
        installed.removeIf(ref -> ref.get() == null);
    }

    /// Re-evaluates all `ShowCheck` delegates after a scroll event. Kept as a
    /// separate hook because PCL2 also calls `ShowRefresh` on layout changes.
    public static void refreshAll() {
        refresh();
    }

    private static @Nullable Controller controllerOf(ScrollPane pane) {
        Object value = pane.getProperties().get(CONTROLLER_KEY);
        return value instanceof Controller controller ? controller : null;
    }

    private static @Nullable ScrollPane find(@Nullable Node node, int depth, boolean primaryOnly) {
        if (node == null || depth > 14 || !node.isVisible()) return null;
        if (node instanceof ScrollPane pane && pane.getStyleClass().contains(MARK)
                && (!primaryOnly || pane.getStyleClass().contains(PRIMARY_MARK))) return pane;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ScrollPane found = find(child, depth + 1, primaryOnly);
                if (found != null) return found;
            }
        }
        return null;
    }

    /// PCL2 ignores the wheel inside an open ComboBox, a multi-line TextBox, a
    /// ComboBoxItem or a PopupRoot, letting those controls scroll themselves.
    private static boolean shouldIgnore(ScrollEvent event) {
        Node node = event.getTarget() instanceof Node target ? target : null;
        while (node != null) {
            if (node instanceof TextArea) return true;
            if (node instanceof TextInputControl input && input.isEditable()
                    && input.getText() != null && input.getText().contains("\n")) return true;
            if (node instanceof ComboBoxBase) return true;
            if (node.getStyleClass().contains("combo-box-popup")
                    || node.getStyleClass().contains("popup-root")) return true;
            node = node.getParent();
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /// Per-pane state. Stored in `Node.getProperties()` so it lives and dies
    /// with the pane instead of leaking through a static map.
    private static final class Controller {
        private final ScrollPane pane;
        /// The animated offset in content pixels (`RealOffset` in PCL2).
        private final SimpleDoubleProperty offset = new SimpleDoubleProperty(0);
        private double realOffset;
        private @Nullable Timeline animation;
        private boolean applying;

        Controller(ScrollPane pane) {
            this.pane = pane;
            this.offset.addListener((obs, oldValue, newValue) -> apply(newValue.doubleValue()));
            pane.viewportBoundsProperty().addListener((obs, oldValue, newValue) -> refresh());
            pane.sceneProperty().addListener((obs, oldValue, newValue) -> refresh());
            pane.visibleProperty().addListener((obs, oldValue, newValue) -> refresh());
        }

        /// PCL2 `PerformVerticalOffsetDelta`: extend the target and restart the
        /// 300ms ease from the current position.
        void scrollBy(double delta) {
            double max = maxOffset();
            if (max <= 0 || Math.abs(delta) < 0.01) return;
            double target = clamp(realOffset + delta, 0, max);
            animateTo(target);
        }

        void animateTo(double target) {
            double max = maxOffset();
            double to = clamp(target, 0, max);
            if (DEBUG) System.err.println("[PCLScroll] animate " + realOffset + " -> " + to + " max=" + max);
            if (animation != null) animation.stop();
            if (!AnimationUtils.isAnimationEnabled() || Math.abs(to - realOffset) < 0.5) {
                offset.set(to);
                return;
            }
            animation = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(offset, realOffset)),
                    new KeyFrame(Duration.millis(EASE_MS),
                            new KeyValue(offset, to, PCLTheme.outFluent(6))));
            animation.play();
        }

        private void apply(double value) {
            realOffset = value;
            double max = maxOffset();
            if (max > 0) {
                applying = true;
                pane.setVvalue(clamp(value / max, 0, 1));
                applying = false;
            }
            refresh();
        }

        /// Scrollbar drags / programmatic setVvalue must rebase RealOffset,
        /// otherwise the next wheel event would jump back to the old target.
        void onExternalVvalue() {
            if (applying) return;
            if (animation != null) {
                animation.stop();
                animation = null;
            }
            realOffset = pane.getVvalue() * maxOffset();
            offset.set(realOffset);
            refresh();
        }

        void syncFromVvalue() {
            onExternalVvalue();
        }

        private double maxOffset() {
            Node content = pane.getContent();
            if (content == null) return 0;
            double contentHeight = content.getLayoutBounds().getHeight();
            double viewportHeight = pane.getViewportBounds().getHeight();
            return Math.max(0, contentHeight - viewportHeight);
        }
    }
}
