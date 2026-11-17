/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.upgrade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.AutoUpdateChannel;
import org.jackhuang.hmcl.setting.AutoUpdateMode;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HPCL's own update channel. The upstream self-update service is deliberately
/// disabled (see Metadata.UPDATE_ENABLED) because it would replace this fork
/// with an official HMCL build. This manager queries GitHub Releases instead.
///
/// A network failure during the *check* is always silent: GitHub can be slow
/// or unreachable from mainland China, and the user explicitly asked not to be
/// notified in that case. Only a manual "check now" reports failure.
public final class HPCLUpdateManager {
    private HPCLUpdateManager() {
    }

    private static final String RELEASES_API =
            "https://api.github.com/repos/KJLLin/HPCL-Launcher/releases?per_page=10";
    private static final java.time.Duration CONNECT_TIMEOUT = java.time.Duration.ofSeconds(8);
    private static final java.time.Duration CHECK_TIMEOUT = java.time.Duration.ofSeconds(15);
    private static final java.time.Duration DOWNLOAD_TIMEOUT = java.time.Duration.ofMinutes(10);
    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final AtomicBoolean applying = new AtomicBoolean(false);
    /// Tags such as v3.16.SNAPSHOT are never offered on the stable channel,
    /// even if someone forgets to tick GitHub's "pre-release" flag.
    private static final Pattern PREVIEW_TAG = Pattern.compile(
            "(?i)(snapshot|nightly|dev|beta|alpha|preview|(?:rc|pre)[-._]?\\d*)");

    /// Distinguishes "this channel has no published build yet" from a network
    /// failure. Both stay silent on an automatic check, but a manual check
    /// can give the user an accurate hint instead of a generic error card.
    private static final class NoReleaseException extends IOException {
        NoReleaseException(String message) {
            super(message);
        }
    }

    private record Asset(String name, String url) {
    }

    private record Release(String tag, String pageUrl, List<Asset> packages, Asset sums) {
    }

    private record CheckResult(Release release, Asset target, String expectedHash, boolean updated) {
    }

    /// Called once on startup. Does nothing until the user opted in.
    public static void init() {
        if (!settings().autoUpdateCheckEnabledProperty().get()) {
            return;
        }
        thread(() -> {
            // Stay off the JavaFX startup path; the first seconds are busy
            // building the PCL-style pages and playing entrance animations.
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
                return;
            }
            checkNow(false);
        }, "HPCL Update Check", true);
    }

    /// @param userInitiated true when triggered from the settings / about page.
    public static void checkNow(boolean userInitiated) {
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        if (userInitiated) {
            Platform.runLater(() -> Controllers.showToast(i18n("update.checking")));
        }

        thread(() -> {
            CheckResult result = null;
            Throwable error = null;
            try {
                result = check();
            } catch (Throwable e) {
                error = e;
            }
            CheckResult finalResult = result;
            Throwable finalError = error;
            Platform.runLater(() -> {
                checking.set(false);
                if (finalResult == null) {
                    if (finalError != null) {
                        LOG.warning("HPCL update check failed", finalError);
                    }
                    // Silent on automatic checks by design.
                    if (userInitiated) {
                        if (finalError instanceof NoReleaseException) {
                            Controllers.showToast(i18n("update.channel.empty"));
                        } else {
                            Controllers.dialog(i18n("update.failed"), i18n("update.failed"),
                                    MessageDialogPane.MessageType.ERROR);
                        }
                    }
                    return;
                }
                if (!finalResult.updated()) {
                    if (userInitiated) {
                        Controllers.showToast(i18n("update.latest"));
                    }
                    return;
                }
                // A rebuilt release under the same tag can differ forever from
                // a locally built jar. Remember this exact (tag, hash) pair so
                // an automatic check never loops: notify/apply once, then stay
                // quiet until the release changes. A manual check always runs.
                String key = dedupeKey(finalResult);
                boolean seen = state().getShownTips().containsKey(key);
                if (seen && !userInitiated) {
                    return;
                }
                state().getShownTips().put(key, Boolean.TRUE);
                AutoUpdateMode mode = settings().autoUpdateModeProperty().get();
                if (mode == AutoUpdateMode.AUTO) {
                    applyAsync(finalResult, userInitiated);
                } else {
                    showNotification(finalResult);
                }
            });
        }, "HPCL Update Check", true);
    }

    private static CheckResult check() throws IOException {
        Release release = fetchLatestRelease();
        Asset target = chooseAsset(release);
        if (target == null) {
            throw new IOException("No compatible package in release " + release.tag());
        }
        String sums = get(release.sums().url(), CHECK_TIMEOUT);
        String expectedHash = findHash(sums, target.name());
        boolean newer = isNewerTag(release.tag());
        // Same tag can be republished (a rebuilt r12 under the same version).
        // Comparing the published checksum against the running package catches
        // that too, so a rebuilt release is still offered.
        boolean changed = expectedHash != null && !localMatches(expectedHash);
        return new CheckResult(release, target, expectedHash, newer || changed);
    }

    private static Release fetchLatestRelease() throws IOException {
        AutoUpdateChannel channel = currentChannel();
        String body = get(RELEASES_API, CHECK_TIMEOUT);
        JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
        for (JsonElement element : releases) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject release = element.getAsJsonObject();
            if (release.has("draft") && release.get("draft").getAsBoolean()) {
                continue;
            }
            String tag = release.has("tag_name") ? release.get("tag_name").getAsString() : "";
            boolean prerelease = release.has("prerelease")
                    && release.get("prerelease").getAsBoolean();
            // Stable subscribers never receive a pre-release, BETA subscribers
            // see both (the API returns newest-first, so BETA gets the newest).
            if (channel == AutoUpdateChannel.STABLE
                    && (prerelease || PREVIEW_TAG.matcher(tag).find())) {
                continue;
            }
            String page = release.has("html_url")
                    ? release.get("html_url").getAsString() : Metadata.REPOSITORY_RELEASES_URL;
            List<Asset> packages = new ArrayList<>();
            Asset sums = null;
            if (release.has("assets") && release.get("assets").isJsonArray()) {
                for (JsonElement assetElement : release.getAsJsonArray("assets")) {
                    if (!assetElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject asset = assetElement.getAsJsonObject();
                    if (!asset.has("name") || !asset.has("browser_download_url")) {
                        continue;
                    }
                    String name = asset.get("name").getAsString();
                    String url = asset.get("browser_download_url").getAsString();
                    if ("SHA256SUMS.txt".equalsIgnoreCase(name)) {
                        sums = new Asset(name, url);
                    } else if (name.toUpperCase(Locale.ROOT).startsWith("HPCL")
                            && (name.endsWith(".jar") || name.endsWith(".exe")
                            || name.endsWith(".sh") || name.endsWith(".deb"))) {
                        packages.add(new Asset(name, url));
                    }
                }
            }
            if (sums != null && !packages.isEmpty()) {
                return new Release(tag, page, packages, sums);
            }
        }
        throw new NoReleaseException(
                "No usable HPCL release on the " + channel.getId() + " channel");
    }

    private static AutoUpdateChannel currentChannel() {
        AutoUpdateChannel channel = settings().autoUpdateChannelProperty().get();
        return channel == null ? AutoUpdateChannel.DEFAULT : channel;
    }

    private static Asset chooseAsset(Release release) {
        String suffix = currentSuffix();
        for (Asset asset : release.packages()) {
            if (asset.name().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                return asset;
            }
        }
        // .deb builds cannot be replaced in place; fall back to the jar.
        for (Asset asset : release.packages()) {
            if (asset.name().endsWith(".jar")) {
                return asset;
            }
        }
        return null;
    }

    private static String currentSuffix() {
        Path self = JarUtils.thisJarPath();
        if (self == null) {
            return ".jar";
        }
        String name = self.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) {
            return ".exe";
        }
        if (name.endsWith(".sh")) {
            return ".sh";
        }
        return ".jar";
    }

    private static boolean isNewerTag(String tag) {
        int[] remote = numbers(tag);
        int[] local = numbers(Metadata.VERSION);
        if (local.length == 0) {
            return false; // development build: rely on checksum comparison
        }
        int length = Math.max(remote.length, local.length);
        for (int i = 0; i < length; i++) {
            int a = i < remote.length ? remote[i] : 0;
            int b = i < local.length ? local[i] : 0;
            if (a != b) {
                return a > b;
            }
        }
        return false;
    }

    private static int[] numbers(String text) {
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        List<Integer> values = new ArrayList<>();
        while (matcher.find() && values.size() < 4) {
            try {
                values.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static boolean localMatches(String expectedHash) {
        Path self = JarUtils.thisJarPath();
        if (self == null || !Files.isRegularFile(self)) {
            return true;
        }
        try {
            return expectedHash.equalsIgnoreCase(sha256(self));
        } catch (Exception e) {
            LOG.warning("Cannot hash the running HPCL package", e);
            return true;
        }
    }

    private static String findHash(String sums, String name) {
        for (String line : sums.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length != 2 || parts[0].length() != 64) {
                continue;
            }
            String file = parts[1].trim();
            if (file.startsWith("*")) {
                file = file.substring(1);
            }
            if (file.equals(name) || file.endsWith("/" + name)) {
                return parts[0];
            }
        }
        return null;
    }

    private static void showNotification(CheckResult result) {
        String version = displayVersion(result.release().tag())
                + " · " + i18n("update.auto_channel." + currentChannel().getId());
        Controllers.showToast(i18n("update.found.github", version));
        var pane = new MessageDialogPane.Builder(
                i18n("update.found.github", version) + "\n"
                        + i18n("update.found.github.subtitle"),
                i18n("update.found"), MessageDialogPane.MessageType.QUESTION)
                .addAction(i18n("update.accept"), () -> applyAsync(result, true))
                .addCancel(i18n("button.cancel"), null)
                .addExtraAction(i18n("about.hpcl.download_site"),
                        () -> FXUtils.openLink(result.release().pageUrl()))
                .build();
        Controllers.dialog(pane);
    }

    private static void applyAsync(CheckResult result, boolean userInitiated) {
        if (!applying.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> Controllers.showToast(i18n("update.auto.verifying")));
        thread(() -> {
            try {
                Path temp = Files.createTempFile("hpcl-update-", suffixOf(result.target().name()));
                download(result.target().url(), temp);
                if (result.expectedHash() != null
                        && !result.expectedHash().equalsIgnoreCase(sha256(temp))) {
                    throw new IOException("SHA-256 mismatch for " + result.target().name());
                }
                Path self = JarUtils.thisJarPath();
                if (self == null) {
                    throw new IOException("Cannot locate the running HPCL package");
                }
                Platform.runLater(() -> {
                    Controllers.showToast(i18n("update.auto.restart"));
                    PauseTransition pause = new PauseTransition(Duration.millis(900));
                    pause.setOnFinished(event -> {
                        try {
                            HPCLUpdateInstaller.requestApply(temp, self);
                        } catch (IOException e) {
                            LOG.warning("Failed to launch the HPCL update helper", e);
                            applying.set(false);
                            if (userInitiated) {
                                Controllers.dialog(i18n("update.auto.failed"),
                                        i18n("update.failed"), MessageDialogPane.MessageType.ERROR);
                            }
                        }
                    });
                    pause.play();
                });
            } catch (Exception e) {
                LOG.warning("HPCL automatic update failed", e);
                applying.set(false);
                // Automatic failures (usually network) stay silent too.
                if (userInitiated) {
                    Platform.runLater(() -> Controllers.dialog(i18n("update.auto.failed"),
                            i18n("update.failed"), MessageDialogPane.MessageType.ERROR));
                }
            }
        }, "HPCL Update Apply", true);
    }

    private static String dedupeKey(CheckResult result) {
        String hash = result.expectedHash() == null
                ? "none" : result.expectedHash().substring(0, Math.min(12, result.expectedHash().length()));
        // The channel is part of the key: switching channels must be able to
        // offer the very same release again instead of being deduplicated.
        return "hpcl.update." + currentChannel().getId() + "."
                + result.release().tag() + "." + hash;
    }

    private static String displayVersion(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }

    private static String suffixOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".jar" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String get(String url, java.time.Duration timeout) throws IOException {
        try {
            HttpResponse<String> response = client().send(request(url, timeout),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while querying " + url, e);
        }
    }

    private static void download(String url, Path target) throws IOException {
        try {
            HttpResponse<Path> response = client().send(request(url, DOWNLOAD_TIMEOUT),
                    HttpResponse.BodyHandlers.ofFile(target, CREATE, WRITE, TRUNCATE_EXISTING));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }

    private static HttpRequest request(String url, java.time.Duration timeout) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "HPCL-Launcher/" + Metadata.VERSION)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
    }

    private static HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
