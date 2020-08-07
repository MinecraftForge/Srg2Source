package net.minecraftforge.srg2source.api;

import org.eclipse.jdt.core.JavaCore;

public enum SourceVersion {
    JAVA_1_6(JavaCore.VERSION_1_6),
    JAVA_1_7(JavaCore.VERSION_1_7),
    JAVA_1_8(JavaCore.VERSION_1_8),
    JAVA_9(JavaCore.VERSION_9),
    JAVA_10(JavaCore.VERSION_10);

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
