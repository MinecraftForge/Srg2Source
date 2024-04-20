/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.range.entries;

import java.util.List;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.util.Util;

public class LocalVariableReference extends RangeEntry {

    public static LocalVariableReference create(int start, int length, String text, String owner, String name, String desc, int index, String varType) {
        return new LocalVariableReference(start, length, text, owner, name, desc, index, varType);
    }

    static LocalVariableReference read(int spec, int start, int length, String text, String data) {
        List<String> pts = Util.unquote(data, 4);
        if (pts.size() != 5)
            throw new IllegalArgumentException("Invalid Local Varaible reference: " + data);
        return new LocalVariableReference(start, length, text, pts.get(0), pts.get(1), pts.get(2), Integer.parseInt(pts.get(3)), pts.get(4));
    }

    private final String owner;
    private final String name;
    private final String desc;
    private final int index;
    private final String varType;

    protected LocalVariableReference(int start, int length, String text, String owner, String name, String desc, int index, String varType) {
        super(RangeEntry.Type.LOCAL_VARIABLE, start, length, text);
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.index = index;
        this.varType = varType;
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

    public String getVarType() {
        return this.varType;
    }

    @Override
    protected String getExtraFields() {
        return "Owner: " + owner + ", Name: " + name + ", Descriptor: " + desc + ", Index: " + index + ", VarType: " + this.varType;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        try {
            out.accept(Util.quote(owner, name, desc, Integer.toString(index), varType));
        } catch (Exception e) {
            System.currentTimeMillis();
        }
    }
}
