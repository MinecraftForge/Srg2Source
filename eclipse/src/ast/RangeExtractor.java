package ast;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

@SuppressWarnings("unchecked")
public class RangeExtractor
{
    private static final String BASE = "C:/Users/Lex/Desktop/mc_dev_rev/1.4.6-R0.3/";
    private static final String LIB = BASE + "libs";
    private static final String SRC = BASE + "src";
    private static PrintWriter logFile = null;
    private static String[] libs = null;
    
    public static void main(String[] args)
    {
        String logFilename = "extracted" + ".rangemap";

        try
        {
            logFile = new PrintWriter(new BufferedWriter(new FileWriter(logFilename)));
        }
        catch (IOException ex) 
        {
            ex.printStackTrace();
            return;
        }
        
        libs = gatherFiles(LIB, ".jar");

        log("Symbol range map extraction starting");

        String[] files = gatherFiles(SRC, ".java");
        log("Processing " + files.length +" files");

        if (files.length == 0)
        {
            return;
        }

        boolean success = false;
        
        try
        {
            success = processFiles(files);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        logFile.close();

        System.out.println("Srg2source batch mode finished - now exiting");
        if (!success)
        {
            System.exit(1);
        }
    }
    
    public static void log(String s)
    {
        System.out.println(s);
        logFile.println(s);
    }
    
    public static String[] gatherFiles(String path, String filter)
    {
        Collection<File> ret = FileUtils.listFiles(new File(path), new SuffixFileFilter(filter), DirectoryFileFilter.DIRECTORY);
        ArrayList<String> names = new ArrayList<String>();
        for (File f : ret)
        {
            names.add(f.getAbsolutePath());
        }
        return names.toArray(new String[names.size()]);
    }
    

    private static boolean processFiles(String[] files) throws Exception
    {
        for (String file : files)
        {
            if (!processFile(file))
            {
                return false;
            }
        }
        return true;
    }
    
    @SuppressWarnings("unchecked")
    private static CompilationUnit createUnit(String name, File file) throws Exception
    {
        ASTParser parser = ASTParser.newParser(AST.JLS4);        
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        Hashtable<String, String> options = JavaCore.getDefaultOptions();
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_6);
        parser.setCompilerOptions(options);
        parser.setUnitName(name);
        parser.setEnvironment(libs, new String[]{SRC}, null, true);
        
        parser.setSource(FileUtils.readFileToString(file).toCharArray());
        return (CompilationUnit)parser.createAST(null);
    }
    
    private static boolean processFile(String path) throws Exception
    {
        String sourceFilePath = path.replace('\\', '/').substring(BASE.length() + 1);
        SymbolRangeEmitter emitter = new SymbolRangeEmitter(sourceFilePath, logFile);
        
        CompilationUnit cu = createUnit(path.replace('\\', '/').substring(SRC.length() + 1), new File(path));

        log("processing " + sourceFilePath);

        PackageDeclaration pkg = cu.getPackage();
        if (pkg != null)
        {
            emitter.emitPackageRange(pkg);
        }

        List<ImportDeclaration> imports = cu.imports();
        for (ImportDeclaration imp : imports)
        {
            emitter.emitImportRange(imp);
        }

        List<AbstractTypeDeclaration> types = (List<AbstractTypeDeclaration>)cu.types();
        for (AbstractTypeDeclaration type : types)
        {
            if (!processAbstractClass(emitter, type))
            {
                return false;
            }
        }
        return true;
    }
    
    private static boolean processAbstractClass(SymbolRangeEmitter emitter, AbstractTypeDeclaration type)
    {
        if (type instanceof AnnotationTypeDeclaration)
        {
            log("Annotation!");
        }
        else if (type instanceof EnumDeclaration)
        {
            log("ENUM!");
        }
        else if (type instanceof TypeDeclaration)
        {
            if (!processClass(emitter, (TypeDeclaration)type))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean processClass(SymbolRangeEmitter emitter, TypeDeclaration clazz)
    {
        String className = emitter.emitClassRange(clazz);
        
        if (clazz.getSuperclassType() != null)
        {
            emitter.emitTypeRange(clazz.getSuperclassType());
        }
        
        for (Type i : (List<Type>)clazz.superInterfaceTypes())
        {
            emitter.emitTypeRange(i);
        }

        // Methods and fields in this class (not 'all', which includes superclass)
        FieldDeclaration[] fields = clazz.getFields();
        for (FieldDeclaration field : fields)
        {
            emitter.emitTypeRange(field.getType());

            for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>)field.fragments())
            {
                emitter.emitFieldRange(frag);
                // Initializer can refer to other symbols, so walk it, too
                SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className);
                if (!walker.walk(frag.getInitializer()))
                {
                    return false;
                }
            }
        }

        for (MethodDeclaration method : clazz.getMethods())
        {
            if (!processMethod(emitter, className, method))
            {
                return false;
            }
        }

        // static initializers
        for (BodyDeclaration body : (List<BodyDeclaration>)clazz.bodyDeclarations())
        {
            if (body instanceof Initializer)
            {
                Initializer init = (Initializer)body;
                SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, "{}", "");
                if (!walker.walk(init.getBody()))
                {
                    return false;
                }
            }
        }


        //Inner classes
        for (BodyDeclaration body : (List<BodyDeclaration>)clazz.bodyDeclarations())
        {
            if (body instanceof AbstractTypeDeclaration)
            {
                if (!processAbstractClass(emitter, (AbstractTypeDeclaration)body))
                {
                    return false;
                }
            }
        }

        return true;
    }
    
    private static boolean processMethod(SymbolRangeEmitter emitter, String className, MethodDeclaration method)
    {
        String methodSignature = emitter.emitMethodRange(method);

        // Return type and throws list
        emitter.emitTypeRange(method.getReturnType2());
        for (Name exc : (List<Name>)method.thrownExceptions())
        {
            emitter.emitThrowRange(exc, (ITypeBinding)exc.resolveBinding());
        }
        
        List<SingleVariableDeclaration> params = (List<SingleVariableDeclaration>)method.parameters();
        HashMap<SingleVariableDeclaration, Integer> paramIds = new HashMap<SingleVariableDeclaration, Integer>();
        
        for (int x = 0; x < params.size(); x++)
        {
            SingleVariableDeclaration param = params.get(x);
            emitter.emitTypeRange(param.getType());
            emitter.emitParameterRange(method, methodSignature, param, x);
            paramIds.put(param, x);
        }

        // Method body
        SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, method.getName().getIdentifier(), methodSignature);

        walker.addMethodParameterIndices(paramIds);

        return walker.walk(method.getBody());
    }

}
