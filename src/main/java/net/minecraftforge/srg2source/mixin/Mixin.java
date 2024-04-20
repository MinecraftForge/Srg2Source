/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;

import net.minecraftforge.srg2source.extract.ExtractUtil;
import net.minecraftforge.srg2source.extract.MixinProcessor;

/*
 * @Mixin has 4 known values:
 * Class<?>[] value: These are hard references to classes which will be parsed by the normal Name processor when walking children
 * int priority: Is an int, so we don't care.
 * boolean remap: A flag that determines if this mixin looks for any obf data...
 *     I am unsure of this has any effect on us, as we are just extracting the target info. The applier uses that info.
 * String[] targets: This is a string representation of target classes.
 *     This we must process and output references for. The names can be in either internal, or source-ish name, but must be fully qualified.
 *     Source-ish means that packages can use / or . but Inner classes use $.
 *     This is an array, and due to Java language specs, this can be either a single string literal, or an array wrapped in {}
 *
 * It is also the root annotation that is needed for all the others to function. And so it is the one in charge of making the 'info' object
 * that all other annotations shove things onto.
 */
public class Mixin extends AnnotationBase {
    public Mixin(MixinProcessor processor) {
        super(processor, MixinAnnotation.MIXIN);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(NormalAnnotation node) {
        ASTNode parent = node.getParent();
        if (!(parent instanceof AbstractTypeDeclaration)) // It should only be valid on these things.
            return error(node, "Found @Mixin annotation on non-type declaration: " + node.toString());
        ITypeBinding owner = ((AbstractTypeDeclaration)parent).resolveBinding();
        Set<String> targets = new HashSet<>();
        Map<String, ITypeBinding> types = new HashMap<>();

        for (MemberValuePair mvp : ((List<MemberValuePair>)node.values())) {
            switch (mvp.getName().toString()) {
                case "priority":
                case "remap":
                    break;
                case "value": // We don't print this out, but we have to resolve them to track Mixin structure.
                    targets.addAll(readClassReferences(mvp.getValue(), types));
                    break;
                case "targets":
                    if (mvp.getValue().getNodeType() == ASTNode.STRING_LITERAL) {
                        StringLiteral literal = (StringLiteral)mvp.getValue();
                        String target = literal.getLiteralValue().replace('.', '/');
                        getBuilder().addClassLiteral(literal.getStartPosition(), literal.getLength(), literal.getEscapedValue(), target);
                        targets.add(target);
                    } else if (mvp.getValue().getNodeType() == ASTNode.ARRAY_INITIALIZER) {
                        ArrayInitializer init = (ArrayInitializer)mvp.getValue();
                        for (Expression exp : ((List<Expression>)init.expressions())) {
                            if (exp.getNodeType() == ASTNode.STRING_LITERAL) {
                                StringLiteral literal = (StringLiteral)exp;
                                String target = literal.getLiteralValue().replace('.', '/');
                                getBuilder().addClassLiteral(literal.getStartPosition(), literal.getLength(), literal.getEscapedValue(), target);
                                targets.add(target);
                            } else
                                return error(node, "Unknown @Mixin member: " + node.toString());
                        }
                    } else
                        return error(node, "Unknown @Mixin member: " + node.toString());
                    break;
                default:
                    return error(node, "Unknown @Mixin member: " + node.toString());
            }
        }

        MixinInfo info  = processor.getOrCreateInfo(owner);
        types.forEach(info::addTarget);
        targets.forEach(info::addTarget);

        return true;
    }

    @Override
    public boolean process(SingleMemberAnnotation node) {
        ASTNode parent = node.getParent();
        if (!(parent instanceof AbstractTypeDeclaration)) // It should only be valid on these things.
            return error(node, "Found @Mixin annotation on non-type declaration: " + node.toString());

        ITypeBinding owner = ((AbstractTypeDeclaration)parent).resolveBinding();
        Map<String, ITypeBinding> types = new HashMap<>();
        Collection<String> targets = readClassReferences(node.getValue(), types);

        MixinInfo info  = processor.getOrCreateInfo(owner);
        types.forEach(info::addTarget);
        targets.forEach(info::addTarget);

        return true;
    }

    @SuppressWarnings("unchecked")
    private Collection<String> readClassReferences(Expression node, Map<String, ITypeBinding> types) {
        if (node.getNodeType() == ASTNode.TYPE_LITERAL) {
            TypeLiteral literal = (TypeLiteral)node;
            ITypeBinding bind = literal.getType().resolveBinding();
            String name = ExtractUtil.getInternalName(getFilename(), bind, literal.getType());
            types.put(name, bind);
            return Arrays.asList(name);
        } else if (node.getNodeType() == ASTNode.ARRAY_INITIALIZER) {
            ArrayInitializer init = (ArrayInitializer)node;
            Set<String> ret = new HashSet<>();
            for (Expression exp : ((List<Expression>)init.expressions())) {
                if (exp.getNodeType() == ASTNode.TYPE_LITERAL) {
                    TypeLiteral literal = (TypeLiteral)exp;
                    ITypeBinding bind = literal.getType().resolveBinding();
                    String name = ExtractUtil.getInternalName(getFilename(), bind, literal.getType());
                    types.put(name, bind);
                    ret.add(name);
                } else {
                    error(node, "Unknown @Mixin member: " + node.toString());
                    return Collections.emptySet();
                }
            }
            return ret;
        } else {
            error(node, "Unknown @Mixin member: " + node.toString());
            return Collections.emptySet();
        }
    }
}
