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

import java.util.List;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.util.Util;

public class ClassLiteral extends RangeEntry {

    public static ClassLiteral create(int start, int length, String text, String className) {
        return new ClassLiteral(start, length, text, className);
    }

    static ClassLiteral read(int spec, int start, int length, String text, String data) {
        List<String> pts = Util.unquote(data, 1);
        if (pts.size() != 1)
            throw new IllegalArgumentException("Invalid Class Literal: " + data);
        return new ClassLiteral(start, length, text, pts.get(0));
    }

    private final String className;

    protected ClassLiteral(int start, int length, String text, String className) {
        super(RangeEntry.Type.CLASS_LITERAL, start, length, text);
        this.className = className;
    }

    public String getClassName() {
        return this.className;
    }

    @Override
    protected String getExtraFields() {
        return "Internal: " + className;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(Util.quote(className));
    }
}
