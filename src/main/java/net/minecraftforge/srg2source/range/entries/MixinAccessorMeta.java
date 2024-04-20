/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.range.entries;

import java.util.List;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.util.MemberInfo;
import net.minecraftforge.srg2source.util.Util;

public class MixinAccessorMeta extends MetaEntry {
    public static MixinAccessorMeta create(String owner, String name, String desc, String targetOwner, String targetName, String targetDesc, String prefix) {
        return new MixinAccessorMeta(new MemberInfo(owner, name, desc), new MemberInfo(targetOwner, targetName, targetDesc), prefix);
    }

    public static MixinAccessorMeta read(String data) {
        List<String> pts = Util.unquote(data, 7);
        if (pts.size() != 7)
            throw new IllegalArgumentException("Invalid Mixin Accessor Meta: " + data);
        return create(pts.get(0), pts.get(1), pts.get(2), pts.get(3), pts.get(4), pts.get(5), pts.get(6));
    }

    private final MemberInfo owner;
    private final MemberInfo target;
    private final String prefix;

    private MixinAccessorMeta(MemberInfo owner, MemberInfo target, String prefix) {
        super(MetaEntry.Type.MIXIN_ACCESSOR);
        this.owner = owner;
        this.target = target;
        this.prefix = prefix;
    }

    public MemberInfo getOwner() {
        return this.owner;
    }

    public MemberInfo getTarget() {
        return this.target;
    }

    public String getPrefix() {
        return this.prefix;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(Util.quote(
            this.owner.getOwner(), this.owner.getName(), this.owner.getDesc(),
            this.target.getOwner(), this.target.getName(), this.target.getDesc(),
            this.prefix
        ));
    }

    @Override
    public String toString() {
        return "MixinAccessorMeta[" + prefix  +", " + this.owner + " -> " + this.target + ']';
    }
}
