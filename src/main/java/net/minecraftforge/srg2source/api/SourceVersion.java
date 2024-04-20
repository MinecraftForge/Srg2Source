/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
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
    JAVA_18(JavaCore.VERSION_18),
    JAVA_19(JavaCore.VERSION_19),
    JAVA_20(JavaCore.VERSION_20),
    JAVA_21(JavaCore.VERSION_21),
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
