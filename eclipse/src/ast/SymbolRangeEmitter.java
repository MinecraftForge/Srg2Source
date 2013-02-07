package ast;

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
            ITypeBinding bind = stype.getName().resolveTypeBinding().getErasure();
            log(commonFields(stype.getName().toString(), stype.getName()) + "class" + FS + bind.getQualifiedName());
        }
        else
        {
            System.out.println("ERROR Unknown Type: " + type + type.getClass() + " " + type.getStartPosition() + FS + type.getStartPosition() + type.getLength());
        }
    }

    public void emitFieldRange(VariableDeclarationFragment field)
    {
        IVariableBinding var = field.resolveBinding();
        String name = var.getName();
        String cls = var.getDeclaringClass().getQualifiedName(); 

        //server|field|net.minecraft.server.WorldManager|server
        log(commonFields(name, field.getName()) + "field" + FS + cls + FS + name);
    }
    
    public String emitMethodRange(MethodDeclaration method)
    {
        IMethodBinding bind = method.resolveBinding();
        String signature = MethodSignatureHelper.getSignature(bind);
        String name = bind.getName();
        String owner = bind.getDeclaringClass().getQualifiedName();
        //WorldManager|method|net.minecraft.server.WorldManager|WorldManager|(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/WorldServer;)V
        log(commonFields(name, method.getName()) + "method" + FS + owner + FS + name + FS + signature);

        return signature;
    }
    
    public void emitParameterRange(MethodDeclaration method, String signature, SingleVariableDeclaration param, int index)
    {
        if (param == null || param.getName() == null)
        {
            return;
        }

        String name = param.getName().getIdentifier();
        IMethodBinding bind = method.resolveBinding();
        String owner = bind.getDeclaringClass().getQualifiedName();
        String mName = bind.getName();

        //entity|param|net.minecraft.server.WorldManager|a|(Lnet/minecraft/server/Entity;)V|entity|0
        log(commonFields(name, param.getName()) + "param" + FS + owner + FS + mName + FS + signature + FS + name + FS + index);
    }
    

    public void emitReferencedClass(Name name, ITypeBinding clazz)
    {
        //String|class|java.lang.String
        log(commonFields(name.toString(), name) + "class" + FS + clazz.getErasure().getQualifiedName());
    }
    

    public void emitReferencedMethod(Name name, IMethodBinding method)
    {
        //systemInstall|method|org.fusesource.jansi.AnsiConsole|systemInstall|()V
        log(commonFields(name.toString(), name) + "method" + 
            FS + method.getDeclaringClass().getErasure().getQualifiedName() + 
            FS + method.getName() + 
            FS + MethodSignatureHelper.getSignature(method));
    }

    public void emitReferencedMethodParameter(Name name, IVariableBinding var, int index)
    {
        IMethodBinding method = var.getDeclaringMethod();
        //out|param|jline.AnsiWindowsTerminal|wrapOutIfNeeded|(Ljava/io/OutputStream;)Ljava/io/OutputStream;|out|0
        log(commonFields(name.toString(), name) + "param" + 
            FS + method.getDeclaringClass().getQualifiedName() + 
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
    

    public void emitReferencedField(Name name, IVariableBinding field)
    {
        ITypeBinding type = field.getDeclaringClass();
        if (type == null && name.toString().equals("length"))
        {
            log("Field: Array Length, skipping");
            return;
        }
        //ansiSupported|field|jline.AnsiWindowsTerminal|ansiSupported
        log(commonFields(name.toString(), name) + "field" + 
            FS + field.getDeclaringClass().getQualifiedName() + 
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

    private String commonFields(String oldText, ASTNode textRange)
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

    private void internalEmitPackageRange(String oldText, ASTNode textRange,
            String packageName, String typeUsedBy)
    {
        log(commonFields(oldText, textRange) + "package" + FS + packageName
                + FS + typeUsedBy);
    }

    private void internalEmitClassRange(String oldText, ASTNode textRange, String className)
    {
        log(commonFields(oldText, textRange) + "class" + FS + className);
    }

    private void internalEmitFieldRange(String oldText, ASTNode textRange, String className, String fieldName)
    {
        log(commonFields(oldText, textRange) + "field" + FS + className + FS + fieldName);
    }

    private void internalEmitMethodRange(String oldText, ASTNode textRange,
            String className, String methodName, String methodSignature)
    {
        log(commonFields(oldText, textRange) + "method" + FS + className + FS
                + methodName + FS + methodSignature);
    }

    private void internalEmitParameterRange(String oldText,
            ASTNode textRange, String className, String methodName,
            String methodSignature, String parameterName, int parameterIndex)
    {
        log(commonFields(oldText, textRange) + "param" + FS + className + FS
                + methodName + FS + methodSignature + FS + parameterName + FS
                + parameterIndex);
    }

    private void internalEmitLocalVariable(String oldText, ASTNode textRange,
            String className, String methodName, String methodSignature,
            String variableName, int variableIndex)
    {
        log(commonFields(oldText, textRange) + "localvar" + FS + className + FS
                + methodName + FS + methodSignature + FS + variableName + FS
                + variableIndex);
    }

    public void log(String s)
    {
        //System.out.println(s);
        if (logFile != null)
        {
            logFile.println(s);
        }
    }

    public void emitThrowRange(Name exc, ITypeBinding type)
    {
        //IOException|class|java.io.IOException
        log(commonFields(exc.getFullyQualifiedName(), exc) + "class" + FS + type.getQualifiedName());
    }
}
