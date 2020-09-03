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

import java.util.HashMap;
import java.util.List;

import org.eclipse.jdt.core.dom.*;
import org.objectweb.asm.Opcodes;

import net.minecraftforge.srg2source.range.RangeMapBuilder;

/**
 * Recursively descends and processes symbol references
 */
public class SymbolReferenceWalker {
    //Copies of the JSL constants, to quiet deprecation warnings
    @SuppressWarnings("deprecation") private static final int JLS2 = AST.JLS2;
    @SuppressWarnings("deprecation") private static final int JLS3 = AST.JLS3;

    private final RangeMapBuilder builder;
    private final String className;
    private final RangeExtractor extractor;
    private final SymbolReferenceWalker parent;
    private final MixinProcessor mixins;
    private HashMap<String, ParamInfo> parameterInfo = new HashMap<>();
    private int anonCount = 0; // Number off encountered anonymous classes

    public SymbolReferenceWalker(RangeExtractor extractor, RangeMapBuilder builder, boolean enableMixins) {
        this.extractor = extractor;
        this.builder = builder;
        this.className = null;
        this.parent = null;
        this.mixins = enableMixins ? new MixinProcessor(this) : null;
    }

    private SymbolReferenceWalker(SymbolReferenceWalker parent, String className) {
        this.extractor = parent.extractor;
        this.builder = parent.builder;
        this.className = className;
        this.parent = parent;
        this.mixins = parent.mixins;
    }

    /**
     * Recursively walk starting from given element
     * Captures and prints exception.
     *
     * @return Returns an exception if one accrued, null if successful.
     */
    public Exception safeWalk(ASTNode node) {
        if (node == null)
            return null;

        try {
            node.accept(this.getVisitor());
            return null;
        } catch (Exception e) {
            e.printStackTrace(extractor.getErrorLogger());
            return e;
        }
    }

    /* =================================== Helper Methods ================================================== */
    private void acceptChild(ASTNode child) {
        if (child == null)
            return;
        child.accept(this.getVisitor());
    }

    @SuppressWarnings("unchecked")
    private void acceptChildren(@SuppressWarnings("rawtypes") List children) {
        if (children == null)
            return;
        for (ASTNode child : ((List<ASTNode>)children))
            acceptChild(child);
    }

    private String getInternalName(ITypeBinding binding, ASTNode node) {
        return ExtractUtil.getInternalName(builder.getFilename(), binding, node);
    }

    private void trackParameters(String name, String desc, IMethodBinding mtd, List<VariableDeclaration> params, int synthetics) {
        //ITypeBinding[] args = mtd.getParameterTypes();
        int index = synthetics; //Modifier.isStatic(mtd.getModifiers()) ? 0 : 1;
        for (int x = 0; x < params.size(); x++) {
            String key = params.get(x).getName().resolveBinding().getKey();
            parameterInfo.put(key, new ParamInfo(className, name, desc, index));

            index++; // We output the arguments in source indexs. Not Bytecode indexs. Because the applier has no idea if a method is static/synthetic/whatever.
            /*
            if (args[x].isPrimitive()) {
                char chr = args[x].getBinaryName().charAt(0); //There has got to be a better way...
                if (chr == 'D' || chr == 'L')
                    index++;
            }
            */
        }
    }

    private ParamInfo findParameter(String key) {
        ParamInfo ret = parameterInfo.get(key);
        if (ret != null)
            return ret;
        return parent == null ? null : parent.findParameter(key);
    }

    @SuppressWarnings("unused")
    public void log(String message) {
        this.extractor.log("# " + message);
    }

    public void error(String message) {
        this.extractor.error("# " + message);
    }

    public void error(ASTNode node, String message) {
        String error = "ERROR: " + builder.getFilename() + " @ " + node.getStartPosition() + ": " + message;
        error(error);
        throw new IllegalStateException(error); //TODO: Non-Fatal
    }

    public RangeMapBuilder getBuilder() {
        return this.builder;
    }

    public RangeExtractor getExtractor() {
        return this.extractor;
    }
    /* ===================================================================================================== */

    private boolean process(AnnotationTypeDeclaration node) {
        builder.addAnnotationDeclaration(node.getStartPosition(), node.getLength(), node.getName().getIdentifier());
        return true;
    }

    /*
     * We have to guess the anon classes ID, which typically is just incremental based on when they are encountered in
     * the source. We also use a child walker so that it has context of what class it is in.
     * TODO: Rewrite to use a stack system for the structure we are in instead of child walkers?
     */
    private boolean process(AnonymousClassDeclaration node) {
        String name = this.className + "$" + ++anonCount;
        SymbolReferenceWalker walker = new SymbolReferenceWalker(this, name);
        builder.addClassDeclaration(node.getStartPosition(), node.getLength(), name);
        walker.acceptChildren(node.bodyDeclarations());
        return false; //We manually walk so don't do so on this visitor
    }

    /*
     * Resolving the label name results in a null binding, unsure if there is a way to properly do so.
     * However we don't really care, so we can skip the name child of this label.
     */
    private boolean process(BreakStatement node) {
        //acceptChild(node.getLabel());
        return false;
    }

    /*
     * Resolving the label name results in a null binding, unsure if there is a way to properly do so.
     * However we don't really care, so we can skip the name child of this label.
     */
    private boolean process(ContinueStatement node) {
        //acceptChild(node.getLabel());
        return false;
    }

    /**
     * We use a child walker to process all class like objects, so that the walker has context of what class it is in.
     * This means we need to cancel processing the children and walk them ourselves in the child walker.
     * TODO: Add a 'ignore' to child walkers and let it walk itself?
     */
    private boolean process(EnumDeclaration node) {
        String name = getInternalName(node.resolveBinding(), node);
        builder.addEnumDeclaration(node.getStartPosition(), node.getLength(), name);

        SymbolReferenceWalker walker = new SymbolReferenceWalker(this, name);
        walker.acceptChild(node.getJavadoc());
        walker.acceptChildren(node.modifiers());
        walker.acceptChild(node.getName());
        walker.acceptChildren(node.superInterfaceTypes());
        walker.acceptChildren(node.enumConstants());
        walker.acceptChildren(node.bodyDeclarations());
        return false;
    }

    /*
     * Resolving the label name results in a null binding, unsure if there is a way to properly do so.
     * However we don't really care, so we can skip the name child of this label.
     */
    private boolean process(LabeledStatement node) {
        //acceptChild(node.getLabel());
        acceptChild(node.getBody());
        return false;
    }

    /*
     * We need to gather all parameters so we can try and provide a proper bytecode index for identification.
     */
    private boolean process(LambdaExpression node) {
        //This isn't the real signature, it doesn't include the prefixed synthetic arguments... TODO: Figure out somethinf for this.
        IMethodBinding mtd = node.resolveMethodBinding();
        String name = mtd.isConstructor() ? "<init>" : mtd.getName();
        String desc = ExtractUtil.getDescriptor(mtd);
        builder.addMethodDeclaration(node.getStartPosition(), node.getLength(), "lambda[" + name + ']', desc);

        @SuppressWarnings("unchecked")
        List<VariableDeclaration> params = (List<VariableDeclaration>)node.parameters();
        builder.addMethodDeclaration(node.getStartPosition(), node.getLength(), name, desc);

        if (!params.isEmpty()) {
            /*
             * This is stupid dirty hack to pull out the lambda id and descriptor
             * Example: Lcom/mojang/blaze3d/matrix/com\mojang\blaze3d\matrix\MatrixStack~MatrixStack;.lambda$0(Ljava/util/ArrayDeque;)V
             */
            String mtdKey = mtd.getKey();
            int idx = mtdKey.indexOf(";.");
            if (idx == -1)
                throw new IllegalArgumentException("Unknown Lambda key format: " + builder.getFilename() + " @ " + node.getStartPosition() + " key " + mtdKey);

            mtdKey = mtdKey.substring(idx + 2); //Strip ugly prefix: lambda$0(Ljava/util/ArrayDeque;)V
            idx = mtdKey.indexOf('(');
            name = mtdKey.substring(0, idx); //Name: lambda$0
            mtdKey = mtdKey.substring(idx); // Strip name: (Ljava/util/ArrayDeque;)V
            idx = mtdKey.indexOf(')');
            // If return type is Object, we need to find the end of it, if it's primitive just add one
            desc = mtdKey.substring(0, (mtdKey.charAt(idx + 1) == 'L' ? mtdKey.indexOf(';', idx) : idx) + 1);


            ITypeBinding[] args = mtd.getParameterTypes();
            trackParameters(name, desc, mtd, params, args.length - params.size());
        }

        return true;
    }

    /*
     * We need to gather all parameters so we can try and provide a proper bytecode index for identification.
     */
    private boolean process(MethodDeclaration node) {
        IMethodBinding mtd = node.resolveBinding();
        String name = mtd.isConstructor() ? "<init>" : mtd.getName();
        String desc = ExtractUtil.getDescriptor(mtd);
        builder.addMethodDeclaration(node.getStartPosition(), node.getLength(), name, desc);

        @SuppressWarnings("unchecked")
        List<VariableDeclaration> params = (List<VariableDeclaration>)node.parameters();
        if (!params.isEmpty()) {
            int synthetic = 0;
            if (mtd.isConstructor()) {
                ITypeBinding type = mtd.getDeclaringClass();
                if (type.isEnum())
                    synthetic = 2; //String, int
                else if (type.isNested() && type.isClass() && ((type.getModifiers() & Opcodes.ACC_STATIC) == 0))
                    synthetic = 1; //Outer class
            }
            trackParameters(name, desc, mtd, params, synthetic);
        }
        return true;
    }

    private boolean process(SimpleName node) {
        IBinding bind = node.resolveBinding();

        if (bind == null) {
            /*if (node.getParent() instanceof MethodInvocation)
                error("Could not resolve method binding: " + builder.getFilename() + " @ " + node.getStartPosition() + " Text: " + node.toString());
            else */
                error(node, "Null IBinding: " + node.toString());
            return false;
        }

        switch (bind.getKind()) {
            case IBinding.TYPE:
                ITypeBinding type = (ITypeBinding)bind;
                if (type.isTypeVariable()) //Generic type names can't be remapped at this time.. should we allow it?
                    return false;

                if (className != null && className.endsWith("ViewFrustum") && node.toString().equals("ChunkRender"))
                    System.currentTimeMillis();
                String clsName = getInternalName(type, node);
                builder.addClassReference(node.getStartPosition(), node.getLength(), node.toString(), clsName, false);
                return true; //There currently arn't any children for SimpleNames, so this return does nothing.

            case IBinding.VARIABLE:
                IVariableBinding var = (IVariableBinding)bind;
                if (var.isField()) { //Fields and Enum Constants
                    if (var.getDeclaringClass() != null) { // Things like array.lenth is a Field reference, but has no declaring class.
                        String owner = getInternalName(var.getDeclaringClass(), node);
                        if (this.mixins != null)
                            owner = this.mixins.getFieldOwner(owner, node.toString(), ExtractUtil.getTypeSignature(var.getType()));
                        builder.addFieldReference(node.getStartPosition(), node.getLength(), node.toString(), owner);
                    }
                } else if (var.isParameter()) {
                    ParamInfo info = findParameter(var.getKey());
                    if (info == null)
                        error(node, "Illegal Argument: " + var.getKey());
                    else
                        builder.addParameterReference(node.getStartPosition(), node.getLength(), node.toString(), info.owner, info.name, info.desc, info.index);
                } else {
                    /*
                     *  These are local variables, the compiler gives them unique IDs in ascending order in var.getVariableId,
                     *  but I am unsure how we could manage renaming these, so we'll skip for now. We'd have to manage a know shifted id index.
                     *  And find some pragmatic way of defining shifted variables...
                     *  As well as defining removal of local variables...
                     *  Honestly this would be cool, but it's out of scope of Minecraft and a lot of work so I'm putting it off.
                     */
                }
                return true;
            case IBinding.METHOD:
                IMethodBinding mtd = ExtractUtil.findRoot((IMethodBinding)bind);
                String owner = getInternalName(mtd.getDeclaringClass(), node);
                String name = mtd.isConstructor() ? "<init>" : mtd.getName();
                String desc = ExtractUtil.getDescriptor(mtd.getMethodDeclaration());
                if (this.mixins == null || !this.mixins.processMethodReference(node, mtd, owner, name, desc))
                    builder.addMethodReference(node.getStartPosition(), node.getLength(), node.toString(), owner, name, desc);
                return true;
            // These I have not tested, so assume it will walk correctly.
            //case IBinding.PACKAGE:
            //case IBinding.ANNOTATION:
            //case IBinding.MEMBER_VALUE_PAIR:
            //case IBinding.MODULE:
            //    System.out.println("Untested Simple " + bind.getClass().getSimpleName() + ": " + node.toString());
            //    return true;
            default:
                error("Unknown IBinding Kind: " + bind.getKind() + " Text: " + node.toString());
                return true;
        }
    }

    private boolean process(QualifiedName node) {
        IBinding bind = node.resolveBinding();

        if (bind == null) {
            //if (node.getParent() instanceof MethodInvocation)
            //    error("Could not resolve method binding: " + builder.getFilename() + " @ " + node.getStartPosition() + " Text: " + node.toString());
            //else
                error(node, "Null IBinding: " + node.toString());
            return false;
        }

        switch (bind.getKind()) {
            case IBinding.TYPE:
                ITypeBinding type = (ITypeBinding)bind;
                if (type.isTypeVariable()) {
                    error(node, "Qualified generic type variable?: " + node.toString());
                } else {
                    // Qualified type names are made up of two parts, the qualifier, and the name.
                    // The qualifier can itself be a QualifiedName, so we walk that and handle it normally.
                    // This essentially allows for unlimited qualified steps.
                    IBinding qualifier = node.getQualifier().resolveBinding();
                    Name name = node.getName();

                    // If the qualifier is a package, we bundle it as a class reference
                    if (qualifier.getKind() == IBinding.PACKAGE)
                        name = node;
                    else // If it's anything else it should recurse properly.
                        acceptChild(node.getQualifier());

                    String clsName = getInternalName(type, node);
                    builder.addClassReference(name.getStartPosition(), name.getLength(), name.toString(), clsName, true);
                }
                return false;
            case IBinding.VARIABLE: // Variables are things like Field references, Dist.CLEINT. We want the parts separately.
                return true;
            case IBinding.PACKAGE: // This should be addressed in the above TYPE case. So we should never get here.
                error(node, "Unknown IBinding PACKAGE case: " + node.toString());
                return false;
            // These I have not tested, so assume it will walk correctly.
            //case IBinding.METHOD:
            //case IBinding.ANNOTATION:
            //case IBinding.MEMBER_VALUE_PAIR:
            //case IBinding.MODULE:
            //    System.out.println("Untested Qualified " + bind.getClass().getSimpleName() + ": " + node.toString());
            //    return true;
            default:
                error(node, "Unknown IBinding Kind: " + bind.getKind() + " Text: " + node.toString());
                return false;
        }
    }

    /**
     * We use a child walker to process all class like objects, so that the walker has context of what class it is in.
     * This means we need to cancel processing the children and walk them ourselves in the child walker.
     * TODO: Add a 'ignore' to child walkers and let it walk itself?
     */
    @SuppressWarnings("deprecation") // getSuperclass is deprecated, but we mimic it exactly
    private boolean process(TypeDeclaration node) {
        String name = getInternalName((ITypeBinding)node.getName().resolveBinding(), node.getName());

        if (node.isInterface())
            builder.addInterfaceDeclaration(node.getStartPosition(), node.getLength(), name);
        else
            builder.addClassDeclaration(node.getStartPosition(), node.getLength(), name);

        SymbolReferenceWalker walker = new SymbolReferenceWalker(this, name);

        if (node.getAST().apiLevel() == JLS2) {
            //walker.acceptChild(node.getJavadoc()); - Don't care
            walker.acceptChild(node.getName());
            walker.acceptChild(node.getSuperclass());
            walker.acceptChildren(node.superInterfaceTypes());
            walker.acceptChildren(node.bodyDeclarations());
        }

        if (node.getAST().apiLevel() >= JLS3) {
            //walker.acceptChild(node.getJavadoc()); - Don't care
            walker.acceptChildren(node.modifiers());
            walker.acceptChild(node.getName());
            walker.acceptChildren(node.typeParameters());
            walker.acceptChild(node.getSuperclassType());
            walker.acceptChildren(node.superInterfaceTypes());
            walker.acceptChildren(node.bodyDeclarations());
        }

        return false;
    }

    /**
     * Right now we do not dump import range information.
     * The Applier does very rudimentary import management itself.
     * We may expose this information in the future, if there becomes a need for it.
     */
    private boolean process(ImportDeclaration node) {
        return false; //Do not walk children, ignore it all
    }

    /**
     * We output the package declaration, explicitly because we want to know that it
     * is a package declaration and not a generic simple name.
     * We need to walk the children because there may be annotations which need to be extracted.
     */
    private boolean process(PackageDeclaration node) {
        if (node.getAST().apiLevel() >= JLS3) {
            //acceptChild(node.getJavadoc()); - Don't care
            acceptChildren(node.annotations());
        }
        //acceptChild(node.getName());
        Name name = node.getName();
        builder.addPackageReference(name.getStartPosition(), name.getLength(), name.getFullyQualifiedName());
        return false;
    }

    /*
     * Sometimes inner classes can be initialized indirectly with references to the outer class.
     * In these cases, the import for the inner class is not needed, because it is qualified by the outer instance.
     * However, the name itself is not a qualified name, so our normal name printing does not know it doesn't need a import.
     *
     * This is what causes ViewFrustum to add the extra ChunkRenderDispatcher.ChunkRender import:
     * `renderChunkFactory.new ChunkRender();`
     * Problem is.... we have no idea how to get the subtype's root name object... Will need to look into this more
     */
    private boolean process(ClassInstanceCreation node) {
        return true;
        /*
        if (node.getExpression() == null)
            return true;

        if (node.toString().contains("ChunkRender()"))
            System.currentTimeMillis();
        if (node.getAST().apiLevel() == JLS2) {
            acceptChild(node.getName());
        } else if (node.getAST().apiLevel() >= JLS3) {
            acceptChildren(node.typeArguments());
            acceptChild(node.getType();
        }
        acceptChildren(node.arguments());
        acceptChild(node.getAnonymousClassDeclaration());
        return false;
        */
    }

    /*
     * There are a few known annotations in the Minecraft community that reference source bindings.
     * Currently we only support a subset of the Mixin (https://github.com/SpongePowered/Mixin) library.
     * TODO: Move this to a generic annotation processor system?
     */
    private boolean process(NormalAnnotation node) {
        if (mixins == null)
            return true;
        String name = getInternalName((ITypeBinding)node.getTypeName().resolveBinding(), node.getTypeName());
        return this.mixins.process(node, name);
    }

    private boolean process(SingleMemberAnnotation node) {
        if (mixins == null)
            return true;
        String name = getInternalName((ITypeBinding)node.getTypeName().resolveBinding(), node.getTypeName());
        return this.mixins.process(node, name);
    }

    private boolean process(MarkerAnnotation node) {
        if (mixins == null)
            return true;
        String name = getInternalName((ITypeBinding)node.getTypeName().resolveBinding(), node.getTypeName());
        return this.mixins.process(node, name);
    }

    private static class ParamInfo {
        private final String owner;
        private final String name;
        private final String desc;
        private final int index;

        private ParamInfo(String owner, String name, String desc, int index) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.index = index;
        }
    }

    public ASTVisitor getVisitor() {
        return visitor;
    }

    private ASTVisitor visitor = new ASTVisitor() {
        @Override public boolean visit(AnnotationTypeDeclaration       node) { return process(node); }
        @Override public boolean visit(AnnotationTypeMemberDeclaration node) { return true; }
        @Override public boolean visit(AnonymousClassDeclaration       node) { return process(node); }
        @Override public boolean visit(ArrayAccess                     node) { return true; }
        @Override public boolean visit(ArrayCreation                   node) { return true; }
        @Override public boolean visit(ArrayInitializer                node) { return true; }
        @Override public boolean visit(ArrayType                       node) { return true; }
        @Override public boolean visit(AssertStatement                 node) { return true; }
        @Override public boolean visit(Assignment                      node) { return true; }
        @Override public boolean visit(Block                           node) { return true; }
        @Override public boolean visit(BlockComment                    node) { return true; }
        @Override public boolean visit(BooleanLiteral                  node) { return true; }
        @Override public boolean visit(BreakStatement                  node) { return process(node); }
        @Override public boolean visit(CastExpression                  node) { return true; }
        @Override public boolean visit(CatchClause                     node) { return true; }
        @Override public boolean visit(CharacterLiteral                node) { return true; }
        @Override public boolean visit(ClassInstanceCreation           node) { return process(node); }
        @Override public boolean visit(ConditionalExpression           node) { return true; }
        @Override public boolean visit(ConstructorInvocation           node) { return true; }
        @Override public boolean visit(ContinueStatement               node) { return process(node); }
        @Override public boolean visit(CompilationUnit                 node) { return true; }
        @Override public boolean visit(CreationReference               node) { return true; }
        @Override public boolean visit(Dimension                       node) { return true; }
        @Override public boolean visit(DoStatement                     node) { return true; }
        @Override public boolean visit(EmptyStatement                  node) { return true; }
        @Override public boolean visit(EnhancedForStatement            node) { return true; }
        @Override public boolean visit(EnumConstantDeclaration         node) { return true; }
        @Override public boolean visit(EnumDeclaration                 node) { return process(node); }
        @Override public boolean visit(ExportsDirective                node) { return true; }
        @Override public boolean visit(ExpressionMethodReference       node) { return true; }
        @Override public boolean visit(ExpressionStatement             node) { return true; }
        @Override public boolean visit(FieldAccess                     node) { return true; }
        @Override public boolean visit(FieldDeclaration                node) { return true; }
        @Override public boolean visit(ForStatement                    node) { return true; }
        @Override public boolean visit(IfStatement                     node) { return true; }
        @Override public boolean visit(ImportDeclaration               node) { return process(node); }
        @Override public boolean visit(InfixExpression                 node) { return true; }
        @Override public boolean visit(Initializer                     node) { return true; }
        @Override public boolean visit(InstanceofExpression            node) { return true; }
        @Override public boolean visit(IntersectionType                node) { return true; }
        @Override public boolean visit(Javadoc                         node) { return true; }
        @Override public boolean visit(LabeledStatement                node) { return process(node); }
        @Override public boolean visit(LambdaExpression                node) { return process(node); }
        @Override public boolean visit(LineComment                     node) { return true; }
        @Override public boolean visit(MarkerAnnotation                node) { return process(node); }
        @Override public boolean visit(MethodDeclaration               node) { return process(node); }
        @Override public boolean visit(MethodInvocation                node) { return true; }
        @Override public boolean visit(MemberRef                       node) { return true; }
        @Override public boolean visit(MemberValuePair                 node) { return true; }
        @Override public boolean visit(MethodRef                       node) { return true; }
        @Override public boolean visit(MethodRefParameter              node) { return true; }
        @Override public boolean visit(Modifier                        node) { return true; }
        @Override public boolean visit(ModuleDeclaration               node) { return true; }
        @Override public boolean visit(ModuleModifier                  node) { return true; }
        @Override public boolean visit(NameQualifiedType               node) { return true; }
        @Override public boolean visit(NormalAnnotation                node) { return process(node); }
        @Override public boolean visit(NullLiteral                     node) { return true; }
        @Override public boolean visit(NumberLiteral                   node) { return true; }
        @Override public boolean visit(OpensDirective                  node) { return true; }
        @Override public boolean visit(PackageDeclaration              node) { return process(node); }
        @Override public boolean visit(ParameterizedType               node) { return true; }
        @Override public boolean visit(ParenthesizedExpression         node) { return true; }
        @Override public boolean visit(PostfixExpression               node) { return true; }
        @Override public boolean visit(PrefixExpression                node) { return true; }
        @Override public boolean visit(PrimitiveType                   node) { return true; }
        @Override public boolean visit(ProvidesDirective               node) { return true; }
        @Override public boolean visit(QualifiedName                   node) { return process(node); }
        @Override public boolean visit(QualifiedType                   node) { return true; }
        @Override public boolean visit(RequiresDirective               node) { return true; }
        @Override public boolean visit(ReturnStatement                 node) { return true; }
        @Override public boolean visit(SimpleName                      node) { return process(node); }
        @Override public boolean visit(SimpleType                      node) { return true; }
        @Override public boolean visit(SingleMemberAnnotation          node) { return process(node); }
        @Override public boolean visit(SingleVariableDeclaration       node) { return true; }
        @Override public boolean visit(StringLiteral                   node) { return true; }
        @Override public boolean visit(SuperConstructorInvocation      node) { return true; }
        @Override public boolean visit(SuperFieldAccess                node) { return true; }
        @Override public boolean visit(SuperMethodInvocation           node) { return true; }
        @Override public boolean visit(SuperMethodReference            node) { return true; }
        @Override public boolean visit(SwitchCase                      node) { return true; }
        @Override public boolean visit(SwitchStatement                 node) { return true; }
        @Override public boolean visit(SynchronizedStatement           node) { return true; }
        @Override public boolean visit(TagElement                      node) { return true; }
        @Override public boolean visit(TextElement                     node) { return true; }
        @Override public boolean visit(ThisExpression                  node) { return true; }
        @Override public boolean visit(ThrowStatement                  node) { return true; }
        @Override public boolean visit(TryStatement                    node) { return true; }
        @Override public boolean visit(TypeDeclaration                 node) { return process(node); }
        @Override public boolean visit(TypeDeclarationStatement        node) { return true; }
        @Override public boolean visit(TypeLiteral                     node) { return true; }
        @Override public boolean visit(TypeMethodReference             node) { return true; }
        @Override public boolean visit(TypeParameter                   node) { return true; }
        @Override public boolean visit(VariableDeclarationExpression   node) { return true; }
        @Override public boolean visit(VariableDeclarationFragment     node) { return true; }
        @Override public boolean visit(VariableDeclarationStatement    node) { return true; }
        @Override public boolean visit(UnionType                       node) { return true; }
        @Override public boolean visit(UsesDirective                   node) { return true; }
        @Override public boolean visit(WhileStatement                  node) { return true; }
        @Override public boolean visit(WildcardType                    node) { return true; }
    };
}
