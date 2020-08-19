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

public class ParameterReference extends RangeEntry {

    public static ParameterReference create(int start, int length, String text, String owner, String name, String desc, int index) {
        return new ParameterReference(start, length, text, owner, name, desc, index);
    }

    static ParameterReference read(int spec, int start, int length, String text, String data) {
        List<String> pts = Util.unquote(data, 3);
        if (pts.size() != 4)
            throw new IllegalArgumentException("Invalid Paarameter reference: " + data);
        return new ParameterReference(start, length, text, pts.get(0), pts.get(1), pts.get(2), Integer.parseInt(pts.get(3)));
    }

    private final String owner;
    private final String name;
    private final String desc;
    private final int index;

    protected ParameterReference(int start, int length, String text, String owner, String name, String desc, int index) {
        super(RangeEntry.Type.PARAMETER, start, length, text);
        if (owner == null)
            System.currentTimeMillis();
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.index = index;
    }

    public String getOwner() {
        return this.owner;
    }

    public String getName() {
        return this.name;
    }

    public String getDescriptor() {
        return this.desc;
    }

    public int getIndex() {
        return this.index;
    }

    @Override
    protected String getExtraFields() {
        return "Owner: " + owner + ", Name: " + name + ", Descriptor: " + desc + ", Index: " + index;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        try {
        out.accept(Util.quote(owner, name, desc, Integer.toString(index)));
        } catch (Exception e) {
            System.currentTimeMillis();
        }
    }
}
