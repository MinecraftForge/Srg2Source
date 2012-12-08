package agaricus.applysrg;

import com.intellij.ide.actions.GotoSymbolAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.JavaRenameRefactoring;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.actions.BasePlatformRefactoringAction;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.FileContentUtil;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

        System.out.println("ApplySrgAction performing");

        // Get filename of .srg from user
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
        FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null);
        VirtualFile[] files = dialog.choose(null, project);
        if (files.length == 0) {
            return;
        }
        VirtualFile file = files[0];

        if (!file.getExtension().equalsIgnoreCase("srg")) {
            Messages.showMessageDialog(project, file.getName() + " is not a .srg file", "Error", Messages.getErrorIcon());
            return;
        }

        List<RenamingClass> classes = new ArrayList<RenamingClass>();
        List<RenamingField> fields = new ArrayList<RenamingField>();
        List<RenamingMethod> methods = new ArrayList<RenamingMethod>();

        try {
            loadSrg(file, classes, fields, methods);
        } catch (IOException e) {
            Messages.showMessageDialog(project, "Failed to load " + file.getName() + ": " + e.getLocalizedMessage(), "Error", Messages.getErrorIcon());
            return;
        }

        System.out.println("Loaded "+classes.size()+" classes, "+fields.size()+" fields, "+methods.size()+" methods");

        int i = 0;
        for (RenamingField field: fields) {
            if (renameField(field.className, field.oldName, field.newName)) {
                System.out.println("Renamed "+field);
            } else {
                System.out.println("FAILED to rename "+field);
            }
        }

        for (RenamingMethod method: methods) {
            if (renameMethod(method.className, method.oldName, method.signature, method.newName)) {
                System.out.println("Renamed "+method);
            } else {
                System.out.println("FAILED to rename "+method);
            }
        }

        for (RenamingClass clazz: classes) {
            if (renameClass(clazz.oldName, clazz.newName)) {
                System.out.println("Renamed "+clazz);
            } else {
                System.out.println("FAILED to rename "+clazz);
            }
        }


        /* test for renaming self
        if (renameClass("agaricus.applysrg.Sample" + "Class", "Sample" + "Class2"))  {
            Messages.showMessageDialog(project, "Renamed first", "Information", Messages.getInformationIcon());
        } else {
            if (renameClass("agaricus.applysrg.Sample" + "Class2", "Sample" + "Class")) {
                if (!renameField("agaricus.applysrg.Sample" + "Class", "field" + "1", "field" + "2")) {
                    renameField("agaricus.applysrg.Sample" + "Class", "field" + "2", "field" + "1");
                }

                if (!renameMethod("agaricus.applysrg.Sample" + "Class", "a", "()V", "b")) { // note: this changes
                    renameMethod("agaricus.applysrg.Sample" + "Class", "b", "()V", "a"); // when renaming! supposed to be a<->b
                }

                Messages.showMessageDialog(project, "Renamed second", "Information", Messages.getInformationIcon());
            } else {
                Messages.showMessageDialog(project, "Failed to rename anything!", "Information", Messages.getInformationIcon());
            }
        }
        */
    }

    void loadSrg(VirtualFile file,
                          List<RenamingClass> classes,
                          List<RenamingField> fields,
                          List<RenamingMethod> methods) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(file.getInputStream());
        BufferedReader reader = new BufferedReader(inputStreamReader);

        do {
            String line = reader.readLine();
            if (line == null) break;

            String[] tokens = line.split(" ");
            if (tokens.length < 3) continue;

            String kind = tokens[0];
            if (kind.equals("CL:")) {
                String oldName = tokens[1];
                String newName = tokens[2];

                classes.add(new RenamingClass(oldName, newName));
            } else if (kind.equals("FD:")) {
                String className = getPathComponent(tokens[1]);
                String oldName = getNameComponent(tokens[1]);

                String newName = getNameComponent(tokens[2]);
                fields.add(new RenamingField(className, oldName, newName));
            } else if (kind.equals("MD:")) {
                String className = getPathComponent(tokens[1]);
                String oldName = getNameComponent(tokens[1]);

                String oldSignature = tokens[2];
                String newName = getNameComponent(tokens[3]);
                //String newSignature = tokens[4]; // unused, changes types but otherwise ignored

                methods.add(new RenamingMethod(className, oldName, oldSignature, newName));
            }
        } while (true);
    }

    /** Get last component of a slash-separated name (symbol name)
     *
     * @param fullName
     * @return Name, for example, "a/b/c" will return "c"
     */
    public static String getNameComponent(String fullName) {
        String[] parts = fullName.split("/");

        return parts[parts.length - 1];
    }

    /** Get the path components of a slash-separated name
     *
     * @param fullName
     * @return Path, for example, "a/b/c" will return "a.b"
     */
    public static String getPathComponent(String fullName) {
        String[] parts = fullName.split("/");

        return StringUtils.join(Arrays.copyOfRange(parts, 0, parts.length - 1), ".");
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

        PsiMethod method = findMethod(psiClass, oldName, signature);
        if (method == null) {
            System.out.println("renameMethod(" + className + "/" + oldName + " " + signature + " -> " + newName + ") failed, no such method");
            return false;
        }

        return renameElement(method, newName);
    }


    public PsiMethod findMethod(PsiClass psiClass, String name, String signature) {
        PsiMethod[] methods = psiClass.findMethodsByName(name, false);

        for (PsiMethod method: methods) {
            if (method.isConstructor())
                continue; // constructors are renamed as part of the class

            System.out.println(" method name: " + method.getName());

            String thisSignature = makeTypeSignatureString(method);

            System.out.println(" signature = " + thisSignature);

            if (signature.equals(thisSignature)) {
                return method;
            }
        }

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

        buf.append(getTypeCodeString(returnType));

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
        Ref<UsageInfo[]> ref = Ref.create(usages);
        if (!refactoring.preprocessUsages(ref)) {
            System.out.println("renameElement(" + psiElement + " -> " + newName + ") preprocessing failed - usages = " + usages);
            Messages.showMessageDialog(project, "Failed to preprocess usages for "+psiElement+" -> "+newName +"- check for collisions", "Information", Messages.getErrorIcon());
            return false;
        }
        refactoring.doRefactoring(usages);

        return true;
    }

}
