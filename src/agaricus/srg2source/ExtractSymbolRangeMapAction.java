package agaricus.srg2source;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class ExtractSymbolRangeMapAction {
    public Project project;
    public JavaPsiFacade facade;
    public PrintWriter logFile;
    public String logFilename;
    public boolean batchMode;

    /** Get list of Java Psi files to process
     *
     * @param event
     * @param useSelectedFiles If true, use user's file selection; otherwise, use all Java files in project
     * @return
     */
    private List<PsiJavaFile> getJavaPsiFiles(AnActionEvent event, boolean useSelectedFiles) {
        List<PsiJavaFile> javaFileList = new ArrayList<PsiJavaFile>();
        PsiManager psiManager = PsiManager.getInstance(project);
        Collection<VirtualFile> chosenFiles;

        if (useSelectedFiles) {
            // Get selected files
            VirtualFile[] selectedFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
            if (selectedFiles == null || selectedFiles.length == 0) {
                String help = "Please select the files you want to transform in the View > Tool Windows > Project view, then try again.";
                log(help);
                if (!batchMode) {
                    Messages.showMessageDialog(project, help, "No selection", Messages.getErrorIcon());
                }
                return null;
            }
            chosenFiles = Arrays.asList(selectedFiles);
        } else {
            // Use all files in project
            chosenFiles = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project));
        }

        log("Chose "+ chosenFiles.size()+" files");
        List<VirtualFile> skippedFiles = new ArrayList<VirtualFile>();
        for(VirtualFile file: chosenFiles) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                // no psi structure for this file
                skippedFiles.add(file);
                continue;
            }

            if (!(psiFile instanceof PsiJavaFile)) {
                log("Skipping non-Java file "+file);
                skippedFiles.add(file);
                // TODO: possibly try to load this file as Java for PSI parsing
                // see http://devnet.jetbrains.net/thread/271253 + https://gist.github.com/4367023
                continue;
            }

            PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;

            javaFileList.add(psiJavaFile);
        }

        if (skippedFiles.size() != 0) {

            StringBuilder sb = new StringBuilder("Non-Java files were selected ("+skippedFiles.size()+" of "+chosenFiles.size()+"): \n\n");
            for (VirtualFile skippedFile: skippedFiles) {
                sb.append("- " + skippedFile.getPresentableName() + "\n");
            }

            if (skippedFiles.size() == chosenFiles.size()) {
                sb.append("\nNo valid Java source files in your project were selected. Please select the files you want to process in View > Tool Windows > Project and try again.");
                log(sb.toString());
                if (!batchMode) {
                    Messages.showMessageDialog(project, sb.toString(), "No Java files selected", Messages.getErrorIcon());
                }
                return null;
            }

            sb.append("\nThe above files will not be processed. Do you want to continue processing the other "+javaFileList.size()+" files?");
            log(sb.toString());
            if (!batchMode) {
                if (Messages.showYesNoDialog(project, sb.toString(), "Skipping non-Java files", Messages.getWarningIcon()) != 0) {
                    return null;
                }
            }
        }

        return javaFileList;
    }

    /**
     * Process all files
     * @return false if an error occurs
     */
    private boolean processFiles(List<PsiJavaFile> psiJavaFiles) {
        for (PsiJavaFile psiJavaFile : psiJavaFiles) {
            if (!processFile(psiJavaFile)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Process a class and any class(es) within
     * @return false if an error occurs
     */
    private boolean processFile(PsiJavaFile psiJavaFile) {
        String base = project.getBasePath().replace('\\', '/') + "/";
        String sourceFilePath = psiJavaFile.getVirtualFile().getCanonicalPath().replace('\\', '/').replace(base, "");
        SymbolRangeEmitter emitter = new SymbolRangeEmitter(sourceFilePath, logFile);

        log("processing "+psiJavaFile+" = "+sourceFilePath);

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
                if (!processClass(emitter, psiClass)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Process class extends/implements list and method throws list
    private void processClassReferenceList(SymbolRangeEmitter emitter, PsiReferenceList psiReferenceList) {
        for (PsiJavaCodeReferenceElement psiJavaCodeReferenceElement : psiReferenceList.getReferenceElements()) {
            emitter.emitTypeRange(psiJavaCodeReferenceElement);
        }
    }

    /**
     * Process a class, including extends/implements list, fields, methods, initializers, and inner falsses
     * @return false if an error occurs
     */
    private boolean processClass(SymbolRangeEmitter emitter, PsiClass psiClass) {
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
            if (!walker.walk(psiField.getInitializer())) {
                return false;
            }
        }

        PsiMethod[] psiMethods = psiClass.getMethods();

        for (PsiMethod psiMethod: psiMethods) {
            if (!processMethod(emitter, className, psiMethod)) {
                return false;
            }
        }

        // Class and instance initializers
        if (psiClass.getInitializers() != null) {
            for (PsiClassInitializer psiClassInitializer : psiClass.getInitializers()) {
                // We call class initializers "{}"...
                SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, "{}", "");
                if (!walker.walk(psiClassInitializer.getBody())) {
                    return false;
                }
            }
        }

        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            if (!processClass(emitter, innerClass)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Process a method, including its type and throws list, parameters, and body with parameters and local variables
     * @return false if an error occurs
     */
    private boolean processMethod(SymbolRangeEmitter emitter, String className, PsiMethod psiMethod) {
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

        if (!walker.walk(psiMethod.getBody())) {
            return false;
        }

        return true;
    }

    public void log(String s) {
        System.out.println(s);
        logFile.println(s);
    }

    public void performAction(Project project, AnActionEvent event, boolean useSelectedFiles, boolean batchMode) {
        this.project = project;
        this.batchMode = batchMode;

        logFilename = project.getBasePath() + "/" + project.getName() + ".rangemap";

        try {
            logFile = new PrintWriter(new BufferedWriter(new FileWriter(logFilename)));
        } catch (IOException ex) {
            ex.printStackTrace();
            if (!batchMode) {
                Messages.showMessageDialog(project, "Failed to open "+logFilename+" for writing: "+ex.getLocalizedMessage(), "File error", Messages.getErrorIcon());
            }
            return;
        }

        log("Symbol range map extraction starting");

        List<PsiJavaFile> psiJavaFiles;

        psiJavaFiles = getJavaPsiFiles(event, useSelectedFiles);
        if (psiJavaFiles == null) {
            return;
        }

        log("Processing "+psiJavaFiles.size()+" files");

        boolean success = processFiles(psiJavaFiles);

        logFile.close();

        if (success) {
            if (!batchMode) {
                Messages.showMessageDialog(project, "Wrote symbol range map to "+logFilename, "Extraction complete", Messages.getInformationIcon());
            }
            done(0);

        } else {
            // Failed - the only current reason for failure is unresolved failures (TODO: update message if new failure modes)
            if (!batchMode) {
                // Note, this occurs in vanilla CB because:
                // If you get this in a bunch of places on in CB on Entity getBukkitEntity() etc. (null referent), its probably
                // IntelliJ getting confused by the Entity class in the server jar added as a library - but overridden
                // in the project. To fix this, replace your jar with the slimmed version at https://github.com/agaricusb/MinecraftRemapping/blob/master/slim-jar.py


                Messages.showMessageDialog(project, "There are unresolved symbol references in this project, unable to continue. Details written to "+logFilename+".\n\n" +
                        "If this occurs with CB, please retry with an updated minecraft-server.jar with the NMS classes CB adds removed.",
                        "Unresolved symbols",
                        Messages.getErrorIcon());
            }
            done(1);
        }
    }

    public void done(int code) {
         if (batchMode) {
            System.out.println("Srg2source batch mode finished - now exiting");
            System.exit(code);
            //ApplicationManager.getApplication().exit(); // may prompt for confirmation
        }
    }
}
