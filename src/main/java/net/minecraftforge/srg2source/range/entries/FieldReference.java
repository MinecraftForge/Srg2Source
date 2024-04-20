/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
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
