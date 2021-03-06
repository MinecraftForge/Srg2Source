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

public class FieldReference extends RangeEntry {
    public static FieldReference create(int start, int length, String text, String owner) {
        return new FieldReference(start, length, text, owner);
    }

    static FieldReference read(int spec, int start, int length, String text, String data) {
        if (data == null || data.isEmpty())
            throw new IllegalArgumentException("Invalid Field reference Missing Owner");
        return new FieldReference(start, length, text, data);
    }

    private final String owner;

    protected FieldReference(int start, int length, String text, String owner) {
        super(RangeEntry.Type.FIELD, start, length, text);
        this.owner = owner;
    }

    public String getName() { // The text is the field name.. Is there any case where this is not true?
        return getText();
    }

    public String getOwner() {
        return owner;
    }

    @Override
    protected String getExtraFields() {
        return "Owner: " + owner;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(owner);
    }
}
