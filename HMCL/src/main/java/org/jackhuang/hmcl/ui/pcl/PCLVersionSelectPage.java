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

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.ArrayList;
import java.util.List;

/// PCL2 `PageSelectRight.xaml`: a card flow of installed versions, an empty
/// state ("无可用版本" + a download button) and a centred `MyLoading` card
/// ("正在获取版本列表") shown while the list is being read.
///
/// This is the third place PCL2 renders `MyLoading`. HPCL reaches it from the
/// "版本选择" entry in the More hub; the loader card plays PCL's entrance /
/// exit while the version list is enumerated on the FX thread.
public final class PCLVersionSelectPage extends StackPane implements DecoratorPage {

    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle("版本选择"));
    private final VBox flow = new VBox(15);
    private final ScrollPane scroll = new ScrollPane(flow);
    private final PCLPageLoader loader = new PCLPageLoader("正在获取版本列表", true);
    private final VBox emptyCard = new VBox(10);

    public PCLVersionSelectPage() {
        getStyleClass().add("hpcl-hub-page");
        setAlignment(Pos.CENTER);

        flow.getStyleClass().add("hpcl-hub-flow");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("hpcl-hub-scroll");

        buildEmptyCard();
        emptyCard.setVisible(false);
        emptyCard.setManaged(false);
        // StackPane stretches children by default; keep the empty card a
        // compact centred card instead of a full-height white sheet.
        emptyCard.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        emptyCard.setMinWidth(320);
        emptyCard.setPrefWidth(360);

        getChildren().setAll(scroll, emptyCard, loader);
        StackPane.setAlignment(emptyCard, Pos.CENTER);
        StackPane.setAlignment(loader, Pos.CENTER);

        // PCL PageLoaderInit: LoaderWait -> LoaderEnter -> LoaderStayForce ->
        // LoaderExit -> ContentEnter, with the pickaxe card fading out first.
        loader.show();
        reload();
    }

    private void buildEmptyCard() {
        emptyCard.getStyleClass().addAll("hpcl-flow-card", "hpcl-task-empty");
        emptyCard.setAlignment(Pos.CENTER);
        emptyCard.setPadding(new Insets(30, 24, 30, 24));
        Label title = new Label("无可用版本");
        title.getStyleClass().add("hpcl-task-empty-title");
        Region line = new Region();
        line.getStyleClass().add("hpcl-hub-divider");
        line.setMaxWidth(220);
        Label body = new Label("未找到任何已安装的游戏版本，请先下载。");
        body.getStyleClass().add("hpcl-hub-text");
        body.setWrapText(true);
        Label go = new Label("下载游戏");
        go.getStyleClass().add("hpcl-hub-link");
        go.setOnMouseClicked(e -> {
            Controllers.getDownloadPage().showGameDownloads();
            Controllers.navigate(Controllers.getDownloadPage());
        });
        emptyCard.getChildren().setAll(title, line, body, go);
    }

    /// Enumerates the installed versions and plays PCL's loader exit.
    public void reload() {
        loader.show();
        javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(Duration.millis(560));
        wait.setOnFinished(e -> {
            List<Node> cards = new ArrayList<>();
            try {
                var versions = GameDirectoryManager.getSelectedRepository().getVersions();
                String selected = GameDirectoryManager.getSelectedRepository().getSelectedInstance();
                for (Version v : versions) {
                    if (v.isHidden()) continue;
                    cards.add(versionCard(v, v.getId().equals(selected)));
                }
            } catch (Exception ignored) {
            }
            loader.setProgress(1);
            loader.hide(() -> {
                boolean empty = cards.isEmpty();
                emptyCard.setVisible(empty);
                emptyCard.setManaged(empty);
                flow.getChildren().setAll(cards);
                PCLPageLoader.reveal(flow);
            });
        });
        wait.play();
    }

    private static VBox versionCard(Version version, boolean selected) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("hpcl-flow-card", "hpcl-version-card");
        card.setPadding(new Insets(12, 16, 13, 16));

        Node icon = SVG.STADIA_CONTROLLER.createIcon(22);
        icon.getStyleClass().add("hpcl-version-icon");
        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().add("hpcl-version-icon-box");

        Label name = new Label(version.getId());
        name.getStyleClass().add("hpcl-version-name");
        String v = version.getVersion();
        Label detail = new Label(v == null || v.isEmpty() ? "本地版本" : v);
        detail.getStyleClass().add("hpcl-hub-muted");

        VBox labels = new VBox(2, name, detail);
        labels.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labels, Priority.ALWAYS);

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        Label badge = new Label(selected ? "当前版本" : "选择");
        badge.getStyleClass().add(selected ? "hpcl-hub-badge" : "hpcl-hub-link");

        HBox row = new HBox(12, iconBox, labels, spring, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().setAll(row);
        card.setOnMouseClicked(e -> {
            GameDirectoryManager.setSelectedInstance(version.getId());
            Controllers.getRootPage().getMainPage().setCurrentGame(version.getId());
        });
        PCLTheme.attachPCLButtonPress(card);
        return card;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        reload();
    }
}
