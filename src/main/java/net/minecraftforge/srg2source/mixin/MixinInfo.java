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

package net.minecraftforge.srg2source.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class MixinInfo {
    private final String owner;
    @Nullable
    private final String target;
    private final Set<String> targets;
    private final Map<String, ShadowInfo> shadows = new HashMap<>();

    MixinInfo(String owner, Set<String> targets) {
        this.owner = owner;
        this.targets = targets;
        this.target = targets.size() == 1 ? targets.iterator().next() : null;
    }

    public String getOwner() {
        return owner;
    }

    @Nullable
    public String getTarget() {
        return this.target;
    }

    public Set<String> getTargets() {
        return this.targets;
    }

    public void addShadow(String name, String desc, String prefix) {
        this.shadows.put(name + ' ' + desc, new ShadowInfo(name, desc, prefix));
    }

    @Nullable
    public ShadowInfo getShadow(String name, String desc) {
        return this.shadows.get(name + ' ' + desc);
    }

    public String getShadedOwner(String name, String desc) {
        return this.target != null && this.shadows.containsKey(name + ' ' + desc) ? this.target : null;
    }

    public class ShadowInfo {
        private final String name;
        private final String desc;
        @Nullable
        private final String prefix;

        private ShadowInfo(String name, String desc, @Nullable String prefix) {
            this.name = name;
            this.desc = desc;
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return name + ' ' + desc + ' ' + prefix;
        }

        public String getName() {
            return this.name;
        }

        public String getDesc() {
            return this.desc;
        }

        @Nullable
        public String getPrefix() {
            return this.prefix;
        }
    }
}