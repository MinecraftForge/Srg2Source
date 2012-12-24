package agaricus.applysrg;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

public class ApplySrgActionExperimental extends AnAction {
    public Project project;
    public JavaPsiFacade facade;

    public ApplySrgActionExperimental() {
        super("Apply Srg");
    }

    /** Get list of Java files selected by the user
     *
     * @param event
     * @return List of Java files with PSI ready to process
     */
    private List<PsiJavaFile> getSelectedJavaFiles(AnActionEvent event) {
        List<PsiJavaFile> javaFileList = new ArrayList<PsiJavaFile>();
        PsiManager psiManager = PsiManager.getInstance(project);

        // Get selected files
        VirtualFile[] selectedFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        if (selectedFiles == null || selectedFiles.length == 0) {
            Messages.showMessageDialog(project, "Please select the files you want to transform in the View > Tool Windows > Project view, then try again.", "No selection", Messages.getErrorIcon());
            return null;
        }
        System.out.println("Selected "+ selectedFiles.length+" files");
        List<VirtualFile> skippedFiles = new ArrayList<VirtualFile>();
        for(VirtualFile file: selectedFiles) {
            System.out.println("- " + file);
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                // no psi structure for this file
                skippedFiles.add(file);
                continue;
            }

            System.out.println("psiFile: " + psiFile);

            if (!(psiFile instanceof PsiJavaFile)) {
                System.out.println("Skipping non-Java file "+file);
                skippedFiles.add(file);
                // TODO: possibly try to load this file as Java for PSI parsing
                // see http://devnet.jetbrains.net/thread/271253 + https://gist.github.com/4367023
                continue;
            }

            PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;

            javaFileList.add(psiJavaFile);
        }

        if (skippedFiles.size() != 0) {

            StringBuilder sb = new StringBuilder("Non-Java files were selected ("+skippedFiles.size()+" of "+selectedFiles.length+"): \n\n");
            for (VirtualFile skippedFile: skippedFiles) {
                sb.append("- " + skippedFile.getPresentableName() + "\n");
            }

            if (skippedFiles.size() == selectedFiles.length) {
                sb.append("\nNo valid Java source files in your project were selected. Please select the files you want to process in View > Tool Windows > Project and try again.");
                Messages.showMessageDialog(project, sb.toString(), "No Java files selected", Messages.getErrorIcon());
                return null;
            }

            sb.append("\nThe above files will not be processed. Do you want to continue processing the other "+javaFileList.size()+" files?");

            if (Messages.showYesNoDialog(project, sb.toString(), "Skipping non-Java files", Messages.getWarningIcon()) != 0) {
                return null;
            }
        }

        return javaFileList;
    }

    private void processFile(PsiJavaFile psiJavaFile) {
        System.out.println("processing "+psiJavaFile);

        PsiPackageStatement psiPackageStatement = psiJavaFile.getPackageStatement();
        if (psiPackageStatement != null) {
            System.out.println("@,"+psiPackageStatement.getPackageReference().getTextRange()+",package,"+psiPackageStatement.getPackageName());
            //TODO psiJavaFile.setPackageName("");
        }

        PsiImportList psiImportList = psiJavaFile.getImportList();

        PsiImportStatementBase[] psiImportStatements = psiImportList.getAllImportStatements();
        for (PsiImportStatementBase psiImportStatement : psiImportStatements) {
            PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = psiImportStatement.getImportReference();

            String qualifiedName = psiJavaCodeReferenceElement.getQualifiedName();
            System.out.println("@,"+psiJavaCodeReferenceElement.getTextRange()+",import,"+qualifiedName);
            // TODO: replace through map
        }


        PsiClass[] psiClasses = psiJavaFile.getClasses();
        for (PsiClass psiClass : psiClasses) {
            processClass(psiClass);
        }
    }

    private void processClass(PsiClass psiClass) {
        String className = psiClass.getQualifiedName();
        System.out.println("@,"+psiClass.getNameIdentifier().getTextRange()+",class,"+className);

        // Methods and fields in this class (not 'all', which includes superclass)

        PsiField[] psiFields = psiClass.getFields();

        for (PsiField psiField : psiFields) {
            PsiTypeElement psiTypeElement = psiField.getTypeElement();
            System.out.println("@,"+psiTypeElement.getTextRange()+",type,"+psiTypeElement.getType().getInternalCanonicalText());
            System.out.println("@,"+psiField.getNameIdentifier().getTextRange()+",field,"+className+","+psiField.getName());
            //TODO psiField.setName("");
        }

        PsiMethod[] psiMethods = psiClass.getMethods();

        for (PsiMethod psiMethod: psiMethods) {
            processMethod(className, psiMethod);
        }
    }

    private void processMethod(String className, PsiMethod psiMethod) {
        System.out.println("@,"+psiMethod.getNameIdentifier().getTextRange()+",method,"+className+","+psiMethod.getName());

        PsiParameterList psiParameterList = psiMethod.getParameterList();
        PsiParameter[] psiParameters = psiParameterList.getParameters();
        for (int pindex = 0; pindex < psiParameters.length; ++pindex) {
            PsiParameter psiParameter = psiParameters[pindex];
            PsiTypeElement psiTypeElement = psiParameter.getTypeElement();

            System.out.println("@,"+psiTypeElement.getTextRange()+",type,"+className+","+psiMethod.getName()+","+pindex+","+psiTypeElement.getType().getInternalCanonicalText());
            System.out.println("@,"+psiParameter.getNameIdentifier().getTextRange()+",methodparam,"+className+","+psiMethod.getName()+","+pindex+","+psiParameter.getName());
        }

        PsiCodeBlock psiCodeBlock = psiMethod.getBody();

        walk(className, psiMethod, psiCodeBlock);

        /*
        PsiStatement[] psiStatements = psiCodeBlock.getStatements();
        for (PsiStatement psiStatement : psiStatements) {
        }
        */
    }

    private void walk(String className, PsiMethod psiMethod, PsiElement psiElement) {
        System.out.println("walking "+className+" "+psiMethod.getName()+" -- "+psiElement);

        for (PsiElement child: psiElement.getChildren()) {
            walk(className, psiMethod, child);
        }
    }


    public void actionPerformed(AnActionEvent event) {
        System.out.println("ApplySrg2Source experimental starting");

        project = event.getData(PlatformDataKeys.PROJECT);
        facade = JavaPsiFacade.getInstance(project);

        VirtualFile projectFileDirectory = event.getData(PlatformDataKeys.PROJECT_FILE_DIRECTORY);
        System.out.println("project file directory = "+projectFileDirectory);

        List<PsiJavaFile> psiJavaFiles = getSelectedJavaFiles(event);
        if (psiJavaFiles == null) {
            return;
        }
        System.out.println("Processing "+psiJavaFiles.size()+" files");


        for (PsiJavaFile psiJavaFile: psiJavaFiles) {
            processFile(psiJavaFile);
        }


        /*
        PsiPackage psiPackage = facade.findPackage("agaricus.applysrg.samplepackage");

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
        }*/

        List<RenamingClass> classes = new ArrayList<RenamingClass>();
        List<RenamingField> fields = new ArrayList<RenamingField>();
        List<RenamingMethod> methods = new ArrayList<RenamingMethod>();
        List<RenamingMethodParametersList> parametersLists = new ArrayList<RenamingMethodParametersList>();

        if (!SrgLoader.promptAndLoadSrg(project, classes, fields, methods, parametersLists))
            return;


    }
}
