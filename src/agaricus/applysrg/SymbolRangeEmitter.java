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
     */
    public void emitPackageRange(PsiPackageStatement psiPackageStatement) {
        internalEmitPackageRange(psiPackageStatement.getPackageReference().getText(), psiPackageStatement.getPackageReference().getTextRange(), psiPackageStatement.getPackageName());
    }

    /**
     * Emit range of import statement, including name of the imported package/class
     */
    public void emitImportRange(PsiImportStatementBase psiImportStatement) {
        // Disabled for now - class references are handled differently in import statements
        // (must be fully qualified...)

        /*
        PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = psiImportStatement.getImportReference();

        String qualifiedName = psiJavaCodeReferenceElement.getQualifiedName(); // note, may be package.*?
        internalEmitClassRange(psiJavaCodeReferenceElement.getText(), psiJavaCodeReferenceElement.getTextRange(), qualifiedName);
        */
    }

    /**
     * Emit class name declaration
     * @return Qualified class name, for referencing class members
     */
    public String emitClassRange(PsiClass psiClass) {
        String className = psiClass.getQualifiedName();

        internalEmitClassRange(psiClass.getNameIdentifier().getText(), psiClass.getNameIdentifier().getTextRange(),className);

        return className;
    }

    /**
     * Emit type reference element range
     * (This is for when types are used, not declared)
     * Only class name references will be emitted
     */
    public void emitTypeRange(PsiTypeElement psiTypeElement) {
        if (psiTypeElement == null) {
            return;
        }

        PsiType psiType = psiTypeElement.getType();

        // Go deeper.. reaching inside arrays
        // We want to report e.g. java.lang.String, not java.lang.String[]
        psiType = psiType.getDeepComponentType();

        if (psiType instanceof PsiPrimitiveType) { // skip int, etc. - they're never going to be renamed
            return;
        }

        // Get identifier referencing this type
        PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = psiTypeElement.getInnermostComponentReferenceElement();

        if (psiJavaCodeReferenceElement == null) {
            // get this on '? extends T'
            System.out.println("WARNING: no code reference element for "+psiTypeElement);
            return;
        }

        PsiElement referenceNameElement = psiJavaCodeReferenceElement.getReferenceNameElement();
        if (!(referenceNameElement instanceof PsiIdentifier)) {
            System.out.println("WARNING: unrecognized reference name element, not identifier: " + referenceNameElement);
            return;
        }
        PsiIdentifier psiIdentifier = (PsiIdentifier)referenceNameElement;

        // Get the "base" parent type name -- without any array brackets, or type parameters
        String baseTypeName = psiType.getInternalCanonicalText();
        if (baseTypeName.contains("<")) {
            // Sorry I couldn't find a better way to do this..
            // The PsiIdentifier range is correct, but it needs to be fully qualified, so it has to come from
            // a PsiType. getDeepComponentType() handles descending into arrays, but not parameterized types. TODO: make better
            baseTypeName = baseTypeName.replaceFirst("<.*", "");
        }
        internalEmitClassRange(psiIdentifier.getText(),psiIdentifier.getTextRange(),baseTypeName);

        // Process type parameters, for example, Integer and Boolean in HashMap<Integer,Boolean>
        // for other examples see https://gist.github.com/4370462
        PsiReferenceParameterList psiReferenceParameterList = psiJavaCodeReferenceElement.getParameterList();
        for (PsiTypeElement innerTypeElement: psiReferenceParameterList.getTypeParameterElements()) {
            emitTypeRange(innerTypeElement);
        }
    }

    /**
     * Emit field name range
     */
    public void emitFieldRange(String className, PsiField psiField) {
        internalEmitFieldRange(psiField.getNameIdentifier().getText(),psiField.getNameIdentifier().getTextRange(),className,psiField.getName());
    }

    /**
     * Emit method declaration name range
     * @return Method signature, for referencing method body members
     */
    public String emitMethodRange(String className, PsiMethod psiMethod) {
        String signature = MethodSignatureHelper.makeTypeSignatureString(psiMethod);

        internalEmitMethodRange(psiMethod.getNameIdentifier().getText(),psiMethod.getNameIdentifier().getTextRange(),className,psiMethod.getName(),signature);

        return signature;
    }

    /**
     * Emit method parameter name declaration range
     */
    public void emitParameterRange(String className, String methodName, String methodSignature, PsiParameter psiParameter, int parameterIndex) {
        if (psiParameter == null || psiParameter.getNameIdentifier() == null) {
            return;
        }

        internalEmitParameterRange(psiParameter.getNameIdentifier().getText(),psiParameter.getNameIdentifier().getTextRange(),className,methodName,methodSignature,psiParameter.getName(),parameterIndex);
    }

    /**
     * Emit local variable declaration name range
     * Local variables can occur in methods, initializers, ...
     */
    public void emitLocalVariableRange(String className, String methodName, String methodSignature, PsiVariable psiVariable, int localVariableIndex) {
        internalEmitLocalVariable(psiVariable.getNameIdentifier().getText(),psiVariable.getNameIdentifier().getTextRange(),className,methodName,methodSignature,psiVariable.getName(),localVariableIndex);
    }

    // Referenced names below (symbol uses, as opposed to declarations)


    /**
     * Emit referenced class name range
     */
    public void emitReferencedClass(PsiElement nameElement, PsiClass psiClass) {
        // TODO: null check for broken references
        internalEmitClassRange(nameElement.getText(),nameElement.getTextRange(),psiClass.getQualifiedName());
    }

    /**
     * Emit referenced field name range
     */
    public void emitReferencedField(PsiElement nameElement, PsiField psiField) {
        PsiClass psiClass = psiField.getContainingClass();

        internalEmitFieldRange(nameElement.getText(),nameElement.getTextRange(),psiClass.getQualifiedName(),psiField.getName());
    }

    /**
     * Emit referenced method name range, that is, a method call
     */
    public void emitReferencedMethod(PsiElement nameElement, PsiMethod psiMethodCalled) {
        PsiClass psiClass = psiMethodCalled.getContainingClass();

        internalEmitMethodRange(nameElement.getText(),nameElement.getTextRange(),psiClass.getQualifiedName(),psiMethodCalled.getName(),MethodSignatureHelper.makeTypeSignatureString(psiMethodCalled));
    }

    /**
     * Emit referenced local variable name range
     * This includes both "local variables" declared in methods, and in foreach/catch sections ("parameters")
     */
    public void emitReferencedLocalVariable(PsiElement nameElement, String className, String methodName, String methodSignature, PsiVariable psiVariable, int localVariableIndex) {
        internalEmitLocalVariable(nameElement.getText(),nameElement.getTextRange(),className,methodName,methodSignature,psiVariable.getName(),localVariableIndex);
    }

    /**
     * Emit referenced method parameter name range
     * Only includes _method_ parameters - for foreach/catch parameters see emitReferencedLocalVariable
     */
    public void emitReferencedMethodParameter(PsiElement nameElement, String className, String methodName, String methodSignature, PsiParameter psiParameter, int index) {
        internalEmitParameterRange(nameElement.getText(),nameElement.getTextRange(),className,methodName,methodSignature,psiParameter.getName(),index);
    }

    // Field separator
    private final String FS = "|";

    private String commonFields(String oldText, TextRange textRange) {
        // Include source filename for opening, textual range start/end, and old text for sanity check
        return "@"+FS+sourceFilePath+FS+textRange.getStartOffset()+FS+textRange.getEndOffset()+FS+oldText+FS;
    }


    // Methods to actually write the output
    // Everything goes through these methods

    private void internalEmitPackageRange(String oldText, TextRange textRange, String packageName) {
        System.out.println(commonFields(oldText, textRange)+"package"+FS+packageName);
    }

    private void internalEmitClassRange(String oldText, TextRange textRange, String className) {
        System.out.println(commonFields(oldText, textRange)+"class"+FS+className);
    }

    private void internalEmitFieldRange(String oldText, TextRange textRange, String className, String fieldName) {
        System.out.println(commonFields(oldText, textRange)+"field"+FS+className+FS+fieldName);
    }

    private void internalEmitMethodRange(String oldText, TextRange textRange, String className, String methodName, String methodSignature) {
        System.out.println(commonFields(oldText, textRange)+"method"+FS+className+FS+methodName+FS+methodSignature);
    }


    private void internalEmitParameterRange(String oldText, TextRange textRange, String className, String methodName, String methodSignature, String parameterName, int parameterIndex) {
        System.out.println(commonFields(oldText, textRange)+"param"+FS+className+FS+methodName+FS+methodSignature+FS+parameterName+FS+parameterIndex);
    }

    private void internalEmitLocalVariable(String oldText, TextRange textRange, String className, String methodName, String methodSignature, String variableName, int variableIndex) {
        System.out.println(commonFields(oldText, textRange)+"localvar"+FS+className+FS+methodName+FS+methodSignature+FS+variableName+FS+variableIndex);
    }
}
