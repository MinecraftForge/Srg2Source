import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class ApplySrgAction extends AnAction {
    public ApplySrgAction() {
        super("Apply Srg");
    }

    public void actionPerformed(AnActionEvent event) {
        // example from http://confluence.jetbrains.net/display/IDEADEV/Getting+Started+with+Plugin+Development
        Project project = event.getData(PlatformDataKeys.PROJECT);
        String txt= Messages.showInputDialog(project, "What is your name?", "Input your name", Messages.getQuestionIcon());
        Messages.showMessageDialog(project, "Hello, " + txt + "!\n I am glad to see you.", "Information", Messages.getInformationIcon());
    }
}
