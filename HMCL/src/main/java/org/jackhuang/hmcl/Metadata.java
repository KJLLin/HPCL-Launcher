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
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.EnumSet;

/**
 * Stores metadata about this application.
 */
public final class Metadata {
    private Metadata() {
    }

    /// Short product name shown in the UI and in file names.
    public static final String NAME = "HPCL";
    /// Full product name shown in the window title and the About page.
    public static final String FULL_NAME = "HPCL Launcher";
    /// The upstream project this launcher is derived from (GPLv3 attribution).
    public static final String UPSTREAM_NAME = "Hello Minecraft! Launcher";
    /// The upstream release this fork is based on.
    public static final String UPSTREAM_VERSION = "3.16.4";
    public static final String VERSION = System.getProperty("hmcl.version.override", JarUtils.getAttribute("hmcl.version", "@develop@"));

    public static final String TITLE = NAME + " " + VERSION;
    public static final String FULL_TITLE = FULL_NAME + " v" + VERSION;

    public static final int MINIMUM_REQUIRED_JAVA_VERSION = 17;
    public static final int MINIMUM_SUPPORTED_JAVA_VERSION = 17;
    public static final int RECOMMENDED_JAVA_VERSION = 21;

    public static final String PUBLISH_URL = "https://hmcl.huangyuhui.net";
    public static final String DOWNLOAD_URL = PUBLISH_URL + "/download";
    public static final String HMCL_UPDATE_URL = System.getProperty("hmcl.update_source.override", PUBLISH_URL + "/api/update_link");
    public static final String MANUAL_UPDATE_URL = "https://github.com/HMCL-dev/HMCL/releases";

    public static final String DOCS_URL = "https://docs.hmcl.net";
    public static final String CONTACT_URL = DOCS_URL + "/help.html";
    public static final String CHANGELOG_URL = DOCS_URL + "/changelog/";
    public static final String EULA_URL = DOCS_URL + "/eula/hmcl.html";
    public static final String GROUPS_URL = "https://www.bilibili.com/opus/905435541874409529";

    public static final String BUILD_CHANNEL = JarUtils.getAttribute("hmcl.version.type", "nightly");
    public static final String GITHUB_SHA = JarUtils.getAttribute("hmcl.version.hash", null);

    /// HPCL is a fork: the upstream self-update service must not be contacted,
    /// otherwise it would silently replace this build with an official HMCL build.
    /// Set -Dhpcl.update.enabled=true only when a dedicated HPCL update endpoint exists.
    public static final boolean UPDATE_ENABLED = Boolean.getBoolean("hpcl.update.enabled");

    public static final Path CURRENT_DIRECTORY = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    public static final Path MINECRAFT_DIRECTORY = OperatingSystem.getWorkingDirectory("minecraft");
    public static final Path HMCL_USER_HOME;
    public static final Path HMCL_LOCAL_HOME;
    public static final Path DEPENDENCIES_DIRECTORY;

    static {
        // HPCL uses its own config directory so that it never reads or writes HMCL's data.
        // Legacy HMCL properties are still accepted as a fallback for migration.
        String hpclHome = System.getProperty("hpcl.home",
                System.getProperty("hmcl.home", System.getenv("HPCL_USER_HOME")));
        if (StringUtils.isBlank(hpclHome)) {
            hpclHome = System.getenv("HMCL_USER_HOME");
        }
        if (StringUtils.isBlank(hpclHome)) {
            if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
                String xdgData = System.getenv("XDG_DATA_HOME");
                if (StringUtils.isNotBlank(xdgData)) {
                    HMCL_USER_HOME = Path.of(xdgData, "hpcl").toAbsolutePath().normalize();
                } else {
                    HMCL_USER_HOME = Path.of(System.getProperty("user.home"), ".local", "share", "hpcl").toAbsolutePath().normalize();
                }
            } else {
                HMCL_USER_HOME = OperatingSystem.getWorkingDirectory("hpcl");
            }
        } else {
            HMCL_USER_HOME = Path.of(hpclHome).toAbsolutePath().normalize();
        }

        String hpclCurrentDir = System.getProperty("hpcl.dir",
                System.getProperty("hmcl.dir", System.getenv("HPCL_LOCAL_HOME")));
        if (StringUtils.isBlank(hpclCurrentDir)) {
            hpclCurrentDir = System.getenv("HMCL_LOCAL_HOME");
        }
        HMCL_LOCAL_HOME = StringUtils.isNotBlank(hpclCurrentDir)
                ? Path.of(hpclCurrentDir).toAbsolutePath().normalize()
                : CURRENT_DIRECTORY.resolve(".hpcl");

        String hmclDependencies = System.getProperty("hpcl.dependencies.dir",
                System.getProperty("hmcl.dependencies.dir", System.getenv("HPCL_DEPENDENCIES_DIR")));
        if (StringUtils.isBlank(hmclDependencies)) {
            hmclDependencies = System.getenv("HMCL_DEPENDENCIES_DIR");
        }
        DEPENDENCIES_DIRECTORY = StringUtils.isNotBlank(hmclDependencies)
                ? Path.of(hmclDependencies).toAbsolutePath().normalize()
                : HMCL_LOCAL_HOME.resolve("dependencies");
    }

    public static boolean isStable() {
        return "stable".equals(BUILD_CHANNEL);
    }

    public static boolean isDev() {
        return "dev".equals(BUILD_CHANNEL);
    }

    public static boolean isNightly() {
        return !isStable() && !isDev();
    }

    public static @Nullable String getSuggestedJavaDownloadLink() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64_OW)
            return "https://www.loongnix.cn/zh/api/java/downloads-jdk21/index.html";
        else {
            EnumSet<Architecture> supportedArchitectures;
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.X86, Architecture.ARM64);
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
                supportedArchitectures = EnumSet.of(
                        Architecture.X86_64, Architecture.X86,
                        Architecture.ARM64, Architecture.ARM32,
                        Architecture.RISCV64, Architecture.LOONGARCH64
                );
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.ARM64);
            else
                supportedArchitectures = EnumSet.noneOf(Architecture.class);
            if (supportedArchitectures.contains(Architecture.SYSTEM_ARCH))
                return String.format("https://docs.hmcl.net/downloads/%s/%s.html",
                        OperatingSystem.CURRENT_OS.getCheckedName(),
                        Architecture.SYSTEM_ARCH.getCheckedName()
                );
            else
                return null;
        }
    }
}
