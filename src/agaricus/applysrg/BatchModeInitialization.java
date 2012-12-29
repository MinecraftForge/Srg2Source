package agaricus.applysrg;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

public class BatchModeInitialization implements ApplicationComponent {
    @NotNull
    @Override
    public String getComponentName() {
        return "srg2source";
    }

    @Override
    public void initComponent() {
        System.out.println("INITIALIZING BATCH MODE");
        // TODO
    }

    @Override
    public void disposeComponent() {

    }
}
