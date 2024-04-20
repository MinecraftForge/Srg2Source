/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.range.entries;

import java.util.List;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.util.Util;

public class MethodLiteral extends RangeEntry {
    public static MethodLiteral create(int start, int length, String text, String owner, String name, String desc) {
        return new MethodLiteral(start, length, text, owner, name, desc);
    }

    static MethodLiteral read(int spec, int start, int length, String text, String data) {
        List<String> pts = Util.unquote(data, 3);
        if (pts.size() != 3)
            throw new IllegalArgumentException("Invalid String Literal Missing Owner, name, or descriptor");
        return new MethodLiteral(start, length, text, pts.get(0), pts.get(1), pts.get(2));
    }

    private final String owner;
    private final String name;
    private final String desc;

    protected MethodLiteral(int start, int length, String text, String owner, String name, String desc) {
        super(RangeEntry.Type.METHOD_LITERAL, start, length, text);
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    public String getName() {
        return this.name;
    }

    public String getOwner() {
        return owner;
    }

    public String getDescriptor() {
        return desc;
    }

    @Override
    protected String getExtraFields() {
        return "Owner: " + owner + ", Name: " + name + ", Descriptor:" + desc;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(Util.quote(owner, name, desc));
    }
}
