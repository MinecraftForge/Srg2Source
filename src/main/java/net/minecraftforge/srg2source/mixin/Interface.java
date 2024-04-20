/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.srg2source.extract.MixinProcessor;

/*
 * @Interface defines a class that the mixin'd target should implement.
 * This allows for 'soft' implementations. Using a prefix for method names which allows for implementing interfaces who
 * have method that conflict with each other, or something in the target's inheritance.
 * Honestly, I am unsure of a real world usecase for this, but it is part of the tool so lets try and support it.
 *
 * As it sits this interface is a metadata holder that gets wrapped in the @Implements interface on a Type.
 * However, if Mumfrey ever decides to he could easily make this a @Repeateable annotation and bypass the wrapper object at
 * the source level. So I'll try and support both cases.
 *
 * Required Values:
 *   Class iface: The interface to be implemented.
 *   String prefix: MUST end with a '$', This is however optional on the methods themselves.
 *       Which means that if I have IFoo { void bizz(); void buzz(); }
 *       I can implement is with:
 *       @Interface(iface = IFoo.class, prefix = "foo$")
 *       class Mixined {
 *         void bizz(){}
 *         void foo$buzz(){}
 *       }
 *       This also holds true for all super interfaces of the target interface.
 *
 * Optional Values:
 *   boolean unique: If true, treats all methods implemeting this interface as if they were annotated with @Unique, Defaults to false
 *   Remap remap: Specifies how the official Annotation Processor will try and find mappings for these methods.
 *       Currently, we do nothing with this and just try and remap everything... But if we have a usecase where this should
 *       actually be cared about.. we can change it.
 *       ALL: All methods are atempted to be remaped -- This is what we default as essentially
 *       FORCE: Javadocs are identicle to ALL?
 *       NONE: Don't remap anything
 *       ONLY_PREFIXED: Only remap prefixed methods
 */
public class Interface extends AnnotationBase {
    public Interface(MixinProcessor processor) {
        super(processor, MixinAnnotation.INTERFACE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(NormalAnnotation node) {
        ITypeBinding parent = findParent(node.getParent());
        if (parent == null)
            return error(node, "Could not resolve owner for @Interface annotation: " + node.toString());

        String prefix = null;
        Type iface = null;

        for (MemberValuePair mvp : ((List<MemberValuePair>)node.values())) {
            switch (mvp.getName().toString()) {
                case "unique":
                case "remap":
                    break;
                case "prefix":
                    if (mvp.getValue().getNodeType() == ASTNode.STRING_LITERAL)
                        prefix = ((StringLiteral)mvp.getValue()).getLiteralValue();
                    else
                        return error(node, "Could not determine prefix value for @Interface annotation: " + node.toString());
                    break;
                case "iface":
                    if (mvp.getValue().getNodeType() == ASTNode.TYPE_LITERAL)
                        iface = ((TypeLiteral)mvp.getValue()).getType();
                    else
                        return error(node, "Could not determine prefix value for @Interface annotation: " + node.toString());
                    break;
                default:
                    return error(node, "Unknown value entry in @Interface annotation: " + node.toString());
            }
        }

        if (prefix == null || iface == null)
            return error(node, "Could not determine prefix/iface for @Interface annotation: " + node.toString());

        MixinInfo info = processor.getOrCreateInfo(parent);
        info.addInterface(prefix, iface);

        return true;
    }

    /*
     * Currently this must be wrapped in a @Implements annotation, however I think it should support @Repeateable so it may not be wrapped in
     * the future. So let's try and find the actual parent.
     */
    @Nullable
    private ITypeBinding findParent(ASTNode parent) {
        int type = parent.getNodeType();
        if (type == ASTNode.SINGLE_MEMBER_ANNOTATION || type == ASTNode.NORMAL_ANNOTATION || type == ASTNode.ARRAY_INITIALIZER)
            return findParent(parent.getParent());
        if (parent instanceof AbstractTypeDeclaration)
            return ((AbstractTypeDeclaration)parent).resolveBinding();
        return null;
    }
}
