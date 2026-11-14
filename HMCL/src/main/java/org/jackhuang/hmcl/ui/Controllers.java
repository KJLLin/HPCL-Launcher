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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.PCLAnimations;
import org.jackhuang.hmcl.ui.pcl.PCLDownloadTaskPage;
import org.jackhuang.hmcl.ui.pcl.PCLHubPage;
import org.jackhuang.hmcl.ui.pcl.PCLLaunchProgressPage;
import org.jackhuang.hmcl.ui.pcl.PCLTheme;
import org.jackhuang.hmcl.ui.pcl.PCLVersionSelectPage;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.DecoratorController;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.terracotta.TerracottaPage;
import org.jackhuang.hmcl.ui.versions.GameListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.setting.SettingsManager.userState;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Controllers {
    public static final String JAVA_VERSION_TIP = "javaVersion";
    public static final String JAVA_INTERPRETED_MODE_TIP = "javaInterpretedMode";
    public static final String SOFTWARE_RENDERING = "softwareRendering";
    public static final String APRIL_FOOLS = "aprilFools";

    private static final int CUSTOM_DECORATION_SHADOW_SIZE = 8;
    private static final int CUSTOM_DECORATION_SHADOW_EXTENT = CUSTOM_DECORATION_SHADOW_SIZE * 2;

    public static final int MIN_CONTENT_WIDTH = 800 + 2; // bg width + border width*2
    public static final int MIN_CONTENT_HEIGHT = 450 + 2 + 40; // bg height + border width*2 + toolbar height
    public static final int MIN_WIDTH = MIN_CONTENT_WIDTH + CUSTOM_DECORATION_SHADOW_EXTENT;
    public static final int MIN_HEIGHT = MIN_CONTENT_HEIGHT + CUSTOM_DECORATION_SHADOW_EXTENT;
    public static final Screen SCREEN = Screen.getPrimary();
    private static InvalidationListener stageSizeChangeListener;
    private static DoubleProperty stageX = new SimpleDoubleProperty();
    private static DoubleProperty stageY = new SimpleDoubleProperty();
    private static DoubleProperty stageWidth = new SimpleDoubleProperty();
    private static DoubleProperty stageHeight = new SimpleDoubleProperty();

    private static Scene scene;
    private static Stage stage;
    private static VersionPage versionPage;
    private static Lazy<GameListPage> gameListPage = new Lazy<>(GameListPage::new);
    private static Lazy<RootPage> rootPage = new Lazy<>(RootPage::new);
    private static DecoratorController decorator;
    private static DownloadPage downloadPage;
    private static PCLDownloadTaskPage downloadTaskPage;
    private static PCLHubPage hubPage;
    private static PCLVersionSelectPage versionSelectPage;
    private static PCLLaunchProgressPage launchProgressPage;
    private static Lazy<AccountListPage> accountListPage = new Lazy<>(() -> {
        AccountListPage accountListPage = new AccountListPage();
        accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
        accountListPage.accountsProperty().bindContent(Accounts.getAccounts());
        accountListPage.authServersProperty().bindContentBidirectional(getAuthlibInjectorServers());
        return accountListPage;
    });
    private static LauncherSettingsPage settingsPage;
    private static Lazy<TerracottaPage> terracottaPage = new Lazy<>(TerracottaPage::new);

    private Controllers() {
    }

    /// Action used by confirmation dialogs that may fail before the confirmed operation is complete.
    @FunctionalInterface
    public interface ThrowingRunnable {
        /// Runs the confirmed action.
        ///
        /// @throws Exception if the action fails
        void run() throws Exception;
    }

    public static Scene getScene() {
        return scene;
    }

    public static Stage getStage() {
        return stage;
    }

    @FXThread
    public static VersionPage getVersionPage() {
        if (versionPage == null) {
            versionPage = new VersionPage();
        }
        return versionPage;
    }

    @FXThread
    public static void prepareVersionPage() {
        if (versionPage == null) {
            LOG.info("Prepare the version page");
            versionPage = FXUtils.prepareNode(new VersionPage());
        }
    }

    @FXThread
    public static GameListPage getGameListPage() {
        return gameListPage.get();
    }

    @FXThread
    public static RootPage getRootPage() {
        return rootPage.get();
    }

    @FXThread
    public static LauncherSettingsPage getSettingsPage() {
        if (settingsPage == null) {
            settingsPage = new LauncherSettingsPage();
        }
        return settingsPage;
    }

    @FXThread
    public static void prepareSettingsPage() {
        if (settingsPage == null) {
            LOG.info("Prepare the settings page");
            settingsPage = FXUtils.prepareNode(new LauncherSettingsPage());
        }
    }

    @FXThread
    public static AccountListPage getAccountListPage() {
        return accountListPage.get();
    }

    @FXThread
    public static DownloadPage getDownloadPage() {
        if (downloadPage == null) {
            downloadPage = new DownloadPage();
        }
        return downloadPage;
    }

    @FXThread
    public static void prepareDownloadPage() {
        if (downloadPage == null) {
            LOG.info("Prepare the download page");
            downloadPage = FXUtils.prepareNode(new DownloadPage());
        }
    }

    /// PCL2 `PageType.TaskManager`: reached from the download ball.
    @FXThread
    public static PCLDownloadTaskPage getDownloadTaskPage() {
        if (downloadTaskPage == null) {
            downloadTaskPage = new PCLDownloadTaskPage();
        }
        return downloadTaskPage;
    }

    /// PCL2 `PageType.Other` (FormMain.xaml.vb `BtnTitleSelect4`): the "更多"
    /// tab is a real top-level page (PageOtherLeft + PageOtherRight), not a
    /// popup menu. `getHubPage` is kept as an alias for the visual-test hook.
    @FXThread
    public static PCLHubPage getMorePage() {
        return getHubPage();
    }

    @FXThread
    public static PCLHubPage getHubPage() {
        if (hubPage == null) {
            hubPage = new PCLHubPage();
        }
        return hubPage;
    }

    /// PCL2 `PageSelectRight` version picker: reached from the hub toolbox.
    @FXThread
    public static PCLVersionSelectPage getVersionSelectPage() {
        if (versionSelectPage == null) {
            versionSelectPage = new PCLVersionSelectPage();
        }
        return versionSelectPage;
    }

    /// PCL2 `PageLaunchLeft.PanLaunching` + `PageSpeedRight` task list: the
    /// real game-launch progress page. Replaces HMCL's TaskExecutorDialogPane.
    @FXThread
    public static PCLLaunchProgressPage getLaunchProgressPage() {
        if (launchProgressPage == null) {
            launchProgressPage = new PCLLaunchProgressPage();
        }
        return launchProgressPage;
    }

    /// Opens the PCL2 launch-progress page and binds a real launch executor.
    /// Called by [org.jackhuang.hmcl.game.LauncherHelper] instead of
    /// `Controllers.dialog(launchingStepsPane)`.
    @FXThread
    public static void showLaunchProgress(TaskExecutor executor, String version,
                                          Runnable cancelAction) {
        showLaunchProgress(executor, version, null, cancelAction);
    }

    /// Overload that carries the real [org.jackhuang.hmcl.auth.Account] so the
    /// PCL2 "登录方式" row shows 离线模式 / 外置登录 / 微软账户 correctly.
    @FXThread
    public static void showLaunchProgress(TaskExecutor executor, String version,
                                          org.jackhuang.hmcl.auth.Account account,
                                          Runnable cancelAction) {
        PCLLaunchProgressPage page = getLaunchProgressPage();
        page.bind(executor, version, account, cancelAction);
        if (decorator != null && decorator.getCurrentPage() != page) {
            navigate(page);
        }
    }

    /// Closes the launch-progress page after a launch (navigates home).
    @FXThread
    public static void hideLaunchProgress() {
        if (launchProgressPage != null) launchProgressPage.unbind();
    }

    /// Pushes download speed into the launch page's `PanLaunchingInfo` row.
    @FXThread
    public static void setLaunchSpeed(long bytesPerSecond) {
        if (launchProgressPage != null) launchProgressPage.setSpeed(bytesPerSecond);
    }

    @FXThread
    public static Node getTerracottaPage() {
        return terracottaPage.get();
    }

    @FXThread
    public static DecoratorController getDecorator() {
        return decorator;
    }

    public static void saveWindowStates() {
        saveWindowBounds();
    }

    private static void saveWindowBounds() {
        if (stageX != null) {
            state().setX(toContentX(stageX.get()) / SCREEN.getBounds().getWidth());
        }
        if (stageY != null) {
            state().setY(toContentY(stageY.get()) / SCREEN.getBounds().getHeight());
        }
        if (stageHeight != null) {
            state().setHeight(toContentHeight(stageHeight.get()));
        }
        if (stageWidth != null) {
            state().setWidth(toContentWidth(stageWidth.get()));
        }
    }

    public static void onApplicationStop() {
        stageSizeChangeListener = null;
        saveWindowBounds();
        stageX = null;
        stageY = null;
        stageHeight = null;
        stageWidth = null;
    }

    private static double toContentX(double stageX) {
        return stageX + CUSTOM_DECORATION_SHADOW_SIZE;
    }

    private static double toContentY(double stageY) {
        return stageY + CUSTOM_DECORATION_SHADOW_SIZE;
    }

    private static double toStageX(double contentX) {
        return contentX - CUSTOM_DECORATION_SHADOW_SIZE;
    }

    private static double toStageY(double contentY) {
        return contentY - CUSTOM_DECORATION_SHADOW_SIZE;
    }

    private static double toContentWidth(double stageWidth) {
        return Math.max(0.0, stageWidth - CUSTOM_DECORATION_SHADOW_EXTENT);
    }

    private static double toContentHeight(double stageHeight) {
        return Math.max(0.0, stageHeight - CUSTOM_DECORATION_SHADOW_EXTENT);
    }

    private static double toStageWidth(double contentWidth) {
        return contentWidth + CUSTOM_DECORATION_SHADOW_EXTENT;
    }

    private static double toStageHeight(double contentHeight) {
        return contentHeight + CUSTOM_DECORATION_SHADOW_EXTENT;
    }

    public static void initialize(Stage stage) {
        LOG.info("Start initializing application");

        LOG.info("April Fools: " + AprilFools.isEnabled());

        if (System.getProperty("prism.lcdtext") == null) {
            @Nullable String fontAntiAliasing = SettingsManager.userSettings().fontAntiAliasingProperty().get();
            if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                LOG.info("Enable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "true");
            } else if ("gray".equalsIgnoreCase(fontAntiAliasing)
                    || OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && SCREEN.getOutputScaleX() > 1) {
                LOG.info("Disable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "false");
            }
        }

        Controllers.stage = stage;

        stageSizeChangeListener = o -> {
            ReadOnlyDoubleProperty sourceProperty = (ReadOnlyDoubleProperty) o;
            DoubleProperty targetProperty;
            switch (sourceProperty.getName()) {
                case "x": {
                    targetProperty = stageX;
                    break;
                }
                case "y": {
                    targetProperty = stageY;
                    break;
                }
                case "width": {
                    targetProperty = stageWidth;
                    break;
                }
                case "height": {
                    targetProperty = stageHeight;
                    break;
                }
                default: {
                    targetProperty = null;
                }
            }

            if (targetProperty != null
                    && Controllers.stage != null
                    && !Controllers.stage.isIconified()
                    // https://github.com/HMCL-dev/HMCL/issues/4290
                    && (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS ||
                    !Controllers.stage.isFullScreen() && !Controllers.stage.isMaximized())
            ) {
                targetProperty.set(sourceProperty.get());
            }
        };

        WeakInvalidationListener weakListener = new WeakInvalidationListener(stageSizeChangeListener);

        // HPCL: PCL opens at a roomier ~1180x720 content area. Upstream defaulted
        // to the 802x492 minimum, which made the card layout look cramped.
        double defaultContentWidth = Math.min(1180, SCREEN.getBounds().getWidth() * 0.86);
        double defaultContentHeight = Math.min(720, SCREEN.getBounds().getHeight() * 0.82);
        double savedWidth = state().getWidth();
        double savedHeight = state().getHeight();
        double initContentWidth = Math.max(MIN_CONTENT_WIDTH,
                savedWidth >= MIN_CONTENT_WIDTH ? savedWidth : defaultContentWidth);
        double initContentHeight = Math.max(MIN_CONTENT_HEIGHT,
                savedHeight >= MIN_CONTENT_HEIGHT ? savedHeight : defaultContentHeight);
        double initWidth = toStageWidth(initContentWidth);
        double initHeight = toStageHeight(initContentHeight);

        {
            double initContentX = state().getX() * SCREEN.getBounds().getWidth();
            double initContentY = state().getY() * SCREEN.getBounds().getHeight();

            boolean invalid = true;
            double border = 20D;
            for (Screen screen : Screen.getScreens()) {
                Rectangle2D bound = screen.getBounds();

                if (bound.getMinX() + border <= initContentX + initContentWidth
                        && initContentX <= bound.getMaxX() - border
                        && bound.getMinY() + border <= initContentY
                        && initContentY <= bound.getMaxY() - border) {
                    invalid = false;
                    break;
                }
            }

            if (invalid) {
                initContentX = (0.5D - initContentWidth / SCREEN.getBounds().getWidth() / 2)
                        * SCREEN.getBounds().getWidth();
                initContentY = (0.5D - initContentHeight / SCREEN.getBounds().getHeight() / 2)
                        * SCREEN.getBounds().getHeight();
            }

            double initX = toStageX(initContentX);
            double initY = toStageY(initContentY);
            stage.setX(initX);
            stage.setY(initY);
            stageX.set(initX);
            stageY.set(initY);
        }

        stage.setHeight(initHeight);
        stage.setWidth(initWidth);
        stageHeight.set(initHeight);
        stageWidth.set(initWidth);
        stage.xProperty().addListener(weakListener);
        stage.yProperty().addListener(weakListener);
        stage.heightProperty().addListener(weakListener);
        stage.widthProperty().addListener(weakListener);

        stage.setOnCloseRequest(e -> Launcher.stopApplication());

        decorator = new DecoratorController(stage, getRootPage());
        getRootPage().getMainPage().showUpdateProperty().bind(UpdateChecker.checkingUpdateProperty().not().and(UpdateChecker.outdatedProperty()));
        getRootPage().getMainPage().showUpdateDialogProperty().bind(
                decorator.backableProperty().not()
                        .and(getRootPage().getMainPage().showUpdateProperty())
                        .and(settings().disableAutoShowUpdateDialogProperty().not())
        );

        if (settings().commonDirectoryTypeProperty().get() == EnumCommonDirectory.CUSTOM &&
                !FileUtils.canCreateDirectory(settings().getResolvedCommonDirectory())) {
            settings().commonDirectoryTypeProperty().set(EnumCommonDirectory.DEFAULT);
            dialog(i18n("launcher.cache_directory.invalid"));
        }

        Lang.thread(JavaManager::initialize, "Search Java", true);

        scene = new Scene(decorator.getDecorator());
        scene.setFill(Color.TRANSPARENT);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        decorator.getDecorator().prefWidthProperty().bind(scene.widthProperty());
        decorator.getDecorator().prefHeightProperty().bind(scene.heightProperty());
        StyleSheets.init(scene);

        FXUtils.setIcon(stage);
        stage.setTitle(Metadata.FULL_TITLE);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);

        if (AnimationUtils.playWindowAnimation()) {
            // PCL2 `FormMain_Loaded` "Form Show" entrance, verbatim:
            //   PanBack starts at TranslateY=+60, Rotate=-4deg, opacity 0
            //   AaCode(AniControlEnabled -= 1, 50)
            //   AaOpacity(Me, target, 250, 100)                 -> fade in
            //   AaDouble(TransformPos.Y += -Y, 600, 100, OutBack/Weak)
            //   AaDouble(TransformRotate.Angle += -A, 500, 100, OutBack/Weak)
            //   AaCode(PanBack.RenderTransform = Nothing, After) -> clear
            // All three transforms share the 100 ms delay and OutBack(Weak)
            // easing; the rotation (not a scale) is what gives PCL2 its
            // distinctive slight "tilt into place" opening.
            Node root = decorator.getDecorator();
            root.setOpacity(0);
            root.setTranslateY(60);
            Rotate entranceRotate = new Rotate(-4);
            root.getTransforms().add(entranceRotate);
            // Pivot around the window centre; recompute once laid out.
            root.layoutBoundsProperty().addListener((o, ov, b) -> {
                entranceRotate.setPivotX(b.getWidth() / 2);
                entranceRotate.setPivotY(b.getHeight() / 2);
            });

            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(0),
                            new KeyValue(root.opacityProperty(), 0, PCLTheme.OUT_WEAK),
                            new KeyValue(root.translateYProperty(), 60, PCLTheme.BACK_WEAK),
                            new KeyValue(entranceRotate.angleProperty(), -4, PCLTheme.BACK_WEAK)),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(root.opacityProperty(), 0, PCLTheme.OUT_WEAK),
                            new KeyValue(root.translateYProperty(), 60, PCLTheme.BACK_WEAK),
                            new KeyValue(entranceRotate.angleProperty(), -4, PCLTheme.BACK_WEAK)),
                    // 250 ms fade (100..350)
                    new KeyFrame(Duration.millis(350),
                            new KeyValue(root.opacityProperty(), 1, PCLTheme.OUT_WEAK)),
                    // 500 ms rotation back to 0 (100..600)
                    new KeyFrame(Duration.millis(600),
                            new KeyValue(entranceRotate.angleProperty(), 0, PCLTheme.BACK_WEAK)),
                    // 600 ms slide back to 0 (100..700)
                    new KeyFrame(Duration.millis(700),
                            new KeyValue(root.translateYProperty(), 0, PCLTheme.BACK_WEAK))
            );
            timeline.setOnFinished(e -> {
                // PCL2 clears PanBack.RenderTransform once loading finished.
                root.getTransforms().remove(entranceRotate);
                root.setTranslateY(0);
                root.setOpacity(1);
            });
            timeline.play();
        }

        if (!Architecture.SYSTEM_ARCH.isX86() && SettingsManager.userState().platformPromptVersionProperty().get() < 1) {
            Runnable continueAction = () -> {
                UserState userState = userState();
                userState.platformPromptVersionProperty().set(1);
            };

            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS && Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                continueAction.run();
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                Controllers.dialog(i18n("fatal.unsupported_platform.windows_arm64"), null, MessageType.INFO, continueAction);
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX &&
                    (Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64
                            || Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64_OW
                            || Architecture.SYSTEM_ARCH == Architecture.MIPS64EL)) {
                Controllers.dialog(i18n("fatal.unsupported_platform.loongarch"), null, MessageType.INFO, continueAction);
            } else {
                Controllers.dialog(i18n("fatal.unsupported_platform"), null, MessageType.WARNING, continueAction);
            }
        }

        if (JavaRuntime.CURRENT_VERSION < Metadata.MINIMUM_SUPPORTED_JAVA_VERSION) {
            Number shownTipVersion = null;
            try {
                shownTipVersion = (Number) state().getShownTips().get(JAVA_VERSION_TIP);
            } catch (ClassCastException e) {
                LOG.warning("Invalid type for shown tips key: " + JAVA_VERSION_TIP, e);
            }
            if (shownTipVersion == null || shownTipVersion.intValue() < Metadata.MINIMUM_SUPPORTED_JAVA_VERSION) {
                MessageDialogPane.Builder builder = new MessageDialogPane.Builder(i18n("fatal.deprecated_java_version"), null, MessageType.WARNING);
                String downloadLink = Metadata.getSuggestedJavaDownloadLink();
                if (downloadLink != null)
                    builder.addHyperLink(
                            i18n("fatal.deprecated_java_version.download_link", Metadata.RECOMMENDED_JAVA_VERSION),
                            downloadLink
                    );
                Controllers.dialog(builder
                        .ok(() -> state().getShownTips().put(JAVA_VERSION_TIP, Metadata.MINIMUM_SUPPORTED_JAVA_VERSION))
                        .build());
            }
        }

        // Check whether JIT is enabled in the current environment
        if (!JavaRuntime.CURRENT_JIT_ENABLED && !Boolean.TRUE.equals(state().getShownTips().get(JAVA_INTERPRETED_MODE_TIP))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.java_interpreted_mode"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            state().getShownTips().put(JAVA_INTERPRETED_MODE_TIP, true))
                    .build());
        }

        // Check whether hardware acceleration is enabled
        if (!FXUtils.GPU_ACCELERATION_ENABLED && !Boolean.TRUE.equals(state().getShownTips().get(SOFTWARE_RENDERING))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.software_rendering"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            state().getShownTips().put(SOFTWARE_RENDERING, true))
                    .build());
        }

        if (SettingsManager.userState().agreementVersionProperty().get() < 1) {
            JFXDialogLayout agreementPane = new JFXDialogLayout();
            agreementPane.setHeading(new Label(i18n("launcher.agreement")));
            agreementPane.setBody(new Label(i18n("launcher.agreement.hint")));
            JFXHyperlink agreementLink = new JFXHyperlink(i18n("launcher.agreement"));
            agreementLink.setExternalLink(Metadata.EULA_URL);
            JFXButton yesButton = new JFXButton(i18n("launcher.agreement.accept"));
            yesButton.getStyleClass().add("dialog-accept");
            yesButton.setOnAction(e -> {
                UserState userState = userState();
                userState.agreementVersionProperty().set(1);
                agreementPane.fireEvent(new DialogCloseEvent());
            });
            JFXButton noButton = new JFXButton(i18n("launcher.agreement.decline"));
            noButton.getStyleClass().add("dialog-cancel");
            noButton.setOnAction(e -> javafx.application.Platform.exit());
            agreementPane.setActions(agreementLink, yesButton, noButton);
            Controllers.dialog(agreementPane);
        }

        aprilFools:
        if (AprilFools.isEnabled()) {
            int currentYear = LocalDate.now().getYear();
            if (state().getShownTips().get(APRIL_FOOLS) instanceof Number year && year.intValue() >= currentYear)
                break aprilFools;

            if (!I18n.getLocale().getLocale().getLanguage().equals("zh"))
                break aprilFools;

            SupportedLocale lzh = SupportedLocale.getSupportedLocales().stream()
                    .filter(locale -> "lzh".equals(locale.getName()))
                    .findFirst().orElse(null);

            if (lzh == null) {
                LOG.warning("No supported locale found for lzh");
                break aprilFools;
            }

            Runnable updateShowTips = () -> state().getShownTips().put(APRIL_FOOLS, currentYear);

            Controllers.confirmWithCountdown(i18n("launcher.april_fools.switch_lzh"), null, 10,
                    MessageType.QUESTION, () -> {
                        Controllers.confirm(i18n("launcher.april_fools.switch_lzh.confirm"), null, MessageType.QUESTION, () -> {
                            LOG.info("Switching locale to " + lzh);

                            updateShowTips.run();
                            settings().languageProperty().set(lzh);

                            Controllers.onApplicationStop();

                            try {
                                FileSaver.waitForAllSaves();
                            } catch (InterruptedException ignored) {
                                // Ignore
                            }

                            try {
                                Restarter.restartSelf();
                            } catch (IOException e) {
                                LOG.warning("Failed to restart self", e);
                            }

                            Platform.exit();
                        }, updateShowTips);
                    }, updateShowTips);
        }
    }

    public static void dialog(Region content) {
        if (decorator != null) {
            // PCL never covers the launcher with HMCL's modal task-progress
            // dialog. Every TaskExecutorDialogPane (game download / Java
            // download / modpack install / launcher steps) is docked to the
            // non-modal bottom-right PCL task layer instead.
            if (content instanceof TaskExecutorDialogPane pane
                    && decorator.showTaskPanel(pane)) {
                return;
            }
            decorator.showDialog(content);
        }
    }

    public static void dialog(String text) {
        dialog(text, null);
    }

    public static void dialog(String text, String title) {
        dialog(text, title, MessageType.INFO);
    }

    public static void dialog(String text, String title, MessageType type) {
        dialog(text, title, type, null);
    }

    public static void dialog(String text, String title, MessageType type, Runnable ok) {
        dialog(new MessageDialogPane.Builder(text, title, type).ok(ok).build());
    }

    public static void confirm(String text, String title, Runnable yes, Runnable no) {
        confirm(text, title, MessageType.QUESTION, yes, no);
    }

    public static void confirm(String text, String title, MessageType type, Runnable yes, Runnable no) {
        dialog(new MessageDialogPane.Builder(text, title, type).yesOrNo(yes, no).build());
    }

    /// Shows a warning that confirms backing up a read-only settings file before overwriting it.
    ///
    /// @param text the file-specific read-only warning
    /// @param overwrite the action that backs up and overwrites the file
    public static void confirmBackupAndOverwrite(String text, ThrowingRunnable overwrite) {
        dialog(new MessageDialogPane.Builder(
                text + "\n\n" + i18n("settings.file.force_write.confirm"),
                i18n("message.warning"),
                MessageType.WARNING)
                .addAction(i18n("settings.file.force_write"), () -> {
                    try {
                        overwrite.run();
                    } catch (Exception e) {
                        LOG.warning("Failed to force overwrite settings file", e);
                        dialog(i18n("message.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                i18n("message.error"),
                                MessageType.ERROR);
                    }
                })
                .addCancel(null)
                .build());
    }

    public static void confirmAction(String text, String title, MessageType type, ButtonBase actionButton) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, null).build());
    }

    public static void confirmAction(String text, String title, MessageType type, ButtonBase actionButton, Runnable cancel) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, cancel).build());
    }

    public static void confirmWithCountdown(String text, String title, int seconds, MessageType messageType,
                                            @Nullable Runnable ok, @Nullable Runnable cancel) {
        if (seconds <= 0)
            throw new IllegalArgumentException("Seconds must be greater than 0");

        JFXButton btnOk = new JFXButton(i18n("button.ok"));
        btnOk.getStyleClass().add(messageType == MessageType.WARNING || messageType == MessageType.ERROR
                ? "dialog-error"
                : "dialog-accept");

        if (ok != null)
            btnOk.setOnAction(e -> ok.run());
        btnOk.setDisable(true);

        KeyFrame[] keyFrames = new KeyFrame[seconds + 1];
        for (int i = 0; i < seconds; i++) {
            keyFrames[i] = new KeyFrame(Duration.seconds(i),
                    new KeyValue(btnOk.textProperty(), i18n("button.ok.countdown", seconds - i)));
        }
        keyFrames[seconds] = new KeyFrame(Duration.seconds(seconds),
                new KeyValue(btnOk.textProperty(), i18n("button.ok")),
                new KeyValue(btnOk.disableProperty(), false));

        Timeline timeline = new Timeline(keyFrames);
        confirmAction(text, title, messageType, btnOk, () -> {
            timeline.stop();
            if (cancel != null)
                cancel.run();
        });
        timeline.play();
    }

    public static void dialogLater(Region content) {
        if (decorator != null)
            decorator.showDialogLater(content);
    }

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult) {
        return prompt(title, onResult, "");
    }

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult, String initialValue, ValidatorBase... validators) {
        InputDialogPane pane = new InputDialogPane(title, initialValue, onResult, validators);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    public static CompletableFuture<List<PromptDialogPane.Builder.Question<?>>> prompt(PromptDialogPane.Builder builder) {
        PromptDialogPane pane = new PromptDialogPane(builder);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    public static TaskExecutorDialogPane taskDialog(TaskExecutor executor, String title, @NotNull TaskCancellationAction onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setExecutor(executor);
        dialog(pane);
        return pane;
    }

    public static TaskExecutorDialogPane taskDialog(Task<?> task, String title, @NotNull TaskCancellationAction onCancel) {
        TaskExecutor executor = task.executor();
        TaskExecutorDialogPane pane = taskDialog(executor, title, onCancel);
        executor.start();
        return pane;
    }

    public static void navigate(Node node) {
        decorator.navigate(node, PCLAnimations.FORWARD, PCLAnimations.DURATION, PCLAnimations.EASE);
    }

    public static void navigateForward(Node node) {
        decorator.navigate(node, PCLAnimations.FORWARD, PCLAnimations.DURATION, PCLAnimations.EASE);
    }

    /// PCL `PageChange(PageType.Launch)`: clear the stack and return home.
    @FXThread
    public static void backToHome() {
        if (decorator != null) decorator.clearToHome();
    }

    public static void showToast(String content) {
        decorator.showToast(content);
    }

    public static void onHyperlinkAction(String href) {
        if (href.startsWith("hmcl://")) {
            switch (href) {
                case "hmcl://settings/feedback":
                    Controllers.getSettingsPage().showFeedback();
                    Controllers.navigate(Controllers.getSettingsPage());
                    break;
                case "hmcl://game/launch":
                    var repository = GameDirectoryManager.getSelectedRepository();
                    Versions.launch(repository, repository.getSelectedInstance(), LauncherHelper::setKeep);
                    break;
            }
        } else {
            FXUtils.openLink(href);
        }
    }

    public static boolean isStopped() {
        return decorator == null;
    }

    public static void shutdown() {
        rootPage = null;
        versionPage = null;
        gameListPage = null;
        downloadPage = null;
        accountListPage = null;
        settingsPage = null;
        terracottaPage = null;
        decorator = null;
        stage = null;
        scene = null;
        onApplicationStop();

        FXUtils.shutdown();
    }
}
