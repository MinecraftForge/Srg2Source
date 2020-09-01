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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Expression;
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
            throw new IllegalStateException("Found @Mixin annotation on non-type declaration: " + node.toString());
        String owner = ExtractUtil.getInternalName(getFilename(), ((AbstractTypeDeclaration)parent).resolveBinding(), parent);
        Set<String> targets = new HashSet<>();

        for (MemberValuePair mvp : ((List<MemberValuePair>)node.values())) {
            switch (mvp.getName().toString()) {
                case "priority":
                case "remap":
                    break;
                case "value": // We don't print this out, but we have to resolve them to track Mixin structure.
                    targets.addAll(readClassReferences(mvp.getValue()));
                    break;
                case "targets":
                    if (mvp.getValue().getNodeType() == Expression.STRING_LITERAL) {
                        StringLiteral literal = (StringLiteral)mvp.getValue();
                        String target = literal.getLiteralValue().replace('.', '/');
                        getBuilder().addClassLiteral(literal.getStartPosition(), literal.getLength(), literal.getEscapedValue(), target);
                        targets.add(target);
                    } else if (mvp.getValue().getNodeType() == Expression.ARRAY_INITIALIZER) {
                        ArrayInitializer init = (ArrayInitializer)mvp.getValue();
                        for (Expression exp : ((List<Expression>)init.expressions())) {
                            if (exp.getNodeType() == Expression.STRING_LITERAL) {
                                StringLiteral literal = (StringLiteral)exp;
                                String target = literal.getLiteralValue().replace('.', '/');
                                getBuilder().addClassLiteral(literal.getStartPosition(), literal.getLength(), literal.getEscapedValue(), target);
                                targets.add(target);
                            } else
                                throw new IllegalArgumentException("Unknown @Mixin member: " + node.toString());
                        }
                    } else
                        throw new IllegalArgumentException("Unknown @Mixin member: " + node.toString());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown @Mixin member: " + node.toString());
            }
        }

        processor.setInfo(owner, new MixinInfo(owner, targets));

        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(SingleMemberAnnotation node) {
        ASTNode parent = node.getParent();
        if (!(parent instanceof AbstractTypeDeclaration)) // It should only be valid on these things.
            throw new IllegalStateException("Found @Mixin annotation on non-type declaration: " + node.toString());
        String owner = ExtractUtil.getInternalName(getFilename(), ((AbstractTypeDeclaration)parent).resolveBinding(), parent);
        Set<String> targets = new HashSet<>(readClassReferences(node.getValue()));
        processor.setInfo(owner, new MixinInfo(owner, targets));

        return true;
    }

    @SuppressWarnings("unchecked")
    private Collection<String> readClassReferences(Expression node) {
        if (node.getNodeType() == Expression.TYPE_LITERAL) {
            TypeLiteral literal = (TypeLiteral)node;
            return Arrays.asList(ExtractUtil.getInternalName(getFilename(), literal.getType().resolveBinding(), literal.getType()));
        } else if (node.getNodeType() == Expression.ARRAY_INITIALIZER) {
            ArrayInitializer init = (ArrayInitializer)node;
            Set<String> ret = new HashSet<>();
            for (Expression exp : ((List<Expression>)init.expressions())) {
                if (exp.getNodeType() == Expression.TYPE_LITERAL) {
                    TypeLiteral literal = (TypeLiteral)exp;
                    ret.add(ExtractUtil.getInternalName(getFilename(), literal.getType().resolveBinding(), literal.getType()));
                } else
                    throw new IllegalArgumentException("Unknown @Mixin member: " + node.toString());
            }
            return ret;
        } else
            throw new IllegalArgumentException("Unknown @Mixin member: " + node.toString());

    }
}
