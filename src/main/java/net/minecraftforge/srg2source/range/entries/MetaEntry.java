/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.range.entries;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class MetaEntry {
    public enum Type {
        MIXIN_ACCESSOR(MixinAccessorMeta::read),
        ;

        private Function<String, MetaEntry> read;
        private Type(Function<String, MetaEntry> read) {
            this.read = read;
        }

        private MetaEntry read(String data) {
            return this.read.apply(data);
        }
    }

    public static MetaEntry read(int spec, String data) {
        int idx = data.indexOf(' ');
        String type = idx == -1 ? data : data.substring(0, idx);
        data = idx == -1 ? "" : data.substring(idx + 1);

        Type t = null;
        try {
            t = Type.valueOf(type.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown Meta Type: " + type);
        }
        return t.read(data);
    }

    private final Type type;
    protected MetaEntry(Type type) {
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }

    public final void write(Consumer<String> out) {
        this.writeInternal(l -> out.accept("meta " + this.type.name().toLowerCase(Locale.ENGLISH) + (l == null || l.isEmpty() ? "" : ' ' + l)));
    }

    protected abstract void writeInternal(Consumer<String> out);
}
