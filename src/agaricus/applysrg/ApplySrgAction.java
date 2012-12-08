package agaricus.applysrg;

import com.intellij.ide.actions.GotoSymbolAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.JavaRenameRefactoring;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.actions.BasePlatformRefactoringAction;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.FileContentUtil;

import java.util.Collection;
import java.util.Set;

public class ApplySrgAction extends AnAction {
    public Project project;
    public JavaPsiFacade facade;
    public JavaRefactoringFactory refactoringFactory;

    public ApplySrgAction() {
        super("Apply Srg");
    }

    public void actionPerformed(AnActionEvent event) {
        project = event.getData(PlatformDataKeys.PROJECT);
        facade = JavaPsiFacade.getInstance(project);
        refactoringFactory = JavaRefactoringFactory.getInstance(project);

        System.out.println("ApplySrgAction performing");

        //PsiManager psiManager = PsiManager.getInstance(project);

        /*  TODO: get filename of .srg
        String txt = Messages.showInputDialog(project, "What is your name?", "Input your name", Messages.getQuestionIcon());
        Messages.showMessageDialog(project, "Hello, " + txt + "!\n I am glad to see you.", "Information", Messages.getInformationIcon());
        */

        if (renameClass("agaricus.applysrg.Sample" + "Class", "Sample" + "Class2"))  {
            Messages.showMessageDialog(project, "Renamed first", "Information", Messages.getInformationIcon());
        } else {
            if (renameClass("agaricus.applysrg.Sample" + "Class2", "Sample" + "Class")) {
                if (!renameField("agaricus.applysrg.Sample" + "Class", "field" + "1", "field" + "2")) {
                    renameField("agaricus.applysrg.Sample" + "Class", "field" + "2", "field" + "1");
                }

                renameMethod("agaricus.applysrg.Sample" + "Class", "a", "()V", "b");

                Messages.showMessageDialog(project, "Renamed second", "Information", Messages.getInformationIcon());
            } else {
                Messages.showMessageDialog(project, "Failed to rename anything!", "Information", Messages.getInformationIcon());
            }
        }
    }

    public boolean renameClass(String oldName, String newName) {
        PsiClass psiClass = facade.findClass(oldName, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            System.out.println("renameClass(" + oldName + " -> " + newName + ") failed, no such class");
            return false;
        }

        return renameElement(psiClass, newName);
    }


    public boolean renameField(String className, String oldName, String newName) {
        PsiClass psiClass = facade.findClass(className, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            System.out.println("renameField(" + className + "/" + oldName + " -> " + newName + ") failed, no such class");
            return false;
        }

        PsiField field = psiClass.findFieldByName(oldName, false);
        if (field == null) {
            System.out.println("renameField(" + className + "/" + oldName + " -> " + newName + ") failed, no such field");
            return false;
        }

        return renameElement(field, newName);
    }

    public boolean renameMethod(String className, String oldName, String signatureString, String newName) {
        PsiClass psiClass = facade.findClass(className, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            System.out.println("renameMethod(" + className + "/" + oldName + " " + signatureString + " -> " + newName + ") failed, no such class");
            return false;
        }

        PsiMethod[] methods = psiClass.findMethodsByName(oldName, false);

        for (PsiMethod method: methods) {
            if (method.isConstructor())
                continue; // constructors are renamed as part of the class

            System.out.println(" method name: " + method.getName());

            MethodSignature thisSignature = method.getSignature(PsiSubstitutor.EMPTY);
            PsiType[] argTypes = thisSignature.getParameterTypes();
            PsiTypeParameter[] typeParameters = thisSignature.getTypeParameters(); // TODO

            for (PsiType argType: argTypes) {
                System.out.println(" arg type presentable " + argType.getPresentableText() + ", internal " + argType.getInternalCanonicalText() + ", canon " + argType.getCanonicalText());
            }

            PsiType returnType = method.getReturnType();
            assert(returnType != null); // only should've been null for constructors
            System.out.println(" return = " + returnType + ", internal " + returnType.getInternalCanonicalText());

            System.out.println(" signature = " + makeTypeSignatureString(method));

            System.out.println("");


            // TODO: check signature
            // TODO: rename
        }


        return true;
    }

    /* Get Java type signature code string from a PsiMethod
    See http://help.eclipse.org/indigo/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fjdt%2Fcore%2FSignature.html
    TODO: is there an existing routine this can be replaced with?
     */
    public String makeTypeSignatureString(PsiMethod method) {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        PsiType[] argTypes = signature.getParameterTypes();
        StringBuilder buf = new StringBuilder();

        buf.append("(");

        for(PsiType argType: argTypes) {
            buf.append(getTypeCodeString(argType));
        }

        buf.append(")");

        PsiType returnType = method.getReturnType();

        buf.append(getTypeCodeString(returnType));

        return buf.toString();
    }

    public String getTypeCodeString(PsiType type)
    {
        if (type instanceof PsiPrimitiveType) {
            PsiPrimitiveType ptype = (PsiPrimitiveType)type;

            if (ptype == PsiType.BYTE) return "B";
            if (ptype == PsiType.CHAR) return "C";
            if (ptype == PsiType.DOUBLE) return "D";
            if (ptype == PsiType.FLOAT) return "F";
            if (ptype == PsiType.INT) return "I";
            if (ptype == PsiType.LONG) return "J";
            if (ptype == PsiType.SHORT) return "S";
            if (ptype == PsiType.VOID) return "V";
            if (ptype == PsiType.BOOLEAN) return "Z";
        }

        if (type instanceof PsiArrayType) {
            PsiArrayType atype = (PsiArrayType)type;
            return "[" + getTypeCodeString(atype.getComponentType());
        }

        // not supported here:
        // T type variable
        // <> optional arguments
        // * wildcard
        // + wildcard extends X
        // - wildcard super X
        // ! capture-of, | intersection type, Q unresolved type


        return "L" + type.getCanonicalText().replaceAll("\\.", "/") + ";";
    }

    public boolean renameElement(PsiElement psiElement, String newName) {
        JavaRenameRefactoring refactoring = refactoringFactory.createRename(psiElement, newName);

        // Rename

        refactoring.setInteractive(null);
        refactoring.setPreviewUsages(false);

        // Instead of calling refactoring.run(), which is interactive (presents a UI asking to accept), do what it
        // does, ourselves - without user intervention.

        UsageInfo[] usages = refactoring.findUsages();
        Ref<UsageInfo[]> ref = Ref.create(usages);
        if (!refactoring.preprocessUsages(ref)) {
            System.out.println("renameElement(" + psiElement + " -> " + newName + ") preprocessing failed - check for collisions. Usages = " + usages);
            Messages.showMessageDialog(project, "Failed to preprocess usages for "+psiElement+" -> "+newName +"- check for collisions", "Information", Messages.getErrorIcon());
            return false;
        }
        refactoring.doRefactoring(usages);

        return true;
    }

}
