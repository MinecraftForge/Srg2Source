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

    public ApplySrgAction() {
        super("Apply Srg");
    }

    public void actionPerformed(AnActionEvent event) {
        project = event.getData(PlatformDataKeys.PROJECT);
        facade = JavaPsiFacade.getInstance(project);

        //PsiManager psiManager = PsiManager.getInstance(project);

        /*  TODO: get filename of .srg
        String txt = Messages.showInputDialog(project, "What is your name?", "Input your name", Messages.getQuestionIcon());
        Messages.showMessageDialog(project, "Hello, " + txt + "!\n I am glad to see you.", "Information", Messages.getInformationIcon());
        */

        if (renameClass("agaricus.applysrg.Sample" + "Class", "Sample" + "Class2"))  {
            Messages.showMessageDialog(project, "Renamed first", "Information", Messages.getInformationIcon());
        } else {
            if (renameClass("agaricus.applysrg.Sample" + "Class2", "Sample" + "Class")) {
                Messages.showMessageDialog(project, "Renamed second", "Information", Messages.getInformationIcon());
            } else {
                Messages.showMessageDialog(project, "Failed to rename anything!", "Information", Messages.getInformationIcon());
            }
        }
    }


     public boolean renameClass(String oldName, String newName) {
        JavaRefactoringFactory refactoringFactory = JavaRefactoringFactory.getInstance(project);

        PsiClass psiClass = facade.findClass(oldName, GlobalSearchScope.allScope(project));

        if (psiClass == null)
            return false;


        JavaRenameRefactoring refactoring = refactoringFactory.createRename(psiClass, newName);


        // Rename

        refactoring.setInteractive(null);
        refactoring.setPreviewUsages(false);

        // Instead of calling refactoring.run(), which is interactive (presents a UI asking to accept), do what it
        // does, ourselves - without user intervention.

        UsageInfo[] usages = refactoring.findUsages();
        Ref<UsageInfo[]> ref = Ref.create(usages);
        if (!refactoring.preprocessUsages(ref)) {
            Messages.showMessageDialog(project, "Failed to preprocess usages - check for collisions", "Information", Messages.getErrorIcon());
            return false;
        }
        refactoring.doRefactoring(usages);

        return true;
     }


}
