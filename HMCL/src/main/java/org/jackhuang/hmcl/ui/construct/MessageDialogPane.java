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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.pcl.PCLTheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MessageDialogPane extends HBox {

    public enum MessageType {
        ERROR(SVG.ERROR),
        INFO(SVG.INFO),
        WARNING(SVG.WARNING),
        QUESTION(SVG.HELP),
        SUCCESS(SVG.CHECK_CIRCLE);

        private final SVG icon;

        MessageType(SVG icon) {
            this.icon = icon;
        }

        public SVG getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return i18n("message." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final HBox actions;

    private @Nullable ButtonBase cancelButton;
    private boolean closing;

    public MessageDialogPane(@NotNull String text, @Nullable String title, @NotNull MessageType type) {
        this.setSpacing(16);
        // PCL2 MyMsg*.xaml: MinWidth="400" with a 25px outer Margin.
        setMinWidth(420);
        this.getStyleClass().add("jfx-dialog-layout");
        // PCL2 MyMsgText/Select/Input have NO leading 40px icon. Warnings are
        // signalled by IsWarn: a red title, a red primary button and a dark
        // red scrim. Map HMCL's error/warning message types onto that state.
        if (type == MessageType.ERROR || type == MessageType.WARNING) {
            getStyleClass().add("hpcl-msg-warn");
        }
        // Mark so DialogUtils does not play the entrance animation twice.
        getProperties().put("hpcl.dialog.animated", Boolean.TRUE);

        VBox vbox = new VBox();
        HBox.setHgrow(vbox, Priority.ALWAYS);
        {
            StackPane titlePane = new StackPane();
            titlePane.getStyleClass().addAll("jfx-layout-heading", "title");
            titlePane.getChildren().setAll(new Label(title != null ? title : type.getDisplayName()));

            StackPane content = new StackPane();
            content.getStyleClass().add("jfx-layout-body");
            EnhancedTextFlow textFlow = new EnhancedTextFlow(text);
            textFlow.setStyle("-fx-font-size: 14px;");
            if (textFlow.computePrefHeight(400.0) <= 350.0)
                content.getChildren().setAll(textFlow);
            else {
                ScrollPane scrollPane = new ScrollPane(textFlow);
                FXUtils.smoothScrolling(scrollPane);
                scrollPane.setPrefHeight(350);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);
                scrollPane.setFitToWidth(true);
                content.getChildren().setAll(scrollPane);
            }

            actions = new HBox();
            actions.getStyleClass().add("jfx-layout-actions");

            vbox.getChildren().setAll(titlePane, content, actions);
        }

        // PCL2's card is a plain vertical stack: heading, 2px rule, caption,
        // right-aligned MyButtons. Keep the HBox shell but drop the icon.
        this.getChildren().setAll(vbox);

        // PCL `MyMsgText.Loaded`: the dialog card starts tilted -4deg and
        // 40px low, then fades in and settles with OutBack / OutFluent.
        parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null) {
                javafx.application.Platform.runLater(() -> PCLTheme.playDialogEnter(this));
            }
        });

        onEscPressed(this, () -> {
            if (cancelButton != null) {
                cancelButton.fire();
            }
        });
    }

    public void addButton(Node btn) {
        addButton(btn, true);
    }

    /// Adds a button. [closeOnAction] mirrors PCL2's `ButtonNAction`: when an
    /// action delegate is supplied the dialog stays open (e.g. EULA's
    /// "查看用户协议与免责声明" opens a web page without dismissing the card).
    public void addButton(Node btn, boolean closeOnAction) {
        // Controls/MyButton.xaml.vb: 0.955 press scale (80ms) and spring back.
        if (!btn.getStyleClass().contains("hpcl-pcl-button"))
            btn.getStyleClass().add("hpcl-pcl-button");
        if (!Boolean.TRUE.equals(btn.getProperties().get("hpcl.pcl.press"))) {
            btn.getProperties().put("hpcl.pcl.press", Boolean.TRUE);
            PCLTheme.attachPCLButtonPress(btn);
        }
        if (closeOnAction) {
            // PCL `MyMsgText.Close`: fade 80ms, slide to Y20 and rotate to 6deg
            // over 150ms before the dialog is actually removed.
            btn.addEventHandler(ActionEvent.ACTION, e -> {
                if (closing) return;
                closing = true;
                PCLTheme.playDialogExit(this,
                        () -> fireEvent(new DialogCloseEvent()));
            });
        }
        actions.getChildren().add(btn);
    }

    public void setCancelButton(@Nullable ButtonBase btn) {
        cancelButton = btn;
    }

    public ButtonBase getCancelButton() {
        return cancelButton;
    }

    private static final class EnhancedTextFlow extends TextFlow {
        EnhancedTextFlow(String text) {
            this.getChildren().setAll(FXUtils.parseSegment(text, Controllers::onHyperlinkAction));
        }

        @Override
        public double computePrefHeight(double width) {
            return super.computePrefHeight(width);
        }
    }

    public static class Builder {
        private final MessageDialogPane dialog;

        public Builder(String text, String title, MessageType type) {
            this.dialog = new MessageDialogPane(text, title, type);
        }

        public Builder addHyperLink(String text, String externalLink) {
            JFXHyperlink link = new JFXHyperlink(text);
            link.setExternalLink(externalLink);
            dialog.actions.getChildren().add(link);
            return this;
        }

        public Builder addAction(Node actionNode) {
            dialog.addButton(actionNode);
            actionNode.getStyleClass().add("dialog-accept");
            return this;
        }

        public Builder addAction(String text, @Nullable Runnable action) {
            JFXButton btnAction = new JFXButton(text);
            btnAction.getStyleClass().add("dialog-accept");
            if (action != null) {
                btnAction.setOnAction(e -> action.run());
            }
            dialog.addButton(btnAction);
            return this;
        }

        /// A third `MyButton` whose action does NOT dismiss the card, exactly
        /// like PCL2's `Button3Action` (FormMain EULA: 查看用户协议…). PCL2
        /// orders the buttons Btn1/Btn2/Btn3 right-to-left with 12px gaps.
        public Builder addExtraAction(String text, @Nullable Runnable action) {
            JFXButton btn = new JFXButton(text);
            btn.getStyleClass().add("dialog-cancel");
            if (action != null) {
                btn.setOnAction(e -> action.run());
            }
            dialog.addButton(btn, false);
            return this;
        }

        public Builder ok(@Nullable Runnable ok) {
            JFXButton btnOk = new JFXButton(i18n("button.ok"));
            btnOk.getStyleClass().add("dialog-accept");
            if (ok != null) {
                btnOk.setOnAction(e -> ok.run());
            }
            dialog.addButton(btnOk);
            dialog.setCancelButton(btnOk);
            return this;
        }

        public Builder addCancel(@Nullable Runnable cancel) {
            return addCancel(i18n("button.cancel"), cancel);
        }

        public Builder addCancel(String cancelText, @Nullable Runnable cancel) {
            JFXButton btnCancel = new JFXButton(cancelText);
            btnCancel.setButtonType(JFXButton.ButtonType.FLAT);
            btnCancel.getStyleClass().add("dialog-cancel");
            if (cancel != null) {
                btnCancel.setOnAction(e -> cancel.run());
            }
            dialog.addButton(btnCancel);
            dialog.setCancelButton(btnCancel);
            return this;
        }

        public Builder yesOrNo(@Nullable Runnable yes, @Nullable Runnable no) {
            JFXButton btnYes = new JFXButton(i18n("button.yes"));
            btnYes.getStyleClass().add("dialog-accept");
            if (yes != null) {
                btnYes.setOnAction(e -> yes.run());
            }
            dialog.addButton(btnYes);

            addCancel(i18n("button.no"), no);
            return this;
        }

        public Builder actionOrCancel(ButtonBase actionButton, Runnable cancel) {
            dialog.addButton(actionButton);

            addCancel(cancel);
            return this;
        }

        public MessageDialogPane build() {
            return dialog;
        }
    }
}
