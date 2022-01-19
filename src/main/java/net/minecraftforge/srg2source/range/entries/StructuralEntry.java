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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.range.IRange;

public class StructuralEntry implements IRange {
    public enum Type {
        ROOT,
        CLASS,
        METHOD((me, data) -> {
            String[] pts = data.split(" ");
            if (pts.length != 4)
                throw new IllegalArgumentException("Missing required parts. Parts Length: " + pts.length);
            return new StructuralEntry(me, Integer.parseInt(pts[0]), Integer.parseInt(pts[1]), pts[2], pts[3]);
        }),
        ENUM,
        ANNOTATION((me, data) -> {
            String[] pts = data.split(" ");
            if (pts.length != 3)
                throw new IllegalArgumentException("Missing required parts. Parts Length: " + pts.length);
            return new StructuralEntry(me, Integer.parseInt(pts[0]), Integer.parseInt(pts[1]), pts[2], null);
        }),
        INTERFACE,
        RECORD;

        private BiFunction<Type, String, StructuralEntry> read;
        private Type(BiFunction<Type, String, StructuralEntry> read) {
            this.read = read;
        }
        private Type() {
            this((me, data) -> {
                String[] pts = data.split(" ");
                if (pts.length != 3)
                    throw new IllegalArgumentException("Missing required parts. Parts Length: " + pts.length);
                return new StructuralEntry(me, Integer.parseInt(pts[0]), Integer.parseInt(pts[1]), pts[2], null);
            });
        }

        private StructuralEntry read(String data) {
            return this.read.apply(this, data);
        }
    }

    public static StructuralEntry createRoot() {
        return new StructuralEntry(Type.ROOT, -1, -1, null, null);
    }

    public static StructuralEntry createAnnotation(int start, int length, String name) {
        return new StructuralEntry(Type.ANNOTATION, start, length, name, null);
    }

    public static StructuralEntry createClass(int start, int length, String name) {
        return new StructuralEntry(Type.CLASS, start, length, name, null);
    }

    public static StructuralEntry createEnum(int start, int length, String name) {
        return new StructuralEntry(Type.ENUM, start, length, name, null);
    }

    public static StructuralEntry createRecord(int start, int length, String name) {
        return new StructuralEntry(Type.RECORD, start, length, name, null);
    }

    public static StructuralEntry createInterface(int start, int length, String name) {
        return new StructuralEntry(Type.INTERFACE, start, length, name, null);
    }

    public static StructuralEntry createMethod(int start, int length, String name, String desc) {
        return new StructuralEntry(Type.METHOD, start, length, name, desc);
    }

    public static StructuralEntry read(int spec, String type, String data) {
        Type t = null;
        try {
            t = Type.valueOf(type.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown Structure Type: " + type);
        }
        return t.read(data);
    }

    private final Type type;
    private final int start;
    private final int length;
    private final String name;
    private final String desc;

    private final List<StructuralEntry> structures;
    private final List<RangeEntry> entries;

    private StructuralEntry(Type type, int start, int length, String name, String desc) {
        this.type = type;
        this.start = start;
        this.length = length;
        this.name = name;
        this.desc = desc;
        this.structures = new ArrayList<>();
        this.entries = new ArrayList<>();
    }

    public void addEntry(RangeEntry entry) {
        this.entries.add(entry);
    }

    public boolean hasEntries() {
        return !this.entries.isEmpty();
    }

    public List<RangeEntry> getEntries(boolean children) {
        final List<RangeEntry> entries = new ArrayList<>();
        entries.addAll(this.entries);

        // Scan structures for children entries
        if (children) {
            for (StructuralEntry structure : this.structures)
                entries.addAll(structure.getEntries(children));
        }

        return Collections.unmodifiableList(entries);
    }

    public void addStructure(StructuralEntry structure) {
        this.structures.add(structure);
    }

    public boolean hasStructures() {
        return !this.structures.isEmpty();
    }

    public List<StructuralEntry> getStructures(boolean children) {
        final List<StructuralEntry> structures = new ArrayList<>();
        structures.addAll(this.structures);

        // Scan structures for childrens
        if (children) {
            for (StructuralEntry structure : this.structures)
                structures.addAll(structure.getStructures(children));
        }

        return Collections.unmodifiableList(structures);
    }

    @Override
    public String toString() {
        return "StructuralEntry[type: " + type.name() +
                ", Start: " + start + ", Len: " + length +
                ", Name: \"" + name + "\", Desc: \"" + desc + "\", Entries: \"" + entries.size()  + "\", Structures: \"" + structures.size() + "\"]";
    }

    public Type getType() {
        return this.type;
    }

    @Override
    public int getStart() {
        return this.start;
    }

    @Override
    public int getLength() {
        return this.length;
    }

    public String getName() {
        return this.name;
    }

    public String getDescriptor() {
        return this.desc;
    }

    public void write(Consumer<String> out) {
        String line = this.type.name().toLowerCase(Locale.ENGLISH) + "def "
                + start + ' ' + length + ' ' + name;
        if (this.type == Type.METHOD)
            line += ' ' + desc;
        out.accept(line);
    }
}