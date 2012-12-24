package agaricus.applysrg;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;

public class SymbolExtractorElementVisitor extends PsiRecursiveElementWalkingVisitor {
    @Override
    public void visitElement(final PsiElement element) {
        System.out.println("visit "+element);
    }
}
