/*
 * HPCL Launcher
 * Copyright (C) 2026 HPCL contributors
 * Derived from Hello Minecraft! Launcher (GPLv3), original copyright (C) huangyuhui and contributors.
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
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.TransitionPane.AnimationProducer;
import org.jackhuang.hmcl.ui.pcl.PCLTheme;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Page transitions modelled on PCL's `FormMain.PageChangeAnim`.
///
/// PCL hides the old page in ~110ms and shows the new one from a small offset
/// over ~350ms with `AniEaseOutFluent` / `AniEaseOutBack`. The total duration
/// here is 460ms, matching `110 + 350`.
@NotNullByDefault
public final class PCLAnimations {

    /// Total duration of a PCL page switch.
    public static final Duration DURATION = Duration.millis(460);

    /// PCL `AniEaseOutFluent(Middle)`.
    public static final Interpolator EASE = PCLTheme.OUT_MIDDLE;

    /// Entering a sub page: old page fades to the left, new page slides in from
    /// the right while fading in.
    public static final AnimationProducer FORWARD = new AnimationProducer() {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
            nextNode.setTranslateX(24);
            nextNode.setOpacity(0);
        }

        @Override
        public Animation animate(Pane container, Node previousNode, Node nextNode,
                                 Duration duration, Interpolator interpolator) {
            Duration hide = duration.multiply(0.24);
            Interpolator out = interpolator == null ? EASE : interpolator;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(previousNode.opacityProperty(), 1, PCLTheme.IN_MIDDLE),
                            new KeyValue(previousNode.translateXProperty(), 0, PCLTheme.IN_MIDDLE),
                            new KeyValue(nextNode.opacityProperty(), 0, out),
                            new KeyValue(nextNode.translateXProperty(), 24, out)),
                    new KeyFrame(hide,
                            new KeyValue(previousNode.opacityProperty(), 0, PCLTheme.IN_MIDDLE),
                            new KeyValue(previousNode.translateXProperty(), -12, PCLTheme.IN_MIDDLE),
                            new KeyValue(nextNode.opacityProperty(), 0, out),
                            new KeyValue(nextNode.translateXProperty(), 24, out)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.opacityProperty(), 1, out),
                            new KeyValue(nextNode.translateXProperty(), 0, out))
            );
        }

        @Override
        public @Nullable AnimationProducer opposite() {
            return BACKWARD;
        }
    };

    /// Returning to the previous page: mirrored version of [PCLAnimations#FORWARD].
    public static final AnimationProducer BACKWARD = new AnimationProducer() {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
            nextNode.setTranslateX(-24);
            nextNode.setOpacity(0);
        }

        @Override
        public Animation animate(Pane container, Node previousNode, Node nextNode,
                                 Duration duration, Interpolator interpolator) {
            Duration hide = duration.multiply(0.24);
            Interpolator out = interpolator == null ? EASE : interpolator;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(previousNode.opacityProperty(), 1, PCLTheme.IN_MIDDLE),
                            new KeyValue(previousNode.translateXProperty(), 0, PCLTheme.IN_MIDDLE),
                            new KeyValue(nextNode.opacityProperty(), 0, out),
                            new KeyValue(nextNode.translateXProperty(), -24, out)),
                    new KeyFrame(hide,
                            new KeyValue(previousNode.opacityProperty(), 0, PCLTheme.IN_MIDDLE),
                            new KeyValue(previousNode.translateXProperty(), 12, PCLTheme.IN_MIDDLE),
                            new KeyValue(nextNode.opacityProperty(), 0, out),
                            new KeyValue(nextNode.translateXProperty(), -24, out)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.opacityProperty(), 1, out),
                            new KeyValue(nextNode.translateXProperty(), 0, out))
            );
        }

        @Override
        public @Nullable AnimationProducer opposite() {
            return FORWARD;
        }
    };

    /// A lightweight fade + scale transition for dialogs and overlays.
    public static final AnimationProducer FADE_SCALE = new AnimationProducer() {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
            nextNode.setScaleX(0.96);
            nextNode.setScaleY(0.96);
            nextNode.setOpacity(0);
        }

        @Override
        public Animation animate(Pane container, Node previousNode, Node nextNode,
                                 Duration duration, Interpolator interpolator) {
            Interpolator out = interpolator == null ? EASE : interpolator;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(previousNode.opacityProperty(), 1, PCLTheme.OUT_WEAK),
                            new KeyValue(nextNode.opacityProperty(), 0, out),
                            new KeyValue(nextNode.scaleXProperty(), 0.96, out),
                            new KeyValue(nextNode.scaleYProperty(), 0.96, out)),
                    new KeyFrame(duration,
                            new KeyValue(previousNode.opacityProperty(), 0, PCLTheme.OUT_WEAK),
                            new KeyValue(nextNode.opacityProperty(), 1, out),
                            new KeyValue(nextNode.scaleXProperty(), 1, out),
                            new KeyValue(nextNode.scaleYProperty(), 1, out))
            );
        }
    };

    private PCLAnimations() {
    }
}
