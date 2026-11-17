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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.SwingUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Applies a downloaded HPCL build. A running JVM cannot overwrite its own
/// jar/exe on Windows, so a short-lived helper process is started from the
/// downloaded package: it waits for the old process to exit, writes the new
/// package over the old path (keeping the PE stub for .exe/.sh builds) and
/// relaunches the launcher.
///
/// The relaunch deliberately restores the previous working directory, JVM
/// options and resolved config roots. Otherwise a hot update would start the
/// new build against a different `.hpcl` directory and every setting - the
/// update channel, the auto-check switch, accounts - would look reset.
public final class HPCLUpdateInstaller {
    private HPCLUpdateInstaller() {
    }

    private static final String APPLY_FLAG = "--hpcl-apply-to";
    private static final String WORKDIR_FLAG = "--hpcl-workdir";
    private static final String JVMARGS_FLAG = "--hpcl-jvm-args";

    /// @return whether the caller should exit (this is the helper process).
    public static boolean processArguments(String[] args) {
        Path target = null;
        String workDir = null;
        List<String> jvmArgs = List.of();
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case APPLY_FLAG -> target = Paths.get(args[++i]);
                case WORKDIR_FLAG -> workDir = args[++i];
                case JVMARGS_FLAG -> jvmArgs = decode(args[++i]);
                default -> {
                }
            }
        }
        if (target == null) {
            return false;
        }
        try {
            install(target, workDir, jvmArgs);
        } catch (Throwable e) {
            LOG.warning("Failed to apply HPCL update", e);
            SwingUtils.showErrorDialog("Failed to apply HPCL update: " + e);
        }
        return true;
    }

    /// Called on the JavaFX thread after the download was verified.
    public static void requestApply(Path downloaded, Path self) throws IOException {
        if (self == null || !Files.isRegularFile(self)) {
            throw new IOException("Cannot locate the running HPCL package");
        }
        String suffix = suffixOf(self.getFileName().toString());
        Path staged = self.resolveSibling(".hpcl-update-" + System.currentTimeMillis() + suffix);
        Files.copy(downloaded, staged, StandardCopyOption.REPLACE_EXISTING);

        // Snapshot everything the new process needs to land in the same
        // configuration directory as the current one.
        List<String> jvmArgs = new ArrayList<>(ManagementFactory.getRuntimeMXBean().getInputArguments());
        addIfAbsent(jvmArgs, "hpcl.home", Metadata.HMCL_USER_HOME.toString());
        addIfAbsent(jvmArgs, "hpcl.dir", Metadata.HMCL_LOCAL_HOME.toString());
        addIfAbsent(jvmArgs, "hpcl.dependencies.dir", Metadata.DEPENDENCIES_DIRECTORY.toString());

        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.addAll(jvmArgs);
        command.add("-jar");
        command.add(staged.toString());
        command.add(APPLY_FLAG);
        command.add(self.toAbsolutePath().toString());
        command.add(WORKDIR_FLAG);
        command.add(System.getProperty("user.dir", ""));
        command.add(JVMARGS_FLAG);
        command.add(encode(jvmArgs));
        LOG.info("Starting HPCL update helper: " + command);
        new ProcessBuilder(command)
                .directory(self.toAbsolutePath().getParent().toFile())
                .inheritIO()
                .start();
        EntryPoint.exit(0);
    }

    private static void install(Path target, String workDir, List<String> jvmArgs) throws Exception {
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

        spawn(target, workDir, jvmArgs);
    }

    private static void spawn(Path target, String workDir, List<String> jvmArgs) throws IOException {
        List<String> command = new ArrayList<>();
        boolean directExe = isExecutable(target) && OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS;
        if (directExe) {
            command.add(target.toAbsolutePath().toString());
        } else {
            command.add(javaBinary());
            command.addAll(jvmArgs);
            command.add("-jar");
            command.add(target.toAbsolutePath().toString());
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        // Restore the original working directory: the default `.hpcl` location
        // is resolved from it, so changing it would hide every setting.
        File previousDir = workDir == null || workDir.isBlank() ? null : new File(workDir);
        builder.directory(previousDir != null && previousDir.isDirectory()
                ? previousDir : target.toAbsolutePath().getParent().toFile());
        // The packaged .exe cannot receive JVM options, so export the resolved
        // roots as environment variables too (Metadata accepts both).
        builder.environment().put("HPCL_USER_HOME", Metadata.HMCL_USER_HOME.toString());
        builder.environment().put("HPCL_LOCAL_HOME", Metadata.HMCL_LOCAL_HOME.toString());
        builder.environment().put("HPCL_DEPENDENCIES_DIR", Metadata.DEPENDENCIES_DIRECTORY.toString());
        builder.inheritIO().start();
    }

    private static void addIfAbsent(List<String> args, String key, String value) {
        String prefix = "-D" + key + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return;
            }
        }
        args.add(prefix + value);
    }

    private static String encode(List<String> args) {
        return Base64.getEncoder().encodeToString(
                String.join("\u0000", args).getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> decode(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        String joined = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        if (joined.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(Arrays.asList(joined.split("\u0000", -1)));
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
