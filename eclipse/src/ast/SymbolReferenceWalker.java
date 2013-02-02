package ast;

import java.util.HashMap;

import org.eclipse.jdt.core.dom.*;

/**
 * Recursively descends and processes symbol references
 */
public class SymbolReferenceWalker extends ASTVisitor
{
    // Where to write results to
    private SymbolRangeEmitter emitter;

    // Where we're at
    private String className;
    private String methodName = "(outside-method)";
    private String methodSignature = "";

    // If currently within // xxx start // xxx end comments
    private boolean withinAddedCode = false;

    /**
     * Variables in the code block, mapped to the order they were declared. This
     * includes PsiLocalVariable from PsiDeclarationStatement, and also
     * PsiParameter from PsiForeachStatement/PsiCatchSection. Both are
     * PsiVariable.
     */
    private HashMap<PsiVariable, Integer> localVariableIndices = new HashMap<PsiVariable, Integer>();
    private int nextLocalVariableIndex = 0;

    // Separate index for variable declarations in "added" code
    private int nextAddedLocalVariableIndex = 100;

    /**
     * Parameters to method, mapped to order in method declaration. Set by
     * caller
     * 
     * @see #addMethodParameterIndices
     */
    private HashMap<SingleVariableDeclaration, Integer> paramIndices = new HashMap<SingleVariableDeclaration, Integer>();

    public SymbolReferenceWalker(SymbolRangeEmitter emitter, String className)
    {
        this.emitter = emitter;
        this.className = className;
    }

    public SymbolReferenceWalker(SymbolRangeEmitter emitter, String className,
            String methodName, String methodSignature)
    {
        this(emitter, className);
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
        startElement.accept(this);
    }

    /**
     * Add map used for labeling method parameters by index
     * 
     * @param indices
     */
    public void addMethodParameterIndices(HashMap<SingleVariableDeclaration, Integer> indices)
    {
        this.paramIndices.putAll(indices);
    }

    /**
     * Record the positional index of a local variable declaration
     * 
     * @param psiVariable
     *            The newly-declared variable
     * @return The new index, unique per method
     */
    private int assignLocalVariableIndex(PsiVariable psiVariable)
    {
        int index = withinAddedCode ? nextAddedLocalVariableIndex
                : nextLocalVariableIndex;

        localVariableIndices.put(psiVariable, index);

        // Variables in "added" code are tracked with a separate index, so they
        // don't shift variables
        // indexes below the added code
        if (withinAddedCode)
        {
            nextAddedLocalVariableIndex++;
        }
        else
        {
            nextLocalVariableIndex++;
        }

        return index;
    }
/*
    private boolean walk(Expression expression, int depth)
    {
        // emitter.log("walking "+className+" "+psiMethod.getName()+" -- "+psiElement);

        if (expression == null)
        { 
            return true; // gracefully ignore
        }

        // Comment possibly telling us this is added code, to track local variables differently
        if (expression instanceof PsiComment)
        {
            PsiComment psiComment = (PsiComment) expression;
            if (psiComment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT)
            { // "//" style comments
                String commentText = psiComment.getText();
                // emitter.log("COMMENT:"+commentText);
                String[] words = commentText.split(" ");

                if (words.length >= 3)
                {
                    // First word is "//", second is "CraftBukkit", "Spigot",
                    // "Forge".., third is "start"/"end"
                    String command = words[2];
                    if (command.equalsIgnoreCase("start"))
                    {
                        withinAddedCode = true;
                    }
                    else if (command.equalsIgnoreCase("end"))
                    {
                        withinAddedCode = false;
                    }
                }
            }
        }

        // New local variable declaration
        if (expression instanceof PsiDeclarationStatement)
        {
            PsiDeclarationStatement psiDeclarationStatement = (PsiDeclarationStatement) expression;

            for (PsiElement declaredElement : psiDeclarationStatement
                    .getDeclaredElements())
            {
                if (declaredElement instanceof PsiClass)
                {
                    emitter.log("TODO: inner class " + declaredElement); // TODO:
                                                                         // process
                                                                         // this?
                }
                else if (declaredElement instanceof PsiLocalVariable)
                {
                    PsiLocalVariable psiLocalVariable = (PsiLocalVariable) declaredElement;

                    emitter.emitTypeRange(psiLocalVariable.getTypeElement());

                    // Record order of variable declarations for references in
                    // body
                    int index = assignLocalVariableIndex(psiLocalVariable);

                    emitter.emitLocalVariableRange(className, methodName,
                            methodSignature, psiLocalVariable, index);
                }
                else
                {
                    emitter.log("WARNING: Unknown declaration "
                            + psiDeclarationStatement);
                }
            }
        }

        // New local variable declaration within try..catch
        if (expression instanceof PsiCatchSection)
        {
            PsiCatchSection psiCatchSection = (PsiCatchSection) expression;
            PsiParameter psiParameter = psiCatchSection.getParameter();
            int index = assignLocalVariableIndex(psiParameter);
            emitter.emitLocalVariableRange(className, methodName,
                    methodSignature, psiParameter, index);
        }

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
    
    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(AnnotationTypeDeclaration node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(AnnotationTypeMemberDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(AnonymousClassDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ArrayAccess node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ArrayCreation node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ArrayInitializer node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ArrayType node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(AssertStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(Assignment node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(Block node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     * <p>Note: {@link LineComment} and {@link BlockComment} nodes are
     * not considered part of main structure of the AST. This method will
     * only be called if a client goes out of their way to visit this
     * kind of node explicitly.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.0
     */
    public boolean visit(BlockComment node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(BooleanLiteral node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(BreakStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(CastExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(CatchClause node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(CharacterLiteral node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ClassInstanceCreation node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(CompilationUnit node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ConditionalExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ConstructorInvocation node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ContinueStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(DoStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(EmptyStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(EnhancedForStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(EnumConstantDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(EnumDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ExpressionStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(FieldAccess node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(FieldDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ForStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(IfStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ImportDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(InfixExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(InstanceofExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(Initializer node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(LabeledStatement node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     * <p>Note: {@link LineComment} and {@link BlockComment} nodes are
     * not considered part of main structure of the AST. This method will
     * only be called if a client goes out of their way to visit this
     * kind of node explicitly.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.0
     */
    public boolean visit(LineComment node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(MarkerAnnotation node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.0
     */
    public boolean visit(MemberRef node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(MemberValuePair node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.0
     */
    public boolean visit(MethodRef node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.0
     */
    public boolean visit(MethodRefParameter node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(MethodDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(MethodInvocation node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(Modifier node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(NormalAnnotation node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(NullLiteral node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(NumberLiteral node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(PackageDeclaration node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(ParameterizedType node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ParenthesizedExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(PostfixExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(PrefixExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(PrimitiveType node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(QualifiedName node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(QualifiedType node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ReturnStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SimpleName node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SimpleType node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(SingleMemberAnnotation node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SingleVariableDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(StringLiteral node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SuperConstructorInvocation node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SuperFieldAccess node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SuperMethodInvocation node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SwitchCase node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SwitchStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(SynchronizedStatement node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.0
     */
    public boolean visit(TagElement node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.0
     */
    public boolean visit(TextElement node) {
        return true;
    }


    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ThisExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(ThrowStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(TryStatement node) {
        return true;
    }
    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(TypeDeclaration node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(TypeDeclarationStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(TypeLiteral node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(TypeParameter node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.7.1
     */
    public boolean visit(UnionType node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(VariableDeclarationExpression node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(VariableDeclarationStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(VariableDeclarationFragment node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     */
    public boolean visit(WhileStatement node) {
        return true;
    }

    /**
     * Visits the given type-specific AST node.
     * <p>
     * The default implementation does nothing and return true.
     * Subclasses may reimplement.
     * </p>
     *
     * @param node the node to visit
     * @return <code>true</code> if the children of this node should be
     * visited, and <code>false</code> if the children of this node should
     * be skipped
     * @since 3.1
     */
    public boolean visit(WildcardType node) {
        return true;
    }
}
