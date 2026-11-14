/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.InstallerItem;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.pcl.PCLTheme;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;

import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class AbstractInstallersPage extends Control implements WizardPage {
    public static final String FABRIC_QUILT_API_TIP = "fabricQuiltApi";
    protected final WizardController controller;

    protected InstallerItem.InstallerItemGroup group;
    protected JFXTextField txtName = new JFXTextField();

    protected BooleanProperty installable = new SimpleBooleanProperty();

    public AbstractInstallersPage(WizardController controller, String gameVersion, DownloadProvider downloadProvider) {
        this.controller = controller;
        this.group = new InstallerItem.InstallerItemGroup(gameVersion, getInstallerItemStyle());

        for (InstallerItem library : group.getLibraries()) {
            String libraryId = library.getLibraryId();
            if (libraryId.equals(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId())) continue;
            library.setOnInstall(() -> {
                if (!Boolean.TRUE.equals(state().getShownTips().get(FABRIC_QUILT_API_TIP))
                        && (LibraryAnalyzer.LibraryType.FABRIC_API.getPatchId().equals(libraryId)
                        || LibraryAnalyzer.LibraryType.QUILT_API.getPatchId().equals(libraryId)
                        || LibraryAnalyzer.LibraryType.LEGACY_FABRIC_API.getPatchId().equals(libraryId))) {
                    Controllers.dialog(new MessageDialogPane.Builder(
                            i18n("install.installer.fabric-quilt-api.warning", i18n("install.installer." + libraryId)),
                            i18n("message.warning"),
                            MessageDialogPane.MessageType.WARNING
                    ).ok(null).addCancel(i18n("button.do_not_show_again"), () -> state().getShownTips().put(FABRIC_QUILT_API_TIP, true)).build());
                }

                if (!(library.resolvedStateProperty().get() instanceof InstallerItem.IncompatibleState))
                    controller.onNext(
                            new VersionsPage(
                                    controller,
                                    i18n("install.installer.choose", i18n("install.installer." + libraryId)),
                                    gameVersion,
                                    downloadProvider,
                                    libraryId,
                                    () -> controller.onPrev(false, Navigation.NavigationDirection.PREVIOUS)
                            ), Navigation.NavigationDirection.NEXT
                    );
            });
            library.setOnRemove(() -> {
                controller.getSettings().remove(libraryId);
                reload();
            });
        }
    }

    protected InstallerItem.Style getInstallerItemStyle() {
        // PCL2 PageDownloadInstall stacks each loader as a full-width 40px
        // MyCard row (Title="Forge" etc.), not a grid of square cards.
        return InstallerItem.Style.LIST_ITEM;
    }

    @Override
    public abstract String getTitle();

    protected abstract void reload();

    @Override
    public void onNavigate(SettingsMap settings) {
        reload();
    }

    @Override
    public abstract void cleanup(SettingsMap settings);

    protected abstract void onInstall();

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallersPageSkin(this);
    }

    protected static class InstallersPageSkin extends SkinBase<AbstractInstallersPage> {
        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected InstallersPageSkin(AbstractInstallersPage control) {
            super(control);

            BorderPane root = new BorderPane();
            root.getStyleClass().add("hpcl-install-page");
            root.setPadding(new Insets(10, 25, 25, 25));

            // --- PCL2 CardSelectName: a MyCard with a big name field on top ---
            {
                HBox versionNamePane = new HBox(10);
                versionNamePane.getStyleClass().addAll("hpcl-flow-card", "hpcl-install-name-card");
                versionNamePane.setStyle("-fx-padding: 16 16 16 18");
                versionNamePane.setAlignment(Pos.CENTER_LEFT);
                versionNamePane.setMaxWidth(Double.MAX_VALUE);

                Label caption = new Label(i18n("version.name"));
                caption.getStyleClass().add("hpcl-install-name-caption");
                control.txtName.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(control.txtName, Priority.ALWAYS);
                versionNamePane.getChildren().setAll(caption, control.txtName);
                root.setTop(versionNamePane);
                BorderPane.setMargin(versionNamePane, new Insets(6, 0, 0, 0));
            }

            // --- PCL2 PanSelect: a vertical stack of full-width MyCard rows ---
            {
                InstallerItem[] libraries = control.group.getLibraries();
                VBox libraryList = new VBox(10);
                libraryList.getStyleClass().add("hpcl-install-list");
                libraryList.setFillWidth(true);
                for (InstallerItem item : libraries) {
                    item.setMaxWidth(Double.MAX_VALUE);
                    VBox.setVgrow(item, Priority.NEVER);
                    libraryList.getChildren().add(item);
                }
                libraryList.setPadding(new Insets(14, 2, 14, 2));

                ScrollPane scrollPane = new ScrollPane(libraryList);
                scrollPane.getStyleClass().add("hpcl-card-flow-scroll");
                scrollPane.setFitToWidth(true);
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                root.setCenter(scrollPane);
                BorderPane.setMargin(scrollPane, new Insets(12, 0, 12, 0));
            }

            // --- PCL2 BtnStart: a wide, centred, blue pill "开始下载" button ---
            {
                JFXButton installButton = new JFXButton("开始下载");
                installButton.getStyleClass().addAll("jfx-button", "hpcl-launch-button",
                        "hpcl-download-start-button");
                installButton.disableProperty().bind(control.installable.not());
                installButton.setPrefWidth(220);
                installButton.setPrefHeight(44);
                installButton.setMaxWidth(Region.USE_PREF_SIZE);
                installButton.setGraphic(SVG.DOWNLOAD.createIcon(18));
                installButton.setGraphicTextGap(10);
                installButton.setOnAction(e -> control.onInstall());
                PCLTheme.attachButtonHover(installButton);

                StackPane footer = new StackPane(installButton);
                footer.setPadding(new Insets(6, 0, 2, 0));
                root.setBottom(footer);
            }

            getChildren().setAll(root);
        }
    }
}
