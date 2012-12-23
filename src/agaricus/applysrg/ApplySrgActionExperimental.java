package agaricus.applysrg;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.JavaRefactoringFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApplySrgActionExperimental extends AnAction {
    public Project project;
    public JavaPsiFacade facade;

    public ApplySrgActionExperimental() {
        super("Apply Srg");
    }

    public void actionPerformed(AnActionEvent event) {
        project = event.getData(PlatformDataKeys.PROJECT);
        facade = JavaPsiFacade.getInstance(project);

        System.out.println("ApplySrg2Source experimental starting");

        VirtualFile[] files = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        System.out.println("files="+files.length);
        System.out.println("files="+files);
        for(VirtualFile file: files) {
            System.out.println(""+file);
        }

        PsiPackage psiPackage = facade.findPackage("agaricus.applysrg");
        PsiClass[] psiClasses = psiPackage.getClasses();
        System.out.println("psiClasses="+psiClasses);

        for (PsiClass psiClass: psiClasses) {
            System.out.println("* "+psiClass.getQualifiedName());
            PsiMethod[] psiMethods = psiClass.getMethods();
            for (PsiMethod psiMethod: psiMethods) {
                System.out.println("- method: "+psiMethod);

                PsiParameterList psiParameterList = psiMethod.getParameterList();
                PsiParameter[] psiParameters = psiParameterList.getParameters();
                PsiCodeBlock psiCodeBlock = psiMethod.getBody();
                PsiElement element = psiCodeBlock.getFirstBodyElement();
                do {
                    element = element.getNextSibling();
                    System.out.println("-- "+element);
                } while(element != null);
            }
            PsiField[] psiFields = psiClass.getFields();
            for (PsiField psiField: psiFields) {
                System.out.println("- field: "+psiField);
            }
        }

        List<RenamingClass> classes = new ArrayList<RenamingClass>();
        List<RenamingField> fields = new ArrayList<RenamingField>();
        List<RenamingMethod> methods = new ArrayList<RenamingMethod>();
        List<RenamingMethodParametersList> parametersLists = new ArrayList<RenamingMethodParametersList>();

        if (!SrgLoader.promptAndLoadSrg(project, classes, fields, methods, parametersLists))
            return;


    }
}
