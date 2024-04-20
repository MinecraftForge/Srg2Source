/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.extract;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;

public class ExtractUtil {
    public static String getInternalName(ITypeBinding binding) {
        return getInternalName("{unknown}", binding, null);
    }
    public static String getInternalName(String filename, ITypeBinding binding, @Nullable ASTNode node) {
        String name = binding.getErasure().getBinaryName();
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("Could not get Binary name! " + filename + (node == null ? "" : " @ " + node.getStartPosition()));
        return name.replace('.', '/'); // Binary name is a mix and includes . and $, so convert to actual binary name;
    }

    public static String getDescriptor(IMethodBinding method) {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        if (method.isConstructor()) { //Synthetic args
            ITypeBinding type = method.getDeclaringClass();
            if (type.isEnum())
                buf.append("Ljava/lang/String;I");
            else if (type.isNested() && type.isClass() && ((type.getModifiers() & Opcodes.ACC_STATIC) == 0))
                buf.append(getTypeSignature(type.getDeclaringClass()));
        }
        for (ITypeBinding param : method.getParameterTypes())
            buf.append(getTypeSignature(param));
        buf.append(')');
        buf.append(getTypeSignature(method.getReturnType()));
        return buf.toString();
    }

    public static String getDescriptor(List<SingleVariableDeclaration> params) {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (SingleVariableDeclaration var : params) {
            ITypeBinding bind = var.getType().resolveBinding();
            if (var.isVarargs())
                bind = bind.createArrayType(1);
            buf.append(getTypeSignature(bind));
        }
        buf.append(')');
        return buf.toString();
    }

    private static final String PRIMITIVE_TYPES = "ZCBSIJFDV";
    // Get the full binary name, including L; wrappers around class names
    public static String getTypeSignature(ITypeBinding type) {
        String ret = type.getErasure().getBinaryName().replace('.', '/');

        int aidx = ret.lastIndexOf('[');
        String prefix = null;
        if (aidx != -1) {
            prefix = ret.substring(0, aidx + 1);
            ret = ret.substring(aidx + 1);
        }

        if (!PRIMITIVE_TYPES.contains(ret) && (ret.charAt(0) != 'L' || ret.charAt(ret.length() - 1) != ';'))
            ret = 'L' + ret + ';';
        return prefix == null ? ret : prefix + ret;
    }

    @Nullable
    public static IMethodBinding findRoot(ITypeBinding type, String name, String desc) {
        for (IMethodBinding bind : type.getDeclaredMethods()) {
            if (bind.getName().equals(name) && getDescriptor(bind).equals(desc))
                return findRoot(bind);
        }

        IMethodBinding root = findRoot(type.getSuperclass(), name, desc);
        if (root != null)
            return root;

        for (ITypeBinding intf : type.getInterfaces()) {
            root = findRoot(intf, name, desc);
            if (root != null)
                return root;
        }

        return null;
    }

    public static IMethodBinding findRoot(IMethodBinding mtd) {
        ITypeBinding clazz = mtd.getDeclaringClass();
        if (clazz == null)
            return mtd;
        IMethodBinding root = findRoot(mtd, clazz.getSuperclass());
        if (root != null)
            return root.getMethodDeclaration();

        for (ITypeBinding intf : clazz.getInterfaces()) {
            root = findRoot(mtd, intf);
            if (root != null)
                return root.getMethodDeclaration();
        }
        return mtd;
    }

    @Nullable
    private static IMethodBinding findRoot(IMethodBinding target, @Nullable ITypeBinding type) {
        if (type == null)
            return null;

        if (target.isConstructor())
            return target;

        for (IMethodBinding mtd : type.getDeclaredMethods())
            if (target.overrides(mtd))
                return findRoot(mtd);

        IMethodBinding root = findRoot(target, type.getSuperclass());
        if (root != null)
            return root;

        for (ITypeBinding intf : type.getInterfaces()) {
            root = findRoot(target, intf);
            if (root != null)
                return root;
        }

        return null;
    }
}
