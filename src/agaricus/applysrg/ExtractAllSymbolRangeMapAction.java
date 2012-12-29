package agaricus.applysrg;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ExtractAllSymbolRangeMapAction extends AnAction {
    public ExtractAllSymbolRangeMapAction() {
        super("Extract all");
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        (new ExtractSymbolRangeMapAction()).performAction(event.getProject(), event, false/*useSelectedFiles*/, false/*batchMode*/);
    }
}
