/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import net.minecraftforge.srg2source.extract.ExtractUtil;
import net.minecraftforge.srg2source.extract.MixinProcessor;

/*
 * @Shadow has 3 known values:
 * boolean remap: Same as @Mixin, I don't think we should care.
 * String[] aliases: Used when targeting a method or field that is known to have multiple names at dev time.
 *     Is there a real world case for this? Only thing I can think of is if this is a multi-target mixin,
 *     where the targets have different names... I don't know how we'd support this.
 * String prefix: Specifies a prefix that is used in the mixin's code when referencing a shadowed method.
 *     According to the javadocs, this is solely intended to be used on methods that are attempting to add
 *     a method override, that returns a different type. As this is a invalid source level design, but is
 *     valid in bytecode.
 *
 *     It is also explicitly stated that this being set on a field is a error condition.
 *
 *     Default: "shadow$"
 */
public class Shadow extends AnnotationBase {
    public Shadow(MixinProcessor processor) {
        super(processor, MixinAnnotation.SHADOW);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(MarkerAnnotation node) {
        ASTNode parent = node.getParent();
        if (parent.getNodeType() == ASTNode.FIELD_DECLARATION) {
            FieldDeclaration fld = (FieldDeclaration)parent;
            for (VariableDeclarationFragment vdf : ((List<VariableDeclarationFragment>)fld.fragments())) {
                IVariableBinding bind = vdf.resolveBinding();
                String owner = ExtractUtil.getInternalName(getFilename(), bind.getDeclaringClass(), parent);
                String name = bind.getName().toString();
                String desc = ExtractUtil.getTypeSignature(bind.getType());
                MixinInfo info = getInfo(owner);
                if (info == null)
                    return error(node, "Invalid @Sadow on " + name + " owner " + owner + " has no @Mixin");
                info.addShadow(name, desc, "shadow$");
            }
        } else if (parent.getNodeType() == ASTNode.METHOD_DECLARATION) {
            MethodDeclaration mtd = (MethodDeclaration)parent;
            IMethodBinding bind = mtd.resolveBinding();
            String owner = ExtractUtil.getInternalName(getFilename(), bind.getDeclaringClass(), parent);
            String name = bind.getName().toString();
            String desc = ExtractUtil.getDescriptor(bind);
            MixinInfo info = getInfo(owner);
            if (info == null)
                return error(node, "Invalid @Sadow on " + name + desc + " owner " + owner + " has no @Mixin");
            info.addShadow(name, desc, "shadow$");
        } else
            return error(node, "Invalid @Shadow target: " + parent.getClass().getName());

        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(NormalAnnotation node) {
        String prefix = "shadow$";

        for (MemberValuePair mvp : ((List<MemberValuePair>)node.values())) {
            switch (mvp.getName().toString()) {
                case "remap": // We don't care
                case "aliases":
                    break;
                case "prefix":
                    if (mvp.getValue().getNodeType() == ASTNode.STRING_LITERAL)
                        prefix = ((StringLiteral)mvp.getValue()).getLiteralValue();
                    else
                        return error(node, "Unknown @Shadow member: " + node.toString());
                    break;
                default:
                    return error(node, "Unknown @Shadow member: " + node.toString());
            }
        }

        ASTNode parent = node.getParent();
        if (parent.getNodeType() == ASTNode.FIELD_DECLARATION) {
            FieldDeclaration fld = (FieldDeclaration)parent;
            for (VariableDeclarationFragment vdf : ((List<VariableDeclarationFragment>)fld.fragments())) {
                IVariableBinding bind = vdf.resolveBinding();
                String owner = ExtractUtil.getInternalName(getFilename(), bind.getDeclaringClass(), parent);
                String type = ExtractUtil.getTypeSignature(bind.getType());
                String name = bind.getName().toString();
                MixinInfo info = getInfo(owner);
                if (info == null)
                    return error(node, "Invalid @Sadow on " + name + " owner " + owner + " has no @Mixin");
                info.addShadow(name, type, prefix);
            }
        } else if (parent.getNodeType() == ASTNode.METHOD_DECLARATION) {
            MethodDeclaration mtd = (MethodDeclaration)parent;
            IMethodBinding bind = mtd.resolveBinding();
            String owner = ExtractUtil.getInternalName(getFilename(), bind.getDeclaringClass(), parent);
            String name = bind.getName().toString();
            String desc = ExtractUtil.getDescriptor(bind);
            MixinInfo info = getInfo(owner);
            if (info == null)
                return error(node, "Invalid @Sadow on " + name + desc + " owner " + owner + " has no @Mixin");
            info.addShadow(name, desc, prefix);
        } else
            return error(node, "Invalid @Shadow target: " + parent.getClass().getName());
        return true;
    }
}
