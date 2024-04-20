/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
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
