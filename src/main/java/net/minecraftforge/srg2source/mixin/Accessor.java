/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.srg2source.extract.ExtractUtil;
import net.minecraftforge.srg2source.extract.MixinProcessor;
import net.minecraftforge.srg2source.mixin.MixinInfo.AccessorName;
import net.minecraftforge.srg2source.mixin.MixinInfo.AccessorType;

/*
 * @Accessor and @Invoker only have 2 values.
 * The standard "remap", and an optional "value"
 * @Accessors are designed for Fields
 *   getter: "get" or "is" takes no arguments, return type must be field type
 *   setter: "set" takes one argument of the field type, and returns void
 * @Invokers are designed for Methods
 *   proxy: "call" or "invoke", Descriptor must match exactly to the target
 *   factory: "new" or "create", Descriptor must match a existing <init> function exept for the return type, which must be the Mixin target's type.
 */
public class Accessor extends AnnotationBase {
    private final String typeName;

    public Accessor(MixinProcessor processor) {
        this(processor, MixinAnnotation.ACCESSOR);
    }

    // This is protected because the @Invoker annotation is the same as @Accessor just for a subset of methods, so this implementation supports both.
    protected Accessor(MixinProcessor processor, MixinAnnotation type) {
        super(processor, type);
        this.typeName = type == MixinAnnotation.ACCESSOR ? "@Accessor" : "@Invoke";
    }

    private MethodDeclaration getMethod(ASTNode node) {
        ASTNode parent = node.getParent();
        if (parent.getNodeType() != ASTNode.METHOD_DECLARATION) { // It should only be valid on these things.
            error(parent, "Found " + typeName + " annotation on non-method declaration: " + node.toString());
            return null;
        }

        return (MethodDeclaration)parent;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(NormalAnnotation node) {
        StringLiteral value = null;

        for (MemberValuePair mvp : ((List<MemberValuePair>)node.values())) {
            switch (mvp.getName().toString()) {
                case "remap":
                    break;
                case "value":
                    if (mvp.getValue().getNodeType() != Expression.STRING_LITERAL)
                        return error(node, "Unknown " + typeName + " value type: " + mvp.getValue().toString());
                    value = (StringLiteral)mvp.getValue();
                    break;
                default:
                    return error(node, "Unknown " + typeName + " member: " + node.toString());
            }
        }

        return process(getMethod(node), value);
    }

    @Override
    public boolean process(SingleMemberAnnotation node) {
        if (node.getValue().getNodeType() != Expression.STRING_LITERAL)
            return error(node, "Unknown " + typeName + " value type: " + node.getValue().toString());

        return process(getMethod(node), (StringLiteral)node.getValue());
    }

    @Override
    public boolean process(MarkerAnnotation node) {
        return process(getMethod(node), null);
    }

    private boolean process(MethodDeclaration mtd, @Nullable StringLiteral value) {
        if (mtd == null)
            return true;
        IMethodBinding bind = mtd.resolveBinding();
        String owner = ExtractUtil.getInternalName(getFilename(), bind.getDeclaringClass(), mtd);
        MixinInfo info = getInfo(owner);
        if (info == null)
            return error(mtd, "Invalid " + typeName + " on " + bind.getName() + ExtractUtil.getDescriptor(bind) + " owner " + owner + " has no @Mixin");
        AccessorName name = AccessorName.from(mtd.getName().toString(), info.getOwner(), value == null ? null : value.getLiteralValue());
        String desc = ExtractUtil.getDescriptor(bind);

        if (info.getTarget() == null)
            return true; // Not a single target, exit off and hope for the best.

        if (name == null)
            return error(value, "Invalid " + typeName + " on " + info.getOwner() + '.' + bind.getName() + desc + " Does not match valid nameing format.");

        switch (name.getType()) {
            case GETTER:
            case SETTER: {
                String tDesc = name.getType() == AccessorType.GETTER ?
                    ExtractUtil.getInternalName(getFilename(), bind.getReturnType(), mtd) :
                    ExtractUtil.getInternalName(getFilename(), bind.getParameterTypes()[0], mtd);
                if (value != null) //Name is specified, so we have to rename the string literal, not the method name
                    getBuilder().addFieldLiteral(value.getStartPosition(), value.getLength(), value.getEscapedValue(), info.getTarget(), name.getTarget());
                else
                    getBuilder().addMixinAccessor(info.getOwner(), name.getMethod(), desc, info.getTarget(), name.getTarget(), tDesc, name.getPrefix());
                break;
            }
            case PROXY: {
                if (value != null)
                    getBuilder().addMethodLiteral(value.getStartPosition(), value.getLength(), value.getEscapedValue(), info.getTarget(), name.getTarget(), desc);
                else
                    getBuilder().addMixinAccessor(info.getOwner(), name.getMethod(), desc, info.getTarget(), name.getTarget(), desc, name.getPrefix());
                break;
            }
            case FACTORY: {
                if (value != null) {
                    if (!"<init>".equals(value.getLiteralValue()))
                        getBuilder().addClassLiteral(value.getStartPosition(), value.getLength(), value.getEscapedValue(), info.getTarget());
                } else {
                    String cDesc = desc.substring(0, desc.lastIndexOf(')') + 1) + 'V'; // All constructors have a void return
                    getBuilder().addMixinAccessor(info.getOwner(), name.getMethod(), desc, info.getTarget(), "<init>", cDesc, name.getPrefix());
                }
                break;
            }
        }
        return true;
    }
}
