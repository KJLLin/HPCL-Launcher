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
package org.jackhuang.hmcl.setting;

/// Which GitHub Releases HPCL's self-update channel subscribes to.
///
/// The mapping to the publisher side is deliberately simple: a release
/// flagged as a GitHub pre-release belongs to BETA, everything else to
/// STABLE. BETA also sees stable releases, so beta users always get the
/// newest build; STABLE users never receive a pre-release.
public enum AutoUpdateChannel {
    /// Only releases that are not marked as pre-release and whose tag does
    /// not contain a preview marker (SNAPSHOT / dev / beta / alpha / rc).
    STABLE("stable"),
    /// Every published release, pre-releases included.
    BETA("beta");

    /// HPCL itself is currently published as a dev / SNAPSHOT pre-release,
    /// so the default follows the beta channel.
    public static final AutoUpdateChannel DEFAULT = BETA;

    private final String id;

    AutoUpdateChannel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
