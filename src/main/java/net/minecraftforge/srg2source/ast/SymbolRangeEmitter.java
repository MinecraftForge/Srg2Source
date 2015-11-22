package net.minecraftforge.srg2source.ast;

import java.io.PrintWriter;
import java.util.List;

import org.eclipse.jdt.core.dom.*;

@SuppressWarnings("unchecked")
public class SymbolRangeEmitter
{
    private String sourceFilePath;
    private PrintWriter logFile;

    public SymbolRangeEmitter(String sourceFilePath, PrintWriter logFile)
    {
        this.sourceFilePath = sourceFilePath;
        this.logFile = logFile;
    }

    public void emitPackageRange(PackageDeclaration pkg)
    {
        String name = pkg.getName().getFullyQualifiedName();
        //jline|package|jline|(file)
        log(commonFields(name, pkg.getName()) + "package" + FS + name + FS + "(file)");
    }

    public String getSourcePath()
    {
        return sourceFilePath;
    }

    /**
     * Emit range of import statement, including name of the imported
     * package/class
     */
    public void emitImportRange(ImportDeclaration imp)
    {
        // Disabled for now - class references are handled differently in import
        // statements
        // (must be fully qualified...)

        /*
         * PsiJavaCodeReferenceElement psiJavaCodeReferenceElement =
         * psiImportStatement.getImportReference();
         *
         * String qualifiedName =
         * psiJavaCodeReferenceElement.getQualifiedName(); // note, may be
         * package.*?
         * internalEmitClassRange(psiJavaCodeReferenceElement.getText(),
         * psiJavaCodeReferenceElement.getTextRange(), qualifiedName);
         */
    }

    /**
     * Emit class name declaration
     *
     * @return Qualified class name, for referencing class members
     */
    public String emitClassRange(TypeDeclaration clazz)
    {
        SimpleName name = clazz.getName();
        String className = name.getIdentifier();
        String qualified = ((ITypeBinding)name.resolveBinding()).getQualifiedName();
        //AnsiWindowsTerminal|class|jline.AnsiWindowsTerminal
        log(commonFields(className, clazz.getName()) + "class" + FS + qualified);
        return qualified;
    }

    /**
     * Emit type reference element range (This is for when types are used, not
     * declared) Only class name references will be emitted
     */
    public void emitTypeRange(Type type)
    {
        if (type == null)
        {
            return;
        }

        // Go deeper.. reaching inside arrays
        // We want to report e.g. java.lang.String, not java.lang.String[]
        if (type.isArrayType())
        {
            type = ((ArrayType)type).getElementType();
        }

        if (type.isPrimitiveType())
        {
            return; // skip int, etc. - they're never going to be renamed
        }

        if (type.isParameterizedType())
        {
            ParameterizedType p = (ParameterizedType)type;
            for (Type t : (List<Type>)p.typeArguments())
            {
                emitTypeRange(t);
            }
            type = p.getType();
        }

        if (type.isWildcardType())
        {
            emitTypeRange(((WildcardType)type).getBound());
            return;
        }

        if (type.isSimpleType())
        {

            SimpleType stype = (SimpleType)type;
            ITypeBinding bind = stype.getName().resolveTypeBinding();
            if (bind.isTypeVariable()) return; // Don't spit out generic identifier.
            log(commonFields(stype.getName().toString(), stype.getName()) + "class" + FS + bind.getErasure().getQualifiedName());
        }
        else
        {
            System.out.println("ERROR Unknown Type: " + type + type.getClass() + " " + type.getStartPosition() + FS + type.getStartPosition() + type.getLength());
        }
    }

    public void emitFieldRange(VariableDeclarationFragment field, String parent)
    {
        IVariableBinding var = field.resolveBinding();
        String name = var.getName();
        String cls = var.getDeclaringClass().getQualifiedName();
        String init = "";

        if (cls.isEmpty())
            cls = parent;

        //server|field|net.minecraft.server.WorldManager|server
        if (name.equals("__OBFID"))
            init = ((StringLiteral)field.getInitializer()).getLiteralValue();
        log(commonFields(name, field.getName()) + "field" + FS + cls + FS + name + FS + init);
    }

    private IMethodBinding resolveOverrides(IMethodBinding bind)
    {
        if (bind == null)
        {
            return null;
        }

        ITypeBinding clazz = bind.getDeclaringClass();
        if (clazz == null)
        {
            return bind;
        }
        //Cuz screw you CraftBukkit using Object names.
        if (bind.getName().equals("clone") || bind.getName().equals("equals"))
        {
            return bind;
        }

        IMethodBinding tmp = resolveOverrides(bind, clazz.getSuperclass());
        if (tmp != null) bind = tmp;;
        for (ITypeBinding intf : clazz.getInterfaces())
        {
            tmp = resolveOverrides(bind, intf);
            if (tmp != null) bind = tmp;
        }
        return bind.getMethodDeclaration();
    }

    private IMethodBinding resolveOverrides(IMethodBinding bind, ITypeBinding type)
    {
        if (type == null || bind.isConstructor())
        {
            return bind;
        }

        for (IMethodBinding sup : type.getDeclaredMethods())
        {
            if (bind.overrides(sup))
            {
                return resolveOverrides(sup);
            }
        }
        IMethodBinding tmp = resolveOverrides(bind, type.getSuperclass());
        if (tmp == bind)
        {
            for (ITypeBinding intf : type.getInterfaces())
            {
                tmp = resolveOverrides(bind, intf);
                if (tmp != null && tmp != bind)
                {
                    return tmp;
                }
            }
            return bind;
        }
        else
        {
            return tmp;
        }
    }

    public String emitMethodRange(MethodDeclaration method, String className, boolean resolve)
    {
        IMethodBinding bind = method.resolveBinding();

        if (resolve)
        {
            bind = resolveOverrides(bind);
        }

        if (bind == null)
        {
            log("Null method binding! " + method);
            throw new RuntimeException("Null method binding! " + method);
        }
        String signature = MethodSignatureHelper.getSignature(bind);
        String name = method.getName().toString();
        String owner = bind.getDeclaringClass().getQualifiedName();
        if (owner.isEmpty()) owner = className;
        //WorldManager|method|net.minecraft.server.WorldManager|WorldManager|(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/WorldServer;)V
        log(commonFields(name, method.getName()) + "method" + FS + owner + FS + name + FS + signature);

        return signature;
    }

    public String getMethodSignature(MethodDeclaration method, boolean resolve)
    {
        IMethodBinding bind = method.resolveBinding();

        if (resolve)
        {
            bind = resolveOverrides(bind);
        }

        if (bind == null)
        {
            log("Null method binding! " + method);
            throw new RuntimeException("Null method binding! " + method);
        }
        return MethodSignatureHelper.getSignature(bind);
    }

    public void emitParameterRange(MethodDeclaration method, String signature, SingleVariableDeclaration param, int index, String className)
    {
        if (param == null || param.getName() == null)
        {
            return;
        }

        String name = param.getName().getIdentifier();
        IMethodBinding bind = method.resolveBinding();

        //special case this shit cuz it annoys me
        IMethodBinding top = resolveOverrides(bind);
        if (top.getDeclaringClass().getQualifiedName().equals("net.minecraft.server.BlockSapling.TreeGenerator")
            && top.getName().toString().equals("generate"))
        {
            bind = top;
        }

        String owner = bind.getDeclaringClass().getQualifiedName();
        String mName = bind.getName();

        if (owner.isEmpty())
            owner = className;

        //entity|param|net.minecraft.server.WorldManager|a|(Lnet/minecraft/server/Entity;)V|entity|0
        log(commonFields(name, param.getName()) + "param" + FS + owner + FS + mName + FS + signature + FS + name + FS + index);
    }


    public void emitReferencedClass(Name name, ITypeBinding clazz)
    {
        //String|class|java.lang.String
        if (clazz.isTypeVariable()) return;
        log(commonFields(name.toString(), name) + "class" + FS + clazz.getErasure().getQualifiedName());
    }


    public void emitReferencedMethod(Name name, IMethodBinding method, String parent)
    {
        method = resolveOverrides(method);
        String cls = method.getDeclaringClass().getErasure().getQualifiedName();
        if (cls.isEmpty())
            cls = parent;
        //systemInstall|method|org.fusesource.jansi.AnsiConsole|systemInstall|()V
        log(commonFields(name.toString(), name) + "method" +
            FS + cls +
            FS + method.getName() +
            FS + MethodSignatureHelper.getSignature(method));
    }

    public void emitReferencedMethodParameter(Name name, IVariableBinding var, int index, String className)
    {
        IMethodBinding method = var.getDeclaringMethod();

        //special case this shit cuz it annoys me
        IMethodBinding top = resolveOverrides(method);
        if (top.getDeclaringClass().getQualifiedName().equals("net.minecraft.server.BlockSapling.TreeGenerator")
            && top.getName().toString().equals("generate"))
        {
            method = top;
        }
        String owner = method.getDeclaringClass().getQualifiedName();
        if (owner.isEmpty()) owner = className;
        //out|param|jline.AnsiWindowsTerminal|wrapOutIfNeeded|(Ljava/io/OutputStream;)Ljava/io/OutputStream;|out|0
        log(commonFields(name.toString(), name) + "param" +
            FS + owner +
            FS + method.getName() +
            FS + MethodSignatureHelper.getSignature(method) +
            FS + name +
            FS + index);
    }

    public void emitLocalVariableRange(Name name, String className, String methodName, String methodSignature, int index)
    {
        //os|localvar|jline.AnsiWindowsTerminal|wrapOutputStream|(Ljava/io/OutputStream;)Ljava/io/OutputStream;|os|0
        log(commonFields(name.toString(), name) + "localvar" +
            FS + className +
            FS + methodName +
            FS + methodSignature +
            FS + name +
            FS + index);
    }


    public void emitReferencedField(Name name, IVariableBinding field, String className)
    {
        ITypeBinding type = field.getDeclaringClass();
        if (type == null && name.toString().equals("length"))
        {
            log("Field: Array Length, skipping");
            return;
        }

        String owner = field.getDeclaringClass().getQualifiedName();
        if (owner.isEmpty())
        {
            owner = className;
        }

        //ansiSupported|field|jline.AnsiWindowsTerminal|ansiSupported
        log(commonFields(name.toString(), name) + "field" +
            FS + owner +
            FS + field.getName());
    }

/*
    /**
     * Emit type range given a PsiJavaCodeReferenceElement
     * /
    public void emitTypeRange(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement)
    {
        PsiElement referenceNameElement = psiJavaCodeReferenceElement
                .getReferenceNameElement();
        if (!(referenceNameElement instanceof PsiIdentifier))
        {
            log("WARNING: unrecognized reference name element, not identifier: "
                    + referenceNameElement);
            return;
        }
        PsiIdentifier psiIdentifier = (PsiIdentifier) referenceNameElement;

        // Package name, if fully qualified
        emitTypeQualifierRangeIfQualified(psiJavaCodeReferenceElement);

        internalEmitClassRange(psiIdentifier.getText(),
                psiIdentifier.getTextRange(),
                psiJavaCodeReferenceElement.getQualifiedName());

        // Process type parameters, for example, Integer and Boolean in
        // HashMap<Integer,Boolean>
        // for other examples see https://gist.github.com/4370462
        PsiReferenceParameterList psiReferenceParameterList = psiJavaCodeReferenceElement
                .getParameterList();
        for (PsiTypeElement innerTypeElement : psiReferenceParameterList
                .getTypeParameterElements())
        {
            emitTypeRange(innerTypeElement);
        }
    }

    /**
     * Emit type qualifier package name for name, if the element is a fully
     * qualified reference
     * /
    public void emitTypeQualifierRangeIfQualified(
            PsiJavaCodeReferenceElement psiJavaCodeReferenceElement)
    {
        // Get the "deep" parent type name -- without any array brackets, or
        // type parameters -- but, still fully qualified
        String deepTypeName = psiJavaCodeReferenceElement.getQualifiedName();

        if (psiJavaCodeReferenceElement.isQualified())
        {
            // Qualified names are for example:
            // agaricus.applysrg.samplepackage.SampleClass fqClass;
            // \ qualifier / deep type identifier

            if (!(psiJavaCodeReferenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement))
            {
                log("WARNING: unrecognized qualifier element type "
                        + psiJavaCodeReferenceElement.getQualifier());
                return;
            }

            PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement) psiJavaCodeReferenceElement
                    .getQualifier();

            // For qualified names, we need to emit the package, too
            // The deep type name is cross-referenced with the package name for
            // remapping
            internalEmitPackageRange(qualifier.getText(),
                    qualifier.getTextRange(), qualifier.getQualifiedName(),
                    deepTypeName);
        }
    }
*/

    // Field separator
    private final String FS = "|";

    public String commonFields(String oldText, ASTNode textRange)
    {
        // Include source filename for opening, textual range start/end, and old
        // text for sanity check
        return "@" + FS + sourceFilePath
                   + FS + textRange.getStartPosition()
                   + FS + (textRange.getStartPosition() + textRange.getLength())
                   + FS + oldText + FS;
    }

    // Methods to actually write the output
    // Everything goes through these methods

    private String tab = "";
    public void tab(){ tab += "   "; }
    public void untab(){ tab = tab.substring(0, tab.length() - 3); }
    public void log(String s)
    {
        //System.out.println(s);
        if (logFile != null)
        {
            logFile.println(tab + s);
        }
        if (s.contains("||"))
            System.exit(1);
    }

    public void emitThrowRange(Name exc, ITypeBinding type)
    {
        //IOException|class|java.io.IOException
        log(commonFields(exc.getFullyQualifiedName(), exc) + "class" + FS + type.getQualifiedName());
    }
}
