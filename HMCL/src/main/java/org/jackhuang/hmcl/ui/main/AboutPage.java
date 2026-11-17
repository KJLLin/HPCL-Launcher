/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.*;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.upgrade.HPCLUpdateManager;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The About page keeps HPCL's own information and the GPLv3 upstream
/// attribution in two separate sections: the screenshot used to stack
/// "HPCL Launcher" and "Upstream - HPCL" in one card, which was both wrong
/// (the upstream is HMCL) and visually confusing.
public final class AboutPage extends SpinnerPane {

    private final WeakListenerHolder holder = new WeakListenerHolder();

    public AboutPage() {
        VBox content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        ComponentList hpcl = new ComponentList();
        {
            // Latest square HPCL logo (v2, per-size generated), not the old "H" tile.
            var launcher = new LineButton();
            launcher.setLargeTitle(true);
            launcher.setLeading(hpclLogo(40));
            launcher.setTitle(Metadata.FULL_NAME);
            launcher.setSubtitle("v" + Metadata.VERSION + " · " + Metadata.UPSTREAM_NAME
                    + " " + Metadata.UPSTREAM_VERSION);
            launcher.setTrailingIcon(SVG.OPEN_IN_NEW);
            launcher.setOnAction(event -> FXUtils.openLink(Metadata.REPOSITORY_URL));

            var build = new LineButton();
            build.setLargeTitle(true);
            build.setTitle(i18n("about.hpcl.build"));
            String sha = Metadata.GITHUB_SHA == null ? "local"
                    : Metadata.GITHUB_SHA.substring(0, Math.min(8, Metadata.GITHUB_SHA.length()));
            build.setSubtitle("v" + Metadata.VERSION + " · " + Metadata.BUILD_CHANNEL + " · " + sha);

            var repository = LineButton.createExternalLinkButton(Metadata.REPOSITORY_URL);
            repository.setLargeTitle(true);
            repository.setLeading(FXUtils.newBuiltinImage("/assets/img/github.png"));
            repository.setTitle(i18n("hpcl.about.repository"));
            repository.setSubtitle(i18n("hpcl.about.repository.statement"));

            var releases = LineButton.createExternalLinkButton(Metadata.REPOSITORY_RELEASES_URL);
            releases.setLargeTitle(true);
            releases.setTitle(i18n("about.hpcl.download_site"));
            releases.setSubtitle(Metadata.REPOSITORY_RELEASES_URL);

            var channel = new LineButton();
            channel.setLargeTitle(true);
            channel.setTitle(i18n("update.auto_channel"));
            var selected = org.jackhuang.hmcl.setting.SettingsManager.settings()
                    .autoUpdateChannelProperty().get();
            String channelId = selected == null
                    ? org.jackhuang.hmcl.setting.AutoUpdateChannel.DEFAULT.getId()
                    : selected.getId();
            channel.setSubtitle(i18n("update.auto_channel." + channelId)
                    + " · " + i18n("update.auto_channel.subtitle"));

            var check = new LineButton();
            check.setLargeTitle(true);
            check.setTitle(i18n("update.check_now"));
            check.setSubtitle(i18n("update.check_now.subtitle"));
            check.setTrailingIcon(SVG.REFRESH);
            check.setOnAction(event -> HPCLUpdateManager.checkNow(true));

            hpcl.getContent().setAll(launcher, build, repository, releases, channel, check);
        }

        ComponentList credentials = new ComponentList();
        {
            boolean microsoft = !Accounts.OAUTH_CALLBACK.getClientId().isEmpty();
            boolean curseForge = org.jackhuang.hmcl.addon.repository
                    .CurseForgeRemoteAddonRepository.isAvailable();
            String ok = i18n("hpcl.credentials.status.configured");
            String missing = i18n("hpcl.credentials.status.missing");
            var state = new LineButton();
            state.setLargeTitle(true);
            state.setTitle(i18n("hpcl.credentials.title"));
            state.setSubtitle(i18n("hpcl.credentials.microsoft.signin") + ": "
                    + (microsoft ? ok : missing) + " · "
                    + i18n("hpcl.credentials.curseforge") + ": " + (curseForge ? ok : missing));
            state.setTrailingIcon(SVG.OPEN_IN_NEW);
            state.setOnAction(event -> FXUtils.openLink(Metadata.REPOSITORY_URL + "#credentials"));
            credentials.getContent().setAll(state);
        }

        // Upstream attribution lives in its own section, never mixed into the
        // HPCL product card. GPLv3 requires keeping the original notice.
        ComponentList upstream = new ComponentList();
        {
            var hmcl = LineButton.createExternalLinkButton("https://github.com/HMCL-dev/HMCL");
            hmcl.setLargeTitle(true);
            hmcl.setLeading(FXUtils.newBuiltinImage("/assets/img/github.png"));
            hmcl.setTitle(i18n("about.hpcl.upstream"));
            hmcl.setSubtitle(i18n("about.hpcl.upstream.statement"));

            var pcl = new LineButton();
            pcl.setLargeTitle(true);
            pcl.setTitle(i18n("about.hpcl.upstream.pcl"));
            pcl.setSubtitle(i18n("about.hpcl.upstream.pcl.statement"));

            upstream.getContent().setAll(hmcl, pcl);
        }

        ComponentList deps = loadIconedTwoLineList("/assets/about/deps.json");

        ComponentList legal = new ComponentList();
        {
            var copyright = new LineButton();
            copyright.setLargeTitle(true);
            copyright.setTitle(i18n("about.copyright"));
            copyright.setSubtitle(i18n("about.copyright.statement"));

            var claim = LineButton.createExternalLinkButton(Metadata.EULA_URL);
            claim.setLargeTitle(true);
            claim.setTitle(i18n("about.claim"));
            claim.setSubtitle(i18n("about.claim.statement"));

            var openSource = LineButton.createExternalLinkButton("https://github.com/HMCL-dev/HMCL");
            openSource.setLargeTitle(true);
            openSource.setTitle(i18n("about.open_source"));
            openSource.setSubtitle(i18n("about.open_source.statement"));

            legal.getContent().setAll(copyright, claim, openSource);
        }

        // HPCL: the upstream "thanks to" contributor roster is intentionally
        // omitted. The dependency licences and legal notices below are kept,
        // since GPLv3 and the bundled third-party licences require them.
        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("about.hpcl.section")),
                hpcl,
                ComponentList.createComponentListTitle(i18n("hpcl.credentials.title")),
                credentials,
                ComponentList.createComponentListTitle(i18n("about.upstream.section")),
                upstream,
                ComponentList.createComponentListTitle(i18n("about.dependency")),
                deps,
                ComponentList.createComponentListTitle(i18n("about.legal")),
                legal
        );
    }

    /// The square HPCL logo shipped in the jar; cached by FXUtils.newBuiltinImage
    /// so navigating to the About page repeatedly does not decode the PNG again.
    private static Node hpclLogo(double size) {
        ImageView view = new ImageView(FXUtils.newBuiltinImage("/assets/img/hpcl-logo.png"));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(true);
        return view;
    }

    private static Image loadImage(String url) {
        return url.startsWith("/")
                ? FXUtils.newBuiltinImage(url)
                : new Image(url);
    }

    private ComponentList loadIconedTwoLineList(String path) {
        ComponentList componentList = new ComponentList();

        InputStream input = FXUtils.class.getResourceAsStream(path);
        if (input == null) {
            LOG.warning("Resources not found: " + path);
            return componentList;
        }

        try {
            JsonArray array = JsonUtils.fromJsonFully(input, JsonArray.class);

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                var button = new LineButton();
                button.setLargeTitle(true);

                if (obj.get("externalLink") instanceof JsonPrimitive externalLink) {
                    button.setTrailingIcon(SVG.OPEN_IN_NEW);

                    String link = externalLink.getAsString();
                    button.setOnAction(event -> FXUtils.openLink(link));
                }

                if (obj.has("image")) {
                    JsonElement image = obj.get("image");
                    if (image.isJsonPrimitive()) {
                        var imageView = new ImageContainer(32, 32);
                        imageView.setImage(loadImage(image.getAsString()));
                        imageView.setMouseTransparent(true);

                        button.setLeading(imageView);
                    } else if (image.isJsonObject()) {
                        holder.add(FXUtils.onWeakChangeAndOperate(Themes.darkModeProperty(), darkMode -> button.setLeading(darkMode
                                ? loadImage(image.getAsJsonObject().get("dark").getAsString())
                                : loadImage(image.getAsJsonObject().get("light").getAsString())
                        )));
                    }
                }

                if (obj.get("title") instanceof JsonPrimitive title)
                    button.setTitle(title.getAsString());
                else if (obj.get("titleLocalized") instanceof JsonPrimitive titleLocalized)
                    button.setTitle(i18n(titleLocalized.getAsString()));

                if (obj.get("subtitle") instanceof JsonPrimitive subtitle)
                    button.setSubtitle(subtitle.getAsString());
                else if (obj.get("subtitleLocalized") instanceof JsonPrimitive subtitleLocalized)
                    button.setSubtitle(i18n(subtitleLocalized.getAsString()));

                componentList.getContent().add(button);
            }
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load list: " + path, e);
        }

        return componentList;
    }
}
