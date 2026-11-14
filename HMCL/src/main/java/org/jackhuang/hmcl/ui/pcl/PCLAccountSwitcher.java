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

import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.account.CreateAccountPane;
import org.jackhuang.hmcl.ui.account.MicrosoftAccountLoginPane;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// PCL's home page lets the user switch the active account in place
/// (`PageLaunchLeft.PanLogin` hosts the login pages; `PanType` switches the
/// login method). This popup reproduces that behaviour: a `MyCard`-styled
/// account list anchored to the home-page avatar, instead of navigating away
/// to HMCL's separate account manager.
public final class PCLAccountSwitcher extends VBox {

    private final VBox list = new VBox(4);
    private final InvalidationListener listener;
    private JFXPopup popup;

    /// Opens the switcher below `owner`, centred on it so the 262px card stays
    /// inside PCL's 300px left pane for both the avatar and the switch button.
    public static void show(Node owner) {
        PCLAccountSwitcher menu = new PCLAccountSwitcher();
        JFXPopup p = new JFXPopup(menu);
        menu.popup = p;
        // JFXPopup enables autoHide by default, which consumes MOUSE_PRESSED
        // in the capture phase and hides the popup BEFORE a row's click
        // handler can run. PCL selects accounts on MouseLeftButtonDown, so we
        // disable autoHide and close on an explicit outside click instead.
        p.setAutoHide(false);
        double width = owner.getLayoutBounds().getWidth();
        double offsetX = width > 0 ? -(262.0 - width) / 2.0 : -14;
        p.show(owner, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT,
                offsetX, owner.getLayoutBounds().getHeight() + 8);

        Scene scene = owner.getScene();
        if (scene != null) {
            EventHandler<MouseEvent> outside = e -> {
                Node target = e.getTarget() instanceof Node n ? n : null;
                while (target != null) {
                    if (target == menu) return; // inside the switcher
                    target = target.getParent();
                }
                menu.hide();
            };
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, outside);
            p.setOnHidden(e -> scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outside));
        }
    }

    private PCLAccountSwitcher() {
        getStyleClass().add("hpcl-account-switcher");
        setPadding(new Insets(12));
        setSpacing(9);
        setPrefWidth(262);
        setMinWidth(262);
        setMaxWidth(262);

        Label title = new Label(i18n("hpcl.account.switch.title"));
        title.getStyleClass().add("hpcl-account-switcher-title");

        Region separator = new Region();
        separator.getStyleClass().add("hpcl-account-switcher-separator");
        separator.setPrefHeight(1);
        separator.setMinHeight(1);
        separator.setMaxHeight(1);

        VBox actions = new VBox(3);
        actions.getChildren().setAll(
                createAction(i18n("hpcl.account.switch.add_offline"), () ->
                        Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE))),
                createAction(i18n("hpcl.account.switch.microsoft"), () ->
                        Controllers.dialog(new MicrosoftAccountLoginPane())),
                createAction(i18n("hpcl.account.switch.manage"), () ->
                        Controllers.navigate(Controllers.getAccountListPage())));

        getChildren().setAll(title, list, separator, actions);

        // PCL refreshes the account list as soon as an account is added or
        // the selection changes; keep a strong ref to the listener.
        listener = o -> Platform.runLater(this::rebuild);
        Accounts.getAccounts().addListener(new WeakInvalidationListener(listener));
        Accounts.selectedAccountProperty().addListener(new WeakInvalidationListener(listener));
        rebuild();
    }

    private void rebuild() {
        list.getChildren().clear();
        Account selected = Accounts.getSelectedAccount();
        for (Account account : Accounts.getAccounts()) {
            list.getChildren().add(createRow(account, account == selected));
        }
        if (Accounts.getAccounts().isEmpty()) {
            Label empty = new Label(i18n("account.empty"));
            empty.getStyleClass().add("hpcl-account-switcher-empty");
            list.getChildren().add(empty);
        }
    }

    private HBox createRow(Account account, boolean active) {
        HBox row = new HBox(10);
        row.getStyleClass().addAll("hpcl-account-switcher-row",
                active ? "hpcl-account-switcher-row-active" : "hpcl-account-switcher-row-idle");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);

        Canvas canvas = new Canvas(30, 30);
        TexturesLoader.bindAvatar(canvas, account);
        Rectangle clip = new Rectangle(30, 30);
        clip.setArcWidth(6);
        clip.setArcHeight(6);
        canvas.setClip(clip);
        StackPane avatar = new StackPane(canvas);
        avatar.getStyleClass().add("hpcl-account-switcher-avatar");

        String name = account.getProfileName();
        Label nameLabel = new Label(name == null || name.isBlank()
                ? account.getProfileID().toString() : name);
        nameLabel.getStyleClass().add("hpcl-account-switcher-name");
        Label typeLabel = new Label(i18n(account instanceof MicrosoftAccount
                ? "hpcl.account.original" : "hpcl.account.offline"));
        typeLabel.getStyleClass().add("hpcl-account-switcher-type");
        VBox texts = new VBox(1, nameLabel, typeLabel);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Label check = new Label("✓");
        check.getStyleClass().add("hpcl-account-switcher-check");
        check.setVisible(active);

        row.getChildren().setAll(avatar, texts, check);
        // PCL `MyListItem` selects on MouseLeftButtonDown, not on Click.
        row.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            Accounts.setSelectedAccount(account);
            e.consume();
            hide();
        });
        return row;
    }

    private HBox createAction(String text, Runnable action) {
        HBox box = new HBox(6);
        box.getStyleClass().add("hpcl-account-switcher-action");
        box.setAlignment(Pos.CENTER_LEFT);
        Label mark = new Label("»");
        mark.getStyleClass().add("hpcl-account-switcher-action-mark");
        Label label = new Label(text);
        label.getStyleClass().add("hpcl-account-switcher-action-text");
        box.getChildren().setAll(mark, label);
        box.setCursor(Cursor.HAND);
        box.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            e.consume();
            hide();
            action.run();
        });
        return box;
    }

    private void hide() {
        if (popup != null) popup.hide();
    }
}
