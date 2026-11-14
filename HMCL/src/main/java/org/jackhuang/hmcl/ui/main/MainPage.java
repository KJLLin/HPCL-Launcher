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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.pcl.PCLLoading;
import org.jackhuang.hmcl.ui.pcl.PCLTheme;
import org.jackhuang.hmcl.ui.versions.GameListPopupMenu;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.download.RemoteVersion.Type.RELEASE;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.ui.FXUtils.SINE;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MainPage extends StackPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final StringProperty currentGame = new SimpleStringProperty(this, "currentGame");
    private final BooleanProperty showUpdate = new SimpleBooleanProperty(this, "showUpdate");
    private final BooleanProperty showUpdateDialog = new SimpleBooleanProperty(this, "showUpdateDialog");
    private final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>(this, "latestVersion");
    private final ObservableList<Version> versions = FXCollections.observableArrayList();
    private HMCLGameRepository repository;

    private final StackPane updatePane;
    private final JFXButton menuButton;
    /// PCL `PanHint CanClose=True`: remembered within one launcher session.
    private static boolean hintDismissed;
    /// PCL `PanLaunching` feedback layered over the bottom of the launch button.
    private final javafx.scene.layout.Region launchProgress = PCLTheme.createLaunchProgress();
    /// PCL `PageLaunchLeft`: the launch button belongs to the LEFT pane, not
    /// to a full-width bar at the bottom of the window. Exposed via
    /// [MainPage#getLaunchBar] so `RootPage` can pin it to the sidebar bottom.
    private final StackPane launchBar = new StackPane();
    /// PCL `PageLaunchLeft.PanLaunching`: a full left-pane overlay shown while
    /// the game boots - pickaxe loading, "正在启动游戏", version name, a 4px
    /// progress strip and a cancel button.
    private final VBox launchingPanel = new VBox(10);
    /// Pickaxe loader inside [launchingPanel] (`MyLoading` with AutoRun).
    private PCLLoading launchingLoader;
    private Label launchingName;
    private Label launchingPercent;
    private javafx.scene.layout.Region launchingFill;
    /// PCL `LabLaunchingStage / Method / Progress / Download` info grid.
    private Label launchingStage;
    private Label launchingMethod;
    private Label launchingProgressValue;
    private Label launchingDownloadLeft;
    private Label launchingDownloadValue;
    private javafx.scene.layout.GridPane launchingInfo;
    /// PCL `PanLaunchingHint`: the "你知道吗" tip shown once launched.
    private javafx.scene.layout.StackPane launchingHint;
    private Label launchingHintText;
    /// PCL `ShowProgress`: displayed progress eases toward the real value by
    /// `shown += (actual - shown) * 0.1 + 0.0025` (LaunchRefresh).
    private double shownProgress;
    private double targetProgress;
    private Timeline launchProgressTimer;
    private Label launchLabel;
    private Label currentLabel;

    private RemoteVersion lastShownVersion;

    {
        HBox titleNode = new HBox(8);
        titleNode.setPadding(new Insets(0, 0, 0, 2));
        titleNode.setAlignment(Pos.CENTER_LEFT);

        ImageView titleIcon = new ImageView(FXUtils.newBuiltinImage("/assets/img/icon-title.png"));
        Label titleLabel = new Label(Metadata.FULL_TITLE);
        if (I18n.isUpsideDown()) {
            titleIcon.setRotate(180);
            titleLabel.setRotate(180);
        }
        titleLabel.getStyleClass().add("jfx-decorator-title");
        // HPCL: the PCL title bar is light, so use the dark ink unconditionally.
        // Upstream bound this to titleFillProperty() which returns white on the
        // opaque title bar, making the title invisible on our light surface.
        titleLabel.setTextFill(javafx.scene.paint.Color.web("#1B2738"));
        titleNode.getChildren().setAll(titleIcon, titleLabel);

        state.setValue(new State(null, titleNode, false, false, true));

        setPadding(new Insets(20));

        // PCL2 has no permanent channel banner on the home page. The old
        // HMCL dev/nightly announcement card is intentionally not built: it
        // covered the top of the card flow with a 190px white slab. Channel
        // information stays available in the settings / about pages.

        updatePane = new StackPane();
        updatePane.setVisible(false);
        updatePane.getStyleClass().add("bubble");
        FXUtils.setLimitWidth(updatePane, 230);
        FXUtils.setLimitHeight(updatePane, 55);
        StackPane.setAlignment(updatePane, Pos.TOP_RIGHT);
        FXUtils.onClicked(updatePane, this::onUpgrade);
        updatePane.setCursor(Cursor.HAND);
        FXUtils.onChange(showUpdateProperty(), this::doAnimation);
        FXUtils.onChange(showUpdateDialogProperty(), this::showUpdateDialog);

        {
            HBox hBox = new HBox();
            hBox.setSpacing(12);
            hBox.setAlignment(Pos.CENTER_LEFT);
            StackPane.setAlignment(hBox, Pos.CENTER_LEFT);
            StackPane.setMargin(hBox, new Insets(9, 12, 9, 16));
            {
                TwoLineListItem prompt = new TwoLineListItem();
                prompt.setSubtitle(i18n("update.bubble.subtitle"));
                prompt.setPickOnBounds(false);
                prompt.titleProperty().bind(BindingMapping.of(latestVersionProperty()).map(latestVersion ->
                        latestVersion == null ? "" : i18n("update.bubble.title", latestVersion.version())));

                hBox.getChildren().setAll(SVG.UPDATE.createIcon(20), prompt);
            }

            JFXButton closeUpdateButton = new JFXButton();
            closeUpdateButton.setGraphic(SVG.CLOSE.createIcon(10));
            StackPane.setAlignment(closeUpdateButton, Pos.TOP_RIGHT);
            closeUpdateButton.getStyleClass().add("toggle-icon-tiny");
            StackPane.setMargin(closeUpdateButton, new Insets(5));
            closeUpdateButton.setOnAction(e -> closeUpdateBubble());

            updatePane.getChildren().setAll(hBox, closeUpdateButton);
        }

        HBox launchPane = new HBox();
        launchPane.getStyleClass().add("launch-pane");
        launchPane.setMaxWidth(Double.MAX_VALUE);
        launchPane.setAlignment(Pos.CENTER);
        FXUtils.onScroll(launchPane, versions, list -> {
            String currentId = getCurrentGame();
            return Lang.indexWhere(list, instance -> instance.getId().equals(currentId));
        }, it -> repository.setSelectedInstance(it.getId()));

        {
            JFXButton launchButton = new JFXButton();
            launchButton.getStyleClass().addAll("launch-button", PCLTheme.LAUNCH_BUTTON);
            launchButton.setDefaultButton(true);
            launchButton.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(launchButton, Priority.ALWAYS);
            PCLTheme.attachButtonHover(launchButton);
            {
                VBox graphic = new VBox();
                graphic.getStyleClass().add("hpcl-launch-graphic");
                graphic.setAlignment(Pos.CENTER);
                launchLabel = new Label();
                launchLabel.getStyleClass().add("hpcl-launch-title");
                currentLabel = new Label();
                // PCL2 `LabVersion`: 11px grey text pinned to the button bottom.
                currentLabel.getStyleClass().add("hpcl-launch-version");

                FXUtils.onChangeAndOperate(currentGameProperty(), new Consumer<>() {
                    private Tooltip tooltip;

                    @Override
                    public void accept(String currentGame) {
                        if (currentGame == null) {
                            launchLabel.setText(i18n("version.launch.empty"));
                            currentLabel.setText(null);
                            graphic.getChildren().setAll(launchLabel);
                            FXUtils.setOnActionWithCooldown(launchButton, () -> {
                                playLaunchFeedback(launchButton);
                                MainPage.this.launchNoGame();
                            });
                            if (tooltip == null)
                                tooltip = new Tooltip(i18n("version.launch.empty.tooltip"));
                            FXUtils.installFastTooltip(launchButton, tooltip);
                        } else {
                            launchLabel.setText(i18n("version.launch"));
                            currentLabel.setText(currentGame);
                            graphic.getChildren().setAll(launchLabel, currentLabel);
                            FXUtils.setOnActionWithCooldown(launchButton, () -> {
                                playLaunchFeedback(launchButton);
                                MainPage.this.launch();
                            });
                            if (tooltip != null)
                                Tooltip.uninstall(launchButton, tooltip);
                        }
                    }
                });

                launchButton.setGraphic(graphic);
            }

            // PCL2 `PageLaunchLeft.xaml` declares ONE 54px BtnLaunch with no
            // attached drop-down; version switching is the separate 35px
            // BtnVersion below it (RootPage#versionSelect). HMCL's white
            // arrow split-button is therefore removed from the layout and
            // kept only as an off-stage action target for compatibility.
            menuButton = new JFXButton();
            menuButton.getStyleClass().addAll("menu-button", PCLTheme.LAUNCH_MENU);
            menuButton.setOnAction(e -> Controllers.navigate(Controllers.getGameListPage()));

            EventHandler<MouseEvent> secondaryClickHandle = event -> {
                if (event.getButton() == MouseButton.SECONDARY && event.getClickCount() == 1) {
                    Controllers.navigate(Controllers.getGameListPage());
                    event.consume();
                }
            };
            launchButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);

            // PCL2 BtnLaunch: a single full-width highlighted button.
            launchPane.getChildren().setAll(launchButton);
            launchProgress.prefWidthProperty().bind(launchButton.widthProperty());
        }

        // PCL PageLaunchLeft.xaml: the launch button is a 54px block inside the
        // 300px left pane (Margin 20,0), NOT a full-width window footer. Package
        // the button row and its progress strip into one node that RootPage
        // pins to the bottom of the sidebar.
        launchBar.getStyleClass().add(PCLTheme.LAUNCH_BAR_ROOT);
        launchPane.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(launchPane, Pos.CENTER);
        // PCL BtnLaunch: Height 54, Margin 20,0 -> 20px breathing room left/right.
        StackPane.setMargin(launchPane, new Insets(0, 0, 0, 0));
        StackPane.setAlignment(launchProgress, Pos.BOTTOM_LEFT);
        launchBar.getChildren().setAll(launchPane, launchProgress);

        // ---- PCL PageLaunchRight.xaml: a scrollable vertical card flow ----
        // StackPanel Margin 25,25,25,10; each MyCard Margin 0,0,0,15.
        VBox cardFlow = new VBox(15);
        cardFlow.getStyleClass().add(PCLTheme.CARD_FLOW);

        // PCL `PanHint CanClose=True`: dismissible for the current session.
        HBox hint = PCLTheme.createHint(i18n("hpcl.home.hint"),
                () -> hintDismissed = true);
        if (hintDismissed) {
            hint.setVisible(false);
            hint.setManaged(false);
        }

        Label currentVersionLabel = new Label();
        currentVersionLabel.getStyleClass().add(PCLTheme.CURRENT_VERSION);
        FXUtils.onChangeAndOperate(currentGameProperty(), value ->
                currentVersionLabel.setText(value == null ? i18n("version.launch.empty") : value));
        VBox versionCard = PCLTheme.createFlowCard(i18n("hpcl.home.current"), currentVersionLabel);

        VBox quickCard = PCLTheme.createFlowCard(i18n("hpcl.home.quick.title"),
                PCLTheme.createCardListItem(SVG.ROCKET_LAUNCH, i18n("version.launch"),
                        i18n("hpcl.home.quick.launch.sub"), () -> {
                            if (getCurrentGame() == null) launchNoGame();
                            else launch();
                        }),
                PCLTheme.createCardListItem(SVG.ADD_CIRCLE, i18n("hpcl.home.quick.install"),
                        i18n("hpcl.home.quick.install.sub"), () -> {
                            Controllers.getDownloadPage().showGameDownloads();
                            Controllers.navigate(Controllers.getDownloadPage());
                        }),
                PCLTheme.createCardListItem(SVG.FORMAT_LIST_BULLETED, i18n("version.manage"),
                        i18n("hpcl.home.quick.versions.sub"),
                        () -> Controllers.navigate(Controllers.getGameListPage())),
                PCLTheme.createCardListItem(SVG.DOWNLOAD, i18n("hpcl.home.quick.content"),
                        i18n("hpcl.home.quick.content.sub"), () -> {
                            Controllers.getDownloadPage().showModpackDownloads();
                            Controllers.navigate(Controllers.getDownloadPage());
                        }));

        // PCL `PageLaunchRight.PanCustom` cards always carry a body; keep the
        // about card from looking like an empty white strip by adding the
        // version line and two PCL-style sided links.
        Label aboutText = new Label(i18n("hpcl.home.about"));
        aboutText.setWrapText(true);
        aboutText.getStyleClass().add("hpcl-about-text");
        Label aboutVersion = new Label(Metadata.FULL_TITLE);
        aboutVersion.getStyleClass().add("hpcl-hub-muted");
        HBox aboutLinks = new HBox(18);
        aboutLinks.setAlignment(Pos.CENTER_LEFT);
        aboutLinks.getChildren().setAll(
                PCLTheme.createSidedLink(i18n("about.open_source"),
                        () -> FXUtils.openLink(Metadata.EULA_URL)),
                PCLTheme.createSidedLink(i18n("help"),
                        () -> Controllers.navigate(Controllers.getMorePage())));
        VBox aboutCard = PCLTheme.createFlowCard(i18n("about"),
                aboutText, aboutVersion, aboutLinks);

        // PCL `PageLaunchRight.PanCustom` preset "你知道吗": a card with a
        // random launcher tip and a refresh MyIconButton in the top-right.
        Label tipText = new Label(randomLaunchHint());
        tipText.setWrapText(true);
        tipText.setMaxWidth(Double.MAX_VALUE);
        tipText.getStyleClass().add("hpcl-hub-text");
        JFXButton tipRefresh = new JFXButton();
        tipRefresh.getStyleClass().add("hpcl-hub-tip-refresh");
        Node tipIcon = SVG.REFRESH.createIcon(16);
        tipIcon.getStyleClass().add("hpcl-hub-tip-refresh-icon");
        tipRefresh.setGraphic(tipIcon);
        tipRefresh.setOnAction(e -> {
            tipText.setText(randomLaunchHint());
            PCLTheme.playTextAppear(tipText, tipText.getText());
        });
        javafx.scene.layout.StackPane tipHead = new javafx.scene.layout.StackPane();
        Label tipTitle = new Label("你知道吗？");
        tipTitle.getStyleClass().add("hpcl-card-title");
        javafx.scene.layout.StackPane.setAlignment(tipTitle, Pos.CENTER_LEFT);
        javafx.scene.layout.StackPane.setAlignment(tipRefresh, Pos.CENTER_RIGHT);
        tipHead.getChildren().setAll(tipTitle, tipRefresh);
        tipHead.setMaxWidth(Double.MAX_VALUE);
        VBox tipCard = PCLTheme.createCard(tipHead, tipText);
        tipCard.getStyleClass().add("hpcl-flow-card");

        cardFlow.getChildren().setAll(hint, versionCard, quickCard, tipCard, aboutCard);

        ScrollPane scroll = new ScrollPane(cardFlow);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hpcl-card-flow-scroll");
        FXUtils.smoothScrolling(scroll);
        StackPane.setAlignment(scroll, Pos.TOP_LEFT);
        // No PCL2 channel banner: the card flow starts at the top.
        StackPane.setMargin(scroll, new Insets(0, 0, 0, 0));

        getChildren().add(0, scroll);
        // PCL `MyPageRight.TriggerEnterAnimation` is applied to every page by
        // DecoratorController.onNavigated, so the launch cards no longer need
        // a separate local stagger (which would double-play the animation).
        getChildren().addAll(updatePane);

    }

    /// PCL PageLaunchLeft: the launch bar lives at the bottom of the left pane.
    public StackPane getLaunchBar() {
        return launchBar;
    }

    /// PCL `PageLaunchLeft.PanLaunching`: the left-pane launching overlay.
    /// `RootPage` stacks it over the sidebar so it can cover the whole pane.
    public VBox getLaunchingPanel() {
        if (launchingLoader == null) buildLaunchingPanel();
        return launchingPanel;
    }

    /// Builds `PanLaunching` from `PageLaunchLeft.xaml`: a centred stack with
    /// the `MyLoading` pickaxe, a 20px skewed title, the version name, a 4px
    /// gradient progress strip and a 35px cancel button.
    private void buildLaunchingPanel() {
        launchingPanel.getStyleClass().add("hpcl-launching-panel");
        launchingPanel.setAlignment(Pos.CENTER);
        launchingPanel.setPadding(new Insets(20, 16, 18, 16));
        // Fill the whole left pane, exactly like PCL's PanLaunching overlay.
        launchingPanel.setMaxWidth(Double.MAX_VALUE);
        launchingPanel.setMaxHeight(Double.MAX_VALUE);
        launchingPanel.setVisible(false);
        launchingPanel.setManaged(false);

        launchingLoader = new PCLLoading();
        launchingLoader.setText("");

        Label title = new Label(i18n("hpcl.launching.title"));
        title.getStyleClass().add("hpcl-launching-title");
        // PCL `SkewTransform AngleX="-3"` on LabLaunchingTitle / Name.
        applySkew(title);

        launchingName = new Label();
        launchingName.getStyleClass().add("hpcl-launching-name");
        applySkew(launchingName);

        // PCL's 4px two-segment strip: a `ColorObject4 -> ColorObject3`
        // gradient on the finished part and `ColorBrush6` at 60% behind it.
        StackPane bar = new StackPane();
        bar.getStyleClass().add("hpcl-launching-bar");
        bar.setMinHeight(4);
        bar.setMaxHeight(4);
        bar.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.layout.Region track = new javafx.scene.layout.Region();
        track.getStyleClass().add("hpcl-launching-bar-track");
        track.setMaxWidth(Double.MAX_VALUE);

        launchingFill = new javafx.scene.layout.Region();
        launchingFill.getStyleClass().add("hpcl-launching-bar-fill");
        launchingFill.setMaxHeight(4);
        javafx.scene.transform.Scale fillScale = new javafx.scene.transform.Scale(0, 1, 0, 0);
        launchingFill.getTransforms().add(fillScale);
        launchingFill.getProperties().put("hpcl.fill.scale", fillScale);
        StackPane.setAlignment(launchingFill, Pos.CENTER_LEFT);

        bar.getChildren().setAll(track, launchingFill);

        // PCL `PanLaunchingInfo`: a 2-column, 4-row grid with right-aligned
        // muted captions and left-aligned values (FontSize 12.5, line gap 5).
        launchingInfo = new javafx.scene.layout.GridPane();
        launchingInfo.getStyleClass().add("hpcl-launching-info");
        launchingInfo.setAlignment(Pos.CENTER);
        launchingInfo.setHgap(15);
        launchingInfo.setVgap(5);
        launchingInfo.setMaxWidth(Double.MAX_VALUE);

        launchingStage = infoValue("下载支持");
        launchingMethod = infoValue("正版登录");
        launchingProgressValue = infoValue("00.00 %");
        launchingDownloadValue = infoValue("0 B/s");
        launchingDownloadLeft = infoCaption("下载速度");
        Label stageLeft = infoCaption("当前步骤");
        Label methodLeft = infoCaption("登录方式");
        Label progressLeft = infoCaption("启动进度");

        launchingInfo.add(stageLeft, 0, 0);
        launchingInfo.add(launchingStage, 1, 0);
        launchingInfo.add(methodLeft, 0, 1);
        launchingInfo.add(launchingMethod, 1, 1);
        launchingInfo.add(progressLeft, 0, 2);
        launchingInfo.add(launchingProgressValue, 1, 2);
        launchingInfo.add(launchingDownloadLeft, 0, 3);
        launchingInfo.add(launchingDownloadValue, 1, 3);
        javafx.scene.layout.GridPane.setHalignment(stageLeft, javafx.geometry.HPos.RIGHT);
        javafx.scene.layout.GridPane.setHalignment(methodLeft, javafx.geometry.HPos.RIGHT);
        javafx.scene.layout.GridPane.setHalignment(progressLeft, javafx.geometry.HPos.RIGHT);
        javafx.scene.layout.GridPane.setHalignment(launchingDownloadLeft, javafx.geometry.HPos.RIGHT);
        // PCL hides the download-speed row when there is no active download.
        launchingDownloadLeft.setVisible(false);
        launchingDownloadValue.setVisible(false);

        // PCL `PanLaunchingHint`: "你知道吗" card, revealed once the game has
        // launched (IsLaunched). 260px wide, a notched top border, 12.5px text.
        launchingHintText = new Label();
        launchingHintText.getStyleClass().add("hpcl-launching-hint-text");
        launchingHintText.setWrapText(true);
        launchingHintText.setMaxWidth(230);
        Label hintCaption = new Label("你知道吗");
        hintCaption.getStyleClass().add("hpcl-launching-hint-caption");
        javafx.scene.layout.VBox hintBody = new javafx.scene.layout.VBox(4, hintCaption, launchingHintText);
        hintBody.setAlignment(Pos.CENTER);
        launchingHint = new javafx.scene.layout.StackPane(hintBody);
        launchingHint.getStyleClass().add("hpcl-launching-hint");
        launchingHint.setVisible(false);
        launchingHint.setManaged(false);
        launchingHint.setOpacity(0);
        launchingHintText.setText(randomLaunchHint());

        JFXButton cancel = new JFXButton(i18n("button.cancel"));
        cancel.getStyleClass().add("hpcl-launching-cancel");
        cancel.setOnAction(e -> hideLaunching());

        launchingPanel.getChildren().setAll(launchingLoader, title, launchingName, bar,
                launchingInfo, launchingHint, cancel);
        VBox.setMargin(bar, new Insets(12, 8, 0, 8));
        VBox.setMargin(launchingInfo, new Insets(2, 8, 0, 8));
        VBox.setMargin(cancel, new Insets(16, 8, 0, 8));
    }

    private static void applySkew(Label label) {
        // PCL SkewTransform AngleX=-3 -> shear factor tan(3deg) ~= 0.0524.
        label.getTransforms().add(new javafx.scene.transform.Shear(-0.0524, 0));
    }

    private static Label infoCaption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hpcl-launching-info-caption");
        return label;
    }

    private static Label infoValue(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hpcl-launching-info-value");
        return label;
    }

    /// PCL `PageOtherTest.GetRandomHint()` picks one of the launcher tips.
    private static String randomLaunchHint() {
        String[] hints = {
                "按住 Shift 点击版本，可以快速打开版本设置哦。",
                "启动器会自动为你匹配最合适的 Java 版本。",
                "在下载页可以安装 Forge、Fabric、NeoForge 与 OptiFine。",
                "右下角的悬浮球能随时打开下载任务管理。",
                "主页可以直接切换账号，不用进入账户列表。"
        };
        return hints[(int) (Math.random() * hints.length)];
    }

    /// PCL `LaunchRefresh`: eases the shown progress toward the real value and
    /// drives the two grid-length segments with a 130ms OutFluent animation.
    public void setLaunchProgress(double actual) {
        targetProgress = Math.max(0, Math.min(1, actual));
        if (launchingLoader == null) return;
        if (launchProgressTimer == null) {
            launchProgressTimer = new Timeline(new KeyFrame(Duration.millis(130), e -> tickLaunchProgress()));
            launchProgressTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
            launchProgressTimer.play();
        }
    }

    private void tickLaunchProgress() {
        if (targetProgress >= shownProgress)
            shownProgress += (targetProgress - shownProgress) * 0.1 + 0.0025;
        else
            shownProgress = targetProgress;
        shownProgress = Math.min(1, shownProgress);
        if (launchingProgressValue != null)
            launchingProgressValue.setText(String.format("%.2f %%", shownProgress * 100));
        Object scaleObj = launchingFill == null ? null : launchingFill.getProperties().get("hpcl.fill.scale");
        if (scaleObj instanceof javafx.scene.transform.Scale scale) {
            scale.setX(shownProgress);
        }
    }

    /// PCL `LabLaunchingDownload`: shows the live speed row only while a
    /// Minecraft launch download is active.
    @SuppressWarnings("FieldCanBeLocal")
    private final Consumer<org.jackhuang.hmcl.task.FetchTask.SpeedEvent> launchingSpeedHandler =
            org.jackhuang.hmcl.task.FetchTask.SPEED_EVENT.registerWeak(
                    event -> javafx.application.Platform.runLater(() -> {
                        if (launchingDownloadValue == null) return;
                        long speed = event.getSpeed();
                        boolean show = speed > 0 && launchingPanel.isVisible();
                        launchingDownloadLeft.setVisible(show);
                        launchingDownloadValue.setVisible(show);
                        launchingDownloadLeft.setManaged(show);
                        launchingDownloadValue.setManaged(show);
                        if (show) launchingDownloadValue.setText(I18n.formatSpeed(speed));
                    }));

    /// Shows `PanLaunching` and starts the pickaxe swing with an indeterminate
    /// sweep (HMCL reports real progress in its own task dialog).
    public void showLaunching() {
        getLaunchingPanel();
        String game = getCurrentGame();
        launchingName.setText(game == null ? "" : game);
        // PCL LaunchStart resets the "你知道吗" hint and the info grid.
        if (launchingHint != null) {
            launchingHint.setVisible(false);
            launchingHint.setManaged(false);
            launchingHint.setOpacity(0);
            launchingHintText.setText(randomLaunchHint());
        }
        shownProgress = 0;
        targetProgress = 0;
        launchingProgressValue.setText("00.00 %");
        launchingStage.setText(i18n("version.launch"));
        launchingMethod.setText(i18n("account.methods.microsoft"));
        launchingPanel.setVisible(true);
        launchingPanel.setManaged(true);
        launchingLoader.start();

        // PCL eases ShowProgress toward the loader's real progress with a
        // 130ms tick; drive it with a synthetic ramp so the strip reads live.
        Object scaleObj = launchingFill.getProperties().get("hpcl.fill.scale");
        if (scaleObj instanceof javafx.scene.transform.Scale scale) {
            if (!AnimationUtils.isAnimationEnabled()) {
                scale.setX(0.35);
            } else {
                Timeline sweep = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(scale.xProperty(), 0.06, PCLTheme.OUT_MIDDLE)),
                        new KeyFrame(Duration.millis(1600), new KeyValue(scale.xProperty(), 0.92, PCLTheme.OUT_MIDDLE)));
                sweep.setCycleCount(javafx.animation.Animation.INDEFINITE);
                sweep.setAutoReverse(true);
                sweep.play();
                launchingFill.getProperties().put("hpcl.fill.anim", sweep);
            }
        }

        // PCL `PageChangeToLaunching`: PanInput fades out (50+110ms) and
        // scales to 1.2, while PanLaunching fades in at 100ms and scales
        // 0.8 -> 1 over 500ms with AniEaseOutBack(Weak).
        if (AnimationUtils.isAnimationEnabled()) {
            launchingPanel.setOpacity(0);
            launchingPanel.setScaleX(0.8);
            launchingPanel.setScaleY(0.8);
            Timeline in = new Timeline(
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(launchingPanel.opacityProperty(), 1, PCLTheme.OUT_WEAK)),
                    new KeyFrame(Duration.millis(600),
                            new KeyValue(launchingPanel.scaleXProperty(), 1, PCLTheme.BACK_WEAK),
                            new KeyValue(launchingPanel.scaleYProperty(), 1, PCLTheme.BACK_WEAK)));
            in.play();
        } else {
            launchingPanel.setOpacity(1);
        }
    }

    /// Hides `PanLaunching` and parks the pickaxe.
    public void hideLaunching() {
        if (launchingLoader == null) return;
        launchingLoader.stop();
        Object anim = launchingFill.getProperties().get("hpcl.fill.anim");
        if (anim instanceof javafx.animation.Animation a) a.stop();
        if (launchProgressTimer != null) {
            launchProgressTimer.stop();
            launchProgressTimer = null;
        }
        Object scaleObj = launchingFill.getProperties().get("hpcl.fill.scale");
        if (scaleObj instanceof javafx.scene.transform.Scale scale) scale.setX(0);
        launchingPanel.setVisible(false);
        launchingPanel.setManaged(false);
        launchingPanel.setOpacity(1);
        launchingPanel.setScaleX(1);
        launchingPanel.setScaleY(1);
    }

    /// PCL `LaunchRefresh` completion: swap the progress row for the
    /// "已启动游戏" title and reveal the "你知道吗" hint.
    public void markLaunchFinished() {
        if (launchingLoader == null) return;
        launchingLoader.stop();
        launchingStage.setText("已启动游戏");
        targetProgress = 1;
        shownProgress = 1;
        tickLaunchProgress();
        if (launchingHint != null) {
            launchingHint.setVisible(true);
            launchingHint.setManaged(true);
            if (AnimationUtils.isAnimationEnabled()) {
                launchingHint.setOpacity(0);
                new Timeline(new KeyFrame(Duration.millis(90),
                        new KeyValue(launchingHint.opacityProperty(), 1, PCLTheme.OUT_WEAK))).play();
            } else {
                launchingHint.setOpacity(1);
            }
        }
    }

    /// Plays the PCL "launching" feedback and restores the button shortly after
    /// the game process has been handed off. The real launch progress is now
    /// rendered by `PCLLaunchProgressPage` (PCL2 PanLaunching + task list),
    /// which is opened by LauncherHelper with the real TaskExecutor; the old
    /// synthetic left-pane overlay is therefore no longer played here.
    private void playLaunchFeedback(JFXButton launchButton) {
        PCLTheme.playLaunchStart(launchButton, launchProgress, launchLabel, currentLabel,
                i18n("hpcl.launch.starting"));
        javafx.animation.PauseTransition restore =
                new javafx.animation.PauseTransition(Duration.seconds(4));
        restore.setOnFinished(e -> {
            PCLTheme.stopLaunchStart(launchButton, launchProgress, launchLabel, currentLabel);
            // Re-apply the current version label after restoring.
            String game = getCurrentGame();
            if (launchLabel != null) launchLabel.setText(game == null ? i18n("version.launch.empty") : i18n("version.launch"));
            if (currentLabel != null) currentLabel.setText(game);
        });
        restore.play();
    }

    /// Creates a PCL-style clickable quick-entry card.
    private VBox createQuickCard(String title, String subtitle, SVG icon, Runnable action) {
        VBox card = new VBox(4);
        card.getStyleClass().add(PCLTheme.QUICK_CARD);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);

        Node iconNode = icon.createIcon(22);
        iconNode.getStyleClass().add("hpcl-quick-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("hpcl-quick-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("hpcl-quick-subtitle");
        card.getChildren().setAll(iconNode, titleLabel, subtitleLabel);
        FXUtils.onClicked(card, action);
        PCLTheme.attachButtonHover(card);
        return card;
    }

    private void showUpdateDialog(boolean show) {
        if (show && getLatestVersion() != null && !Objects.equals(getLatestVersion(), lastShownVersion)
                && !Objects.equals(state().getPromptedVersion(), getLatestVersion().version())
        ) {
            lastShownVersion = getLatestVersion();
            Controllers.dialogLater(new MessageDialogPane.Builder("", i18n("update.bubble.title", getLatestVersion().version()), MessageDialogPane.MessageType.INFO)
                    .addAction(i18n("button.view"), () -> {
                        state().setPromptedVersion(getLatestVersion().version());
                        onUpgrade();
                    })
                    .addCancel(null)
                    .build());
        }
    }

    private void doAnimation(boolean show) {
        if (AnimationUtils.isAnimationEnabled()) {
            Duration duration = Duration.millis(320);
            Timeline nowAnimation = new Timeline();
            nowAnimation.getKeyFrames().addAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(updatePane.translateXProperty(), show ? 260 : 0, SINE)),
                    new KeyFrame(duration,
                            new KeyValue(updatePane.translateXProperty(), show ? 0 : 260, SINE)));
            if (show) nowAnimation.getKeyFrames().add(
                    new KeyFrame(Duration.ZERO, e -> updatePane.setVisible(true)));
            else nowAnimation.getKeyFrames().add(
                    new KeyFrame(duration, e -> updatePane.setVisible(false)));
            nowAnimation.play();
        } else {
            updatePane.setVisible(show);
        }
    }

    private void launch() {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        Versions.launch(repository, repository.getSelectedInstance());
    }

    private void launchNoGame() {
        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
        VersionList<?> versionList = downloadProvider.getVersionListById("game");

        Holder<String> gameVersionHolder = new Holder<>();
        Task<?> task = versionList.refreshAsync("")
                .thenSupplyAsync(() -> versionList.getVersions("").stream()
                        .filter(it -> it.getVersionType() == RELEASE)
                        .filter(it -> NativePatcher.checkSupportedStatus(GameVersionNumber.asGameVersion(it.getGameVersion()), Platform.SYSTEM_PLATFORM, OperatingSystem.SYSTEM_VERSION) != NativePatcher.SupportStatus.UNSUPPORTED)
                        .sorted()
                        .findFirst()
                        .orElseThrow(() -> new IOException("No versions found")))
                .thenComposeAsync(version -> {
                    HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
                    DefaultDependencyManager dependency = repository.getDependency();
                    String gameVersion = gameVersionHolder.value = version.getGameVersion();

                    return dependency.gameBuilder()
                            .name(gameVersion)
                            .gameVersion(gameVersion)
                            .buildAsync();
                })
                .whenComplete(any -> GameDirectoryManager.getSelectedRepository().refreshVersions())
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        GameDirectoryManager.getSelectedRepository().setSelectedInstance(gameVersionHolder.value);
                        launch();
                    } else if (exception instanceof CancellationException) {
                        Controllers.showToast(i18n("message.cancelled"));
                    } else {
                        LOG.warning("Failed to install game", exception);
                        Controllers.dialog(StringUtils.getStackTrace(exception),
                                i18n("install.failed"),
                                MessageDialogPane.MessageType.WARNING);
                    }
                });
        Controllers.taskDialog(task, i18n("version.launch.empty.installing"), TaskCancellationAction.NORMAL);
    }

    private void onUpgrade() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    private void closeUpdateBubble() {
        showUpdate.unbind();
        showUpdate.set(false);
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public GameDirectory getGameDirectory() {
        return repository.getGameDirectory();
    }

    public HMCLGameRepository getRepository() {
        return repository;
    }

    public String getCurrentGame() {
        return currentGame.get();
    }

    public StringProperty currentGameProperty() {
        return currentGame;
    }

    public void setCurrentGame(String currentGame) {
        this.currentGame.set(currentGame);
    }

    public ObservableList<Version> getVersions() {
        return versions;
    }

    public boolean isShowUpdate() {
        return showUpdate.get();
    }

    public BooleanProperty showUpdateProperty() {
        return showUpdate;
    }

    public void setShowUpdate(boolean showUpdate) {
        this.showUpdate.set(showUpdate);
    }

    public boolean isShowUpdateDialog() {
        return showUpdateDialog.get();
    }

    public BooleanProperty showUpdateDialogProperty() {
        return showUpdateDialog;
    }

    public void setShowUpdateDialog(boolean showUpdateDialog) {
        this.showUpdateDialog.set(showUpdateDialog);
    }

    public RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    public ObjectProperty<RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    public void setLatestVersion(RemoteVersion latestVersion) {
        this.latestVersion.set(latestVersion);
    }

    public void initVersions(HMCLGameRepository repository, List<Version> versions) {
        FXUtils.checkFxUserThread();
        this.repository = repository;
        this.versions.setAll(versions);
    }
}
