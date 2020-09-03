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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;

import net.minecraftforge.srg2source.extract.ExtractUtil;
import net.minecraftforge.srg2source.extract.MixinProcessor;

/*
 * @Overwrite defines a method that must replace an existing method inside the targeted class.
 * The method name, and signature must match the targeted method.
 * There are three known values for this, all optional.
 * String constraints: When this should apply, we don't care we're just renaming.
 * boolean remap: Same as all other remap values, we might care, but not right now
 * String[] aliases: These are potential string literals holding the method name of the target method.
 *   This we could care about, however at the current point in time we don't.
 *   I think this is mainly for multi-target mixins, and each entry could be the name in one specific target.
 *   Perhaps support it? If we can find a proper test case.
 */
public class Overwrite extends AnnotationBase {
    public Overwrite(MixinProcessor processor) {
        super(processor, MixinAnnotation.OVERWRITE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(NormalAnnotation node) {
        if (node.getParent().getNodeType() != ASTNode.METHOD_DECLARATION)
            return error(node, "Invalid @Overwrite, Must be attached to a method declaration");
        IMethodBinding method = ((MethodDeclaration)node.getParent()).resolveBinding();

        /*
        Set<StringLiteral> aliases = new HashSet<>();
        for (MemberValuePair mvp : ((List<MemberValuePair>)node.values())) {
            switch (mvp.getName().toString()) {
                case "constraints":
                case "remap":
                    break;
                case "aliases":
                    if (mvp.getValue().getNodeType() == ASTNode.STRING_LITERAL)
                        aliases.add((StringLiteral)mvp.getValue());
                    else if (mvp.getValue().getNodeType() == ASTNode.ARRAY_INITIALIZER) {
                        ArrayInitializer init = (ArrayInitializer)mvp.getValue();
                        for (Expression exp : ((List<Expression>)init.expressions())) {
                            if (exp.getNodeType() == ASTNode.STRING_LITERAL)
                                aliases.add((StringLiteral)exp);
                            else
                                return error(node, "Could not determine aliases value for @Overwrite annotation: " + node.toString());
                        }
                    } else
                        return error(node, "Could not determine aliases value for @Overwrite annotation: " + node.toString());
                    break;
                default:
                    return error(node, "Unknown value entry in @Overwrite annotation: " + node.toString());
            }
        }
        */

        MixinInfo info = getInfo(ExtractUtil.getInternalName(getBuilder().getFilename(), method.getDeclaringClass(), node));
        if (info == null)
            return error(node, "Could not determine @Mixin owner for @Overwrite method: " + node.toString());
        info.addOverwrite(method.getName(), ExtractUtil.getDescriptor(method));

        return true;
    }

    @Override
    public boolean process(MarkerAnnotation node) {
        if (node.getParent().getNodeType() != ASTNode.METHOD_DECLARATION)
            return error(node, "Invalid @Overwrite, Must be attached to a method declaration");
        IMethodBinding method = ((MethodDeclaration)node.getParent()).resolveBinding();
        MixinInfo info = getInfo(ExtractUtil.getInternalName(getBuilder().getFilename(), method.getDeclaringClass(), node));
        if (info == null)
            return error(node, "Could not determine @Mixin owner for @Overwrite method: " + node.toString());
        info.addOverwrite(method.getName(), ExtractUtil.getDescriptor(method));
        return true;
    }
}
