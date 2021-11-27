/*
 * Srg2Source
 * Copyright (c) 2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srg2source.api;

import org.eclipse.jdt.core.JavaCore;

public enum SourceVersion {
    JAVA_1_6(JavaCore.VERSION_1_6),
    JAVA_1_7(JavaCore.VERSION_1_7),
    JAVA_1_8(JavaCore.VERSION_1_8),
    JAVA_9(JavaCore.VERSION_9),
    JAVA_10(JavaCore.VERSION_10),
    JAVA_11(JavaCore.VERSION_11),
    JAVA_12(JavaCore.VERSION_12),
    JAVA_13(JavaCore.VERSION_13),
    JAVA_14(JavaCore.VERSION_14),
    JAVA_15(JavaCore.VERSION_15),
    JAVA_16(JavaCore.VERSION_16),
    JAVA_17(JavaCore.VERSION_17),
    ;

    private String spec;
    private SourceVersion(String spec) {
        this.spec = spec;
    }

    public String getSpec() {
        return spec;
    }

    public static SourceVersion parse(String name) {
        if (name == null)
            return null;

        for (SourceVersion v : SourceVersion.values()) {
            if (v.name().equals(name) || v.getSpec().equals(name))
                return v;
        }

        return null;
    }
}
