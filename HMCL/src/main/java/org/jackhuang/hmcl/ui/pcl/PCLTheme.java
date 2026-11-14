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
package org.jackhuang.hmcl.ui.pcl;

import com.jfoenix.controls.JFXButton;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// PCL-style visual language for HPCL, implemented with JavaFX.
///
/// The palette, easing curves and durations below are transcribed from PCL's
/// open-source implementation (`ModSecret.vb`, `ModAnimation.vb`,
/// `MyPageRight.vb`, `MyButton.xaml.vb`, `MyCard.vb`):
/// `Meloong-Git/PCL` on GitHub. Only the design parameters are reused; all
/// code here is an original JavaFX implementation.
@NotNullByDefault
public final class PCLTheme {

    // region Palette (PCL ModSecret.vb Color1..Color8, HSL hue 210 / sat 85)

    /// Deep slate used for text and idle card shadows.
    public static final String C1 = "#343D4A";
    /// Deep blue, used for pressed states and borders.
    public static final String C2 = "#0B5BCB";
    /// Primary PCL blue.
    public static final String C3 = "#1370F3";
    /// Bright blue, used for active highlights.
    public static final String C4 = "#4890F5";
    /// Light blue.
    public static final String C5 = "#96C0F9";
    /// Very light blue.
    public static final String C6 = "#D5E6FD";
    /// Hover background.
    public static final String C7 = "#DEECFD";
    /// Page/sidebar background.
    public static final String C8 = "#EAF2FE";

    // endregion

    // region Easing (PCL ModAnimation.vb)

    /// Returns PCL's `AniEaseOutFluent`: `1 - (1 - t)^power`.
    public static Interpolator outFluent(int power) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return 1 - Math.pow(1 - t, power);
            }
        };
    }

    /// Returns PCL's `AniEaseInFluent`: `t^power`.
    public static Interpolator inFluent(int power) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return Math.pow(t, power);
            }
        };
    }

    /// Returns PCL's `AniEaseOutBack`: `1 - (1-t)^p * cos(1.5*pi*t)`,
    /// where `p = 3 - power * 0.5` and power follows `AniEasePower`.
    public static Interpolator outBack(int power) {
        final double p = 3 - power * 0.5;
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return 1 - Math.pow(1 - t, p) * Math.cos(1.5 * Math.PI * t);
            }
        };
    }

    /// Returns PCL's `AniEaseOutElastic`: `p = power + 4`,
    /// `1 - (1-t)^((p-1)/4) * cos((p-3.5)*pi*t^1.5)`.
    /// PCL uses `AniEasePower.Weak` (2 -> p=6) for toast cards.
    public static Interpolator outElastic(int power) {
        final double p = power + 4;
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                double u = 1 - t;
                return 1 - Math.pow(u, (p - 1) * 0.25)
                        * Math.cos((p - 3.5) * Math.PI * Math.pow(t, 1.5));
            }
        };
    }

    /// Returns PCL's `AniEaseInBack`: `t^p * cos(1.5*pi*(1-t))`,
    /// where `p = 3 - power * 0.5`. Used by the `MyLoading` pickaxe wind-up.
    public static Interpolator inBack(int power) {
        final double p = 3 - power * 0.5;
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return Math.pow(t, p) * Math.cos(1.5 * Math.PI * (1 - t));
            }
        };
    }

    /// PCL `AniEaseInoutFluent`: slow in / slow out, symmetric around 0.5.
    public static Interpolator inoutFluent(int power) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return t < 0.5
                        ? Math.pow(2 * t, power) * 0.5
                        : 1 - Math.pow(2 * (1 - t), power) * 0.5;
            }
        };
    }

    /// PCL `AniEaseOutFluentWithInitial`: an ease that starts with a non-zero
    /// velocity (`InitialPixelPerSecond`) and settles smoothly. `initial` is
    /// the normalised initial speed; PCL clamps it so it never goes backwards.
    public static Interpolator outFluentWithInitial(int power, double initial) {
        final double alpha = Math.max(0, initial - 1);
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                if (t >= 1) return 1;
                double fluent = 1 - Math.pow(1 - t, power);
                // Blend a linear head start into the fluent tail.
                double head = alpha * t * Math.pow(1 - t, power - 1);
                return Math.max(0, Math.min(1, fluent + head));
            }
        };
    }

    /// PCL `AniEaseInCar`: accelerate like a car pulling away.
    public static Interpolator inCar(int power) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return Math.pow(t, power);
            }
        };
    }

    /// PCL `AniEaseOutCar`: decelerate like a car braking to a stop.
    public static Interpolator outCar(int power) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return 1 - Math.pow(1 - t, power);
            }
        };
    }

    /// PCL `AniEaseInElastic`: overshoot at the start.
    public static Interpolator inElastic(int power) {
        final double p = power + 4;
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                if (t <= 0) return 0;
                if (t >= 1) return 1;
                return Math.pow(t, (p - 1) * 0.25)
                        * Math.sin((p - 3.5) * Math.PI * Math.pow(1 - t, 1.5));
            }
        };
    }

    /// PCL `AniEasePower.Weak` = 2, `Middle` = 3, `Strong` = 4, `ExtraStrong` = 5.
    public static final Interpolator OUT_WEAK = outFluent(2);
    public static final Interpolator OUT_MIDDLE = outFluent(3);
    public static final Interpolator OUT_STRONG = outFluent(4);
    public static final Interpolator OUT_EXTRA_STRONG = outFluent(5);
    public static final Interpolator IN_WEAK = inFluent(2);
    public static final Interpolator IN_MIDDLE = inFluent(3);
    public static final Interpolator IN_STRONG = inFluent(4);
    public static final Interpolator BACK_WEAK = outBack(2);
    public static final Interpolator BACK_MIDDLE = outBack(3);
    public static final Interpolator BACK_STRONG = outBack(4);
    public static final Interpolator IN_BACK_WEAK = inBack(2);
    public static final Interpolator ELASTIC_WEAK = outElastic(2);
    public static final Interpolator IN_ELASTIC_WEAK = inElastic(2);
    public static final Interpolator INOUT_WEAK = inoutFluent(2);
    public static final Interpolator INOUT_MIDDLE = inoutFluent(3);
    public static final Interpolator CAR_OUT_WEAK = outCar(2);
    public static final Interpolator CAR_IN_WEAK = inCar(2);

    // endregion

    // region Durations (milliseconds, from PCL sources)

    /// Default PCL animation duration: `AaX(..., Time := 400)`.
    public static final Duration DEFAULT = Duration.millis(400);
    /// Page switch total: 110ms hide + 350ms show (`PageChangeAnim`).
    public static final Duration PAGE_SWITCH = Duration.millis(460);
    /// Card entrance single step (`MyPageRight.TriggerShowAnimation`).
    public static final Duration CARD_FADE = Duration.millis(100);
    public static final Duration CARD_SLIDE = Duration.millis(250);
    public static final Duration CARD_BACK = Duration.millis(350);
    /// Stagger between cards on enter / exit.
    public static final int ENTER_STAGGER = 25;
    public static final int EXIT_STAGGER = 15;
    /// `MyHint.AniDispose`: scale -0.08 in 200ms, fade 200ms, collapse after 100ms.
    public static final Duration HINT_DISPOSE = Duration.millis(200);
    /// `MyMsgText`: rotate -4deg + Y40 -> 0, 120ms fade @60ms, 300ms ease.
    public static final int DIALOG_FADE_DELAY = 60;
    public static final int DIALOG_FADE = 120;
    public static final int DIALOG_SETTLE = 300;
    /// `AaTextAppear`: 70ms per character.
    public static final int TEXT_APPEAR_PER_CHAR = 70;

    // endregion

    // region Style class constants

    public static final String TOP_BAR = "hpcl-topbar";
    public static final String NAV_ITEM = "hpcl-nav-item";
    public static final String NAV_ITEM_ACTIVE = "hpcl-nav-item-active";
    public static final String SIDEBAR = "hpcl-sidebar";
    public static final String CARD = "hpcl-card";
    public static final String BRAND = "hpcl-brand";
    public static final String BRAND_TITLE = "hpcl-brand-title";
    public static final String BRAND_SUBTITLE = "hpcl-brand-subtitle";
    public static final String CARD_TITLE = "hpcl-card-title";
    public static final String BADGE = "hpcl-badge";
    public static final String HOME = "hpcl-home";
    public static final String HOME_TITLE = "hpcl-home-title";
    public static final String HOME_SUBTITLE = "hpcl-home-subtitle";
    public static final String QUICK_ROW = "hpcl-quick-row";
    public static final String QUICK_CARD = "hpcl-quick-card";
    public static final String LAUNCH_BAR = "hpcl-launch-bar";
    public static final String LAUNCH_BUTTON = "hpcl-launch-button";
    public static final String LAUNCH_MENU = "hpcl-launch-menu";
    public static final String LAUNCH_PROGRESS = "hpcl-launch-progress";
    /// The unfinished (light-blue) half of PCL's two-segment launch bar.
    public static final String LAUNCH_PROGRESS_TRACK = "hpcl-launch-progress-track";
    public static final String SECTION_TITLE = "hpcl-section-title";
    public static final String CURRENT_VERSION = "hpcl-current-version";
    public static final String HOME_HINT = "hpcl-home-hint";
    /// PCL `MyHint` (Controls/MyHint.xaml) bar above the home card flow.
    public static final String HINT = "hpcl-hint";
    /// PCL `MyCard` as used by PageLaunchRight (heading + inset body).
    public static final String FLOW_CARD = "hpcl-flow-card";
    /// PCL `MyListItem` row inside a card.
    public static final String CARD_LIST_ITEM = "hpcl-card-list-item";
    public static final String CARD_FLOW = "hpcl-card-flow";
    public static final String LAUNCH_BAR_ROOT = "hpcl-launch-bar-root";

    // endregion

    /// Creates the HPCL brand block shown at the top of the left sidebar.
    public static Node createBrand() {
        VBox box = new VBox(2);
        box.getStyleClass().add(BRAND);

        Label title = new Label(Metadata.NAME);
        title.getStyleClass().add(BRAND_TITLE);
        Label subtitle = new Label("PCL STYLE");
        subtitle.getStyleClass().add(BRAND_SUBTITLE);

        box.getChildren().setAll(title, subtitle);
        return box;
    }

    /// Creates one navigation button with PCL hover/press animation.
    public static JFXButton createNavItem(String title, SVG icon, Runnable action) {
        JFXButton button = new JFXButton();
        button.getStyleClass().add(NAV_ITEM);
        button.setFocusTraversable(false);
        button.setMinHeight(38);

        HBox graphic = new HBox(7);
        graphic.setAlignment(Pos.CENTER_LEFT);
        Node iconNode = icon.createIcon(16);
        iconNode.getStyleClass().add("hpcl-nav-icon");
        Label label = new Label(title);
        label.getStyleClass().add("hpcl-nav-label");
        graphic.getChildren().setAll(iconNode, label);
        button.setGraphic(graphic);
        button.setOnAction(e -> action.run());
        attachButtonHover(button);
        attachPCLButtonPress(button);
        return button;
    }

    /// PCL `FormMain.PanTitle`: a single 48px blue bar. It stacks three
    /// layers - the logo/window-button base, the centred `PanTitleMain` pills,
    /// and the `PanTitleInner` back arrow + sub-page name.
    public static final class GlobalTopBar extends StackPane {
        private final List<JFXButton> items = new ArrayList<>();
        private int lastActive = -1;
        private final HBox navLayer;
        private final HBox innerBox;
        private final HBox logoBox;
        private final Label innerTitle;
        private boolean showingInner;
        private Timeline switchAnim;

        private GlobalTopBar(HBox navLayer, HBox innerBox, HBox logoBox, Label innerTitle) {
            getStyleClass().add(TOP_BAR);
            setMaxWidth(Double.MAX_VALUE);
            setPickOnBounds(false);
            this.navLayer = navLayer;
            this.innerBox = innerBox;
            this.logoBox = logoBox;
            this.innerTitle = innerTitle;

            // PanTitleSelect is centred on the whole bar (FormMain.xaml uses a
            // 1*/Auto/1* Grid). PanTitleInner lives as the first child of the
            // base HBox instead of a StackPane layer: a pref-sized StackPane
            // child can be laid out with zero width and never paint.
            StackPane.setAlignment(navLayer, Pos.CENTER);
            navLayer.setPickOnBounds(false);
            navLayer.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            innerBox.setManaged(false);
            innerBox.setVisible(false);
            innerBox.setMouseTransparent(true);
        }

        /// Highlights the navigation entry at `index`, or none when negative.
        /// On change, plays PCL's incoming-title animation (FormMain.xaml.vb
        /// `PageChangeActual` / `PanTitleInner`): 150ms fade, and a 350ms
        /// `AniEaseOutBack` slide from -12px with a 200ms delay.
        public void setActive(int index) {
            for (int i = 0; i < items.size(); i++) {
                setNavActive(items.get(i), i == index);
            }
            if (index != lastActive) {
                lastActive = index;
                if (index >= 0 && index < items.size()) {
                    playNavSwitch(items.get(index));
                }
            }
        }

        private static void playNavSwitch(JFXButton item) {
            if (!AnimationUtils.isAnimationEnabled()) return;
            Node graphic = item.getGraphic() != null ? item.getGraphic() : item;
            graphic.setTranslateX(-12);
            graphic.setOpacity(0.35);
            new Timeline(new KeyFrame(Duration.millis(150),
                    new KeyValue(graphic.opacityProperty(), 1, OUT_WEAK))).play();
            new Timeline(
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(graphic.translateXProperty(), -12, BACK_MIDDLE)),
                    new KeyFrame(Duration.millis(550),
                            new KeyValue(graphic.translateXProperty(), 0, BACK_MIDDLE))
            ).play();
        }

        private void stopSwitch() {
            if (switchAnim != null) {
                switchAnim.stop();
                switchAnim = null;
            }
        }

        /// PCL `PageChangeActual`: swap PanTitleMain -> PanTitleInner. The
        /// pills fade out in 150ms and slide +12px; the inner layer fades in
        /// after a 200ms delay (150ms) and slides -16px -> 0 over 350ms with
        /// `AniEaseOutBack`. A single strongly-referenced Timeline owns the
        /// whole swap and writes the final state in onFinished.
        public void showInner(String title) {
            String name = title == null ? "" : title;
            if (showingInner) {
                // PCL `AaTextAppear`: 70ms per character on a title change.
                PCLTheme.playTextAppear(innerTitle, name);
                return;
            }
            showingInner = true;
            PCLTheme.playTextAppear(innerTitle, name);
            stopSwitch();
            // PCL collapses PanTitleMain (including ShapeTitleLogo) on
            // sub-pages, and shows PanTitleInner at the far left instead.
            logoBox.setVisible(false);
            logoBox.setManaged(false);
            innerBox.setVisible(true);
            innerBox.setManaged(true);
            innerBox.setMouseTransparent(false);
            navLayer.setMouseTransparent(true);
            if (!AnimationUtils.isAnimationEnabled()) {
                navLayer.setOpacity(0);
                navLayer.setTranslateX(0);
                innerBox.setOpacity(1);
                innerBox.setTranslateX(0);
                return;
            }
            // Safe defaults: even if the Timeline is skipped, the sub-page
            // layer stays visible and the pills stay hidden.
            innerBox.setOpacity(1);
            innerBox.setTranslateX(0);
            navLayer.setOpacity(0);
            navLayer.setTranslateX(0);
            switchAnim = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(navLayer.opacityProperty(), 1, OUT_WEAK),
                            new KeyValue(navLayer.translateXProperty(), 0, IN_WEAK),
                            new KeyValue(innerBox.opacityProperty(), 0, OUT_WEAK),
                            new KeyValue(innerBox.translateXProperty(), -16, BACK_MIDDLE)),
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(navLayer.opacityProperty(), 0, OUT_WEAK),
                            new KeyValue(navLayer.translateXProperty(), 12, IN_WEAK)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(innerBox.opacityProperty(), 0, OUT_WEAK),
                            new KeyValue(innerBox.translateXProperty(), -16, BACK_MIDDLE)),
                    new KeyFrame(Duration.millis(350),
                            new KeyValue(innerBox.opacityProperty(), 1, OUT_WEAK)),
                    new KeyFrame(Duration.millis(550),
                            new KeyValue(innerBox.translateXProperty(), 0, BACK_MIDDLE))
            );
            switchAnim.setOnFinished(e -> {
                innerBox.setOpacity(1);
                innerBox.setTranslateX(0);
                navLayer.setOpacity(0);
                navLayer.setTranslateX(0);
            });
            switchAnim.play();
        }

        /// PCL `PageChangeExit`: reverse the swap (150ms fade + -18px slide,
        /// then a 200ms-delayed 150ms fade and 350ms `AniEaseOutBack(Weak)`).
        public void showMain() {
            if (!showingInner) return;
            showingInner = false;
            stopSwitch();
            innerBox.setVisible(false);
            innerBox.setManaged(false);
            logoBox.setVisible(true);
            logoBox.setManaged(true);
            innerBox.setMouseTransparent(true);
            navLayer.setMouseTransparent(false);
            if (!AnimationUtils.isAnimationEnabled()) {
                innerBox.setOpacity(0);
                innerBox.setTranslateX(0);
                navLayer.setOpacity(1);
                navLayer.setTranslateX(0);
                return;
            }
            // Safe defaults, then PCL `PageChangeExit`: 200ms-delayed 150ms
            // fade and 350ms AniEaseOutBack(Weak) slide for PanTitleMain.
            navLayer.setOpacity(1);
            navLayer.setTranslateX(0);
            innerBox.setOpacity(0);
            innerBox.setTranslateX(0);
            switchAnim = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(navLayer.opacityProperty(), 0, OUT_WEAK),
                            new KeyValue(navLayer.translateXProperty(), 12, BACK_WEAK)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(navLayer.opacityProperty(), 0, OUT_WEAK),
                            new KeyValue(navLayer.translateXProperty(), 12, BACK_WEAK)),
                    new KeyFrame(Duration.millis(350),
                            new KeyValue(navLayer.opacityProperty(), 1, OUT_WEAK)),
                    new KeyFrame(Duration.millis(550),
                            new KeyValue(navLayer.translateXProperty(), 0, BACK_WEAK))
            );
            switchAnim.setOnFinished(e -> {
                navLayer.setOpacity(1);
                navLayer.setTranslateX(0);
            });
            switchAnim.play();
        }
    }

    /// PCL `LabTitleLogo` (FormMain.xaml): the launcher name as plain white
    /// bold text at the left of `PanTitle`. PCL writes "PCL" with no logo mark,
    /// so HPCL mirrors that with a bare text wordmark.
    private static HBox createTitleLogo() {
        HBox box = new HBox();
        box.getStyleClass().add("hpcl-title-logo");
        box.setAlignment(Pos.CENTER_LEFT);

        Label text = new Label("HPCL");
        text.getStyleClass().add("hpcl-title-logo-text");
        box.getChildren().setAll(text);
        return box;
    }

    /// Creates PCL's single blue title bar. `base` holds the logo and the two
    /// white `MyIconButton` window buttons; `navLayer` is the centred
    /// PanTitleSelect; `innerBox` is PanTitleInner.
    ///
    /// PCL2 `FormMain.xaml` PanTitleSelect carries FIVE entries:
    /// BtnTitleSelect0 启动 / 1 下载 / 2 联机 / 3 设置 / 4 更多.
    /// `更多` is a real top-level page (`PageType.Other` -> PageOtherLeft),
    /// not a popup menu, so the caller passes a plain navigation Runnable.
    public static GlobalTopBar createGlobalTopBar(Runnable launch, Runnable download,
                                                  Runnable link, Runnable settings,
                                                  Runnable more,
                                                  Runnable back, Runnable minimize,
                                                  Runnable close) {
        // PanTitleInner: back arrow + sub-page name. It is the FIRST child of
        // the base HBox, so it can never be laid out with zero width.
        HBox innerBox = new HBox(10);
        innerBox.getStyleClass().add("hpcl-topbar-inner");
        innerBox.setAlignment(Pos.CENTER_LEFT);
        JFXButton backButton = createWindowButton(SVG.ARROW_BACK, back);
        Label innerTitle = new Label();
        innerTitle.getStyleClass().add("hpcl-title-inner-label");
        innerBox.getChildren().setAll(backButton, innerTitle);

        HBox logoBox = createTitleLogo();
        HBox base = new HBox();
        base.getStyleClass().add("hpcl-topbar-base");
        base.setAlignment(Pos.CENTER_LEFT);
        base.setMaxWidth(Double.MAX_VALUE);
        Region middle = new Region();
        HBox.setHgrow(middle, Priority.ALWAYS);
        base.getChildren().setAll(innerBox, logoBox, middle,
                createWindowButtons(minimize, close));
        StackPane.setAlignment(base, Pos.CENTER);

        // PCL `MyRadioButton Margin="5,0"` gives a 10px gap between pills.
        HBox navLayer = new HBox(10);
        navLayer.getStyleClass().add("hpcl-topbar-nav");
        navLayer.setAlignment(Pos.CENTER);
        JFXButton launchItem = createNavItem(i18n("version.launch"), SVG.ROCKET_LAUNCH, launch);
        JFXButton downloadItem = createNavItem(i18n("download"), SVG.DOWNLOAD, download);
        JFXButton linkItem = createNavItem(i18n("hpcl.nav.link"), SVG.LINK, link);
        JFXButton settingsItem = createNavItem(i18n("settings"), SVG.SETTINGS, settings);
        JFXButton moreItem = createNavItem(i18n("hpcl.nav.more"), SVG.MORE_GRID, more);
        navLayer.getChildren().setAll(launchItem, downloadItem, linkItem, settingsItem, moreItem);

        // PCL `FormMain.xaml`: <MyImage x:Name="ImgTitle" Stretch="UniformToFill"/>
        // sits between PanTitle's gradient and the pills. The theme strips are
        // near-transparent 1200x80 overlays that add a faint texture to the
        // solid blue title bar. Layer one behind the base HBox.
        Region titleTexture = new Region();
        titleTexture.getStyleClass().add("hpcl-topbar-texture");
        titleTexture.setMouseTransparent(true);
        titleTexture.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(titleTexture, Pos.CENTER);

        GlobalTopBar bar = new GlobalTopBar(navLayer, innerBox, logoBox, innerTitle);
        bar.items.addAll(List.of(launchItem, downloadItem, linkItem, settingsItem, moreItem));
        bar.getChildren().setAll(titleTexture, base, navLayer);
        bar.setActive(0);
        return bar;
    }

    /// PCL `MyRadioButton` used as a login-type chip (`ColorType="Highlight"`):
    /// the selected chip is a solid blue pill with white ink, the other is a
    /// white pill with blue ink and a blue outline.
    public static HBox createTypeBadge(String text, String svgPath, boolean active, Runnable action) {
        HBox badge = new HBox(6);
        badge.getStyleClass().addAll("hpcl-type-badge",
                active ? "hpcl-type-badge-active" : "hpcl-type-badge-idle");
        badge.setAlignment(Pos.CENTER);

        if (svgPath != null && !svgPath.isEmpty()) {
            javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
            icon.setContent(svgPath);
            icon.getStyleClass().add("hpcl-type-badge-icon");
            javafx.geometry.Bounds b = icon.getLayoutBounds();
            double s = 13.0 / Math.max(1, b.getHeight());
            icon.setScaleX(s);
            icon.setScaleY(s);
            badge.getChildren().add(icon);
        }

        Label label = new Label(text);
        label.getStyleClass().add("hpcl-type-badge-text");
        badge.getChildren().add(label);

        if (action != null) {
            badge.setCursor(javafx.scene.Cursor.HAND);
            badge.setOnMouseClicked(e -> action.run());
        }
        return badge;
    }

    /// PCL `RadioLoginType_Change`: refresh the selected login-type chip as
    /// soon as the active account changes, instead of only at startup.
    public static void setTypeBadgeActive(HBox badge, boolean active) {
        badge.getStyleClass().removeAll("hpcl-type-badge-active", "hpcl-type-badge-idle");
        badge.getStyleClass().add(active ? "hpcl-type-badge-active" : "hpcl-type-badge-idle");
    }

    /// PCL `PageLaunchLeft` footer links ("» 购买正版", "» 前往官网"): a muted
    /// chevron followed by 12px grey text.
    public static HBox createSidedLink(String text, Runnable action) {
        HBox box = new HBox(4);
        box.getStyleClass().add("hpcl-sided-link");
        box.setAlignment(Pos.CENTER);
        Label chevron = new Label("»");
        chevron.getStyleClass().add("hpcl-sided-link-chevron");
        Label label = new Label(text);
        label.getStyleClass().add("hpcl-sided-link-text");
        box.getChildren().setAll(chevron, label);
        if (action != null) {
            box.setCursor(javafx.scene.Cursor.HAND);
            box.setOnMouseClicked(e -> action.run());
        }
        return box;
    }

    /// PCL `FormMain.xaml`: BtnTitleMin (Margin 0,0,44,0) and BtnTitleClose
    /// (Margin 0,0,12,0) are 28x28 MyIconButton with Theme="White".
    private static HBox createWindowButtons(Runnable minimize, Runnable close) {
        HBox box = new HBox(4);
        box.getStyleClass().add("hpcl-window-buttons");
        box.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().setAll(
                createWindowButton(SVG.MINIMIZE_CENTER, minimize),
                createWindowButton(SVG.CLOSE, close));
        return box;
    }

    private static JFXButton createWindowButton(SVG icon, Runnable action) {
        JFXButton button = new JFXButton();
        button.getStyleClass().add("hpcl-window-button");
        button.setFocusTraversable(false);
        Node graphic = icon.createIcon(14);
        graphic.getStyleClass().add("hpcl-window-icon");
        button.setGraphic(graphic);
        button.setOnAction(e -> action.run());
        return button;
    }

    /// Marks a navigation item as active (PCL uses a white translucent pill).
    public static void setNavActive(Node item, boolean active) {
        if (active) {
            if (!item.getStyleClass().contains(NAV_ITEM_ACTIVE)) {
                item.getStyleClass().add(NAV_ITEM_ACTIVE);
            }
        } else {
            item.getStyleClass().remove(NAV_ITEM_ACTIVE);
        }
    }

    /// Creates a PCL card (`MyCard.vb`): white 245/255 surface, 5px radius,
    /// 3px idle shadow at 0.07 opacity. The 15/12 padding matches PCL's
    /// `MainTextBlock.Margin = (15, 12, 0, 0)` for the card heading.
    public static VBox createCard(Node... children) {
        VBox card = new VBox(8);
        card.getStyleClass().add(CARD);
        card.setPadding(new Insets(12, 15, 14, 15));
        card.getChildren().setAll(children);
        return card;
    }

    /// Creates the bold 13px card heading used by PCL's `MyCard`.
    public static Label createCardTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(CARD_TITLE);
        return label;
    }

    /// Creates a small rounded version badge.
    public static Label createBadge(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(BADGE);
        return label;
    }

    /// PCL `PanTypeOne` (PageLaunchLeft.xaml): a translucent capsule of height
    /// 27 / radius 13 with a blue icon and blue 14px text showing the login type.
    public static HBox createLoginBadge(SVG icon, String text) {
        HBox badge = new HBox(8);
        badge.getStyleClass().add("hpcl-login-badge");
        badge.setAlignment(Pos.CENTER);
        Node iconNode = icon.createIcon(14);
        iconNode.getStyleClass().add("hpcl-login-badge-icon");
        Label label = new Label(text);
        label.getStyleClass().add("hpcl-login-badge-text");
        badge.getChildren().setAll(iconNode, label);
        return badge;
    }

    /// PCL `MyHint` (Controls/MyHint.xaml): blue 3px left border, radius 2,
    /// 12/9 padding. Sits above the card flow on the launch page.
    public static HBox createHint(String text) {
        return createHint(text, null);
    }

    /// PCL `MyHint CanClose="True"` (PageLaunchRight.xaml PanHint): a small
    /// white ✕ at the right edge dismisses the capsule. `onClose` is called
    /// after the node is hidden so callers can persist the dismissal.
    public static HBox createHint(String text, Runnable onClose) {
        HBox hint = new HBox();
        hint.getStyleClass().add(HINT);
        hint.setAlignment(Pos.CENTER_LEFT);
        hint.setMaxWidth(Double.MAX_VALUE);
        Label label = new Label(text);
        label.setWrapText(true);
        // Without an explicit max width the HBox never hands the label the
        // spare width, so the ✕ would sit right after the text instead of at
        // PCL's far-right BtnClose position.
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("hpcl-hint-text");
        HBox.setHgrow(label, Priority.ALWAYS);
        if (onClose == null) {
            hint.getChildren().setAll(label);
        } else {
            JFXButton close = new JFXButton();
            close.getStyleClass().add("hpcl-hint-close");
            close.setFocusTraversable(false);
            Node x = SVG.CLOSE.createIcon(10);
            x.getStyleClass().add("hpcl-hint-close-icon");
            close.setGraphic(x);
            // PCL `MyHint.BtnClose_Click`: 200ms shrink + fade, then collapse.
            close.setOnAction(e -> playHintDispose(hint, onClose));
            hint.getChildren().setAll(label, close);
        }
        return hint;
    }

    /// PCL `MyCard` as used by `PageLaunchRight.xaml`: white card with a 13px
    /// bold heading at (15,12) and body inset from y=34 (Margin 15,34,15,13).
    public static VBox createFlowCard(String title, Node... content) {
        VBox card = new VBox(4);
        card.getStyleClass().add(FLOW_CARD);
        card.setMaxWidth(Double.MAX_VALUE);
        VBox body = new VBox(2);
        body.getStyleClass().add("hpcl-flow-card-body");
        body.getChildren().setAll(content);
        card.getChildren().setAll(createCardTitle(title), body);
        return card;
    }

    /// PCL `MyListItem`: a clickable row with a leading icon, title/subtitle
    /// and a trailing arrow; hover/press feedback follows `MyListItem.vb`.
    public static HBox createCardListItem(SVG icon, String title, String subtitle, Runnable action) {
        HBox row = new HBox(12);
        row.getStyleClass().add(CARD_LIST_ITEM);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        Node iconNode = icon.createIcon(20);
        iconNode.getStyleClass().add("hpcl-list-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("hpcl-list-title");
        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("hpcl-list-subtitle");
        VBox texts = new VBox(1);
        texts.getChildren().setAll(titleLabel, subLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Node arrow = SVG.ARROW_FORWARD.createIcon(16);
        arrow.getStyleClass().add("hpcl-list-arrow");
        row.getChildren().setAll(iconNode, texts, spacer, arrow);
        row.setOnMouseClicked(e -> action.run());
        attachListItemFeedback(row);
        return row;
    }

    // endregion

    // region Animations

    /// Plays PCL's staggered card entrance: fade 100ms, slide 250ms,
    /// settle with `AniEaseOutBack` at 350ms, 25ms stagger per card.
    public static void playEntrance(List<? extends Node> nodes) {
        if (!AnimationUtils.isAnimationEnabled()) {
            for (Node node : nodes) {
                node.setOpacity(1);
                node.setTranslateY(0);
                node.setScaleX(1);
                node.setScaleY(1);
            }
            return;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            // Several PCL entry paths can target the same card in one pulse
            // (page navigation + a page's own stagger). Only play it once.
            Object last = node.getProperties().get("hpcl.enter.at");
            if (last instanceof Long t && now - t < 600) continue;
            node.getProperties().put("hpcl.enter.at", now);
            int delay = i * ENTER_STAGGER;
            node.setOpacity(0);
            node.setTranslateY(-16);
            // MyPageRight.TriggerEnterAnimation starts three AniData entries
            // in parallel at the same Delay: a 100ms opacity fade, a 250ms
            // relative +5px slide (AniEaseOutFluent) and a 350ms relative
            // +11px slide (AniEaseOutBack). The two slides add to +16px, so
            // the control lands exactly at y=0 at 350ms.
            new StaggeredEntrance(node, delay).play();
        }
    }

    /// Runs PCL's three parallel entrance curves on one control. A Timeline
    /// cannot own two KeyValues for the same property without them fighting,
    /// so the offsets are summed per frame instead.
    private static final class StaggeredEntrance extends Transition {
        private final Node node;
        private final double delay;
        private final double total;

        StaggeredEntrance(Node node, double delay) {
            this.node = node;
            this.delay = delay;
            this.total = delay + 350;
            setCycleDuration(Duration.millis(total));
            setInterpolator(Interpolator.LINEAR);
        }

        @Override
        protected void interpolate(double fraction) {
            double local = Math.max(0, fraction * total - delay);
            node.setOpacity(OUT_WEAK.interpolate(0, 1, clamp01(local / 100)));
            double fluent = OUT_MIDDLE.interpolate(0, 1, clamp01(local / 250));
            double back = BACK_MIDDLE.interpolate(0, 1, clamp01(local / 350));
            node.setTranslateY(-16 + 5 * fluent + 11 * back);
        }

        private static double clamp01(double value) {
            return value < 0 ? 0 : (value > 1 ? 1 : value);
        }
    }

    /// Convenience overload of [PCLTheme#playEntrance].
    public static void playEntrance(Node... nodes) {
        playEntrance(Arrays.asList(nodes));
    }

    /// PCL `AniDispose(Card, True, ...)` (PageSpeedLeft.TaskRefresh): a task
    /// card that finished or was cancelled fades, slides right and collapses
    /// before PanMain drops it.
    public static void playCardDispose(Node card, Runnable onFinished) {
        if (!AnimationUtils.isAnimationEnabled()) {
            onFinished.run();
            return;
        }
        Timeline out = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(card.opacityProperty(), 1, IN_WEAK),
                        new KeyValue(card.translateXProperty(), 0, IN_WEAK),
                        new KeyValue(card.scaleYProperty(), 1, IN_WEAK)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(card.opacityProperty(), 0, IN_WEAK),
                        new KeyValue(card.translateXProperty(), 18, IN_WEAK),
                        new KeyValue(card.scaleYProperty(), 0.86, IN_WEAK)));
        out.setOnFinished(e -> onFinished.run());
        out.play();
    }

    /// PCL `MyExtraButton.Ribble`: a soft ring expands out of the download
    /// ball and fades away. Fired once when a new task joins the queue.
    public static void playRibble(StackPane host, double radius) {
        if (host == null || !AnimationUtils.isAnimationEnabled()) return;
        Circle ring = new Circle(radius);
        ring.getStyleClass().add("hpcl-ribble");
        ring.setOpacity(0.5);
        ring.setMouseTransparent(true);
        ring.setScaleX(0.2);
        ring.setScaleY(0.2);
        host.getChildren().add(0, ring);
        Timeline ripple = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ring.scaleXProperty(), 0.2),
                        new KeyValue(ring.scaleYProperty(), 0.2),
                        new KeyValue(ring.opacityProperty(), 0.5)),
                new KeyFrame(Duration.millis(1000),
                        new KeyValue(ring.scaleXProperty(), 2.2, OUT_STRONG),
                        new KeyValue(ring.scaleYProperty(), 2.2, OUT_STRONG),
                        new KeyValue(ring.opacityProperty(), 0, IN_WEAK)));
        ripple.setOnFinished(e -> host.getChildren().remove(ring));
        ripple.play();
    }

    /// Plays PCL's staggered card exit: 70ms fade and -6px slide, 15ms stagger.
    public static void playExit(List<? extends Node> nodes) {
        if (!AnimationUtils.isAnimationEnabled()) {
            for (Node node : nodes) {
                node.setOpacity(0);
            }
            return;
        }

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int delay = i * EXIT_STAGGER;
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(delay),
                            new KeyValue(node.opacityProperty(), 1, OUT_WEAK),
                            new KeyValue(node.translateYProperty(), 0, OUT_WEAK)),
                    new KeyFrame(Duration.millis(delay + 70),
                            new KeyValue(node.opacityProperty(), 0, OUT_WEAK),
                            new KeyValue(node.translateYProperty(), -6, OUT_WEAK))
            );
            timeline.play();
        }
    }

    /// Creates PCL's two-segment launch-progress bar (`PageLaunchLeft.xaml`,
    /// `ProgressLaunchingFinished` / `ProgressLaunchingUnfinished`):
    /// a 4px light-blue unfinished track plus a blue finished segment that
    /// grows from the left. The finished segment uses PCL's horizontal
    /// `ColorObject4 -> ColorObject3` gradient (see hpcl.css).
    public static Region createLaunchProgress() {
        StackPane root = new StackPane();
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPrefHeight(4);
        root.setMaxHeight(4);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMouseTransparent(true);
        root.setVisible(false);

        Region track = new Region();
        track.getStyleClass().add(LAUNCH_PROGRESS_TRACK);
        track.setMaxHeight(4);
        track.setMaxWidth(Double.MAX_VALUE);

        Region finished = new Region();
        finished.getStyleClass().add(LAUNCH_PROGRESS);
        finished.setMaxHeight(4);
        finished.setMaxWidth(Double.MAX_VALUE);
        // Anchor the horizontal scale at the left edge so the segment grows
        // from left to right instead of from the centre.
        Scale scale = new Scale(1, 1, 0, 0);
        scale.setX(0);
        finished.getTransforms().add(scale);

        root.getChildren().setAll(track, finished);
        root.getProperties().put("hpcl.launch.scale", scale);
        return root;
    }

    /// PCL `PanLaunching`: while the game boots, the button dims slightly and
    /// a light bar sweeps across it. Returns the running animation so the
    /// caller can stop it when the launcher navigates away.
    public static Animation playLaunchStart(Node button, Region progress, Label mainLabel,
                                            Label subLabel, String startingText) {
        progress.setVisible(true);
        String previousMain = mainLabel.getText();
        String previousSub = subLabel == null ? null : subLabel.getText();
        mainLabel.setText(startingText);
        if (subLabel != null) subLabel.setText("");

        Object scaleObj = progress.getProperties().get("hpcl.launch.scale");
        if (!(scaleObj instanceof Scale scale)) return null;

        if (!AnimationUtils.isAnimationEnabled()) {
            scale.setX(1);
            return null;
        }

        scale.setX(0);
        Timeline sweep = new Timeline(
                new KeyFrame(Duration.millis(0),
                        new KeyValue(scale.xProperty(), 0, OUT_MIDDLE),
                        new KeyValue(button.opacityProperty(), 0.88, OUT_WEAK)),
                new KeyFrame(Duration.millis(700),
                        new KeyValue(scale.xProperty(), 1, OUT_MIDDLE),
                        new KeyValue(button.opacityProperty(), 1, OUT_WEAK))
        );
        sweep.setCycleCount(Animation.INDEFINITE);
        sweep.setAutoReverse(true);
        sweep.play();
        button.getProperties().put("hpcl.launch.animation", sweep);
        button.getProperties().put("hpcl.launch.main", previousMain);
        button.getProperties().put("hpcl.launch.sub", previousSub);
        return sweep;
    }

    /// Restores the launch button after a launch attempt (PCL PanLaunching hides).
    public static void stopLaunchStart(Node button, Region progress, Label mainLabel, Label subLabel) {
        Object animation = button.getProperties().get("hpcl.launch.animation");
        if (animation instanceof Animation a) a.stop();
        Object scaleObj = progress.getProperties().get("hpcl.launch.scale");
        if (scaleObj instanceof Scale s) s.setX(0);
        progress.setVisible(false);
        button.setOpacity(1);
        Object main = button.getProperties().get("hpcl.launch.main");
        Object sub = button.getProperties().get("hpcl.launch.sub");
        if (main instanceof String s) mainLabel.setText(s);
        if (subLabel != null && sub instanceof String s) subLabel.setText(s);
    }

    /// Attaches PCL list-item feedback (`MyListItem.xaml.vb` `RefreshColor`):
    /// `Time = 120` on hover/press and `Time = 180` on leave, a 0.98 press
    /// scale over `Time*0.9 = 108ms`, a `Time*1.2 = 144ms` release, and a
    /// `Time*3 = 540ms` idle settle, all with `AniEaseOutFluent`.
    /// The matching background colours (ColorBrush7 / 6 / Bg1) are in hpcl.css.
    public static void attachListItemFeedback(Node item) {
        item.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> animateScale(item, 1.0, 144, OUT_MIDDLE));
        item.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> animateScale(item, 0.98, 108, OUT_MIDDLE));
        item.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> animateScale(item, 1.0, 144, OUT_MIDDLE));
        item.addEventHandler(MouseEvent.MOUSE_EXITED, e -> animateScale(item, 1.0, 540, OUT_MIDDLE));
    }

    /// Attaches PCL button feedback: 300ms enter scale, 800ms leave scale,
    /// 80ms press to 0.955 and 700ms release (`MyButton.xaml.vb`).
    public static void attachButtonHover(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> animateScale(node, 1.0, 300, OUT_MIDDLE));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> animateScale(node, 1.0, 800, OUT_STRONG));
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> animateScale(node, 0.955, 80, OUT_EXTRA_STRONG));
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> animateScale(node, 1.0, 700, OUT_MIDDLE));
    }

    private static void animateScale(Node node, double target, int millis, Interpolator interpolator) {
        if (!AnimationUtils.isAnimationEnabled()) {
            node.setScaleX(target);
            node.setScaleY(target);
            return;
        }
        Animation previous = node.getProperties().get("hpcl.scale.animation") instanceof Animation
                ? (Animation) node.getProperties().get("hpcl.scale.animation") : null;
        if (previous != null) {
            previous.stop();
        }
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(millis),
                new KeyValue(node.scaleXProperty(), target, interpolator),
                new KeyValue(node.scaleYProperty(), target, interpolator)));
        node.getProperties().put("hpcl.scale.animation", timeline);
        timeline.play();
    }

    // endregion

    // region Page-wide animations (PCL MyPageRight / MyPageLeft)

    /// Collects the same granularity of controls as PCL's `GetAllAnimControls`:
    /// MyCard / MyHint / MyExtraTextButton / TextBlock / MyTextButton are
    /// animated as single blocks; Panels are recursed into, but the children
    /// *inside* a card are never animated separately.
    private static void collectAnimControls(Node node, List<Node> out, int depth, boolean skipSidebar) {
        if (node == null || !node.isVisible() || out.size() >= 28 || depth > 9) return;
        // The 300px left pane has its own PCL `MyPageLeft` animation
        // (horizontal slide + 0.96 scale); never double-animate it.
        if (skipSidebar && (hasStyle(node, "hpcl-sidebar")
                || hasStyle(node, "hpcl-page-left"))) return;
        boolean card = hasStyle(node, "hpcl-flow-card") || hasStyle(node, "hpcl-card")
                || hasStyle(node, "hpcl-login-badge") || hasStyle(node, "hint")
                || hasStyle(node, "hpcl-hint") || hasStyle(node, "notice-pane");
        if (card || node instanceof Text) {
            out.add(node);
            return;
        }
        if (node instanceof Label && depth <= 6) {
            out.add(node);
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectAnimControls(child, out, depth + 1, skipSidebar);
            }
        }
    }

    private static boolean hasStyle(Node node, String style) {
        return node.getStyleClass().contains(style);
    }

    /// PCL `MyPageRight.TriggerEnterAnimation`, applied to *every* page rather
    /// than only the launch page: -16px start, 100ms fade, 250/350ms slide and
    /// OutBack settle, 25ms stagger per block. Invoked from `onNavigated`.
    public static void playPageEnter(Node page) {
        if (page == null) return;
        List<Node> blocks = new ArrayList<>();
        collectAnimControls(page, blocks, 0, true);
        if (blocks.isEmpty() && page instanceof Parent parent
                && !parent.getChildrenUnmodifiable().isEmpty()) {
            blocks.add(parent.getChildrenUnmodifiable().get(0));
        }
        if (!blocks.isEmpty()) playEntrance(blocks);
    }

    /// PCL `MyPageRight.TriggerExitAnimation`: 70ms fade, -6px slide,
    /// 15ms stagger.
    public static void playPageExit(Node page) {
        if (page == null) return;
        List<Node> blocks = new ArrayList<>();
        collectAnimControls(page, blocks, 0, true);
        if (!blocks.isEmpty()) playExit(blocks);
    }

    /// PCL `MyPageLeft.TriggerShowAnimation`: the sidebar itself scales from
    /// 0.96 to 1 over 400ms with `AniEaseOutBack(2)` and fades in 100ms, while
    /// each child slides from -25px with a *decreasing* stagger
    /// (`Max(15 - Id, 7) * 2`).
    public static void playSidebarEnter(Node sidebar) {
        if (sidebar == null) return;
        if (!AnimationUtils.isAnimationEnabled()) {
            sidebar.setOpacity(1);
            sidebar.setScaleX(1);
            sidebar.setScaleY(1);
            return;
        }
        sidebar.setOpacity(0);
        sidebar.setScaleX(0.96);
        sidebar.setScaleY(0.96);
        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(sidebar.opacityProperty(), 0, OUT_WEAK),
                        new KeyValue(sidebar.scaleXProperty(), 0.96, BACK_WEAK),
                        new KeyValue(sidebar.scaleYProperty(), 0.96, BACK_WEAK)),
                new KeyFrame(Duration.millis(100),
                        new KeyValue(sidebar.opacityProperty(), 1, OUT_WEAK)),
                new KeyFrame(Duration.millis(400),
                        new KeyValue(sidebar.scaleXProperty(), 1, BACK_WEAK),
                        new KeyValue(sidebar.scaleYProperty(), 1, BACK_WEAK))
        ).play();

        List<Node> blocks = new ArrayList<>();
        collectAnimControls(sidebar, blocks, 0, false);
        int delay = 0;
        for (int i = 0; i < blocks.size(); i++) {
            Node node = blocks.get(i);
            node.setOpacity(0);
            node.setTranslateX(-25);
            new Timeline(
                    new KeyFrame(Duration.millis(delay),
                            new KeyValue(node.opacityProperty(), 0, OUT_WEAK),
                            new KeyValue(node.translateXProperty(), -25, OUT_MIDDLE)),
                    new KeyFrame(Duration.millis(delay + 100),
                            new KeyValue(node.opacityProperty(), 1, OUT_WEAK)),
                    new KeyFrame(Duration.millis(delay + 200),
                            new KeyValue(node.translateXProperty(), -20, OUT_MIDDLE)),
                    new KeyFrame(Duration.millis(delay + 300),
                            new KeyValue(node.translateXProperty(), 0, BACK_WEAK))
            ).play();
            delay += Math.max(15 - i, 7) * 2;
        }
    }

    /// PCL `MyMsgText.Loaded`: starts at rotate -4deg / Y40 / opacity 0,
    /// fades in 120ms after a 60ms delay, and settles over 300ms with
    /// `AniEaseOutBack(2)` (position) and `AniEaseOutFluent(2)` (rotation).
    public static void playDialogEnter(Node dialog) {
        if (dialog == null || !AnimationUtils.isAnimationEnabled()) return;
        dialog.setOpacity(0);
        dialog.setRotate(-4);
        dialog.setTranslateY(40);
        new Timeline(
                new KeyFrame(Duration.millis(DIALOG_FADE_DELAY),
                        new KeyValue(dialog.opacityProperty(), 0, OUT_WEAK)),
                new KeyFrame(Duration.millis(DIALOG_FADE_DELAY + DIALOG_FADE),
                        new KeyValue(dialog.opacityProperty(), 1, OUT_WEAK))
        ).play();
        new Timeline(
                new KeyFrame(Duration.millis(DIALOG_FADE_DELAY),
                        new KeyValue(dialog.translateYProperty(), 40, BACK_WEAK),
                        new KeyValue(dialog.rotateProperty(), -4, OUT_WEAK)),
                new KeyFrame(Duration.millis(DIALOG_FADE_DELAY + DIALOG_SETTLE),
                        new KeyValue(dialog.translateYProperty(), 0, BACK_WEAK),
                        new KeyValue(dialog.rotateProperty(), 0, OUT_WEAK))
        ).play();
    }

    /// PCL `MyMsgText.Close`: 80ms fade after 20ms, Y -> 20 over 150ms
    /// `AniEaseOutFluent`, rotate -> 6deg over 150ms `AniEaseInFluent(2)`.
    public static void playDialogExit(Node dialog, Runnable onFinished) {
        if (dialog == null || !AnimationUtils.isAnimationEnabled()) {
            onFinished.run();
            return;
        }
        Timeline exit = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(dialog.opacityProperty(), 1, OUT_WEAK),
                        new KeyValue(dialog.translateYProperty(), 0, OUT_MIDDLE),
                        new KeyValue(dialog.rotateProperty(), 0, IN_WEAK)),
                new KeyFrame(Duration.millis(20),
                        new KeyValue(dialog.opacityProperty(), 1, OUT_WEAK)),
                new KeyFrame(Duration.millis(100),
                        new KeyValue(dialog.opacityProperty(), 0, OUT_WEAK)),
                new KeyFrame(Duration.millis(150),
                        new KeyValue(dialog.translateYProperty(), 20, OUT_MIDDLE),
                        new KeyValue(dialog.rotateProperty(), 6, IN_WEAK))
        );
        exit.setOnFinished(e -> onFinished.run());
        exit.play();
    }

    /// PCL `AaTextAppear`: 70ms per character, used for page headings.
    public static void playTextAppear(Label label, String text) {
        if (label == null) return;
        if (!AnimationUtils.isAnimationEnabled() || text.length() > 48 || text.isEmpty()) {
            label.setText(text);
            return;
        }
        Timeline timeline = new Timeline();
        for (int i = 1; i <= text.length(); i++) {
            final String partial = text.substring(0, i);
            timeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis((long) i * TEXT_APPEAR_PER_CHAR),
                    e -> label.setText(partial)));
        }
        timeline.play();
    }

    // region Widget animations (PCL control .vb files)

    /// PCL `MyButton_LeftMouseDown`: scale to 0.955 over 80ms
    /// (`AniEaseOutFluent(ExtraStrong)`) plus a -0.01 settle over 700ms;
    /// release returns to 1 over 300ms with a 10ms delay
    /// (`AniEaseOutFluent(Middle)`). Border colour animates 100ms in /
    /// 200ms out, so this only drives the transform.
    public static void attachPCLButtonPress(Node node) {
        if (node == null) return;
        node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!AnimationUtils.isAnimationEnabled()) return;
            new Timeline(new KeyFrame(Duration.millis(80),
                    new KeyValue(node.scaleXProperty(), 0.955, OUT_EXTRA_STRONG),
                    new KeyValue(node.scaleYProperty(), 0.955, OUT_EXTRA_STRONG))).play();
        });
        node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            if (!AnimationUtils.isAnimationEnabled()) return;
            new Timeline(new KeyFrame(Duration.millis(10),
                            new KeyValue(node.scaleXProperty(), node.getScaleX(), OUT_MIDDLE),
                            new KeyValue(node.scaleYProperty(), node.getScaleY(), OUT_MIDDLE)),
                    new KeyFrame(Duration.millis(310),
                            new KeyValue(node.scaleXProperty(), 1, OUT_MIDDLE),
                            new KeyValue(node.scaleYProperty(), 1, OUT_MIDDLE))).play();
        });
    }

    /// PCL `MyButton` colour timing constants, exposed for other widgets.
    public static final int BUTTON_COLOR_IN = 100;
    public static final int BUTTON_COLOR_OUT = 200;

    /// PCL `AniDispose(MyHint)`: scale -0.08 (200ms InFluent), fade out
    /// (200ms OutFluent), collapse height (150ms, 100ms delay, OutFluent),
    /// then hide. Used when a closable hint is dismissed.
    public static void playHintDispose(Node hint, Runnable onFinished) {
        if (!AnimationUtils.isAnimationEnabled()) {
            hint.setVisible(false);
            hint.setManaged(false);
            if (onFinished != null) onFinished.run();
            return;
        }
        double startScale = hint.getScaleX();
        Timeline dispose = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(hint.scaleXProperty(), startScale, IN_WEAK),
                        new KeyValue(hint.scaleYProperty(), hint.getScaleY(), IN_WEAK),
                        new KeyValue(hint.opacityProperty(), hint.getOpacity(), OUT_WEAK)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(hint.scaleXProperty(), startScale - 0.08, IN_WEAK),
                        new KeyValue(hint.scaleYProperty(), startScale - 0.08, IN_WEAK),
                        new KeyValue(hint.opacityProperty(), 0, OUT_WEAK)));
        dispose.setOnFinished(e -> {
            hint.setVisible(false);
            hint.setManaged(false);
            hint.setOpacity(1);
            if (onFinished != null) onFinished.run();
        });
        dispose.play();
    }

    /// PCL `MyListItem.Checked` animation: the selection bar grows in two
    /// OutFluent / OutBack segments (200ms + 300ms) and the foreground moves
    /// to ColorBrush3/2 (200ms); unchecking shrinks it over 120ms InFluent.
    /// JavaFX has no grid-length tween, so we animate a Region's scaleY.
    public static void playListItemChecked(Region checkBar, boolean checked) {
        if (checkBar == null) return;
        if (!AnimationUtils.isAnimationEnabled()) {
            checkBar.setScaleY(checked ? 1 : 0);
            checkBar.setOpacity(checked ? 1 : 0);
            return;
        }
        if (checked) {
            checkBar.setVisible(true);
            new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(checkBar.scaleYProperty(), 0.4, OUT_WEAK),
                            new KeyValue(checkBar.opacityProperty(), 1, OUT_WEAK)),
                    new KeyFrame(Duration.millis(500),
                            new KeyValue(checkBar.scaleYProperty(), 1, BACK_WEAK))).play();
        } else {
            Timeline out = new Timeline(new KeyFrame(Duration.millis(120),
                    new KeyValue(checkBar.scaleYProperty(), 0, IN_WEAK),
                    new KeyValue(checkBar.opacityProperty(), 0, IN_WEAK)));
            out.setOnFinished(e -> checkBar.setVisible(false));
            out.play();
        }
    }

    /// PCL `MyCard.StartHeightAnimation`: expand/collapse a card with a
    /// 150ms OutFluent(ExtraStrong) pref-height tween plus a fade. `delta`
    /// is the height change in px (positive = expand).
    public static void playCardHeight(Region card, double delta, Runnable onFinished) {
        if (card == null || Math.abs(delta) < 1) return;
        double from = card.getPrefHeight() > 0 ? card.getPrefHeight() : card.getHeight();
        double to = Math.max(0, from + delta);
        if (!AnimationUtils.isAnimationEnabled()) {
            card.setPrefHeight(Region.USE_COMPUTED_SIZE);
            if (onFinished != null) onFinished.run();
            return;
        }
        card.setPrefHeight(from);
        Timeline t = new Timeline(new KeyFrame(Duration.millis(150),
                new KeyValue(card.prefHeightProperty(), to, OUT_EXTRA_STRONG)));
        t.setOnFinished(e -> {
            card.setPrefHeight(Region.USE_COMPUTED_SIZE);
            if (onFinished != null) onFinished.run();
        });
        t.play();
    }

    /// PCL `AaStack(Stack, 100, 25)`: reveal a StackPanel's children one by
    /// one (100ms fade each, 25ms stagger). Complements the card-flow
    /// entrance for flat list containers.
    public static void playStackEnter(javafx.scene.layout.Pane pane) {
        if (pane == null) return;
        java.util.List<Node> nodes = new java.util.ArrayList<>(pane.getChildren());
        if (!AnimationUtils.isAnimationEnabled()) return;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int delay = i * 25;
            node.setOpacity(0);
            new Timeline(new KeyFrame(Duration.millis(delay),
                            new KeyValue(node.opacityProperty(), 0, OUT_WEAK)),
                    new KeyFrame(Duration.millis(delay + 100),
                            new KeyValue(node.opacityProperty(), 1, OUT_WEAK))).play();
        }
    }

    /// PCL `MyCard` hover: title ink ColorBrush1 -> ColorBrush2 and shadow
    /// opacity 0.07 -> 0.4 over 90ms; leaving reverses in 90ms.
    public static void attachCardHover(Region card) {
        if (card == null) return;
        card.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED,
                e -> new Timeline(new KeyFrame(Duration.millis(90),
                        new KeyValue(card.opacityProperty(), 1, OUT_WEAK))).play());
    }

    // endregion

    // region Toast cards (PCL ModMain.Hint / FrmMain.PanHint)

    /// Creates the global toast layer. PCL anchors `PanHint` at the bottom
    /// left of the window with `Margin 0,0,0,20` and `IsHitTestVisible=False`.
    public static ToastLayer createToastLayer() {
        return new ToastLayer();
    }

    /// PCL `HintType`: Blue / Green / Red.
    public enum ToastKind { INFO, SUCCESS, ERROR }

    /// PCL `ModMain.HintTick`: a stack of translucent gradient capsules that
    /// slide in from the left with `AniEaseOutElastic(Weak)`, saturate from a
    /// 30% white wash over 250ms, stay for `800 + clamp(len,5,23)*180` ms,
    /// then slide back out. Duplicate hints shake instead of stacking.
    public static final class ToastLayer extends VBox {
        private ToastLayer() {
            setAlignment(Pos.BOTTOM_LEFT);
            setSpacing(6);
            setPadding(new Insets(0, 0, 20, 0));
            setMouseTransparent(true);
            setPickOnBounds(false);
            setFillWidth(false);
            setMaxHeight(Region.USE_PREF_SIZE);
            getStyleClass().add("hpcl-toast-layer");
        }

        public void show(String text) {
            show(text, ToastKind.INFO);
        }

        public void show(String text, ToastKind kind) {
            if (text == null) return;
            final String normalized = text.replaceAll("\\s+", " ").trim();
            if (normalized.isEmpty()) return;
            if (!Platform.isFxApplicationThread()) {
                Platform.runLater(() -> show(normalized, kind));
                return;
            }
            for (Node node : getChildren()) {
                if (node instanceof ToastCard card && card.content.equals(normalized)) {
                    card.bump(kind);
                    return;
                }
            }
            if (getChildren().size() >= 20) return;
            ToastCard card = new ToastCard(normalized, kind);
            getChildren().add(card);
            card.playEnter();
        }
    }

    private static String toastStyle(ToastKind kind) {
        if (kind == ToastKind.SUCCESS) return "hpcl-toast-success";
        if (kind == ToastKind.ERROR) return "hpcl-toast-error";
        return "hpcl-toast-info";
    }

    private static final class ToastCard extends StackPane {
        final String content;
        final Region wash = new Region();
        PauseTransition hold;
        Timeline enter, exit, shake;
        boolean leaving;

        ToastCard(String text, ToastKind kind) {
            this.content = text;
            getStyleClass().addAll("hpcl-toast-card", toastStyle(kind));
            setAlignment(Pos.CENTER_LEFT);
            setMinHeight(26);
            setPrefHeight(26);
            setMaxHeight(26);
            setMaxWidth(Region.USE_PREF_SIZE);
            setTranslateX(-70);
            wash.getStyleClass().add("hpcl-toast-wash");
            wash.setOpacity(0.7);
            Label label = new Label(text);
            label.getStyleClass().add("hpcl-toast-text");
            label.setPadding(new Insets(5, 8, 5, 33));
            getChildren().setAll(wash, label);
        }

        int holdTime() {
            int len = Math.max(5, Math.min(23, content.length()));
            return 800 + len * 180;
        }

        void playEnter() {
            if (!AnimationUtils.isAnimationEnabled()) {
                setTranslateX(-20);
                setOpacity(1);
                wash.setOpacity(0);
                scheduleHide(holdTime());
                return;
            }
            enter = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(translateXProperty(), -70, ELASTIC_WEAK),
                            new KeyValue(opacityProperty(), 0, OUT_WEAK)),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(opacityProperty(), 1, OUT_WEAK)),
                    new KeyFrame(Duration.millis(400),
                            new KeyValue(translateXProperty(), -20, ELASTIC_WEAK))
            );
            enter.play();
            new Timeline(
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(wash.opacityProperty(), 0.7, OUT_WEAK)),
                    new KeyFrame(Duration.millis(350),
                            new KeyValue(wash.opacityProperty(), 0, OUT_WEAK))
            ).play();
            scheduleHide(holdTime());
        }

        void scheduleHide(int delay) {
            if (hold != null) hold.stop();
            hold = new PauseTransition(Duration.millis(delay));
            hold.setOnFinished(e -> playExit());
            hold.play();
        }

        /// PCL duplicate-hint path: four 50ms shake steps, a gradient refresh
        /// and a reset of the auto-hide timer.
        void bump(ToastKind kind) {
            String target = toastStyle(kind);
            if (!getStyleClass().contains(target)) {
                getStyleClass().removeIf(s -> s.startsWith("hpcl-toast-")
                        && !s.equals("hpcl-toast-card") && !s.equals(target));
                getStyleClass().add(target);
            }
            if (leaving || !AnimationUtils.isAnimationEnabled()) {
                scheduleHide(holdTime());
                return;
            }
            if (shake != null) shake.stop();
            shake = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(translateXProperty(), -20, OUT_WEAK)),
                    new KeyFrame(Duration.millis(50),
                            new KeyValue(translateXProperty(), -32, OUT_WEAK)),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(translateXProperty(), -28, IN_WEAK)),
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(translateXProperty(), -12, OUT_WEAK)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(translateXProperty(), -20, IN_WEAK)),
                    new KeyFrame(Duration.millis(250),
                            new KeyValue(wash.opacityProperty(), 0.0, OUT_WEAK))
            );
            shake.play();
            scheduleHide(holdTime());
        }

        void playExit() {
            if (leaving) return;
            leaving = true;
            if (!AnimationUtils.isAnimationEnabled()) {
                removeSelf();
                return;
            }
            if (enter != null) enter.stop();
            if (shake != null) shake.stop();
            exit = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(translateXProperty(), -20, IN_WEAK),
                            new KeyValue(opacityProperty(), 1, OUT_WEAK)),
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(opacityProperty(), 0, OUT_WEAK)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(translateXProperty(), -70, IN_WEAK))
            );
            exit.setOnFinished(e -> removeSelf());
            exit.play();
        }

        void removeSelf() {
            if (hold != null) hold.stop();
            if (getParent() instanceof VBox box) box.getChildren().remove(this);
        }
    }

    // endregion

    private PCLTheme() {
    }
}
