package agaricus.applysrg;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ExtractSymbolRangeMapAction extends AnAction {
    public Project project;
    public JavaPsiFacade facade;

    public ExtractSymbolRangeMapAction() {
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
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                // no psi structure for this file
                skippedFiles.add(file);
                continue;
            }

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
        String sourceFilePath = psiJavaFile.getVirtualFile().getPath().replace(project.getBasePath() + "/", "");
        SymbolRangeEmitter emitter = new SymbolRangeEmitter(sourceFilePath);

        System.out.println("processing "+psiJavaFile+" = "+sourceFilePath);

        PsiPackageStatement psiPackageStatement = psiJavaFile.getPackageStatement();
        if (psiPackageStatement != null) {
            emitter.emitPackageRange(psiPackageStatement);
        }

        PsiImportList psiImportList = psiJavaFile.getImportList();
        if (psiImportList != null) {
            PsiImportStatementBase[] psiImportStatements = psiImportList.getAllImportStatements();
            if (psiImportStatements != null) {
                for (PsiImportStatementBase psiImportStatement : psiImportStatements) {
                    emitter.emitImportRange(psiImportStatement);
                }
            }
        }

        PsiClass[] psiClasses = psiJavaFile.getClasses();
        if (psiClasses != null) {
            for (PsiClass psiClass : psiClasses) {
                processClass(emitter, psiClass);
            }
        }
    }

    // Process class extends/implements list and method throws list
    private void processClassReferenceList(SymbolRangeEmitter emitter, PsiReferenceList psiReferenceList) {
        PsiJavaCodeReferenceElement[] psiJavaCodeReferenceElements = psiReferenceList.getReferenceElements();
        PsiClassType[] psiClassTypes = psiReferenceList.getReferencedTypes();
        for (int i = 0; i < psiJavaCodeReferenceElements.length; ++i) {
            emitter.emitReferencedClass(psiJavaCodeReferenceElements[i].getReferenceNameElement(),  psiClassTypes[i].resolve()); // TODO: resolve needed?
        }
    }

    private void processClass(SymbolRangeEmitter emitter, PsiClass psiClass) {
        String className = emitter.emitClassRange(psiClass);

        processClassReferenceList(emitter, psiClass.getExtendsList());
        processClassReferenceList(emitter, psiClass.getImplementsList());

        // Methods and fields in this class (not 'all', which includes superclass)
        PsiField[] psiFields = psiClass.getFields();

        for (PsiField psiField : psiFields) {
            emitter.emitTypeRange(psiField.getTypeElement());
            emitter.emitFieldRange(className, psiField);

            // Initializer can refer to other symbols, so walk it, too
            SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className);
            walker.walk(psiField.getInitializer());
        }

        PsiMethod[] psiMethods = psiClass.getMethods();

        for (PsiMethod psiMethod: psiMethods) {
            processMethod(emitter, className, psiMethod);
        }

        // Class and instance initializers
        if (psiClass.getInitializers() != null) {
            for (PsiClassInitializer psiClassInitializer : psiClass.getInitializers()) {
                // We call class initializers "{}"...
                SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, "{}", "");
                walker.walk(psiClassInitializer.getBody());
            }
        }
    }

    private void processMethod(SymbolRangeEmitter emitter, String className, PsiMethod psiMethod) {
        String methodSignature = emitter.emitMethodRange(className, psiMethod);

        // Return type and throws list
        emitter.emitTypeRange(psiMethod.getReturnTypeElement());
        processClassReferenceList(emitter, psiMethod.getThrowsList());

        // Parameters
        PsiParameterList psiParameterList = psiMethod.getParameterList();
        HashMap<PsiParameter,Integer> parameterIndices = new HashMap<PsiParameter, Integer>();

        if (psiParameterList != null) {
            PsiParameter[] psiParameters = psiParameterList.getParameters();
            for (int parameterIndex = 0; parameterIndex < psiParameters.length; ++parameterIndex) {
                PsiParameter psiParameter = psiParameters[parameterIndex];

                emitter.emitTypeRange(psiParameter.getTypeElement());
                emitter.emitParameterRange(className, psiMethod.getName(), methodSignature, psiParameter, parameterIndex);

                if (psiParameter != null) {
                    // Store index for method body references
                    parameterIndices.put(psiParameter, parameterIndex);
                }
            }
        }

        // Method body
        SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, psiMethod.getName(), methodSignature);

        walker.addMethodParameterIndices(parameterIndices);

        walker.walk(psiMethod.getBody());
    }


    public void actionPerformed(AnActionEvent event) {
        System.out.println("ApplySrg2Source experimental starting");

        project = event.getData(PlatformDataKeys.PROJECT);
        facade = JavaPsiFacade.getInstance(project);

        VirtualFile projectFileDirectory = event.getData(PlatformDataKeys.PROJECT_FILE_DIRECTORY);

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
