package agaricus.applysrg;

import com.intellij.psi.*;

import java.util.HashMap;

/**
 * Recursively descends and processes symbol references
 */
public class SymbolReferenceWalker {
    private String className;
    private String methodName = "(outside-method)";
    private String methodSignature = "";

    /**
     * Variables in the code block, mapped to the order they were declared.
     * This includes PsiLocalVariable from PsiDeclarationStatement, and also
     * PsiParameter from PsiForeachStatement/PsiCatchSection. Both are PsiVariable.
     */
    private HashMap<PsiVariable, Integer> localVariableIndices = new HashMap<PsiVariable, Integer>();
    private int nextLocalVariableIndex = 0;

    /**
     * Parameters to method, mapped to order in method declaration.
     * Set by caller
     * @see #addMethodParameterIndices
     */
    private HashMap<PsiParameter, Integer> methodParameterIndices = new HashMap<PsiParameter, Integer>();

    public SymbolReferenceWalker(String className) {
        this.className = className;
    }

    public SymbolReferenceWalker(String className, String methodName, String methodSignature) {
        this.className = className;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
    }

    /**
     * Recursively walk starting from given element
     * @param startElement
     */
    public void walk(PsiElement startElement) {
        walk(startElement, 0);
    }

    /**
     * Add map used for labeling method parameters by index
     * @param methodParameterIndices
     */
    public void addMethodParameterIndices(HashMap<PsiParameter, Integer> methodParameterIndices) {
        this.methodParameterIndices.putAll(methodParameterIndices);
    }


    private void walk(PsiElement psiElement, int depth) {
        //System.out.println("walking "+className+" "+psiMethod.getName()+" -- "+psiElement);

        if (psiElement == null) {
            return;
        }

        if (psiElement instanceof PsiDeclarationStatement) {
            PsiDeclarationStatement psiDeclarationStatement = (PsiDeclarationStatement)psiElement;

            for (PsiElement declaredElement : psiDeclarationStatement.getDeclaredElements()) {
                if (declaredElement instanceof PsiClass) {
                    System.out.println("TODO: inner class "+declaredElement); // TODO: process this?
                } else if (declaredElement instanceof PsiLocalVariable) {
                    PsiLocalVariable psiLocalVariable = (PsiLocalVariable)declaredElement;

                    System.out.println("@,"+psiLocalVariable.getTypeElement().getTextRange()+",type,"+psiLocalVariable.getType().getInternalCanonicalText());
                    System.out.println("@,"+psiLocalVariable.getNameIdentifier().getTextRange()+",localvar,"+className+","+methodName+","+methodSignature+","+psiLocalVariable.getName()+","+ nextLocalVariableIndex);

                    // Record order of variable declarations for references in body
                    localVariableIndices.put(psiLocalVariable, nextLocalVariableIndex);
                    nextLocalVariableIndex++;
                } else {
                    System.out.println("Unknown declaration "+psiDeclarationStatement);
                }
            }
        }

        if (psiElement instanceof PsiReferenceExpression) {
            PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression)psiElement;

            //PsiExpression psiQualifierExpression = psiReferenceExpression.getQualifierExpression();
            //PsiType psiQualifierType = psiQualifierExpression != null ? psiQualifierExpression.getType() : null;

            // What this reference expression actually refers to
            PsiElement referentElement = psiReferenceExpression.resolve();

            // Identifier token naming this reference without qualification
            PsiElement nameElement = psiReferenceExpression.getReferenceNameElement();
            String name = psiReferenceExpression.getReferenceName();

            if (referentElement instanceof PsiPackage) {

            } else if (referentElement instanceof PsiClass) {
                PsiClass psiClass = (PsiClass)referentElement;

                System.out.println("@,"+nameElement.getTextRange()+",class,"+psiClass.getQualifiedName());
            } else if (referentElement instanceof PsiField) {
                PsiField psiField = (PsiField)referentElement;
                PsiClass psiClass = psiField.getContainingClass();

                System.out.println("@,"+nameElement.getTextRange()+",field,"+psiClass.getQualifiedName()+","+psiField.getName());
            } else if (referentElement instanceof PsiMethod) {
                PsiMethod psiMethodCalled = (PsiMethod)referentElement;
                PsiClass psiClass = psiMethodCalled.getContainingClass();

                System.out.println("@,"+nameElement.getTextRange()+",method,"+psiClass.getQualifiedName()+","+psiMethodCalled.getName()+","+MethodSignatureHelper.makeTypeSignatureString(psiMethodCalled));
            } else if (referentElement instanceof PsiLocalVariable) {
                PsiLocalVariable psiLocalVariable = (PsiLocalVariable)referentElement;

                // Index of local variable as declared in method
                int index;
                if (!localVariableIndices.containsKey(psiLocalVariable))  {
                    index = -1;
                    System.out.println("couldn't find local variable index for "+psiLocalVariable+" in "+localVariableIndices);
                } else {
                    index = localVariableIndices.get(psiLocalVariable);
                }
                System.out.println("@,"+nameElement.getTextRange()+",localvar,"+className+","+methodName+","+methodSignature+","+psiLocalVariable.getName()+","+index);

            } else if (referentElement instanceof PsiParameter) {
                PsiParameter psiParameter = (PsiParameter)referentElement;

                PsiElement declarationScope = psiParameter.getDeclarationScope();

                if (declarationScope instanceof PsiMethod) {
                    // Method parameter

                    int index;
                    if (!methodParameterIndices.containsKey(psiParameter)) {
                        index = -1;
                        System.out.println("couldn't find method parameter index for "+psiParameter+" in "+methodParameterIndices);
                    } else {
                        index = methodParameterIndices.get(psiParameter);
                    }

                    System.out.println("@,"+nameElement.getTextRange()+",param,"+className+","+methodName+","+methodSignature+","+psiParameter.getName()+","+index);
                } else if (declarationScope instanceof PsiForeachStatement || declarationScope instanceof PsiCatchSection) {
                    // New variable declared with for(type var:...) and try{}catch(type var){}
                    // For some reason, PSI calls these "parameters", but they're more like local variable declarations
                    // Treat them as such

                    System.out.println("@,"+psiParameter.getTypeElement().getTextRange()+",type,"+psiParameter.getType().getInternalCanonicalText());
                    System.out.println("@,"+psiParameter.getNameIdentifier().getTextRange()+",localvar,"+className+","+methodName+","+methodSignature+","+psiParameter.getName()+","+ nextLocalVariableIndex);
                    localVariableIndices.put(psiParameter, nextLocalVariableIndex);
                    nextLocalVariableIndex++;
                } else {
                    System.out.println("parameter "+psiParameter+" in unknown declaration scope "+declarationScope);
                }

            } else {
                System.out.println("ignoring unknown referent "+referentElement+" in "+className+" "+methodName+","+methodSignature);
            }

            /*
            System.out.println("   ref "+psiReferenceExpression+
                    " nameElement="+nameElement+
                    " name="+psiReferenceExpression.getReferenceName()+
                    " resolve="+psiReferenceExpression.resolve()+
                    " text="+psiReferenceExpression.getText()+
                    " qualifiedName="+psiReferenceExpression.getQualifiedName()+
                    " qualifierExpr="+psiReferenceExpression.getQualifierExpression()
                );
                */
        }

        PsiElement[] children = psiElement.getChildren();
        if (children != null) {
            for (PsiElement child: children) {
                walk(child, depth + 1);
            }
        }
    }
}
