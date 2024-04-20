/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.apply;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraftforge.srg2source.range.RangeMap;
import net.minecraftforge.srg2source.range.entries.MetaEntry;
import net.minecraftforge.srg2source.range.entries.MixinAccessorMeta;

public class ClassMeta {
    public static ClassMeta create(RangeApplier applier, Map<String, RangeMap> ranges) {
        ClassMeta ret = new ClassMeta(applier);
        for (RangeMap range : ranges.values()) {
            for (MetaEntry entry : range.getMeta()) {
                switch (entry.getType()) {
                    case MIXIN_ACCESSOR: {
                        MixinAccessorMeta acc = (MixinAccessorMeta)entry;
                        ret.accessors.computeIfAbsent(acc.getOwner().getOwner(), k -> new HashMap<>()).put(acc.getOwner().getName() + acc.getOwner().getDesc(), acc);
                        break;
                    }
                }
            }
        }
        return ret;
    }

    private final RangeApplier applier;
    private final Map<String, Map<String, MixinAccessorMeta>> accessors = new HashMap<>();

    private ClassMeta(RangeApplier applier) {
        this.applier = applier; //TODO: Abstract this  to a IMapper interface?
    }

    public String mapMethod(String owner, String name, String desc) {
        Map<String, MixinAccessorMeta> tmp = accessors.get(owner);
        if (tmp == null)
            return name;

        MixinAccessorMeta acc = tmp.get(name + desc);
        if (acc == null)
            return name;

        String tName = acc.getTarget().getName();

        if (acc.getTarget().getDesc().indexOf('(') == -1) { //Fields
            String renamed = this.applier.mapField(acc.getTarget().getOwner(), tName);
            if (renamed != name)
                return acc.getPrefix() + renamed.substring(0, 1).toUpperCase(Locale.ROOT) + renamed.substring(1);
        } else {
            if ("<init>".equals(tName)) {
                String renamed = this.applier.mapClass(acc.getTarget().getOwner());
                if (renamed != acc.getTarget().getOwner()) {
                    int idx = renamed.lastIndexOf('$');
                    if (idx != -1) renamed = renamed.substring(idx + 1);
                    idx = renamed.lastIndexOf('/');
                    if (idx != -1) renamed = renamed.substring(idx + 1);
                    return acc.getPrefix() + renamed.substring(0, 1).toUpperCase(Locale.ROOT) + renamed.substring(1);
                }
            } else {
                String renamed = this.applier.mapMethod(acc.getTarget().getOwner(), tName, acc.getTarget().getDesc());
                if (renamed != name)
                    return acc.getPrefix() + renamed.substring(0, 1).toUpperCase(Locale.ROOT) + renamed.substring(1);
            }
        }
        return name;
    }
}
