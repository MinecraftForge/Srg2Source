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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.JavaRenameRefactoring;
import com.intellij.usageView.UsageInfo;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;

public class ApplySrgAction extends AnAction {
    public Project project;
    public JavaPsiFacade facade;
    public JavaRefactoringFactory refactoringFactory;

    public ApplySrgAction() {
        super("Apply Srg");
    }

    public static int[] baz = null;

    int foo() { throw new NotImplementedException(); }

    public void actionPerformed(AnActionEvent event) {
        project = event.getData(PlatformDataKeys.PROJECT);
        facade = JavaPsiFacade.getInstance(project);
        refactoringFactory = JavaRefactoringFactory.getInstance(project);

        System.out.println("ApplySrg2Source starting");

        // Get filename of .srg from user
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
        FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null);
        VirtualFile[] files = dialog.choose(null, project);
        if (files.length == 0) {
            return;
        }
        VirtualFile file = files[0];

        List<RenamingClass> classes = new ArrayList<RenamingClass>();
        List<RenamingField> fields = new ArrayList<RenamingField>();
        List<RenamingMethod> methods = new ArrayList<RenamingMethod>();
        List<RenamingMethodParametersList> parametersLists = new ArrayList<RenamingMethodParametersList>();

        try {
            SrgLoader.loadSrg(file, classes, fields, methods, parametersLists);
        } catch (IOException e) {
            Messages.showMessageDialog(project, "Failed to load " + file.getName() + ": " + e.getLocalizedMessage(), "Error", Messages.getErrorIcon());
            return;
        }

        System.out.println("Loaded "+fields.size()+" fields, "+methods.size()+" methods, "+parametersLists.size()+" method parameters lists, "+classes.size()+" classes, from " + file.getName());

        int okFields = 0;
        for (RenamingField field: fields) {
            if (renameField(field.className, field.oldName, field.newName)) {
                System.out.println("Renamed "+field);
                okFields += 1;
            } else {
                System.out.println("FAILED to rename "+field);
            }
        }

        int okMethods = 0;
        for (RenamingMethod method: methods) {
            if (renameMethod(method.className, method.oldName, method.signature, method.newName)) {
                System.out.println("Renamed "+method);
                okMethods += 1;
            } else {
                System.out.println("FAILED to rename "+method);
            }
        }

        int okParametersLists = 0;
        for (RenamingMethodParametersList parameters : parametersLists) {
            if (renameParametersList(parameters.className, parameters.methodName, parameters.methodSignature, parameters.newParameterNames)) {
                System.out.println("Renamed "+ parameters);
                okParametersLists += 1;
            } else {
                System.out.println("FAILED to rename "+ parameters);
            }
        }

        int okClasses = 0;
        for (RenamingClass clazz: classes) {
            if (renameClass(clazz.oldName, clazz.newName)) {
                System.out.println("Renamed "+clazz);
                okClasses += 1;
            } else {
                System.out.println("FAILED to rename "+clazz);
            }
        }

        String status = "Renamed "+
                okFields+"/"+fields.size()+" fields, "+
                okMethods+"/"+methods.size()+" methods, " +
                okParametersLists+"/"+ parametersLists.size()+" parameter lists, " +
                okClasses+"/"+classes.size()+" classes";

        System.out.println(status);

        Messages.showMessageDialog(project, status, "Rename complete", Messages.getInformationIcon());
    }

    public boolean renameClass(String oldName, String newName) {
        PsiClass psiClass = facade.findClass(oldName, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            System.out.println("renameClass(" + oldName + " -> " + newName + ") failed, no such class");
            return false;
        }

        return renameElement(psiClass, newName);
    }


    public boolean renameField(String className, String oldName, String newName) {
        PsiClass psiClass = facade.findClass(className, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            System.out.println("renameField(" + className + " " + oldName + " -> " + newName + ") failed, no such class");
            return false;
        }

        PsiField field = psiClass.findFieldByName(oldName, false);
        if (field == null) {
            System.out.println("renameField(" + className + " " + oldName + " -> " + newName + ") failed, no such field");
            return false;
        }

        return renameElement(field, newName);
    }

    public boolean renameMethod(String className, String oldName, String signature, String newName) {
        PsiClass psiClass = facade.findClass(className, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            System.out.println("renameMethod(" + className + "/" + oldName + " " + signature + " -> " + newName + ") failed, no such class");
            return false;
        }

        PsiMethod method = findMethod(psiClass, oldName, signature, false);
        if (method == null) {
            System.out.println("renameMethod(" + className + "/" + oldName + " " + signature + " -> " + newName + ") failed, no such method");
            return false;
        }

        return renameElement(method, newName);
    }

    public boolean renameParametersList(final String className, final String methodName, final String methodSignature, final String[] newParameterNames) {
        // TODO: can these psi nodes be cached for speed? we look them up on every rename!
        long start = System.currentTimeMillis();
        PsiClass psiClass = facade.findClass(className, GlobalSearchScope.allScope(project));
        System.out.println("time,findClass,"+className+","+(System.currentTimeMillis() - start));

        if (psiClass == null) {
            System.out.println("renameParametersList(" + className + "/" + methodName + " " + methodSignature+ " (" +  newParameterNames + ") failed, no such class");
            return false;
        }

        PsiMethod method = findMethod(psiClass, methodName, methodSignature, true); // find method, including constructor methods
        if (method == null) {
            System.out.println("renameParametersList(" + className + "/" + methodName + " " + methodSignature + " (" + newParameterNames + ") failed, no such method");
            return false;
        }

        PsiParameterList psiParameterList = method.getParameterList();

        if (newParameterNames.length != psiParameterList.getParametersCount()) {
            System.out.println("renameParametersList(" + className + "/" + methodName + " " + methodSignature + " (" + newParameterNames + ") failed, rename had " + newParameterNames.length + " parameters, but source had " + psiParameterList.getParametersCount());
            return false;
        }

        final PsiParameter[] psiParameters = psiParameterList.getParameters();

        for (int i = 0; i < psiParameters.length; i += 1) {
            renameElement(psiParameters[i], newParameterNames[i]);
        }

        return true;
    }

    public PsiMethod findMethod(PsiClass psiClass, String name, String signature, boolean allowConstructor) {
        long start = System.currentTimeMillis();
        PsiMethod[] methods = psiClass.findMethodsByName(name, false);

        for (PsiMethod method: methods) {
            if (method.isConstructor() && !allowConstructor)
                continue; // constructor method names are renamed as part of the class

            System.out.println(" method name: " + method.getName());

            String thisSignature = makeTypeSignatureString(method);

            System.out.println(" signature = " + thisSignature);

            if (signature.equals(thisSignature)) {
                System.out.println("time,findMethod,"+psiClass.getName()+","+name+","+signature+","+(System.currentTimeMillis() - start));
                return method;
            }
        }

        System.out.println("time,findMethod,"+psiClass.getName()+","+name+","+signature+","+(System.currentTimeMillis() - start));
        return null;
    }

    /* Get Java type signature code string from a PsiMethod
    See http://help.eclipse.org/indigo/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fjdt%2Fcore%2FSignature.html
    TODO: is there an existing routine this can be replaced with?
     */
    public String makeTypeSignatureString(PsiMethod method) {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        PsiType[] argTypes = signature.getParameterTypes();
        StringBuilder buf = new StringBuilder();

        buf.append("(");

        for(PsiType argType: argTypes) {
            buf.append(getTypeCodeString(argType));
        }

        buf.append(")");

        PsiType returnType = method.getReturnType();

        if (returnType == null) {
            // Constructors have no return type
            // Using void as a syntactic placeholder here - TODO - something better?
            buf.append("V");
        } else {
            buf.append(getTypeCodeString(returnType));
        }

        return buf.toString();
    }

    public String getTypeCodeString(PsiType type)
    {
        if (type instanceof PsiPrimitiveType) {
            PsiPrimitiveType ptype = (PsiPrimitiveType)type;

            if (ptype == PsiType.BYTE) return "B";
            if (ptype == PsiType.CHAR) return "C";
            if (ptype == PsiType.DOUBLE) return "D";
            if (ptype == PsiType.FLOAT) return "F";
            if (ptype == PsiType.INT) return "I";
            if (ptype == PsiType.LONG) return "J";
            if (ptype == PsiType.SHORT) return "S";
            if (ptype == PsiType.VOID) return "V";
            if (ptype == PsiType.BOOLEAN) return "Z";
        }

        if (type instanceof PsiArrayType) {
            PsiArrayType atype = (PsiArrayType)type;
            return "[" + getTypeCodeString(atype.getComponentType());
        }

        // not supported here:
        // T type variable
        // <> optional arguments
        // * wildcard
        // + wildcard extends X
        // - wildcard super X
        // ! capture-of, | intersection type, Q unresolved type


        return "L" + type.getCanonicalText().replaceAll("\\.", "/") + ";";
    }

    public boolean renameElement(PsiElement psiElement, String newName) {
        long start = System.currentTimeMillis();
        JavaRenameRefactoring refactoring = refactoringFactory.createRename(psiElement, newName);

        // Rename

        refactoring.setInteractive(null);
        refactoring.setPreviewUsages(false);

        // While tempting, this is probably more trouble than it is worth - especially with
        // obfuscated names like 'a', which would be translated in comments even though they are
        // perfectly good English words, unrelated to the symbol names. Maybe try with a careful diff.
        refactoring.setSearchInComments(false);

        // Instead of calling refactoring.run(), which is interactive (presents a UI asking to accept), do what it
        // does, ourselves - without user intervention.

        UsageInfo[] usages = refactoring.findUsages();
        /* preprocessUsages checks for conflicts, but presents interactive UI :(
        Ref<UsageInfo[]> ref = Ref.create(usages);
        if (!refactoring.preprocessUsages(ref)) {
            System.out.println("renameElement(" + psiElement + " -> " + newName + ") preprocessing failed - usages = " + usages);
            return false;
        }
        */
        refactoring.doRefactoring(usages);

        System.out.println("time,renameElement,"+psiElement+","+newName+","+(System.currentTimeMillis() - start));

        return true;
    }

}
