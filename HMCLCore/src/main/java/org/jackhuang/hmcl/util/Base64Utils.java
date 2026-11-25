/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

/// Decodes basic Base64 without going through the JDK decoder, and identifies
/// runtimes that need the Base64 intrinsics workaround.
///
/// `java.util.Base64` delegates to a HotSpot C2 intrinsic on x86-64 CPUs with
/// AVX-512 VBMI. That intrinsic has a heap out-of-bounds write when the input
/// ends with padding (CVE-2026-114938, "Java4Shell"). The decoder below is a
/// plain Java implementation, so it never reaches the affected code path.
///
/// @see <a href="https://openjdk.org/groups/vulnerability/advisories/2026-11-17">OpenJDK advisory</a>
@NotNullByDefault
public final class Base64Utils {

    /// Decodes a string in the basic Base64 alphabet into a newly allocated byte array.
    /// A final group of two or three characters may omit padding. If present, padding
    /// must complete the group. Unused low bits of the last digit are ignored.
    ///
    /// @throws IllegalArgumentException if the input contains an invalid character
    ///         (including whitespace), an incomplete digit group, or incorrect padding
    public static byte[] decode(String encoded) {
        int length = encoded.length();
        int digits = length;
        if (encoded.endsWith("=")) {
            if ((length & 3) != 0)
                throw new IllegalArgumentException("Invalid Base64 padding");
            digits -= encoded.charAt(length - 2) == '=' ? 2 : 1;
        }

        int tail = digits % 4;
        if (tail == 1)
            throw new IllegalArgumentException("Incomplete Base64 group");

        byte[] decoded = new byte[digits / 4 * 3 + tail * 6 / 8];
        int bits = 0;
        int buffer = 0;
        int offset = 0;
        for (int i = 0; i < digits; i++) {
            buffer = (buffer << 6) | decodeDigit(encoded.charAt(i));
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                decoded[offset++] = (byte) (buffer >>> bits);
            }
        }
        return decoded;
    }

    /// Returns the six-bit value of a basic Base64 digit.
    ///
    /// @throws IllegalArgumentException if the character is outside the basic alphabet
    private static int decodeDigit(char ch) {
        if (ch >= 'A' && ch <= 'Z') return ch - 'A';
        if (ch >= 'a' && ch <= 'z') return ch - 'a' + 26;
        if (ch >= '0' && ch <= '9') return ch - '0' + 52;
        if (ch == '+') return 62;
        if (ch == '/') return 63;
        throw new IllegalArgumentException("Invalid Base64 character");
    }

    /// Returns whether the game JVM should disable Base64 intrinsics for CVE-2026-114938.
    ///
    /// Only x86-64 runtimes at the affected versions need the flag; ARM64 and the
    /// already-fixed releases keep the intrinsic for decoding performance.
    public static boolean shouldDisableBase64Intrinsics(JavaInfo javaInfo) {
        if (javaInfo.getPlatform().arch() != Architecture.X86_64)
            return false;

        int javaFeatureVersion = javaInfo.getParsedVersion();
        VersionNumber javaVersion = javaInfo.getVersionNumber();
        return switch (javaFeatureVersion) {
            case 18, 19, 20, 22, 23, 24, 26 -> true;
            case 17 -> javaVersion.compareTo("17.0.6") >= 0 && javaVersion.compareTo("17.0.21.1") < 0;
            case 21 -> javaVersion.compareTo("21.0.13.1") < 0;
            case 25 -> javaVersion.compareTo("25.0.5.1") < 0;
            case 27 -> javaVersion.compareTo("27.0.1.1") < 0;
            default -> false;
        };
    }

    /// Prevents instantiation.
    private Base64Utils() {
    }
}
