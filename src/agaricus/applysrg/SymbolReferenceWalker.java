package agaricus.applysrg;

import com.intellij.psi.*;

/**
 * Recursively descends and processes symbol references
 */
public class SymbolReferenceWalker {
    public String className;
    public String methodName = "(outside-method)";
    public String methodSignature = "";

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

                // TODO: index of local variable as declared in method
                System.out.println("@,"+nameElement.getTextRange()+",localvar,"+className+","+methodName+","+methodSignature+","+psiLocalVariable.getName());

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
