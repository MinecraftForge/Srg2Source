package agaricus.applysrg;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;

public class SymbolRangeEmitter {
    String sourceFilePath;

    public SymbolRangeEmitter(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
    }

    /**
     * Emit range of package statement, declaring the package the file resides within
     * @param psiPackageStatement
     */
    public void emitPackageRange(PsiPackageStatement psiPackageStatement) {
        System.out.println("@,"+sourceFilePath+","+psiPackageStatement.getPackageReference().getTextRange()+",package,"+psiPackageStatement.getPackageName());
    }

    /**
     * Emit range of import statement, including name of the imported package/class
     * @param psiImportStatement
     */
    public void emitImportRange(PsiImportStatementBase psiImportStatement) {
        PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = psiImportStatement.getImportReference();

        String qualifiedName = psiJavaCodeReferenceElement.getQualifiedName();
        System.out.println("@,"+sourceFilePath+","+psiJavaCodeReferenceElement.getTextRange()+",class,"+qualifiedName); // note, may be package.*?
    }

    /**
     * Emit class name declaration
     * @param psiClass
     * @return Qualified class name, for referencing class members
     */
    public String emitClassRange(PsiClass psiClass) {
        String className = psiClass.getQualifiedName();

        System.out.println("@,"+sourceFilePath+","+psiClass.getNameIdentifier().getTextRange()+",class,"+className);

        return className;
    }

    /**
     * Emit type reference element range
     * (This is for when types are used, not declared)
     * Only class name references will be emitted
     * @param psiTypeElement
     */
    public void emitTypeRange(PsiTypeElement psiTypeElement) {
        if (psiTypeElement == null) {
            return;
        }

        PsiType psiType = psiTypeElement.getType();

        if (psiType instanceof PsiPrimitiveType) { // skip int, etc. - they're never going to be renamed
            return;
        }

        // TODO: getDeepComponentType() - reach inside arrays, but, need to consider text offsets

        System.out.println("@,"+sourceFilePath+","+psiTypeElement.getTextRange()+",class,"+psiType.getInternalCanonicalText());

        // Process type parameters, for example, String in HashMap<String,String>
        // for other examples see https://gist.github.com/4370462
        PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = psiTypeElement.getInnermostComponentReferenceElement();
        PsiReferenceParameterList psiReferenceParameterList = psiJavaCodeReferenceElement.getParameterList();
        for (PsiTypeElement innerTypeElement: psiReferenceParameterList.getTypeParameterElements()) {
            emitTypeRange(innerTypeElement);
        }
    }

    /**
     * Emit field name range
     * @param className
     * @param psiField
     */
    public void emitFieldRange(String className, PsiField psiField) {
        System.out.println("@,"+sourceFilePath+","+psiField.getNameIdentifier().getTextRange()+",field,"+className+","+psiField.getName());
    }

    /**
     * Emit method declaration name range
     * @param className
     * @param psiMethod
     * @return Method signature, for referencing method body members
     */
    public String emitMethodRange(String className, PsiMethod psiMethod) {
        String signature = MethodSignatureHelper.makeTypeSignatureString(psiMethod);

        System.out.println("@,"+sourceFilePath+","+psiMethod.getNameIdentifier().getTextRange()+",method,"+className+","+psiMethod.getName()+","+signature);

        return signature;
    }

    /**
     * Emit method parameter name declaration range
     * @param className
     * @param methodName
     * @param methodSignature
     * @param psiParameter
     * @param parameterIndex
     */
    public void emitParameterRange(String className, String methodName, String methodSignature, PsiParameter psiParameter, int parameterIndex) {
        if (psiParameter == null || psiParameter.getNameIdentifier() == null) {
            return;
        }

        System.out.println("@,"+sourceFilePath+","+psiParameter.getNameIdentifier().getTextRange()+",param,"+className+","+methodName+","+methodSignature+","+psiParameter.getName()+","+parameterIndex);
    }

    /**
     * Emit local variable declaration name range
     * Local variables can occur in methods, initializers, ...
     * @param className
     * @param methodName
     * @param methodSignature
     * @param psiLocalVariable
     * @param localVariableIndex
     */
    public void emitLocalVariableRange(String className, String methodName, String methodSignature, PsiLocalVariable psiLocalVariable, int localVariableIndex) {
        System.out.println("@,"+sourceFilePath+","+psiLocalVariable.getNameIdentifier().getTextRange()+",localvar,"+className+","+methodName+","+methodSignature+","+psiLocalVariable.getName()+","+localVariableIndex);
    }

    // Referenced names below (symbol uses, as opposed to declarations)


    /**
     * Emit referenced class name range
     * @param nameElement
     * @param psiClass
     */
    public void emitReferencedClass(PsiElement nameElement, PsiClass psiClass) {
        System.out.println("@,"+sourceFilePath+","+nameElement.getTextRange()+",class,"+psiClass.getQualifiedName());
    }

    /**
     * Emit referenced field name range
     * @param nameElement
     * @param psiField
     */
    public void emitReferencedField(PsiElement nameElement, PsiField psiField) {
        PsiClass psiClass = psiField.getContainingClass();

        System.out.println("@,"+sourceFilePath+","+nameElement.getTextRange()+",field,"+psiClass.getQualifiedName()+","+psiField.getName());
    }

    /**
     * Emit referenced method name range, that is, a method call
     * @param nameElement
     * @param psiMethodCalled
     */
    public void emitReferencedMethod(PsiElement nameElement, PsiMethod psiMethodCalled) {
        PsiClass psiClass = psiMethodCalled.getContainingClass();

        System.out.println("@,"+sourceFilePath+","+nameElement.getTextRange()+",method,"+psiClass.getQualifiedName()+","+psiMethodCalled.getName()+","+MethodSignatureHelper.makeTypeSignatureString(psiMethodCalled));
    }

    /**
     * Emit referenced local variable name range
     * This includes both "local variables" declared in methods, and in foreach/catch sections ("parameters")
     * @param nameElement
     * @param className
     * @param methodName
     * @param methodSignature
     * @param psiVariable
     * @param localVariableIndex
     */
    public void emitReferencedLocalVariable(PsiElement nameElement, String className, String methodName, String methodSignature, PsiVariable psiVariable, int localVariableIndex) {
        System.out.println("@,"+sourceFilePath+","+nameElement.getTextRange()+",localvar,"+className+","+methodName+","+methodSignature+","+psiVariable.getName()+","+localVariableIndex);
    }

    /**
     * Emit referenced method parameter name range
     * Only includes _method_ parameters - for foreach/catch parameters see emitReferencedLocalVariable
     * @param nameElement
     * @param className
     * @param methodName
     * @param methodSignature
     * @param psiParameter
     * @param index
     */
    public void emitReferencedMethodParameter(PsiElement nameElement, String className, String methodName, String methodSignature, PsiParameter psiParameter, int index) {
        System.out.println("@,"+sourceFilePath+","+nameElement.getTextRange()+",param,"+className+","+methodName+","+methodSignature+","+psiParameter.getName()+","+index);
    }
}
