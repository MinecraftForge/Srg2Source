package agaricus.applysrg;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ExtractSelectedSymbolRangeMapAction extends AnAction {
    public ExtractSelectedSymbolRangeMapAction() {
        super("Extract selected");
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        (new ExtractSymbolRangeMapAction()).performAction(event, true/*useSelectedFiles*/, false/*batchMode*/);
    }
}
