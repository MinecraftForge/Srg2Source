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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    @Override
    public String toString() {
        return "Mixin[" + targets.stream().collect(Collectors.joining(",")) + ']';
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
            return "Shadow[" + name + ' ' + desc + ' ' + prefix + ']';
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

    public enum AccessorType {
        GETTER("get", "is"),
        SETTER("set"),
        PROXY("call", "invoke"),
        FACTORY("new", "create");

        private final String[] prefixes;
        private AccessorType(String... prefixes) {
            this.prefixes = prefixes;
        }

        private String[] getPrefixes() {
            return this.prefixes;
        }

        @Nullable
        private static AccessorType getByPrefix(String prefix) {
            for (AccessorType e : values())
                for (String v : e.getPrefixes())
                    if (v.equals(prefix))
                        return e;
            return null;
        }
    }

    public static class AccessorName {
        //Must be one of the prefixes, a capitol letter, then any letters and optional 'md'.. Unsure what the md is for:
        // https://github.com/SpongePowered/Mixin/blob/2817c670e8794a3076a421ed0098e80ed2a89b25/src/main/java/org/spongepowered/asm/mixin/gen/AccessorInfo.java#L148
        private static final Pattern REGEX = Pattern.compile("^(" + Arrays.stream(AccessorType.values()).map(e -> e.prefixes).flatMap(Arrays::stream).collect(Collectors.joining("|")) + ")(([A-Z])(.*?))(_\\$md.*)?$");

        @Nullable
        public static AccessorName from(String methodName, String owner, @Nullable String value) {
            Matcher matcher = REGEX.matcher(methodName);
            if (!matcher.matches())
                return null;

            String prefix = matcher.group(1);
            AccessorType type = AccessorType.getByPrefix(prefix);
            if (type == null)
                return null;

            boolean isUpper = type != AccessorType.FACTORY && matcher.group(2).toUpperCase(Locale.ROOT).equals(matcher.group(2));
            String target = value != null ? value : isUpper ? matcher.group(2) : matcher.group(3).toLowerCase(Locale.ROOT) + matcher.group(4);

            return new AccessorName(type, methodName, target, prefix);
        }


        private final AccessorType type;
        private final String methodName;
        private final String target;
        private final String prefix;

        private AccessorName(AccessorType type, String methodName, String target, String prefix) {
            this.type = type;
            this.methodName = methodName;
            this.target = target;
            this.prefix = prefix;
        }

        public AccessorType getType() {
            return this.type;
        }

        public String getMethod() {
            return this.methodName;
        }

        public String getTarget() {
            return this.target;
        }

        public String getPrefix() {
            return this.prefix;
        }

        @Override
        public String toString() {
            return "AccessorName[" +
                    "type=" + this.type.name() +
                    ",method=" + this.methodName +
                    ",target=" + this.target +
                    ",prefix=" + this.prefix + ']';
        }
    }
}