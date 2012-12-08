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
    public ApplySrgAction() {
        super("Apply Srg");
    }

    public void actionPerformed(AnActionEvent event) {
        Project project = event.getData(PlatformDataKeys.PROJECT);

        Module[] modules = ModuleManager.getInstance(project).getModules();

        VirtualFile file = project.getBaseDir();


        PsiManager psiManager = PsiManager.getInstance(project);

        JavaRefactoringFactory refactoringFactory = JavaRefactoringFactory.getInstance(project);

        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

        PsiClass psiClass = facade.findClass("agaricus.applysrg.SampleClass2", GlobalSearchScope.allScope(project));

        Messages.showMessageDialog(project, "Found class: "+psiClass, "Information", Messages.getInformationIcon());

        JavaRenameRefactoring refactoring = refactoringFactory.createRename(psiClass, "SampleClass3");


        // Rename

        refactoring.setInteractive(null);
        refactoring.setPreviewUsages(false);

        // Instead of calling refactoring.run(), which is interactive (presents a UI asking to accept), do what it
        // does, ourselves - without user intervention.

        UsageInfo[] usages = refactoring.findUsages();
        Ref<UsageInfo[]> ref = Ref.create(usages);
        if (!refactoring.preprocessUsages(ref)) {
            Messages.showMessageDialog(project, "Failed to preprocess usages - check for collisions", "Information", Messages.getErrorIcon());
            return;
        }
        refactoring.doRefactoring(usages);

        // PsiMigration - Refactor > Migrate..., but only maps classes and packages (not fields or methods)
        // PsiElementVisitor
        // BaseJavaLocalInspectionTool


        // GotoSymbolAction
        // ReferencesSearch
        // RenameRefactoring

        /*  TODO: get filename of .srg
        String txt = Messages.showInputDialog(project, "What is your name?", "Input your name", Messages.getQuestionIcon());
        Messages.showMessageDialog(project, "Hello, " + txt + "!\n I am glad to see you.", "Information", Messages.getInformationIcon());
        */

        // TODO: apply
    }
}
