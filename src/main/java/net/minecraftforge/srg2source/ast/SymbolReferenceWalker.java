package net.minecraftforge.srg2source.ast;

import java.util.HashMap;

import org.eclipse.jdt.core.dom.*;

/**
 * Recursively descends and processes symbol references
 */
@SuppressWarnings("unused")
public class SymbolReferenceWalker extends ASTVisitor
{
    // Where to write results to
    private SymbolRangeEmitter emitter;

    // Where we're at
    private String className;
    private String methodName = "(outside-method)";
    private String methodSignature = "";

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
    }

    public SymbolReferenceWalker(SymbolRangeEmitter emitter, String className, int[] newCode, String methodName, String methodSignature)
    {
        this(emitter, className, newCode);
        this.methodName = methodName;
        this.methodSignature = methodSignature;
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
    
    public void setParams(HashMap<String, Integer> indices)
    {
        this.paramIndices.putAll(indices);
    }
    
    private boolean withinNewCode(int index)
    {
        for (int x = 0; x < newCodeRanges.length; x += 2)
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
/*
    private boolean walk(Expression expression, int depth)
    {
        // .. and foreach
        if (expression instanceof PsiForeachStatement)
        {
            PsiForeachStatement psiForeachStatement = (PsiForeachStatement) expression;
            PsiParameter psiParameter = psiForeachStatement
                    .getIterationParameter();
            int index = assignLocalVariableIndex(psiParameter);
            emitter.emitLocalVariableRange(className, methodName,
                    methodSignature, psiParameter, index);
        }

        // Variable reference
        if (expression instanceof PsiJavaCodeReferenceElement)
        {
            PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = (PsiJavaCodeReferenceElement) expression;

            // Identifier token naming this reference without qualification
            PsiElement nameElement = psiJavaCodeReferenceElement
                    .getReferenceNameElement();

            // What this reference expression actually refers to
            PsiElement referentElement = psiJavaCodeReferenceElement.resolve();

            if (referentElement == null)
            {
                // Element references something that doesn't exist! This shows
                // in red in the IDE, as unresolved symbols.
                // Fail hard
                emitter.log("FAILURE: unresolved symbol: null referent "
                        + expression + " in " + className + " " + methodName
                        + "," + methodSignature + "," + nameElement.getText()
                        + "," + nameElement.getTextRange());
                /*
                 * if (methodSignature.contains("<")) { // for some reason -
                 * MCPC fails to remap, with this and only this one broken
                 * reference: // FAILURE: unresolved symbol: null referent null
                 * in cpw.mods.fml.common.event.FMLFingerprintViolationEvent
                 * FMLFingerprintViolationEvent
                 * ,(ZLjava/io/File;Lcom/google/common
                 * /collect/ImmutableSet<java/lang/String>;)V // just ignore it
                 * emitter.log("TODO: support templated method parameter here");
                 * } else { return false; }
                 * /
            }
            else if (referentElement instanceof PsiPackage)
            {
                // Not logging package since includes net, net.minecraft,
                // net.minecraft.server.. all components
                // TODO: log reference for rename
                // emitter.log("PKGREF"+referentElement+" name="+nameElement);
            }
            else if (referentElement instanceof PsiClass)
            {
                emitter.emitReferencedClass(nameElement,
                        (PsiClass) referentElement);
                // TODO
                // emitter.emitTypeQualifierRangeIfQualified((psiJavaCodeReferenceElement));
            }
            else if (referentElement instanceof PsiField)
            {
                emitter.emitReferencedField(nameElement,
                        (PsiField) referentElement);
                // TODO
                // emitter.emitTypeQualifierRangeIfQualified((psiJavaCodeReferenceElement));
            }
            else if (referentElement instanceof PsiMethod)
            {
                emitter.emitReferencedMethod(nameElement,
                        (PsiMethod) referentElement);
                // TODO
                // emitter.emitTypeQualifierRangeIfQualified((psiJavaCodeReferenceElement));
            }
            else if (referentElement instanceof PsiLocalVariable)
            {
                PsiLocalVariable psiLocalVariable = (PsiLocalVariable) referentElement;

                // Index of local variable as declared in method
                int index;
                if (!localVariableIndices.containsKey(psiLocalVariable))
                {
                    index = -1;
                    emitter.log("couldn't find local variable index for "
                            + psiLocalVariable + " in " + localVariableIndices);
                }
                else
                {
                    index = localVariableIndices.get(psiLocalVariable);
                }

                emitter.emitReferencedLocalVariable(nameElement, className,
                        methodName, methodSignature, psiLocalVariable, index);
            }
            else if (referentElement instanceof PsiParameter)
            {
                PsiParameter psiParameter = (PsiParameter) referentElement;

                PsiElement declarationScope = psiParameter
                        .getDeclarationScope();

                if (declarationScope instanceof PsiMethod)
                {
                    // Method parameter

                    int index;
                    if (!paramIndices.containsKey(psiParameter))
                    {
                        index = -1;
                        // TODO: properly handle parameters in inner classes..
                        // currently we always look at the outer method,
                        // but there could be parameters in a method in an inner
                        // class. This currently causes four errors in CB,
                        // CraftTask and CraftScheduler, since it makes heavy
                        // use of anonymous inner classes.
                        emitter.log("WARNING: couldn't find method parameter index for "
                                + psiParameter
                                + " in "
                                + paramIndices);
                    }
                    else
                    {
                        index = paramIndices.get(psiParameter);
                    }

                    emitter.emitReferencedMethodParameter(nameElement,
                            className, methodName, methodSignature,
                            psiParameter, index);
                }
                else if (declarationScope instanceof PsiForeachStatement
                        || declarationScope instanceof PsiCatchSection)
                {
                    // New variable declared with for(type var:...) and
                    // try{}catch(type var){}
                    // For some reason, PSI calls these "parameters", but
                    // they're more like local variable declarations
                    // Treat them as such

                    int index;
                    if (!localVariableIndices.containsKey(psiParameter))
                    {
                        index = -1;
                        emitter.log("WARNING: couldn't find non-method parameter index for "
                                + psiParameter + " in " + localVariableIndices);
                    }
                    else
                    {
                        index = localVariableIndices.get(psiParameter);
                    }
                    emitter.emitTypeRange(psiParameter.getTypeElement());
                    emitter.emitReferencedLocalVariable(nameElement, className,
                            methodName, methodSignature, psiParameter, index);
                }
                else
                {
                    emitter.log("WARNING: parameter " + psiParameter
                            + " in unknown declaration scope "
                            + declarationScope);
                }
            }
            else
            {
                emitter.log("WARNING: ignoring unknown referent "
                        + referentElement + " in " + className + " "
                        + methodName + "," + methodSignature);
            }

            /*
             * emitter.log("   ref "+psiReferenceExpression+
             * " nameElement="+nameElement+
             * " name="+psiReferenceExpression.getReferenceName()+
             * " resolve="+psiReferenceExpression.resolve()+
             * " text="+psiReferenceExpression.getText()+
             * " qualifiedName="+psiReferenceExpression.getQualifiedName()+
             * " qualifierExpr="+psiReferenceExpression.getQualifierExpression()
             * );
             * /
        }

        PsiElement[] children = expression.getChildren();
        if (children != null)
        {
            for (PsiElement child : children)
            {
                if (!walk(child, depth + 1)) { return false; // fail
                }
            }
        }
        return true;
    }
    */
    public boolean visit(AnnotationTypeDeclaration node)
    {
        //emitter.log("Annotation: " + node.getName().getIdentifier());
        return true;
    }
    public boolean visit(AnnotationTypeMemberDeclaration node)
    {
        //emitter.log("AnnotationTypeMember: " + node.getName().getIdentifier());
        return true;
    }

    public boolean visit(AnonymousClassDeclaration node)
    {
        emitter.log("AnonymousClassDeclaration: " + node.getStartPosition() + '|' + (node.getStartPosition() + node.getLength()));
        return true;
    }

    public boolean visit(ArrayAccess node)
    {
        //emitter.log("ArrayAccess: " + node);
        return true;
    }

    public boolean visit(ArrayCreation node)
    {
        //emitter.log("ArrayCreation: " + node);
        return true;
    }

    public boolean visit(ArrayInitializer node)
    {
        //emitter.log("ArrayIntializer: " + node);
        return true;
    }

    public boolean visit(ArrayType node)
    {
        //emitter.log("ArrayType: " + node);
        return true;
    }

    public boolean visit(AssertStatement node)
    {
        //emitter.log("AssertStatement: " + node);
        return true;
    }

    public boolean visit(Assignment node)
    {
        //emitter.log("Assignment: " + node);
        return true;
    }
    public boolean visit(CastExpression node)
    {
        //emitter.log("CastExpression: " + node);
        return true;
    }
    
    public boolean visit(CatchClause node)
    {
        //emitter.log("CatchClause: " + node);
        return true;
    }

    public boolean visit(ClassInstanceCreation node)
    {
        //emitter.log("ClassInstanceCreation: " + node);
        return true;
    }

    public boolean visit(CompilationUnit node)
    {
        //emitter.log("CompilationUnit: " + node);
        return true;
    }

    public boolean visit(ConstructorInvocation node)
    {
        //emitter.log("ConstructorInvocation: " + node);
        return true;
    }
    public boolean visit(EnumConstantDeclaration node)
    {
        //emitter.log("EnumConstantDeclaration: " + node);
        return true;
    }
    public boolean visit(EnumDeclaration node)
    {
        //emitter.log("EnumDeclaration: " + node);
        return true;
    }
    public boolean visit(ExpressionStatement node)
    {
        //emitter.log("ExpressionStatement: " + node);
        return true;
    }
    public boolean visit(FieldAccess node)
    {
        //emitter.log("FieldAccess: " + node);
        return true;
    }
    public boolean visit(FieldDeclaration node) {
        return true;
    }
    public boolean visit(ForStatement node) {
        return true;
    }
    public boolean visit(IfStatement node) {
        return true;
    }
    public boolean visit(LabeledStatement node) {
        return true;
    }
    public boolean visit(MethodDeclaration node) {
        return true;
    }
    public boolean visit(MethodInvocation node) {
        return true;
    }
    public boolean visit(ParameterizedType node) {
        return true;
    }
    public boolean visit(QualifiedName node)
    {
        //emitter.log("QualifiedName: " + node);
        return true;
    }
    public boolean visit(QualifiedType node)
    {
        //emitter.log("QualifiedType: " + node);
        return true;
    }
    public boolean visit(SimpleName node)
    {
        IBinding bind = node.resolveBinding();
        if (bind instanceof IMethodBinding)
        {
            emitter.emitReferencedMethod(node, (IMethodBinding)bind);
        }
        else if (bind instanceof ITypeBinding)
        {
            emitter.emitReferencedClass(node, (ITypeBinding)bind);
        }
        else if (bind instanceof IVariableBinding)
        {
            IVariableBinding var = (IVariableBinding)bind;
            if (var.isParameter())
            {
                String id = node.getIdentifier();
                Integer i = (paramIndices.containsKey(id) ? paramIndices.get(id) : -1);
                emitter.emitReferencedMethodParameter(node, var, i);
            }
            else if (var.isField())
            {
                emitter.emitReferencedField(node, var.getVariableDeclaration());
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
            emitter.log("ERROR SimpleName: " + node + " " + node.resolveBinding());
        }
        return true;
    }
    public boolean visit(SimpleType node)
    {
        IBinding bind = node.resolveBinding();
        if (bind instanceof ITypeBinding)
        {
            emitter.emitReferencedClass(node.getName(), (ITypeBinding)bind);
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
        
        node.getType().accept(this);
        if (node.getInitializer() != null)
        {
            node.getInitializer().accept(this);
        }
        return false;
    }
    public boolean visit(TypeDeclaration node) {
        return true;
    }
    public boolean visit(TypeLiteral node) {
        return true;
    }
    public boolean visit(TypeParameter node) {
        return true;
    }
    public boolean visit(VariableDeclarationFragment node)
    {
        int index = assignLocalVariableIndex(node.getName(), node.resolveBinding());
        //emitter.emitLocalVariableRange(node.getName(), className, methodName, methodSignature, index);
        
        if (node.getInitializer() != null)
        {
            node.getInitializer().accept(this);
        }
        return false;
    }
}
