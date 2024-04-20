/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.range.entries;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.range.IRange;

public class StructuralEntry implements IRange {
    public enum Type {
        CLASS,
        METHOD((me, data) -> {
            String[] pts = data.split(" ");
            if (pts.length != 4)
                throw new IllegalArgumentException("Missing required parts. Parts Length: " + pts.length);
            return new StructuralEntry(me, Integer.parseInt(pts[0]), Integer.parseInt(pts[1]), pts[2], pts[2]);
        }),
        ENUM,
        ANNOTATION,
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

    private StructuralEntry(Type type, int start, int length, String name, String desc) {
        this.type = type;
        this.start = start;
        this.length = length;
        this.name = name;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "StructuralEntry[type: " + type.name() +
                ", Start: " + start + ", Len: " + length +
                ", Name: \"" + name + "\", Desc: \"" + desc + "\"]";
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