/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.range.entries;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.range.IRange;
import net.minecraftforge.srg2source.util.Util;

public abstract class RangeEntry implements IRange {
    public enum Type {
        PACKAGE(PackageReference::read),
        CLASS(ClassReference::read),
        CLASS_LITERAL(ClassLiteral::read),
        FIELD(FieldReference::read),
        FIELD_LITERAL(FieldLiteral::read),
        METHOD(MethodReference::read),
        METHOD_LITERAL(MethodLiteral::read),
        PARAMETER(ParameterReference::read),
        LOCAL_VARIABLE(LocalVariableReference::read),
        ;

        private Factory<?> factory;
        private Type(Factory<?> factory) {
            this.factory = factory;
        }
        private RangeEntry read(int spec, String data) {
            List<String> pts = Util.unquote(data, 3);
            if (pts.size() < 3)
                throw new IllegalArgumentException("Invalid line, must contain atleast 3 parts: " + data);
            return this.factory.create(spec, Integer.parseInt(pts.get(0)), Integer.parseInt(pts.get(1)), pts.get(2), pts.size() == 4 ? pts.get(3) : "");
        }
    }

    public static RangeEntry read(int spec, String type, String data) {
        Type ret = null;
        try {
            ret = Type.valueOf(type.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown Structure Type: " + type.toUpperCase(Locale.ENGLISH));
        }
        return ret.read(spec, data);
    }

    private final Type type;
    private final int start;
    private final int length;
    private final String text;
    private String toString = null;

    protected RangeEntry(Type type, int start, int length, String text) {
        this.type = type;
        this.start = start;
        this.length = length;
        this.text = text;
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

    public String getText() {
        return this.text;
    }

    @Override
    public String toString() {
        if (toString == null) {
            String extra = getExtraFields();
            toString = getClass().getSimpleName() + '[' + start + ':' + length +
                    ", \"" + text + "\"" +
                    (extra == null ? "" : ", " + extra) +
                    "]";
        }
        return toString;
    }

    protected String getExtraFields() {
        return null;
    }

    public final void write(Consumer<String> out) {
        this.writeInternal(l -> out.accept(this.type.name().toLowerCase(Locale.ENGLISH) + ' ' +
                getStart() + ' ' + getLength() + ' ' + Util.quote(getText()) + (l == null ? "" : ' ' + l)));
    }

    protected abstract void writeInternal(Consumer<String> out);

    @FunctionalInterface
    interface Factory<T extends RangeEntry> {
        T create(int spec, int start, int length, String text, String data);
    }
}