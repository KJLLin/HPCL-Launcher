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
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXButton;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane.AnimationProducer;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.main.AboutPage;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.pcl.PCLDownloadCenter;
import org.jackhuang.hmcl.ui.pcl.PCLDownloadTaskPage;
import org.jackhuang.hmcl.ui.pcl.PCLHubPage;
import org.jackhuang.hmcl.ui.pcl.PCLTheme;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DecoratorController {
    private final Decorator decorator;
    private final Navigator navigator;
    /// Global PCL-style top navigation bar, visible on every page.
    private final PCLTheme.GlobalTopBar topBar;
    /// PCL `FrmMain.PanHint`: bottom-left stack of gradient toast capsules.
    private final PCLTheme.ToastLayer toastLayer;
    /// PCL's floating download ball, anchored to the bottom-right corner.
    private final org.jackhuang.hmcl.ui.pcl.PCLDownloadBall downloadBall =
            new org.jackhuang.hmcl.ui.pcl.PCLDownloadBall();
    private static final String TASK_HANDLER_KEY = "hpcl.task.close.handler";

    public DecoratorController(Stage stage, Node mainPage) {
        decorator = new Decorator(stage);
        decorator.titleTransparentProperty().bind(Themes.titleBarTransparentProperty());

        topBar = createTopBar();
        // PCL `PanTitle` is the only title bar, so it owns window dragging.
        decorator.capableDraggingWindow(topBar);
        // Keep HMCL's double-click-to-maximize on the new blue title bar,
        // ignoring clicks that land on the pills or the window buttons.
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            topBar.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) return;
                Node target = e.getTarget() instanceof Node n ? n : null;
                while (target != null && target != topBar) {
                    if (target instanceof javafx.scene.control.ButtonBase) return;
                    target = target.getParent();
                }
                stage.setMaximized(!stage.isMaximized());
                e.consume();
            });
        }
        toastLayer = PCLTheme.createToastLayer();

        navigator = new Navigator();
        navigator.setOnNavigated(this::onNavigated);
        navigator.init(mainPage);

        BorderPane contentRoot = new BorderPane();
        contentRoot.setTop(topBar);
        contentRoot.setCenter(navigator);
        // PCL anchors PanHint at the bottom left; keep it as a sibling of the
        // content root inside the decorator's StackPane content placeholder.
        StackPane.setAlignment(toastLayer, Pos.BOTTOM_LEFT);
        // PCL keeps its download-speed ball at the bottom-right, above page
        // content and below modal dialogs.
        StackPane.setAlignment(downloadBall, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(downloadBall, new javafx.geometry.Insets(0, 20, 20, 0));
        // PCL2 has no floating white square progress box. Download progress
        // lives on the blue `BtnExtraDownload` ball (bottom-right) and in the
        // TaskManager card flow only; completion is reported via PCL Hint() ->
        // Toast. So the task-panel layer is intentionally NOT docked here.
        decorator.getContent().setAll(contentRoot, downloadBall, toastLayer);
        // PCL `BtnExtraDownload_Click`: the download ball opens TaskManager.
        downloadBall.setOnOpen(() -> {
            if (!(navigator.getCurrentPage() instanceof PCLDownloadTaskPage))
                Controllers.navigate(Controllers.getDownloadTaskPage());
        });
        decorator.onCloseNavButtonActionProperty().set(e -> close());
        decorator.onBackNavButtonActionProperty().set(e -> back());
        decorator.onRefreshNavButtonActionProperty().set(e -> refresh());

        setupAuthlibInjectorDnD();

        decorator.contentBackgroundProperty().bind(Themes.backgroundProperty());

        // pass key events to current dialog / current page
        decorator.addEventFilter(KeyEvent.ANY, e -> {
            if (!(e.getTarget() instanceof Node t)) {
                return;
            }

            Node newTarget;

            JFXDialogPane currentDialogPane = null;
            if (decorator.getDrawerWrapper() != null) {
                currentDialogPane = (JFXDialogPane) decorator.getDrawerWrapper().getProperties().get(DialogUtils.PROPERTY_DIALOG_PANE_INSTANCE);
            }

            if (currentDialogPane != null && currentDialogPane.peek().isPresent()) {
                newTarget = currentDialogPane.peek().get();
            } else {
                newTarget = navigator.getCurrentPage();
            }
            boolean needsRedirect = true;

            while (t != null) {
                if (t == newTarget) {
                    // current event target is in newTarget
                    needsRedirect = false;
                    break;
                }
                t = t.getParent();
            }
            if (!needsRedirect) {
                return;
            }

            e.consume();
            newTarget.fireEvent(e.copyFor(e.getSource(), newTarget));
        });

        // press ESC to go back
        onEscPressed(navigator, this::back);

        // https://github.com/HMCL-dev/HMCL/issues/4290
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            // press F11 to toggle full screen
            navigator.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.F11) {
                    stage.setFullScreen(!stage.isFullScreen());
                    e.consume();
                }
            });
        }

        navigator.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.BACK) {
                back();
                e.consume();
            }
        });

        // Opt-in visual regression hooks (disabled by default, no effect on
        // normal users). -Dhpcl.visual.test keeps the three PCL HintType
        // capsules on screen (also exercising the duplicate-hint shake);
        // -Dhpcl.dialog.test opens a sample MyMsgText-style dialog card.
        if (System.getProperty("hpcl.visual.test") != null) {
            javafx.animation.Timeline loop = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.seconds(2.5), e -> {
                        toastLayer.show("HPCL 启动器已就绪", PCLTheme.ToastKind.INFO);
                        toastLayer.show("已复制到剪贴板", PCLTheme.ToastKind.SUCCESS);
                        toastLayer.show("无法连接到更新服务器", PCLTheme.ToastKind.ERROR);
                    }));
            loop.setCycleCount(javafx.animation.Animation.INDEFINITE);
            loop.play();
        }
        if (System.getProperty("hpcl.dialog.test") != null) {
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(24000));
            delay.setOnFinished(e -> {
                MessageDialogPane dialog = new MessageDialogPane.Builder(
                        "弹窗卡片按 PCL MyMsgText 复刻：圆角 7、浅灰白底、23px 标题与 2px 分隔线、15px 正文，并以倾斜回弹动画进场。",
                        "弹窗卡片验证", MessageDialogPane.MessageType.INFO)
                        .ok(null).build();
                showDialog(dialog);
            });
            delay.play();
        }
        // -Dhpcl.launching.test shows the PCL PanLaunching left-pane overlay
        // (pickaxe + 正在启动游戏 + progress strip + cancel) for screenshots.
        if (System.getProperty("hpcl.launching.test") != null) {
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1500));
            delay.setOnFinished(e -> {
                Controllers.getRootPage().getMainPage().showLaunching();
                Controllers.getRootPage().getMainPage().setLaunchProgress(0.69);
            });
            delay.play();
        }
        // -Dhpcl.hub.test / -Dhpcl.versions.test open the new PCL2-style pages
        // (PageOtherLeft hub and PageSelectRight picker) with their pickaxe
        // loader cards, for real-session captures.
        if (System.getProperty("hpcl.hub.test") != null) {
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1400));
            delay.setOnFinished(e -> Controllers.navigate(Controllers.getHubPage()));
            delay.play();
        }
        if (System.getProperty("hpcl.versions.test") != null) {
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1400));
            delay.setOnFinished(e -> Controllers.navigate(Controllers.getVersionSelectPage()));
            delay.play();
        }
        // -Dhpcl.ball.test keeps the floating download ball on screen with a
        // fixed speed so the widget can be captured in a real session.
        if (System.getProperty("hpcl.ball.test") != null) {
            javafx.animation.Timeline loop = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(500),
                            e -> downloadBall.showTestSpeed(1_572_864L)));
            loop.setCycleCount(javafx.animation.Animation.INDEFINITE);
            loop.play();
        }
        // -Dhpcl.task.test demonstrates PCL2's completion Hint (toast). PCL2
        // has no floating white square task card, so nothing is docked.
        if (System.getProperty("hpcl.task.test") != null) {
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1800));
            delay.setOnFinished(e -> showToast(i18n("message.downloading") + " · 1.5 MB/s",
                    PCLTheme.ToastKind.INFO));
            delay.play();
        }
        // -Dhpcl.tasks.test fabricates live download tasks so TaskManager,
        // the ball and the statistics sidebar can be captured in a real
        // session without performing a network download.
        if (System.getProperty("hpcl.tasks.test") != null) {
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1200));
            delay.setOnFinished(e -> {
                PCLDownloadCenter center = PCLDownloadCenter.getInstance();
                PCLDownloadCenter.DownloadTask a = center.createDemoTask(
                        "下载 Minecraft 1.20.4",
                        "client-1.20.4.jar", "libraries / guava", "libraries / gson",
                        "assets / index", "natives / lwjgl", "版本 JSON");
                PCLDownloadCenter.DownloadTask b = center.createDemoTask(
                        "安装 Fabric Loader 0.15.7",
                        "fabric-loader.jar", "intermediary 映射", "Fabric API");
                for (int i = 0; i < 4; i++) center.tickDemo(a, 0.16);
                for (int i = 0; i < 2; i++) center.tickDemo(b, 0.21);
                javafx.animation.Timeline tick = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(Duration.millis(800), ev -> {
                            center.tickDemo(a, 0.09);
                            center.tickDemo(b, 0.07);
                        }));
                tick.setCycleCount(javafx.animation.Animation.INDEFINITE);
                tick.play();
                Controllers.navigate(Controllers.getDownloadTaskPage());
            });
            delay.play();
        }
        // -Dhpcl.launchpage.test opens the PCL2 game-launch progress page
        // driven by a real TaskExecutor, for real-session screenshots.
        // -Dhpcl.launchpage.offline.test additionally supplies a genuine
        // OFFLINE account so the "登录方式" row can be verified to read
        // 离线模式 instead of 微软账户 (the bug this guard protects against).
        if (System.getProperty("hpcl.launchpage.test") != null
                || System.getProperty("hpcl.launchpage.offline.test") != null) {
            boolean offline = System.getProperty("hpcl.launchpage.offline.test") != null;
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1400));
            delay.setOnFinished(e -> {
                org.jackhuang.hmcl.task.Task<?> chain = org.jackhuang.hmcl.task.Task.allOf(
                        org.jackhuang.hmcl.task.Task.runAsync("检查游戏完整性", () -> Thread.sleep(5000)),
                        org.jackhuang.hmcl.task.Task.runAsync("检查 Java 运行时", () -> Thread.sleep(9000)),
                        org.jackhuang.hmcl.task.Task.runAsync("补全游戏依赖", () -> Thread.sleep(20000)),
                        org.jackhuang.hmcl.task.Task.runAsync("登录账户", () -> Thread.sleep(13000)),
                        org.jackhuang.hmcl.task.Task.runAsync("等待游戏启动", () -> Thread.sleep(26000)));
                org.jackhuang.hmcl.task.TaskExecutor exec = chain.executor();
                if (offline) {
                    org.jackhuang.hmcl.auth.Account account =
                            org.jackhuang.hmcl.setting.Accounts.FACTORY_OFFLINE
                                    .create("Steve", java.util.UUID.randomUUID());
                    Controllers.showLaunchProgress(exec, "1.20.4-forge", account, exec::cancel);
                } else {
                    Controllers.showLaunchProgress(exec, "1.20.4-forge", exec::cancel);
                }
                exec.start();
            });
            delay.play();
        }
        // -Dhpcl.realtask.test runs a genuine TaskExecutor through
        // Controllers.taskDialog, proving the registry captures real task
        // events (onRunning/onFinished/onStop) instead of only demo data.
        if (System.getProperty("hpcl.realtask.test") != null) {
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(1200));
            delay.setOnFinished(e -> {
                org.jackhuang.hmcl.task.Task<?> chain = org.jackhuang.hmcl.task.Task.allOf(
                        org.jackhuang.hmcl.task.Task.runAsync("client-1.20.4.jar", () -> Thread.sleep(14000)),
                        org.jackhuang.hmcl.task.Task.runAsync("libraries / guava", () -> Thread.sleep(9000)),
                        org.jackhuang.hmcl.task.Task.runAsync("libraries / gson", () -> Thread.sleep(6000)),
                        org.jackhuang.hmcl.task.Task.runAsync("assets / index", () -> Thread.sleep(11000)));
                Controllers.taskDialog(chain, "下载 Minecraft 1.20.4", TaskCancellationAction.NO_CANCEL);
                Controllers.navigate(Controllers.getDownloadTaskPage());
            });
            delay.play();
        }
    }

    public Decorator getDecorator() {
        return decorator;
    }

    /// Builds PCL2's five-tab PanTitleSelect. Every tab is a real top-level
    /// page (FormMain.xaml.vb `PageChangeActual`): 启动 / 下载 / 联机 / 设置 /
    /// 更多. In particular `更多` opens `PageType.Other` (PCLMorePage), NOT a
    /// dropdown menu — the old ContextMenu was an HMCL-ism absent from PCL2.
    private PCLTheme.GlobalTopBar createTopBar() {
        return PCLTheme.createGlobalTopBar(
                // 0 启动: PCL returns to the root of the page stack instead of
                // pushing another home page onto it.
                () -> navigator.clear(),
                // 1 下载
                () -> {
                    Controllers.getDownloadPage().showGameDownloads();
                    Controllers.navigate(Controllers.getDownloadPage());
                },
                // 2 联机 (PCL `PageType.Link`): HPCL's real online feature.
                () -> Controllers.navigate(Controllers.getTerracottaPage()),
                // 3 设置
                () -> {
                    Controllers.getSettingsPage().showGameSettings(GameDirectoryManager.getSelectedRepository());
                    Controllers.navigate(Controllers.getSettingsPage());
                },
                // 4 更多 (PCL `PageType.Other` -> PageOtherLeft).
                () -> Controllers.navigate(Controllers.getMorePage()),
                // PCL `BtnTitleInner` (back), `BtnTitleMin`, `BtnTitleClose`.
                this::back,
                decorator::minimize,
                decorator::close);
    }

    /// PCL `FormMain.PageNameGet`: a display name for sub-pages. HMCL pages
    /// implementing DecoratorPage expose it through State.title(); the few
    /// plain pages (AboutPage, AccountListPage, ...) are mapped by type.
    private static String pageTitleOf(Node node) {
        if (node instanceof DecoratorPage dp) {
            DecoratorPage.State state = dp.stateProperty().get();
            if (state != null && state.title() != null && !state.title().isEmpty())
                return state.title();
        }
        if (node instanceof AboutPage) return i18n("about");
        if (node instanceof org.jackhuang.hmcl.ui.account.AccountListPage) return i18n("account");
        if (node instanceof org.jackhuang.hmcl.ui.versions.GameListPage) return i18n("version.manage");
        if (node instanceof org.jackhuang.hmcl.ui.terracotta.TerracottaControllerPage) return i18n("terracotta");
        return Metadata.NAME;
    }

    // ==== Navigation ====

    public void navigate(Node node, AnimationProducer animationProducer, Duration duration, Interpolator interpolator) {
        navigator.navigate(node, animationProducer, duration, interpolator);
    }

    /// Currently displayed page (used to avoid re-navigating to the same page).
    public Node getCurrentPage() {
        return navigator.getCurrentPage();
    }

    /// PCL `PageChange(PageType.Launch)`: home wins over any sub-page stack.
    public void clearToHome() {
        navigator.clear();
    }

    public BooleanProperty backableProperty() {
        return navigator.backableProperty();
    }

    private void close() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

            if (page.isPageCloseable()) {
                page.closePage();
                return;
            }
        }
        navigator.clear();
    }

    private void back() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

            if (page.back()) {
                if (navigator.canGoBack()) {
                    navigator.close();
                }
            }
        } else {
            if (navigator.canGoBack()) {
                navigator.close();
            }
        }
    }

    private void refresh() {
        if (navigator.getCurrentPage() instanceof Refreshable) {
            Refreshable refreshable = (Refreshable) navigator.getCurrentPage();

            if (refreshable.refreshableProperty().get())
                refreshable.refresh();
        }
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (event.getSource() != this.navigator) return;
        Node to = event.getNode();

        // Keep the PCL2 PanTitleSelect highlight in sync with the page.
        // Indices follow FormMain.xaml BtnTitleSelect0..4:
        // 0 启动 / 1 下载 / 2 联机 / 3 设置 / 4 更多.
        if (to instanceof RootPage
                || to instanceof org.jackhuang.hmcl.ui.pcl.PCLLaunchProgressPage) topBar.setActive(0);
        else if (to instanceof DownloadPage || to instanceof PCLDownloadTaskPage) topBar.setActive(1);
        else if (to instanceof org.jackhuang.hmcl.ui.terracotta.TerracottaPage
                || to instanceof org.jackhuang.hmcl.ui.terracotta.TerracottaControllerPage)
            topBar.setActive(2);
        else if (to instanceof LauncherSettingsPage) topBar.setActive(3);
        else if (to instanceof PCLHubPage) topBar.setActive(4);
        else topBar.setActive(-1);

        // PCL `BtnExtraDownload_ShowCheck`: the ball hides while TaskManager
        // is open, so it never overlaps the very list it opens.
        downloadBall.setTaskPageVisible(to instanceof PCLDownloadTaskPage);

        // PCL `PageChangeActual`: the four top-level pages show PanTitleMain
        // (the centred pills); every deeper page swaps to PanTitleInner with
        // a back arrow and the page name, using PCL's 150/200/350ms timings.
        boolean topLevel = to instanceof RootPage
                || to instanceof DownloadPage
                || to instanceof LauncherSettingsPage
                || to instanceof org.jackhuang.hmcl.ui.terracotta.TerracottaPage
                || to instanceof PCLHubPage;
        if (topLevel) {
            topBar.showMain();
        } else {
            topBar.showInner(pageTitleOf(to));
        }

        if (to instanceof Refreshable) {
            decorator.canRefreshProperty().bind(((Refreshable) to).refreshableProperty());
        } else {
            decorator.canRefreshProperty().unbind();
            decorator.canRefreshProperty().set(false);
        }

        decorator.canCloseProperty().set(navigator.size() > 2);

        if (to instanceof DecoratorPage) {
            decorator.showCloseAsHomeProperty().set(!((DecoratorPage) to).isPageCloseable());
        } else {
            decorator.showCloseAsHomeProperty().set(true);
        }

        decorator.setNavigationDirection(event.getDirection());

        // state property should be updated at last.
        if (to instanceof DecoratorPage) {
            decorator.stateProperty().bind(((DecoratorPage) to).stateProperty());
        } else {
            decorator.stateProperty().unbind();
            decorator.stateProperty().set(new DecoratorPage.State("", null, navigator.canGoBack(), false, true));
        }

        if (to instanceof Region) {
            Region region = (Region) to;
            // Let root pane fix window size.
            StackPane parent = (StackPane) region.getParent();
            region.prefWidthProperty().bind(parent.widthProperty());
            region.prefHeightProperty().bind(parent.heightProperty());
        }

        // PCL `PageChangeAnim` runs BOTH rails on *every* page change:
        // MyPageRight stagger for the content and MyPageLeft 0.96 scale +
        // decreasing horizontal stagger for the left rail. Defer one pulse so
        // the TransitionPane container animation has already been initialised.
        Platform.runLater(() -> {
            PCLTheme.playPageEnter(to);
            // RootPage already starts its own sidebar animation in its Skin.
            if (to instanceof DecoratorAnimatedPage && !(to instanceof RootPage))
                PCLTheme.playSidebarEnter(((DecoratorAnimatedPage) to).getLeft());
        });
    }

    // ==== Dialog ====
    public void showDialog(Node node) {
        DialogUtils.show(decorator, node);
    }

    // ==== PCL task handling (replaces HMCL's modal task-progress dialog) ====

    /// Intercepts a task pane. PCL2 shows download progress on the floating
    /// ball and in the TaskManager card flow (both driven by
    /// [org.jackhuang.hmcl.ui.pcl.PCLDownloadCenter]) — there is no floating
    /// white square progress box. We only keep the PCL `Hint()` completion
    /// toast. Returns true so [Controllers#dialog] skips the JFXDialog.
    @SuppressWarnings("unchecked")
    public boolean showTaskPanel(TaskExecutorDialogPane pane) {
        if (pane == null) return false;
        // Launch steps are rendered by the PCL2 launch-progress page, so
        // swallow them here instead of docking a redundant task card.
        if (pane.isSuppressFromTaskLayer()) return true;

        // LauncherHelper reuses one pane for the whole session, so drop the
        // previous close handler before registering a new one.
        Object previous = pane.getProperties().get(TASK_HANDLER_KEY);
        if (previous instanceof javafx.event.EventHandler<?> handler) {
            pane.removeEventHandler(DialogCloseEvent.CLOSE,
                    (javafx.event.EventHandler<DialogCloseEvent>) handler);
        }
        javafx.event.EventHandler<DialogCloseEvent> closeHandler = e -> notifyTaskFinished(pane);
        pane.addEventHandler(DialogCloseEvent.CLOSE, closeHandler);
        pane.getProperties().put(TASK_HANDLER_KEY, closeHandler);
        return true;
    }

    /// PCL `ModMain.Hint`: a compact coloured toast when a download ends.
    private void notifyTaskFinished(TaskExecutorDialogPane pane) {
        if (pane.isUserClosed()) return;
        if (pane.isCancelled()) {
            showToast(i18n("hpcl.task.cancelled"), PCLTheme.ToastKind.INFO);
        } else if (pane.isFailed()) {
            showToast(i18n("hpcl.task.failed") + "：" + pane.getTitle(),
                    PCLTheme.ToastKind.ERROR);
        } else {
            showToast(pane.getTitle() + " · " + i18n("message.success"),
                    PCLTheme.ToastKind.SUCCESS);
        }
    }

    private void closeDialog(Node node) {
        DialogUtils.close(node);
    }

    public void showDialogLater(Node node) {
        DialogUtils.showLater(decorator, node);
    }

    // ==== Toast ====

    public void showToast(String content) {
        // PCL `Hint()` carries a HintType (Blue / Green / Red). HMCL's toast
        // API only passes a string, so infer the closest PCL colour from the
        // message; callers can still force a kind via showToast(content, kind).
        showToast(content, inferToastKind(content));
    }

    public void showToast(String content, PCLTheme.ToastKind kind) {
        toastLayer.show(content, kind);
    }

    private static PCLTheme.ToastKind inferToastKind(String content) {
        if (content == null) return PCLTheme.ToastKind.INFO;
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        if (content.contains("失败") || content.contains("错误") || content.contains("无法")
                || content.contains("中断") || lower.contains("failed") || lower.contains("error")) {
            return PCLTheme.ToastKind.ERROR;
        }
        if (content.contains("成功") || content.contains("完成") || content.contains("已复制")
                || lower.contains("success") || lower.contains("copied")) {
            return PCLTheme.ToastKind.SUCCESS;
        }
        return PCLTheme.ToastKind.INFO;
    }

    // ==== Wizard ====

    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    public void startWizard(WizardProvider wizardProvider, String category) {
        FXUtils.checkFxUserThread();

        navigator.navigate(new DecoratorWizardDisplayer(wizardProvider, category),
                ContainerAnimations.FORWARD, Motion.SHORT4, Motion.EASE);
    }

    // ==== Authlib Injector DnD ====

    private void setupAuthlibInjectorDnD() {
        decorator.addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        decorator.addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
    }
}
