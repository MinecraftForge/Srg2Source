/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.util;

import java.util.Objects;

public class MemberInfo {
    private final String owner;
    private final String name;
    private final String desc;

    public MemberInfo(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    public String getOwner() {
        return this.owner;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return this.desc;
    }

    @Override
    public String toString() {
        return this.owner  + '/' + this.name + this.desc;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.owner, this.name, this.desc);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MemberInfo m = (MemberInfo)o;
        return Objects.equals(this.owner, m.owner) &&
               Objects.equals(this.name, m.name) &&
               Objects.equals(this.desc, m.desc);
    }

}
