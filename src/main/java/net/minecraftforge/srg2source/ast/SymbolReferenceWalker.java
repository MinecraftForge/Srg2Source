package net.minecraftforge.srg2source.ast;

import java.util.HashMap;
import java.util.List;
import org.eclipse.jdt.core.dom.*;

/**
 * Recursively descends and processes symbol references
 */
@SuppressWarnings({"unused", "unchecked"})
public class SymbolReferenceWalker extends ASTVisitor
{
    private static final String FS = "|";

    // Where to write results to
    private SymbolRangeEmitter emitter;

    // Where we're at
    private String className;
    private SymbolReferenceWalker parent = null;

    private int[] newCodeRanges = new int[0];

    /**
     * Variables in the code block, mapped to the order they were declared. This
     * includes PsiLocalVariable from PsiDeclarationStatement, and also
     * PsiParameter from PsiForeachStatement/PsiCatchSection. Both are
     * PsiVariable.
     */
    private HashMap<IVariableBinding, Integer> localVars = new HashMap<IVariableBinding, Integer>();
    private int nextIndex = 0;
    private int nextIndexNew = 100;    // Separate index for variable declarations in "added" code

    private HashMap<String, Integer> paramIndices = new HashMap<String, Integer>();

    public SymbolReferenceWalker(SymbolRangeEmitter emitter, String className, int[] newCode)
    {
        this.emitter = emitter;
        this.className = className;
        this.newCodeRanges = newCode;
    }

    private SymbolReferenceWalker(String className, SymbolReferenceWalker parent)
    {
        this(parent.emitter, className, parent.newCodeRanges);
        this.parent = parent != null && parent.className != null ? parent : null;
    }

    /**
     * Recursively walk starting from given element
     *
     * @param startElement
     * @return true if successful, or false if failed due to unresolved symbols
     */
    public boolean walk(ASTNode startElement)
    {
        if (startElement == null)
        {
            return true;
        }

        try
        {
            startElement.accept(this);
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public boolean walk(List<ASTNode> elements)
    {
        if (elements == null)
        {
            return true;
        }

        boolean ret = true;
        for (ASTNode node : elements)
        {
            try
            {
                node.accept(this);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                ret = false;
            }
        }
        return ret;
    }

    public void setParams(HashMap<String, Integer> indices)
    {
        this.paramIndices.putAll(indices);
    }

    private boolean withinNewCode(int index)
    {
        for (int x = 0; x < newCodeRanges.length - 1; x += 2)
        {
            int start = newCodeRanges[x];
            int end = newCodeRanges[x+1];
            if (index >= start && index <= end)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Record the positional index of a local variable declaration
     *
     * @param binding
     *            The newly-declared variable
     * @return The new index, unique per method
     */
    private int assignLocalVariableIndex(SimpleName name, IVariableBinding binding)
    {
        boolean added = withinNewCode(name.getStartPosition());
        int index = added ? nextIndexNew : nextIndex;

        localVars.put(binding, index);

        // Variables in "added" code are tracked with a separate index, so they
        // don't shift variables
        // indexes below the added code
        if (added)
            nextIndexNew++;
        else
            nextIndex++;

        return index;
    }

    public boolean visit(AnnotationTypeDeclaration node) {
        emitter.log("Annotation Start: " + ((ITypeBinding)node.getName().resolveBinding()).getQualifiedName());
        emitter.tab();
        return true;
    }
    public void endVisit(AnnotationTypeDeclaration node) {
        emitter.log("Annotation End  : " + ((ITypeBinding)node.getName().resolveBinding()).getQualifiedName());
        emitter.untab();
    }

    private int anonCount = 1;
    public boolean visit(AnonymousClassDeclaration node)
    {
        String name = this.className + "$" + anonCount++;
        emitter.log("Anon Class Start: " + name);// + bind.getName());// + //.getDeclaringClass().getQualifiedName());
        emitter.tab();

        SymbolReferenceWalker walker = new SymbolReferenceWalker(name, this);
        for (BodyDeclaration body : (List<BodyDeclaration>)node.bodyDeclarations())
        {
            if (body instanceof FieldDeclaration)
            {
                FieldDeclaration field = (FieldDeclaration)body;
                emitter.emitTypeRange(field.getType());

                for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>)field.fragments())
                {
                    emitter.emitFieldRange(frag, name);
                    // Initializer can refer to other symbols, so walk it, too
                    SymbolReferenceWalker init = new SymbolReferenceWalker(name, walker);
                    init.anonCount = walker.anonCount;
                    init.walk(frag.getInitializer());
                    walker.anonCount = init.anonCount;
                }
            }
            else
            {
                walker.walk(body);
            }
        }

        emitter.untab();
        emitter.log("Anon Class End: " + name);
        return false;
    }

    public boolean visit(AnnotationTypeMemberDeclaration node) { return true; }
    public boolean visit(ArrayAccess             node) { return true; }
    public boolean visit(ArrayCreation           node) { return true; }
    public boolean visit(ArrayInitializer        node) { return true; }
    public boolean visit(ArrayType               node) { return true; }
    public boolean visit(AssertStatement         node) { return true; }
    public boolean visit(Assignment              node) { return true; }
    public boolean visit(CastExpression          node) { return true; }
    public boolean visit(CatchClause             node) { return true; }
    public boolean visit(CompilationUnit         node) { return true; }
    public boolean visit(ConstructorInvocation   node) { return true; }
    public boolean visit(EnumConstantDeclaration node) { return true; }
    public boolean visit(ExpressionStatement     node) { return true; }
    public boolean visit(FieldAccess             node) { return true; }
    public boolean visit(ForStatement            node) { return true; }
    public boolean visit(IfStatement             node) { return true; }
    public boolean visit(LabeledStatement        node) { return true; }
    public boolean visit(TypeLiteral             node) { return true; }
    public boolean visit(TypeParameter           node) { return true; }
    public boolean visit(MethodInvocation        node) { return true; }
    public boolean visit(ParameterizedType       node) { return true; }
    public boolean visit(QualifiedType           node) { return true; }
    public boolean visit(MarkerAnnotation        node) { return true; }
    public boolean visit(NormalAnnotation        node) { return true; }
    public boolean visit(SingleMemberAnnotation  node) { return true; }

    public boolean visit(ClassInstanceCreation node) {
        walk(node.getExpression());
        if (node.getAST().apiLevel() == 2) {
            walk(node.getName());
        }
        if (node.getAST().apiLevel() >= 3) {
            walk(node.typeArguments());
            emitter.emitTypeRange(node.getType(), node.getExpression() != null);
        }
        walk(node.arguments());
        walk(node.getAnonymousClassDeclaration());
        return false;
    }

    public boolean visit(FieldDeclaration node) {
        emitter.emitTypeRange(node.getType()); //TODO: This double prints due to the walk below, Why are we specifically handling fields?

        this.walk(node.getJavadoc());

        if (node.getAST().apiLevel() >= 3)
            walk(node.modifiers());

        walk(node.getType());

        for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>)node.fragments())
        {
            emitter.emitFieldRange(frag, className);
            // Initializer can refer to other symbols, so walk it, too
            SymbolReferenceWalker walker = new SymbolReferenceWalker(className, this);
            walker.anonCount = this.anonCount;
            walker.walk(frag.getInitializer());
            this.anonCount = walker.anonCount;
        }
        return false;
    }
    public boolean visit(EnumDeclaration node) {
        ITypeBinding bind = (ITypeBinding)node.getName().resolveBinding();
        if (bind == null) {
            emitter.error("Could not bind ENUM: " + node.getName() + " in " + className);
            return false;
        }
        String name = ((ITypeBinding)node.getName().resolveBinding()).getQualifiedName();

        emitter.log("Enum Start: " + name);
        emitter.tab();

        SymbolReferenceWalker walker = new SymbolReferenceWalker(name, this);

        walker.walk(node.getJavadoc());
        walker.walk(node.modifiers());
        walker.walk(node.getName());
        walker.walk(node.superInterfaceTypes());
        walker.walk(node.enumConstants());
        walker.walk(node.bodyDeclarations());

        emitter.untab();
        emitter.log("Enum End  : " + name);
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean visit(MethodDeclaration node) {
        String signature = emitter.getMethodSignature(node, false);
        String name = node.getName().toString();
        //emitter.emitMethodRange(node, className, true);

        emitter.log("Method Start: " + name + signature);
        emitter.tab();

        List<SingleVariableDeclaration> params = (List<SingleVariableDeclaration>)node.parameters();
        HashMap<String, Integer> paramIds = new HashMap<String, Integer>();

        for (int x = 0; x < params.size(); x++)
        {
            paramIds.put(params.get(x).getName().getIdentifier(), x);

            SingleVariableDeclaration param = params.get(x);
            emitter.emitParameterRange(node, signature, param, x, className);
            paramIds.put(param.getName().getIdentifier(), x);
        }

        // Method body
        SymbolReferenceWalker walker = new SymbolReferenceWalker(className, this);

        walker.anonCount = this.anonCount;
        walker.setParams(paramIds);

        walker.walk(node.getJavadoc());
        if(node.getAST().apiLevel() == 2) {
           walker.walk(node.getReturnType());
        } else {
           walker.walk(node.modifiers());
           walker.walk(node.typeParameters());
           walker.walk(node.getReturnType2());
        }

        walker.walk(node.getName());
        if(node.getAST().apiLevel() >= 8) {
            walker.walk(node.getReceiverType());
           walker.walk(node.getReceiverQualifier());
        }

        walker.walk(node.parameters());
        if(node.getAST().apiLevel() >= 8) {
            walker.walk(node.extraDimensions());
           walker.walk(node.thrownExceptionTypes());
        } else {
            walker.walk(node.thrownExceptions());
        }

        walker.walk(node.getBody());

        this.anonCount = walker.anonCount;


        emitter.untab();
        emitter.log("Method End: " + name + signature);
        return false;
    }
    public boolean visit(SimpleName node)
    {
        IBinding bind = node.resolveBinding();
        if (bind instanceof IMethodBinding)
        {
            emitter.emitReferencedMethod(node, (IMethodBinding)bind, this.className);
        }
        else if (bind instanceof ITypeBinding)
        {
            boolean qualified = node.getParent() instanceof QualifiedType && ((QualifiedType)node.getParent()).getName().equals(node);
            emitter.emitReferencedClass(node, (ITypeBinding)bind, qualified);
        }
        else if (bind instanceof IVariableBinding)
        {
            IVariableBinding var = (IVariableBinding)bind;
            if (var.isParameter())
            {
                String id = node.getIdentifier();
                String owner = className;
                SymbolReferenceWalker walker = this;

                Integer i = (paramIndices.containsKey(id) ? paramIndices.get(id) : -1);
                while (i == -1 && walker.parent != null)
                {
                    walker = walker.parent;
                    owner = walker.className;
                    i = (walker.paramIndices.containsKey(id) ? walker.paramIndices.get(id) : -1);
                }
                emitter.emitReferencedMethodParameter(node, var, i, owner);
            }
            else if (var.isField())
            {
                emitter.emitReferencedField(node, var.getVariableDeclaration(), className);
            }
            else
            {
                //emitter.emitLocalVariableRange(node, className, methodName, methodSignature, localVars.get(var.getVariableDeclaration()));
            }
        }
        else if (bind instanceof IPackageBinding)
        {
            //Dont care, grab those else ware
        }
        else
        {
            emitter.log("ERROR SimpleName: " + node + " " + bind);
        }
        return true;
    }

    public boolean visit(QualifiedName node)
    {
        IBinding bind = node.resolveBinding();
        if (bind instanceof ITypeBinding)
        {
            emitter.emitReferencedClass(node, (ITypeBinding)bind, false);
            return false;
        }
        return true;
    }

    public boolean visit(SimpleType node)
    {
        IBinding bind = node.resolveBinding();
        if (bind instanceof ITypeBinding)
        {
            emitter.emitReferencedClass(node.getName(), (ITypeBinding)bind, false);
        }
        else
        {
            emitter.log("ERROR SimpleType: " + node + " " + (bind != null ? bind.getClass() : "null"));
        }
        return false;
    }

    public boolean visit(SingleVariableDeclaration node)
    {
        int index = this.assignLocalVariableIndex(node.getName(), node.resolveBinding());
        //emitter.emitLocalVariableRange(node.getName(), className, methodName, methodSignature, index);

        if(node.getAST().apiLevel() >= 3)
            walk(node.modifiers());

        walk(node.getType());

        if(node.getAST().apiLevel() >= 8 && node.isVarargs())
            walk(node.varargsAnnotations());

        //this.acceptChild(this, node.getName()); if we want to track local vars...?

        if(node.getAST().apiLevel() >= 8)
            walk(node.extraDimensions());

        walk(node.getInitializer());

        return false;
    }

    public boolean visit(TypeDeclaration node) {
        final ITypeBinding binding = (ITypeBinding)node.getName().resolveBinding();
        String name = binding.getQualifiedName();
        if (name.isEmpty()) // local or anonymous type
        {
            String simpleName = binding.getName(); // may contain $
            String binaryName = binding.getBinaryName();
            if (!binaryName.endsWith(simpleName)) throw new AssertionError(binaryName + " does not end with " + simpleName);
            // binary names look like 'pkgA.pkgB.Class$Inner$1Local'
            // this tries to keep it similar to currently generated names for anonymous names (pkgA.pkgB.Class.Inner$1)
            int simpleNameEndIndex = binaryName.length() - simpleName.length();
            String binarySimpleName = binaryName.substring(binaryName.lastIndexOf("$", simpleNameEndIndex));
            name = className + binarySimpleName;
        }

        emitter.log("Class Start: " + name);
        emitter.tab();

        SymbolReferenceWalker walker = new SymbolReferenceWalker(name, this);

        if (node.getAST().apiLevel() == 2)
        {
            walker.walk(node.getJavadoc());
            walker.walk(node.getName());
            walker.walk(node.getSuperclassType());
            walker.walk(node.superInterfaceTypes());
            walker.walk(node.bodyDeclarations());
        }

        if (node.getAST().apiLevel() >= 2)
        {
            walker.walk(node.getJavadoc());
            walker.walk(node.modifiers());
            walker.walk(node.getName());
            walker.walk(node.typeParameters());
            walker.walk(node.getSuperclassType());
            walker.walk(node.superInterfaceTypes());
            walker.walk(node.bodyDeclarations());
        }

        emitter.untab();
        emitter.log("Class End  : " + name);
        return false;
    }
    public boolean visit(VariableDeclarationFragment node)
    {
        int index = assignLocalVariableIndex(node.getName(), node.resolveBinding());
        //emitter.emitLocalVariableRange(node.getName(), className, methodName, methodSignature, index);

        //walk(node.getName());

        if (node.getAST().apiLevel() >= 8)
            walk(node.extraDimensions());
        walk(node.getInitializer());

        return false;
    }

    @Override
    public boolean visit(PackageDeclaration node)
    {
        emitter.emitPackageRange(node);
        return true;
    }

    @Override
    public boolean visit(ImportDeclaration node)
    {
        emitter.emitImportRange(node);
        return false; // We don't emit anything because this is handled elsewhere in our import reorganizer.
    }
}
