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
