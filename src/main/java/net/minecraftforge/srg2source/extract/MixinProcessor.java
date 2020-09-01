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

package net.minecraftforge.srg2source.extract;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/*
 * This is an attempt to add support for remapping projects that take advantage of the https://github.com/SpongePowered/Mixin project.
 * However, as there are many ways that code can be written for this and still be valid to the compiler. This does a best effort.
 *
 * Notably, when the annotations require string parameters. We support simple string literals, doing things like "java.lang." + "String" is not supported and will error.
 */

class MixinProcessor {
    private static final String MIXIN  = "org/spongepowered/asm/mixin/Mixin";
    private static final String SHADOW = "org/spongepowered/asm/mixin/Shadow";

    private final SymbolReferenceWalker walker;
    private final Map<String, MixinInfo> mixins = new HashMap<>();

    MixinProcessor(SymbolReferenceWalker walker) {
        this.walker = walker;
    }

    boolean process(NormalAnnotation node, String name) {
        switch (name) {
            case MIXIN:  return processMixin(node);
            case SHADOW: return processShadow(node);
        }
        return true;
    }

    boolean process(SingleMemberAnnotation node, String name) {
        switch (name) {
            case MIXIN:  return processMixin(node);
            case SHADOW: throw new IllegalArgumentException("@Shadow can not be a single member annotation, it has no known \"value\" method");
        }
        return true;
    }

    boolean process(MarkerAnnotation node, String name) {
        switch (name) {
            case MIXIN:  throw new IllegalArgumentException("@Mixin can not be a marker annotation, it must have a value of some kind");
            case SHADOW: return processShadow(node);
        }
        return true;
    }

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
     */
    @SuppressWarnings("unchecked")
    private boolean processMixin(NormalAnnotation node) {
        ASTNode parent = node.getParent();
        if (!(parent instanceof AbstractTypeDeclaration)) // It should only be valid on these things.
            throw new IllegalStateException("Found @Mixin annotation on non-type declaration: " + node.toString());
        String owner = walker.getInternalName(((AbstractTypeDeclaration)parent).resolveBinding(), parent);
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
                        walker.getBuilder().addClassLiteral(literal.getStartPosition(), literal.getLength(), literal.getEscapedValue(), target);
                        targets.add(target);
                    } else if (mvp.getValue().getNodeType() == Expression.ARRAY_INITIALIZER) {
                        ArrayInitializer init = (ArrayInitializer)mvp.getValue();
                        for (Expression exp : ((List<Expression>)init.expressions())) {
                            if (exp.getNodeType() == Expression.STRING_LITERAL) {
                                StringLiteral literal = (StringLiteral)exp;
                                String target = literal.getLiteralValue().replace('.', '/');
                                walker.getBuilder().addClassLiteral(literal.getStartPosition(), literal.getLength(), literal.getEscapedValue(), target);
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

        this.mixins.put(owner, new MixinInfo(owner, targets));

        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean processMixin(SingleMemberAnnotation node) {
        ASTNode parent = node.getParent();
        if (!(parent instanceof AbstractTypeDeclaration)) // It should only be valid on these things.
            throw new IllegalStateException("Found @Mixin annotation on non-type declaration: " + node.toString());
        String owner = walker.getInternalName(((AbstractTypeDeclaration)parent).resolveBinding(), parent);
        Set<String> targets = new HashSet<>(readClassReferences(node.getValue()));
        this.mixins.put(owner, new MixinInfo(owner, targets));

        return true;
    }

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
    @SuppressWarnings("unchecked")
    private boolean processShadow(MarkerAnnotation node) {
        ASTNode parent = node.getParent();
        if (parent.getNodeType() == ASTNode.FIELD_DECLARATION) {
            FieldDeclaration fld = (FieldDeclaration)parent;
            for (VariableDeclarationFragment vdf : ((List<VariableDeclarationFragment>)fld.fragments())) {
                IVariableBinding bind = vdf.resolveBinding();
                String owner = walker.getInternalName(bind.getDeclaringClass(), parent);
                String name = bind.getName().toString();
                String desc = walker.getTypeSignature(bind.getType());
                MixinInfo info = this.mixins.get(owner);
                if (info == null)
                    throw new IllegalStateException("Invalid @Sadow on " + name + " owner " + owner + " has no @Mixin");
                info.addField(name, desc, "shadow$");
            }
        } else if (parent.getNodeType() == ASTNode.METHOD_DECLARATION) {
            MethodDeclaration mtd = (MethodDeclaration)parent;
            IMethodBinding bind = mtd.resolveBinding();
            String owner = walker.getInternalName(bind.getDeclaringClass(), parent);
            String name = bind.getName().toString();
            String desc = walker.getDescriptor(bind);
            MixinInfo info = this.mixins.get(owner);
            if (info == null)
                throw new IllegalStateException("Invalid @Sadow on " + name + desc + " owner " + owner + " has no @Mixin");
            info.addMethod(name, desc, "shadow$");
        } else {
            throw new IllegalArgumentException("Invalid @Shadow target: " + parent.getClass().getName());
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean processShadow(NormalAnnotation node) {
        String prefix = "shadow$";

        for (MemberValuePair mvp : ((List<MemberValuePair>)node.values())) {
            switch (mvp.getName().toString()) {
                case "remap": // We don't care
                case "aliases":
                    break;
                case "prefix":
                    if (mvp.getValue().getNodeType() == Expression.STRING_LITERAL)
                        prefix = ((StringLiteral)mvp.getValue()).getLiteralValue();
                    else
                        throw new IllegalArgumentException("Unknown @Shadow member: " + node.toString());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown @Shadow member: " + node.toString());
            }
        }

        ASTNode parent = node.getParent();
        if (parent.getNodeType() == ASTNode.FIELD_DECLARATION) {
            FieldDeclaration fld = (FieldDeclaration)parent;
            for (VariableDeclarationFragment vdf : ((List<VariableDeclarationFragment>)fld.fragments())) {
                IVariableBinding bind = vdf.resolveBinding();
                String type = walker.getTypeSignature(bind.getType());
                String owner = walker.getInternalName(bind.getDeclaringClass(), parent);
                String name = bind.getName().toString();
                MixinInfo info = this.mixins.get(owner);
                if (info == null)
                    throw new IllegalStateException("Invalid @Sadow on " + name + " owner " + owner + " has no @Mixin");
                info.addField(name, type, prefix);
            }
        } else if (parent.getNodeType() == ASTNode.METHOD_DECLARATION) {
            MethodDeclaration mtd = (MethodDeclaration)parent;
            IMethodBinding bind = mtd.resolveBinding();
            String owner = walker.getInternalName(bind.getDeclaringClass(), parent);
            String name = bind.getName().toString();
            String desc = walker.getDescriptor(bind);
            MixinInfo info = this.mixins.get(owner);
            if (info == null)
                throw new IllegalStateException("Invalid @Sadow on " + name + desc + " owner " + owner + " has no @Mixin");
            info.addMethod(name, desc, prefix);
        } else {
            throw new IllegalArgumentException("Invalid @Shadow target: " + parent.getClass().getName());
        }
        return true;
    }


    @SuppressWarnings("unchecked")
    private Collection<String> readClassReferences(Expression node) {
        if (node.getNodeType() == Expression.TYPE_LITERAL) {
            TypeLiteral literal = (TypeLiteral)node;
            return Arrays.asList(walker.getInternalName(literal.getType().resolveBinding(), literal.getType()));
        } else if (node.getNodeType() == Expression.ARRAY_INITIALIZER) {
            ArrayInitializer init = (ArrayInitializer)node;
            Set<String> ret = new HashSet<>();
            for (Expression exp : ((List<Expression>)init.expressions())) {
                if (exp.getNodeType() == Expression.TYPE_LITERAL) {
                    TypeLiteral literal = (TypeLiteral)exp;
                    ret.add(walker.getInternalName(literal.getType().resolveBinding(), literal.getType()));
                } else
                    throw new IllegalArgumentException("Unknown @Mixin member: " + node.toString());
            }
            return ret;
        } else
            throw new IllegalArgumentException("Unknown @Mixin member: " + node.toString());

    }

    String getFieldOwner(String owner, String name, String type) {
        MixinInfo info = this.mixins.get(owner);
        if (info == null)
            return owner;
        String ret = info.getFieldOwner(name, type);
        return ret == null ? owner : ret;
    }

   boolean processMethodReference(SimpleName node, IMethodBinding root, String owner, String name, String desc) {
       MixinInfo info = this.mixins.get(owner);
       if (info == null || info.target == null)
           return false;
       ShadowInfo shadow = info.methods.get(name + ' ' + desc);
       if (shadow == null)
           return false;

       int offset = 0;
       if (shadow.prefix != null && name.startsWith(shadow.prefix))
           offset = shadow.prefix.length();

       walker.getBuilder().addMethodReference(node.getStartPosition() + offset, node.getLength() - offset, node.toString().substring(offset), info.target, name.substring(offset), desc);

       return true;
   }

    private static class MixinInfo {
        private final String owner;
        private final String target;
        private final Set<String> targets;
        private final Map<String, ShadowInfo> fields = new HashMap<>();
        private final Map<String, ShadowInfo> methods = new HashMap<>();

        MixinInfo(String owner, Set<String> targets) {
            this.owner = owner;
            this.targets = targets;
            this.target = targets.size() == 1 ? targets.iterator().next() : null;
        }

        private void addField(String name, String desc, String prefix) {
            this.fields.put(name + ' ' + desc, new ShadowInfo(name, desc, prefix));
        }

        private void addMethod(String name, String desc, String prefix) {
            this.methods.put(name + ' ' + desc, new ShadowInfo(name, desc, prefix));
        }

        public String getFieldOwner(String name, String desc) {
            return this.target != null && this.fields.containsKey(name + ' ' + desc) ? this.target : null;
        }
    }

    private static class ShadowInfo {
        private final String name;
        private final String prefix;
        private final String desc;

        private ShadowInfo(String name, String desc, String prefix) {
            this.name = name;
            this.desc = desc;
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return name + ' ' + desc + ' ' + prefix;
        }
    }
}
