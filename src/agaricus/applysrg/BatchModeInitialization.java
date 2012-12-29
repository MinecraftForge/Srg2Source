package agaricus.applysrg;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

/**
 * Component to initialize the project listener for batch mode processing
 */

public class BatchModeInitialization implements ApplicationComponent {
    BatchModeProjectListener listener;

    @NotNull
    @Override
    public String getComponentName() {
        return "srg2source";
    }

    @Override
    public void initComponent() {
        listener = new BatchModeProjectListener();

        ProjectManager.getInstance().addProjectManagerListener(listener);
    }

    @Override
    public void disposeComponent() {

    }
}
