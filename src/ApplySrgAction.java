import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RenameRefactoring;
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
        PsiDirectory psiDirectory = psiManager.findDirectory(file);

        PsiElement[] psiElements = psiDirectory.getChildren();

        Messages.showMessageDialog(project, "Project file: " + file.getPath() + ", modules: " + modules.length + ", elements=" + psiElements.length, "Information", Messages.getInformationIcon());

        // TODO RenameRefactoring

        /*  TODO: get filename of .srg
        String txt = Messages.showInputDialog(project, "What is your name?", "Input your name", Messages.getQuestionIcon());
        Messages.showMessageDialog(project, "Hello, " + txt + "!\n I am glad to see you.", "Information", Messages.getInformationIcon());
        */

        // TODO: apply
    }
}
