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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import org.jackhuang.hmcl.game.TexturesLoader;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaMetadata;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.account.AccountListPopupMenu;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.ui.nbt.NBTFileType;
import org.jackhuang.hmcl.ui.pcl.PCLTheme;
import org.jackhuang.hmcl.ui.versions.GameAdvancedListItem;
import org.jackhuang.hmcl.ui.versions.GameListPopupMenu;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class RootPage extends DecoratorAnimatedPage implements DecoratorPage {
    private MainPage mainPage = null;

    public RootPage() {
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class)
                .register(event -> onRefreshedVersions((HMCLGameRepository) event.getSource()));

        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        if (repository.isLoaded())
            onRefreshedVersions(GameDirectoryManager.getSelectedRepository());

        getStyleClass().remove("gray-background");
        getLeft().getStyleClass().add("gray-background");
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return getMainPage().stateProperty();
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    public MainPage getMainPage() {
        if (mainPage == null) {
            MainPage mainPage = new MainPage();
            FXUtils.applyDragListener(mainPage,
                    file -> ModpackHelper.isFileModpackByExtension(file) || NBTFileType.isNBTFileByExtension(file) || "json".equalsIgnoreCase(FileUtils.getExtension(file)),
                    modpacks -> {
                        Path file = modpacks.get(0);
                        if (ModpackHelper.isFileModpackByExtension(file)) {
                            Controllers.getDecorator().startWizard(
                                    new ModpackInstallWizardProvider(GameDirectoryManager.getSelectedRepository(), file),
                                    i18n("install.modpack"));
                        } else if (NBTFileType.isNBTFileByExtension(file)) {
                            try {
                                Controllers.navigate(new NBTEditorPage(file));
                            } catch (Throwable e) {
                                LOG.warning("Fail to open nbt file", e);
                                Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                            }
                        } else if ("json".equalsIgnoreCase(FileUtils.getExtension(file))) {
                            Versions.installFromJson(GameDirectoryManager.getSelectedRepository(), file);
                        }
                    });

            FXUtils.onChangeAndOperate(GameDirectoryManager.selectedInstanceProperty(), mainPage::setCurrentGame);
            mainPage.latestVersionProperty().bind(UpdateChecker.latestVersionProperty());

            GameDirectoryManager.registerVersionsListener(repository -> {
                GameDirectory gameDirectory = repository.getGameDirectory();
                List<Version> children = repository.getVersions().parallelStream()
                        .filter(version -> !version.isHidden())
                        .sorted(Comparator
                                .comparing((Version version) -> Lang.requireNonNullElse(version.getReleaseTime(), Instant.EPOCH))
                                .thenComparing(version -> VersionNumber.asVersion(repository.getGameVersion(version).orElse(version.getId()))))
                        .collect(Collectors.toList());
                runInFX(() -> {
                    if (gameDirectory == GameDirectoryManager.getSelectedGameDirectory())
                        mainPage.initVersions(repository, children);
                });
            });
            this.mainPage = mainPage;
        }
        return mainPage;
    }

    /// PCL-style root layout: card-based left sidebar + blue horizontal top
    /// navigation bar. All original navigation actions are preserved.
    private static class Skin extends DecoratorAnimatedPageSkin<RootPage> {

        protected Skin(RootPage control) {
            super(control);

            // Account card
            AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
            accountListItem.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));
            FXUtils.onSecondaryButtonClicked(accountListItem, () -> AccountListPopupMenu.show(accountListItem, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, accountListItem.getWidth(), 0));
            accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());

            // Version card
            GameAdvancedListItem gameListItem = new GameAdvancedListItem();
            gameListItem.setOnAction(e -> {
                String version = GameDirectoryManager.getSelectedRepository().getSelectedInstance();
                if (version == null) {
                    Controllers.navigate(Controllers.getGameListPage());
                } else {
                    Versions.modifyGameSettings(GameDirectoryManager.getSelectedRepository(), version);
                }
            });
            FXUtils.onScroll(gameListItem, getSkinnable().getMainPage().getVersions(), list -> {
                String currentId = getSkinnable().getMainPage().getCurrentGame();
                return Lang.indexWhere(list, instance -> instance.getId().equals(currentId));
            }, it -> getSkinnable().getMainPage().getRepository().setSelectedInstance(it.getId()));
            if (AnimationUtils.isAnimationEnabled()) {
                FXUtils.prepareOnMouseEnter(gameListItem, Controllers::prepareVersionPage);
            }
            FXUtils.onSecondaryButtonClicked(gameListItem, () -> showGameListPopupMenu(gameListItem));

            AdvancedListItem gameItem = new AdvancedListItem();
            gameItem.setLeftIcon(SVG.FORMAT_LIST_BULLETED);
            gameItem.setTitle(i18n("version.manage"));
            gameItem.setOnAction(e -> Controllers.navigate(Controllers.getGameListPage()));
            FXUtils.onSecondaryButtonClicked(gameItem, () -> showGameListPopupMenu(gameItem));

            AdvancedListItem downloadItem = new AdvancedListItem();
            downloadItem.setLeftIcon(SVG.DOWNLOAD);
            downloadItem.setTitle(i18n("download"));
            downloadItem.setOnAction(e -> {
                Controllers.getDownloadPage().showGameDownloads();
                Controllers.navigate(Controllers.getDownloadPage());
            });
            if (AnimationUtils.isAnimationEnabled()) {
                FXUtils.prepareOnMouseEnter(downloadItem, Controllers::prepareDownloadPage);
            }

            AdvancedListItem launcherSettingsItem = new AdvancedListItem();
            launcherSettingsItem.setLeftIcon(SVG.SETTINGS);
            launcherSettingsItem.setTitle(i18n("settings"));
            launcherSettingsItem.setOnAction(e -> {
                Controllers.getSettingsPage().showGameSettings(GameDirectoryManager.getSelectedRepository());
                Controllers.navigate(Controllers.getSettingsPage());
            });
            if (AnimationUtils.isAnimationEnabled()) {
                FXUtils.prepareOnMouseEnter(launcherSettingsItem, Controllers::prepareSettingsPage);
            }

            AdvancedListItem terracottaItem = new AdvancedListItem();
            terracottaItem.setLeftIcon(SVG.LINK);
            terracottaItem.setTitle(i18n("terracotta"));
            terracottaItem.setOnAction(e -> {
                if (TerracottaMetadata.PROVIDER != null) {
                    Controllers.navigate(Controllers.getTerracottaPage());
                } else {
                    String message;
                    if (Architecture.SYSTEM_ARCH.getBits() == Bits.BIT_32)
                        message = i18n("terracotta.unsupported.arch.32bit");
                    else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                            && !OperatingSystem.SYSTEM_VERSION.isAtLeast(OSVersion.WINDOWS_10))
                        message = i18n("terracotta.unsupported.os.windows.old");
                    else if (Platform.SYSTEM_PLATFORM.equals(OperatingSystem.LINUX, Architecture.LOONGARCH64_OW))
                        message = i18n("terracotta.unsupported.arch.loongarch64_ow");
                    else
                        message = i18n("terracotta.unsupported");

                    Controllers.dialog(message, null, MessageDialogPane.MessageType.WARNING);
                }
            });

            AdvancedListItem feedbackItem = new AdvancedListItem();
            feedbackItem.setLeftIcon(SVG.CHAT);
            feedbackItem.setTitle(i18n("contact.chat"));
            feedbackItem.setOnAction(e -> {
                Controllers.getSettingsPage().showFeedback();
                Controllers.navigate(Controllers.getSettingsPage());
            });

            // ---- PCL PageLaunchLeft: type chips + avatar + account + buttons ----
            // PCL switches the active account right on the home page
            // (PanLogin / PanType), so the avatar, name and switch button open
            // an inline PCL account switcher instead of navigating away to
            // HMCL's separate account manager.
            javafx.scene.canvas.Canvas avatarCanvas = new javafx.scene.canvas.Canvas(64, 64);
            TexturesLoader.drawAvatar(avatarCanvas, TexturesLoader.getDefaultSkinImage());
            StackPane avatar = new StackPane(avatarCanvas);
            avatar.getStyleClass().add("hpcl-account-avatar");
            avatar.setCursor(javafx.scene.Cursor.HAND);
            Runnable showAccountSwitcher = () ->
                    org.jackhuang.hmcl.ui.pcl.PCLAccountSwitcher.show(avatar);
            FXUtils.onClicked(avatar, showAccountSwitcher);

            // PCL `RadioLoginType0/5`: the chips switch the login method. Here
            // they select the first account of that type, or open the matching
            // add-account pane when none exists yet.
            Runnable selectOriginal = () -> {
                for (var a : Accounts.getAccounts()) {
                    if (a instanceof org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount) {
                        Accounts.setSelectedAccount(a);
                        return;
                    }
                }
                Controllers.dialog(new org.jackhuang.hmcl.ui.account.MicrosoftAccountLoginPane());
            };
            Runnable selectOffline = () -> {
                for (var a : Accounts.getAccounts()) {
                    if (!(a instanceof org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount)) {
                        Accounts.setSelectedAccount(a);
                        return;
                    }
                }
                Controllers.dialog(new org.jackhuang.hmcl.ui.account.CreateAccountPane(Accounts.FACTORY_OFFLINE));
            };

            boolean isMicrosoft = Accounts.getSelectedAccount()
                    instanceof org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
            HBox originalBadge = PCLTheme.createTypeBadge(
                    i18n("hpcl.account.original"), null, isMicrosoft, selectOriginal);
            HBox offlineBadge = PCLTheme.createTypeBadge(
                    i18n("hpcl.account.offline"), null, !isMicrosoft, selectOffline);
            HBox typeRow = new HBox(10);
            typeRow.setAlignment(Pos.CENTER);
            typeRow.getChildren().setAll(originalBadge, offlineBadge);

            Label accountName = new Label();
            accountName.getStyleClass().add("hpcl-account-name");
            accountName.setCursor(javafx.scene.Cursor.HAND);
            FXUtils.onClicked(accountName, showAccountSwitcher);
            Label accountType = new Label();
            accountType.getStyleClass().add("hpcl-account-type");

            JFXButton loginButton = new JFXButton(i18n("account.create"));
            loginButton.getStyleClass().add("hpcl-account-login");
            loginButton.setMaxWidth(Double.MAX_VALUE);
            loginButton.setOnAction(e -> {
                if (Accounts.getAccounts().isEmpty()) {
                    Controllers.dialog(new org.jackhuang.hmcl.ui.account.CreateAccountPane(
                            Accounts.FACTORY_OFFLINE));
                } else {
                    org.jackhuang.hmcl.ui.pcl.PCLAccountSwitcher.show(loginButton);
                }
            });

            FXUtils.onChangeAndOperate(Accounts.selectedAccountProperty(), account -> {
                boolean ms = account instanceof org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
                // PCL `RadioLoginType_Change`: chips follow the active account.
                PCLTheme.setTypeBadgeActive(originalBadge, ms);
                PCLTheme.setTypeBadgeActive(offlineBadge, !ms);
                if (account == null) {
                    accountName.setText(i18n("account.missing"));
                    accountType.setText(i18n("account.missing.add"));
                    loginButton.setText(i18n("account.create"));
                    TexturesLoader.unbindAvatar(avatarCanvas);
                    TexturesLoader.drawAvatar(avatarCanvas, TexturesLoader.getDefaultSkinImage());
                } else {
                    String profile = account.getProfileName();
                    accountName.setText(profile == null || profile.isBlank()
                            ? account.getProfileID().toString() : profile);
                    accountType.setText(ms
                            ? i18n("hpcl.account.original") : i18n("hpcl.account.offline"));
                    loginButton.setText(i18n("hpcl.account.switch"));
                    TexturesLoader.bindAvatar(avatarCanvas, account);
                }
            });

            VBox accountBox = new VBox(9, avatar, accountName, accountType, loginButton);
            accountBox.setAlignment(Pos.CENTER);
            accountBox.getStyleClass().add("hpcl-account-box");

            HBox accountLinks = new HBox(16);
            accountLinks.setAlignment(Pos.CENTER);
            accountLinks.getChildren().setAll(
                    PCLTheme.createSidedLink(i18n("hpcl.account.buy"), () ->
                            FXUtils.openLink("https://www.minecraft.net/store/minecraft-deluxe-collection-pc")),
                    PCLTheme.createSidedLink(i18n("hpcl.account.website"), () ->
                            FXUtils.openLink(Metadata.CONTACT_URL)));

            StackPane launchBar = getSkinnable().getMainPage().getLaunchBar();
            launchBar.setMaxWidth(Double.MAX_VALUE);
            launchBar.setMinHeight(54);
            launchBar.setMaxHeight(54);

            // PCL `BtnVersion`: a 35px secondary button under the launch block.
            JFXButton versionSelect = new JFXButton(i18n("hpcl.account.select"));
            versionSelect.getStyleClass().add("hpcl-version-select");
            versionSelect.setMaxWidth(Double.MAX_VALUE);
            versionSelect.setMinHeight(35);
            versionSelect.setPrefHeight(35);
            versionSelect.setOnAction(e -> Controllers.navigate(Controllers.getGameListPage()));
            PCLTheme.attachButtonHover(versionSelect);

            VBox sidebar = new VBox(14);
            sidebar.getStyleClass().add(PCLTheme.SIDEBAR);
            sidebar.setMaxWidth(Double.MAX_VALUE);
            // PCL centres the account block between the type chips and the
            // bottom launch block, so give it a flexible spacer on each side.
            Region spacerTop = new Region();
            VBox.setVgrow(spacerTop, Priority.ALWAYS);
            Region spacerBottom = new Region();
            VBox.setVgrow(spacerBottom, Priority.ALWAYS);
            sidebar.getChildren().setAll(typeRow, spacerTop, accountBox, spacerBottom,
                    accountLinks, launchBar, versionSelect);

            // PCL `PanLaunching` overlays the whole left pane while the game
            // boots, so stack it above the sidebar instead of beside it.
            StackPane sidebarStack = new StackPane(sidebar);
            sidebar.setMaxHeight(Double.MAX_VALUE);
            // `DecoratorAnimatedPage.left` is a VBox, so the wrapper needs an
            // explicit vertical grow to fill the window height.
            VBox.setVgrow(sidebarStack, Priority.ALWAYS);
            VBox launching = getSkinnable().getMainPage().getLaunchingPanel();
            sidebarStack.getChildren().add(launching);
            StackPane.setAlignment(launching, Pos.CENTER);

            // The blue top navigation bar lives in DecoratorController so it stays
            // visible on every page, exactly like PCL's FormMain title bar.
            setLeft(sidebarStack);
            setCenter(getSkinnable().getMainPage());

            // PCL `MyPageLeft.TriggerShowAnimation`: the whole left pane
            // scales from 0.96 and its cards slide in horizontally from -25px
            // with a decreasing stagger. The right pane is handled globally by
            // DecoratorController.onNavigated (MyPageRight.TriggerEnterAnimation).
            javafx.application.Platform.runLater(() -> PCLTheme.playSidebarEnter(sidebar));

            // Opt-in headless verification hook (disabled by default): opens
            // the PCL home-page account switcher so it can be captured in a
            // real Xvfb session. Normal users never see this.
            if (System.getProperty("hpcl.account.test") != null) {
                // Two demo offline accounts so the switcher list (with the
                // active-item blue bar) can be captured in a real session.
                if (Accounts.getAccounts().isEmpty()) {
                    Accounts.getAccounts().addAll(
                            Accounts.FACTORY_OFFLINE.create("Steve", java.util.UUID.randomUUID()),
                            Accounts.FACTORY_OFFLINE.create("Alex", java.util.UUID.randomUUID()));
                    Accounts.setSelectedAccount(Accounts.getAccounts().get(0));
                }
                javafx.animation.PauseTransition delay =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(1800));
                delay.setOnFinished(e ->
                        org.jackhuang.hmcl.ui.pcl.PCLAccountSwitcher.show(avatar));
                delay.play();
            }
        }

        public void showGameListPopupMenu(Region gameListItem) {
            GameListPopupMenu.show(gameListItem,
                    JFXPopup.PopupVPosition.TOP,
                    JFXPopup.PopupHPosition.LEFT,
                    gameListItem.getWidth(),
                    0,
                    getSkinnable().getMainPage().getRepository(),
                    getSkinnable().getMainPage().getVersions());
        }
    }

    private boolean checkedModpack = false;

    private void onRefreshedVersions(HMCLGameRepository repository) {
        runInFX(() -> {
            if (!checkedModpack) {
                checkedModpack = true;

                if (repository.getVersionCount() == 0) {
                    Path zipModpack = Metadata.CURRENT_DIRECTORY.resolve("modpack.zip");
                    Path mrpackModpack = Metadata.CURRENT_DIRECTORY.resolve("modpack.mrpack");

                    Path modpackFile;
                    if (Files.exists(zipModpack)) {
                        modpackFile = zipModpack;
                    } else if (Files.exists(mrpackModpack)) {
                        modpackFile = mrpackModpack;
                    } else {
                        modpackFile = null;
                    }

                    if (modpackFile != null) {
                        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(modpackFile))
                                .thenApplyAsync(encoding -> ModpackHelper.readModpackManifest(modpackFile, encoding))
                                .thenApplyAsync(modpack -> ModpackHelper
                                        .getInstallTask(repository, modpackFile, modpack.getName(), modpack, null)
                                        .executor())
                                .thenAcceptAsync(Schedulers.javafx(), executor -> {
                                    Controllers.taskDialog(executor, i18n("modpack.installing"), TaskCancellationAction.NO_CANCEL);
                                    executor.start();
                                }).start();
                    }
                }
            }
        });
    }
}
