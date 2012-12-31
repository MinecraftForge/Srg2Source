package agaricus.srg2source;

import com.intellij.openapi.application.ApplicationManager;
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

            // Psi isn't initialized yet in projectOpened - see discussions at
            // http://devnet.jetbrains.net/message/522053#522053
            // http://devnet.jetbrains.net/message/4882696#4882696
            // so do it later..
            ApplicationManager.getApplication().invokeLater(new LaterRunnable(project));
        }
    }

    private class LaterRunnable implements Runnable {
        private final Project project;

        public LaterRunnable(Project project) {
            this.project = project;
        }

        public void run() {
            System.out.println("project initialized now? " + project.isInitialized());

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
