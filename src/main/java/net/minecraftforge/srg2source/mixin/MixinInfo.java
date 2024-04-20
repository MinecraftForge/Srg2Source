/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Type;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.srg2source.extract.ExtractUtil;

public class MixinInfo {
    private final String owner;
    private final ITypeBinding ownerType;

    @Nullable
    private String target;
    private final Set<String> targets = new HashSet<>();
    private final Map<String, ITypeBinding> targetTypes = new HashMap<>();
    private final Map<String, ShadowInfo> shadows = new HashMap<>();
    private final List<InterfaceInfo> interfaces = new ArrayList<>();
    private final Set<String> overwrites = new HashSet<>();

    public MixinInfo(String owner, ITypeBinding ownerType) {
        this.owner = owner;
        this.ownerType = ownerType;
    }

    public boolean isValid() {
        return !this.targets.isEmpty();
    }

    public String getOwner() {
        return owner;
    }

    public ITypeBinding getOwnerType() {
        return this.ownerType;
    }

    @Nullable
    public String getTarget() {
        return this.targets.size() == 1 ? this.target : null;
    }

    @Nullable
    public ITypeBinding getTargetType() {
        return this.targets.size() == 1 ? getTargetType(this.target) : null;
    }

    @Nullable
    public ITypeBinding getTargetType(String target) {
        return this.targetTypes.computeIfAbsent(target, t -> {
            return null; //TODO: Try and resolve?
        });
    }

    public Set<String> getTargets() {
        return this.targets;
    }

    public void addTarget(String name) {
        addTarget(name, null);
    }

    public void addTarget(String name, @Nullable ITypeBinding bind) {
        targets.add(name);

        if (bind != null)
            targetTypes.put(name, bind);

        if (this.target == null)
            this.target = name;
    }

    public void addShadow(String name, String desc, String prefix) {
        this.shadows.put(name + ' ' + desc, new ShadowInfo(name, desc, prefix));
    }

    @Nullable
    public ShadowInfo getShadow(String name, String desc) {
        return this.shadows.get(name + ' ' + desc);
    }

    public void addInterface(String prefix, Type type) {
        this.interfaces.add(new InterfaceInfo(prefix, type));
    }

    public List<InterfaceInfo> getInterfaces() {
        return this.interfaces;
    }

    public String getShadedOwner(String name, String desc) {
        return this.target != null && this.shadows.containsKey(name + ' ' + desc) ? this.target : null;
    }

    public void addOverwrite(String name, String desc) {
        this.overwrites.add(name + desc);
    }

    public boolean isOverwrite(String name, String desc) {
        return this.overwrites.contains(name + desc);
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

    public class InterfaceInfo {
        private final String target;
        private final ITypeBinding type;
        private final String prefix;
        private final Map<String, String> methods;

        private InterfaceInfo(String prefix, Type type) {
            this.prefix = prefix;
            this.type = type.resolveBinding();
            this.target = ExtractUtil.getInternalName(this.type);
            this.methods = buildMethods(this.type, new HashMap<>());
        }

        private Map<String, String> buildMethods(ITypeBinding binding, Map<String, String> ret) {
            for (IMethodBinding bind : binding.getDeclaredMethods()) {
                String key = bind.getName() + ExtractUtil.getDescriptor(bind);
                if (!ret.containsKey(key))
                    ret.put(key, ExtractUtil.getInternalName(ExtractUtil.findRoot(bind).getDeclaringClass()));
            }

            for (ITypeBinding bind : binding.getInterfaces())
                buildMethods(bind, ret);
            return ret;
        }

        public String getTarget() {
            return this.target;
        }

        public ITypeBinding getType() {
            return this.type;
        }

        public String getPrefix() {
            return this.prefix;
        }

        @Override
        public String toString() {
            return "Interface[" + getTarget() + ", " + getPrefix() + ']';
        }

        @Nullable
        public String findOwner(String name, String desc) {
            String key = (name.startsWith(prefix) ? name.substring(prefix.length()) : name) + desc;
            return this.methods.get(key);
        }
    }
}