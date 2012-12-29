package agaricus.applysrg;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;

import java.io.File;

public class BatchModeProjectListener implements ProjectManagerListener {
    @Override
    public void projectOpened(Project project) {
        String cookiePath = project.getBasePath() + "/" + "srg2source-batchmode";
        File file = new File(cookiePath);
        if (file.exists()) {
            System.out.println("Srg2source batch mode detected");

            System.out.println("project initialized? " + project.isInitialized());

            if (project.isInitialized()) {
                (new ExtractSymbolRangeMapAction()).performAction(project, null, false/*useSelectedFiles*/, true/*batchMode*/);
            }
        }
    }

    @Override
    public boolean canCloseProject(Project project) {
        return true;
    }

    @Override
    public void projectClosed(Project project) {
    }

    @Override
    public void projectClosing(Project project) {
    }
}
