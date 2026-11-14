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

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// PCL2 `PageType.Other` — the FIFTH PanTitleSelect tab ("更多", FormMain.xaml
/// BtnTitleSelect4). PCL2 opens a real page here (`PageChangeActual` ->
/// `PageOtherLeft` + `FrmOtherLeft.PageGet(SubType)`); it is NOT a dropdown
/// menu. The left rail reproduces PageOtherLeft.xaml's five `MyListItem`
/// RadioBox rows — 帮助 / 关于与鸣谢 / 百宝箱 / 反馈 / 新功能投票 — and the
/// right pane swaps its card flow per row, just like the VB `PageGet`.
///
/// HPCL-specific capabilities (system information, skin preview, version and
/// task pages) live inside 百宝箱 so the tab still exposes every real feature.
/// The first visit shows PCL's `MyPageRight.PageLoaderInit` pickaxe card, the
/// second place PCL2 renders `MyLoading` besides the launch overlay.
public final class PCLHubPage extends BorderPane implements DecoratorPage {

    private enum Section {
        HELP("帮助", SVG.HELP),
        ABOUT("关于与鸣谢", SVG.INFO),
        TOOLBOX("百宝箱", SVG.PACKAGE2),
        FEEDBACK("反馈", SVG.FEEDBACK),
        VOTE("新功能投票", SVG.GRAPH2);

        final String title;
        final SVG icon;
        Section(String title, SVG icon) { this.title = title; this.icon = icon; }
    }

    private record HelpEntry(String title, String subtitle, String answer) {}

    private static final List<HelpEntry> HELP_ENTRIES = List.of(
            new HelpEntry("如何启动游戏", "版本、Java 与账号的最低要求",
                    "在左栏选择要启动的游戏版本与账号，点击蓝色「开始游戏」。首次启动会自动下载缺少的依赖，进度会显示在右下角蓝色悬浮球中。"),
            new HelpEntry("安装新版本或整合包", "下载页的原版游戏、Mod、整合包",
                    "切到顶部「下载」页，选择版本或整合包后点击安装；下载任务可在「下载管理」里查看实时速度与进度。"),
            new HelpEntry("正版与离线登录", "微软账号 / 离线账号的区别",
                    "主页左栏的正版 / 离线标签可切换登录方式。正版需要微软账号授权；离线账号只需一个玩家名即可进入游戏。"),
            new HelpEntry("联机大厅", "陶瓦联机与多人游戏",
                    "顶部「联机」页进入多人联机大厅。若当前平台不受支持，页面会给出对应提示。"),
            new HelpEntry("下载速度慢", "更换下载源与并发数",
                    "在「设置」→ 全局设置里切换下载源，或降低并发下载数后重试。"),
            new HelpEntry("启动失败 / 缺少 Java", "Java 运行时自动补全",
                    "HPCL 会为游戏自动匹配 Java。若仍失败，请在设置中检查 Java 路径，并查看启动日志定位错误。"),
            new HelpEntry("界面与动画", "PCL2 风格的页面切换",
                    "页面切换、卡片进入、按钮按压与通知提示均按 PCL2 的缓动曲线实现；可在设置中关闭动画以适配低性能设备。")
    );

    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle("更多"));
    private final VBox flow = new VBox(15);
    private final VBox sidebar = new VBox(2);
    private final StackPane contentStack = new StackPane();
    private final ScrollPane scroll = new ScrollPane(flow);
    private final PCLPageLoader loader = new PCLPageLoader("正在加载", true);
    private final List<HBox> navItems = new ArrayList<>();
    private Section current;
    private boolean loaded;
    private VBox helpList;

    public PCLHubPage() {
        getStyleClass().add("hpcl-hub-page");
        setLeft(buildSidebar());
        setCenter(buildContent());
        // PCL `PageOtherLeft.TriggerShowAnimation`: the rail scales from 0.96
        // and its five MyListItem rows slide in horizontally with a decreasing
        // stagger, just like every other left pane.
        Platform.runLater(() -> PCLTheme.playSidebarEnter(sidebar));

        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("hpcl-hub-scroll");
        flow.getStyleClass().add("hpcl-hub-flow");

        contentStack.getChildren().setAll(scroll, loader);
        StackPane.setAlignment(loader, Pos.CENTER);

        // PCL PageLoaderInit: the loader card waits, then exits while the
        // content plays its staggered entrance. Show it once per session.
        loader.show();
        javafx.animation.PauseTransition delay =
                new javafx.animation.PauseTransition(Duration.millis(700));
        delay.setOnFinished(e -> {
            // -Dhpcl.loader.hold keeps the pickaxe card for screenshot checks.
            if (System.getProperty("hpcl.loader.hold") != null) return;
            loader.setProgress(1);
            loader.hide(() -> {
                loaded = true;
                PCLPageLoader.reveal(flow);
            });
        });
        delay.play();

        select(Section.HELP);
    }

    /// PCL `PageOtherLeft.xaml`: a 200px rail of `MyListItem RadioBox` rows,
    /// with the launcher version pinned at the bottom.
    private VBox buildSidebar() {
        VBox box = sidebar;
        box.getStyleClass().add("hpcl-hub-sidebar");
        for (Section s : Section.values()) {
            HBox item = navItem(s);
            navItems.add(item);
            box.getChildren().add(item);
        }
        Region spring = new Region();
        VBox.setVgrow(spring, Priority.ALWAYS);
        box.getChildren().add(spring);
        Label foot = new Label(Metadata.FULL_TITLE);
        foot.getStyleClass().add("hpcl-hub-muted");
        foot.setWrapText(true);
        box.getChildren().add(foot);
        return box;
    }

    private HBox navItem(Section section) {
        HBox item = new HBox(10);
        item.getStyleClass().add("hpcl-hub-nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        Node icon = section.icon.createIcon(18);
        Label text = new Label(section.title);
        text.getStyleClass().add("hpcl-hub-nav-text");
        item.getChildren().setAll(icon, text);
        item.setOnMouseClicked(e -> select(section));
        PCLTheme.attachPCLButtonPress(item);
        return item;
    }

    private Region buildContent() {
        return contentStack;
    }

    private void select(Section section) {
        if (current == section && !flow.getChildren().isEmpty()) return;
        current = section;
        for (int i = 0; i < navItems.size(); i++) {
            boolean active = Section.values()[i] == section;
            if (active) {
                if (!navItems.get(i).getStyleClass().contains("hpcl-hub-nav-active"))
                    navItems.get(i).getStyleClass().add("hpcl-hub-nav-active");
            } else {
                navItems.get(i).getStyleClass().remove("hpcl-hub-nav-active");
            }
        }
        List<Node> cards = new ArrayList<>();
        switch (section) {
            case HELP -> buildHelp(cards);
            case ABOUT -> buildAbout(cards);
            case TOOLBOX -> buildToolbox(cards);
            case FEEDBACK -> buildFeedback(cards);
            case VOTE -> buildVote(cards);
        }
        flow.getChildren().setAll(cards);
        scroll.setVvalue(0);
        if (loaded && AnimationUtils.isAnimationEnabled())
            PCLTheme.playEntrance(new ArrayList<>(cards));
    }

    // ==== card builders =================================================

    private static VBox card(String title, Node... children) {
        return PCLTheme.createFlowCard(title, children);
    }

    private static Label text(String value, String style) {
        Label label = new Label(value);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static HBox link(String label, String url) {
        return actionLink(label, () -> FXUtils.openLink(url));
    }

    private static HBox actionLink(String label, Runnable action) {
        Label l = new Label(label);
        l.getStyleClass().add("hpcl-hub-link");
        HBox box = new HBox(l);
        box.setCursor(javafx.scene.Cursor.HAND);
        box.setOnMouseClicked(e -> action.run());
        return box;
    }

    /// PCL `PageOtherHelp.xaml`: `MySearchBox` above a `MyCard` list. The
    /// search box filters the entries in place (PCL2 filters its help list).
    private void buildHelp(List<Node> out) {
        TextField search = new TextField();
        search.getStyleClass().add("hpcl-hub-search");
        search.setMaxWidth(Double.MAX_VALUE);
        // PCL MySearchBox uses HintText; JavaFX's prompt text can miss the CJK
        // face on some JREs, so overlay a real Label instead.
        Label hint = new Label("搜索帮助");
        hint.getStyleClass().add("hpcl-hub-search-hint");
        hint.setMouseTransparent(true);
        StackPane searchBox = new StackPane(search, hint);
        StackPane.setAlignment(hint, Pos.CENTER_LEFT);
        StackPane.setMargin(hint, new Insets(0, 0, 0, 12));
        helpList = new VBox(6);
        helpList.getStyleClass().add("hpcl-hub-help-list");
        refreshHelp("");
        search.textProperty().addListener((obs, old, value) -> {
            hint.setVisible(value == null || value.isEmpty());
            refreshHelp(value == null ? "" : value);
        });
        out.add(card("帮助", searchBox, helpList));
        out.add(card("快速上手",
                text("① 在「下载」页安装游戏版本或整合包。\n" +
                        "② 返回主页，用左栏选择版本与账号。\n" +
                        "③ 点击「启动游戏」即可进入 Minecraft。", "hpcl-hub-text"),
                PCLTheme.createHint("点击任意帮助条目可展开详细说明；下载时右下角的蓝色悬浮球可以打开任务管理。"),
                link("打开完整帮助文档", Metadata.CONTACT_URL)));
    }

    private void refreshHelp(String query) {
        if (helpList == null) return;
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Node> rows = new ArrayList<>();
        for (HelpEntry entry : HELP_ENTRIES) {
            if (!q.isEmpty() && !(entry.title() + entry.subtitle() + entry.answer())
                    .toLowerCase(Locale.ROOT).contains(q)) continue;
            rows.add(expandableHelpItem(entry));
        }
        if (rows.isEmpty()) rows.add(text("没有匹配的帮助条目，换个关键词试试。", "hpcl-hub-muted"));
        helpList.getChildren().setAll(rows);
    }

    private static VBox expandableHelpItem(HelpEntry entry) {
        VBox box = new VBox(0);
        HBox row = PCLTheme.createCardListItem(SVG.HELP, entry.title(), entry.subtitle(), () -> {});
        Label answer = text(entry.answer(), "hpcl-hub-text");
        answer.getStyleClass().add("hpcl-hub-answer");
        answer.setVisible(false);
        answer.setManaged(false);
        box.getChildren().setAll(row, answer);
        row.setOnMouseClicked(e -> {
            boolean show = !answer.isVisible();
            answer.setVisible(show);
            answer.setManaged(show);
            if (show && AnimationUtils.isAnimationEnabled())
                PCLTheme.playEntrance(List.of(answer));
        });
        return box;
    }

    private void buildAbout(List<Node> out) {
        out.add(card("关于 " + Metadata.NAME,
                text(Metadata.FULL_TITLE + "\n基于 " + Metadata.UPSTREAM_NAME
                        + " v" + Metadata.UPSTREAM_VERSION
                        + "，界面与动画按 PCL 开源实现复刻。", "hpcl-hub-text"),
                statRow("当前版本", Metadata.VERSION),
                statRow("构建通道", Metadata.BUILD_CHANNEL),
                link("开源许可 · GPLv3", Metadata.EULA_URL)));
        out.add(card("致谢",
                text("感谢 HMCL 与 PCL 开源社区提供的实现参考。本启动器仅复用其设计参数，代码为独立实现。", "hpcl-hub-text"),
                link("HMCL 项目主页", Metadata.PUBLISH_URL),
                actionLink("查看许可证与依赖", () ->
                        Controllers.navigate(new org.jackhuang.hmcl.ui.main.AboutPage()))));
    }

    private static HBox statRow(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("hpcl-hub-muted");
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        Label v = new Label(value);
        v.getStyleClass().add("hpcl-hub-text");
        HBox row = new HBox(10, k, spring, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /// PCL `PageOtherTest` (百宝箱): quick entries plus HPCL's real tools.
    private void buildToolbox(List<Node> out) {
        out.add(card("快速入口",
                PCLTheme.createCardListItem(SVG.PACKAGE2, "版本选择", "PCL2 风格的版本选择页",
                        () -> Controllers.navigate(Controllers.getVersionSelectPage())),
                PCLTheme.createCardListItem(SVG.DOWNLOAD, "下载游戏", "安装新版本与整合包",
                        () -> { Controllers.getDownloadPage().showGameDownloads();
                                Controllers.navigate(Controllers.getDownloadPage()); }),
                PCLTheme.createCardListItem(SVG.FORMAT_LIST_BULLETED, "下载任务", "查看实时下载进度",
                        () -> Controllers.navigate(Controllers.getDownloadTaskPage())),
                PCLTheme.createCardListItem(SVG.SETTINGS, "全局设置", "Java、下载源与外观",
                        () -> Controllers.navigate(Controllers.getSettingsPage())),
                PCLTheme.createCardListItem(SVG.FOLDER_OPEN, "版本管理", "管理已安装的游戏版本",
                        () -> Controllers.navigate(Controllers.getGameListPage())),
                PCLTheme.createCardListItem(SVG.PERSON, "账户管理", "添加或切换游戏账号",
                        () -> Controllers.navigate(Controllers.getAccountListPage()))));
        Runtime rt = Runtime.getRuntime();
        out.add(card("运行环境",
                statRow("Java 版本", System.getProperty("java.version", "?")),
                statRow("Java 供应商", System.getProperty("java.vendor", "?")),
                statRow("操作系统", System.getProperty("os.name", "?") + " "
                        + System.getProperty("os.arch", "")),
                statRow("当前平台", OperatingSystem.CURRENT_OS.name()),
                statRow("最大内存", rt.maxMemory() / (1024 * 1024) + " MB"),
                statRow("已分配内存", rt.totalMemory() / (1024 * 1024) + " MB"),
                statRow("可用处理器", Integer.toString(rt.availableProcessors())),
                statRow("界面语言", I18n.getLocale().getName())));
        String user = Accounts.getSelectedAccount() == null
                ? "（未登录）" : Accounts.getSelectedAccount().getProfileName();
        HBox skins = new HBox(18);
        skins.setAlignment(Pos.CENTER_LEFT);
        skins.getChildren().setAll(skinBox("Steve", "/assets/img/pcl/steve.png"),
                skinBox("Alex", "/assets/img/pcl/alex.png"));
        out.add(card("内置皮肤 · 当前账号：" + user, skins));
        out.add(card("关于本页",
                text("此页面按 PCL2 的 PageOtherLeft / PageOtherTest 结构制作，内容取自 HPCL 实际功能与运行环境。", "hpcl-hub-muted")));
    }

    private static VBox skinBox(String name, String resource) {
        ImageView view = new ImageView();
        try {
            view.setImage(new Image(resource, 64, 64, true, true));
        } catch (Exception ignored) {
        }
        view.setFitWidth(64);
        view.setFitHeight(64);
        view.setPreserveRatio(true);
        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("hpcl-hub-skin-frame");
        frame.setMinSize(72, 72);
        frame.setPrefSize(72, 72);
        frame.setMaxSize(72, 72);
        Label label = new Label(name);
        label.getStyleClass().add("hpcl-hub-muted");
        VBox box = new VBox(6, frame, label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /// PCL `PageOtherLeft.ItemFeedback`.
    private void buildFeedback(List<Node> out) {
        out.add(card("反馈问题",
                text("遇到崩溃、下载失败或界面异常？请先准备好启动日志与复现步骤，再通过下面的入口提交。", "hpcl-hub-text"),
                link("提交反馈", Metadata.CONTACT_URL),
                link("查看帮助文档", Metadata.CONTACT_URL)));
        out.add(card("反馈小贴士",
                text("· 说明 HPCL 版本、操作系统与 Java 版本。\n" +
                        "· 附上启动日志（设置 → 日志）而不是截图。\n" +
                        "· 下载类问题请注明所用下载源。", "hpcl-hub-text")));
    }

    /// PCL `PageOtherLeft.ItemVote` — new-feature voting.
    private void buildVote(List<Node> out) {
        out.add(card("新功能投票",
                text("PCL2 在「更多」页提供新功能投票。这里列出 HPCL 后续可以优先完善的方向，欢迎在社区反馈。", "hpcl-hub-text"),
                voteRow("更完整的联机大厅", "联机页功能与状态提示"),
                voteRow("主题与背景自定义", "更多 PCL2 风格配色"),
                voteRow("整合包一键更新", "自动比对版本差异"),
                link("前往社区讨论", Metadata.CONTACT_URL)));
    }

    private static HBox voteRow(String title, String subtitle) {
        Label badge = new Label("候选");
        badge.getStyleClass().add("hpcl-hub-badge");
        VBox texts = new VBox(1,
                text(title, "hpcl-hub-text"),
                text(subtitle, "hpcl-hub-muted"));
        HBox row = new HBox(12, badge, texts);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        if (current == Section.TOOLBOX) {
            // Rebuild the live runtime numbers.
            List<Node> cards = new ArrayList<>();
            buildToolbox(cards);
            flow.getChildren().setAll(cards);
        }
    }

    @Override
    public void onDecoratorPageNavigating(org.jackhuang.hmcl.ui.construct.Navigator.NavigationEvent event) {
        // The "More" page paints its own PCL surfaces.
    }
}
