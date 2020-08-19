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

import java.util.function.Consumer;

public class PackageReference extends RangeEntry {
    public static PackageReference create(int start, int length, String name) {
        return new PackageReference(start, length, name);
    }

    static PackageReference read(int spec, int start, int length, String text, String data) {
        return new PackageReference(start, length, text);
    }

    protected PackageReference(int start, int length, String text) {
        super(Type.PACKAGE, start, length, text);
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(null);
    }
}
