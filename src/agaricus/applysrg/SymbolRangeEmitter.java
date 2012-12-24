package agaricus.applysrg;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiPackageStatement;

public class SymbolRangeEmitter {
    String sourceFilePath;

    public SymbolRangeEmitter(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
    }

    public void emitPackageRange(PsiPackageStatement psiPackageStatement) {
        System.out.println("@,"+sourceFilePath+","+psiPackageStatement.getPackageReference().getTextRange()+",package,"+psiPackageStatement.getPackageName());
    }
}
