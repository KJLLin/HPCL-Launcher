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

import org.jackhuang.hmcl.EntryPoint;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.SwingUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Applies a downloaded HPCL build. A running JVM cannot overwrite its own
/// jar/exe on Windows, so a short-lived helper process is started from the
/// downloaded package: it waits for the old process to exit, writes the new
/// package over the old path (keeping the PE stub for .exe/.sh builds) and
/// relaunches the launcher.
public final class HPCLUpdateInstaller {
    private HPCLUpdateInstaller() {
    }

    private static final String APPLY_FLAG = "--hpcl-apply-to";

    /// @return whether the caller should exit (this is the helper process).
    public static boolean processArguments(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (APPLY_FLAG.equals(args[i])) {
                try {
                    install(Paths.get(args[i + 1]));
                } catch (Throwable e) {
                    LOG.warning("Failed to apply HPCL update", e);
                    SwingUtils.showErrorDialog("Failed to apply HPCL update: " + e);
                }
                return true;
            }
        }
        return false;
    }

    /// Called on the JavaFX thread after the download was verified.
    public static void requestApply(Path downloaded, Path self) throws IOException {
        if (self == null || !Files.isRegularFile(self)) {
            throw new IOException("Cannot locate the running HPCL package");
        }
        String suffix = suffixOf(self.getFileName().toString());
        Path staged = self.resolveSibling(".hpcl-update-" + System.currentTimeMillis() + suffix);
        Files.copy(downloaded, staged, StandardCopyOption.REPLACE_EXISTING);

        List<String> command = new ArrayList<>(Arrays.asList(
                javaBinary(), "-jar", staged.toString(), APPLY_FLAG, self.toAbsolutePath().toString()));
        LOG.info("Starting HPCL update helper: " + command);
        new ProcessBuilder(command)
                .directory(self.toAbsolutePath().getParent().toFile())
                .inheritIO()
                .start();
        EntryPoint.exit(0);
    }

    private static void install(Path target) throws Exception {
        Path self = JarUtils.thisJarPath();
        if (self == null) {
            throw new IOException("Cannot locate the staged update package");
        }

        // The old process needs a moment to release its file handle on Windows.
        IOException last = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            try {
                if (isExecutable(target)) {
                    ExecutableHeaderHelper.copyWithHeader(self, target);
                } else {
                    ExecutableHeaderHelper.copyWithoutHeader(self, target);
                }
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                Thread.sleep(500);
            }
        }
        if (last != null) {
            throw last;
        }

        // Remove older staged packages; the currently running one cannot be
        // deleted on Windows, so leave it for the next cleanup round.
        try (var stream = Files.list(target.toAbsolutePath().getParent())) {
            stream.filter(p -> p.getFileName().toString().startsWith(".hpcl-update-")
                    && !p.toAbsolutePath().equals(self.toAbsolutePath()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }

        spawn(target);
    }

    private static void spawn(Path target) throws IOException {
        List<String> command = new ArrayList<>();
        if (isExecutable(target) && OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            command.add(target.toAbsolutePath().toString());
        } else {
            command.add(javaBinary());
            command.add("-jar");
            command.add(target.toAbsolutePath().toString());
        }
        new ProcessBuilder(command)
                .directory(target.toAbsolutePath().getParent().toFile())
                .inheritIO()
                .start();
    }

    private static boolean isExecutable(Path path) {
        String suffix = suffixOf(path.getFileName().toString());
        return ".exe".equals(suffix) || ".sh".equals(suffix);
    }

    private static String suffixOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin",
                OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "java.exe" : "java")
                .toString();
    }
}
