package agaricus.applysrg;

import com.intellij.psi.*;

import java.util.HashMap;

/**
 * Recursively descends and processes symbol references
 */
public class SymbolReferenceWalker {
    public String className;
    public String methodName = "(outside-method)";
    public String methodSignature = "";
    public HashMap<PsiLocalVariable, Integer> localVariableIndices = new HashMap<PsiLocalVariable, Integer>();
    public int nextLocalVariableIndex = 0;

    public SymbolReferenceWalker(String className) {
        this.className = className;
    }

    public SymbolReferenceWalker(String className, String methodName, String methodSignature) {
        this.className = className;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
    }

    public void walk(PsiElement startElement) {
        walk(startElement, 0);
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

                // TODO: index of parameter as in method parameter list
                System.out.println("@,"+nameElement.getTextRange()+",param,"+className+","+methodName+","+methodSignature+","+psiParameter.getName());
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
