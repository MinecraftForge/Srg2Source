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

public class FieldLiteral extends RangeEntry {
    public static FieldLiteral create(int start, int length, String text, String owner, String name) {
        return new FieldLiteral(start, length, text, owner, name);
    }

    static FieldLiteral read(int spec, int start, int length, String text, String data) {
        List<String> pts = Util.unquote(data, 2);
        if (pts.size() != 2)
            throw new IllegalArgumentException("Invalid Field Literal Missing Owner and name");
        return new FieldLiteral(start, length, text, pts.get(0), pts.get(1));
    }

    private final String owner;
    private final String name;

    protected FieldLiteral(int start, int length, String text, String owner, String name) {
        super(RangeEntry.Type.FIELD_LITERAL, start, length, text);
        this.owner = owner;
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public String getOwner() {
        return owner;
    }

    @Override
    protected String getExtraFields() {
        return "Owner: " + owner + ", Name: " + name;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(Util.quote(owner, name));
    }
}
