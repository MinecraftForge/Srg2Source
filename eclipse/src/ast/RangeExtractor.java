package ast;

import java.io.*;
import java.nio.charset.Charset;
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
    private static String BASE = "D:/CraftBukkitWork/Srg2Source/python/craftbukkit/";
    private static String LIB = "C:/Users/Lex/Desktop/mc_dev_rev/1.4.6-R0.3/libs";
    private static String SRC = BASE + "src/main/java";
    private static PrintWriter logFile = null;
    private static String[] libs = null;
    
    public static void main(String[] args)
    {
        if (args.length != 3)
        {
            System.out.println("Usage: RangeExtract [SourceDir] [LibDir] [OutFile]");
            System.exit(1);
        }
        
        SRC = new File(args[0]).getAbsolutePath();
        if  (!args[1].equalsIgnoreCase("none") && args[1].length() != 0)
        {
            if (args[1].contains(File.pathSeparator))
            {
                libs = args[1].split(File.pathSeparator);
            }
            else
            {
                LIB = new File(args[1]).getAbsolutePath();
                libs = gatherFiles(LIB, ".jar");
            }
        }
        else
        {
            libs = new String[0];
        }
        
        String logFilename = args[2];

        try
        {
            logFile = new PrintWriter(new BufferedWriter(new FileWriter(logFilename)));
        }
        catch (IOException ex) 
        {
            ex.printStackTrace();
            return;
        }

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
    
    private static CompilationUnit createUnit(String name, String data) throws Exception
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
        
        parser.setSource(data.toCharArray());
        return (CompilationUnit)parser.createAST(null);
    }
    
    private static boolean processFile(String path) throws Exception
    {
        String sourceFilePath = path.replace('\\', '/').substring(SRC.length() + 1);
        SymbolRangeEmitter emitter = new SymbolRangeEmitter(sourceFilePath, logFile);
        String data = FileUtils.readFileToString(new File(path), Charset.forName("UTF-8")).replaceAll("\r", "");
        
        CompilationUnit cu = createUnit(path.replace('\\', '/').substring(SRC.length() + 1), data);

        log("processing " + sourceFilePath);

        int[] newCode = getNewCodeRanges(cu, data);
        
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
            if (!processAbstractClass(emitter, type, newCode))
            {
                return false;
            }
        }
        return true;
    }
    
    private static boolean processAbstractClass(SymbolRangeEmitter emitter, AbstractTypeDeclaration type, int[] newCode)
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
            if (!processClass(emitter, (TypeDeclaration)type, newCode))
            {
                return false;
            }
        }
        return true;
    }

    public static boolean processClass(SymbolRangeEmitter emitter, TypeDeclaration clazz, int[] newCode)
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
                SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, newCode);
                if (!walker.walk(frag.getInitializer()))
                {
                    return false;
                }
            }
        }

        for (MethodDeclaration method : clazz.getMethods())
        {
            if (!processMethod(emitter, className, method, newCode))
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
                SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, newCode, "{}", "");
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
                if (!processAbstractClass(emitter, (AbstractTypeDeclaration)body, newCode))
                {
                    return false;
                }
            }
        }

        return true;
    }
    
    public static boolean processMethod(SymbolRangeEmitter emitter, String className, MethodDeclaration method, int[] newCode)
    {
        String methodSignature = emitter.emitMethodRange(method);

        // Return type and throws list
        emitter.emitTypeRange(method.getReturnType2());
        for (Name exc : (List<Name>)method.thrownExceptions())
        {
            emitter.emitThrowRange(exc, (ITypeBinding)exc.resolveBinding());
        }
        
        List<SingleVariableDeclaration> params = (List<SingleVariableDeclaration>)method.parameters();
        HashMap<String, Integer> paramIds = new HashMap<String, Integer>();
        
        for (int x = 0; x < params.size(); x++)
        {
            SingleVariableDeclaration param = params.get(x);
            emitter.emitTypeRange(param.getType());
            emitter.emitParameterRange(method, methodSignature, param, x);
            paramIds.put(param.getName().getIdentifier(), x);
        }

        // Method body
        SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, newCode, method.getName().getIdentifier(), methodSignature);

        walker.setParams(paramIds);

        return walker.walk(method.getBody());
    }
    
    private static int[] getNewCodeRanges(CompilationUnit cu, String data)
    {
        boolean inside = false;
        ArrayList<Integer> ret = new ArrayList<Integer>();
        for (Comment cmt : (List<Comment>)cu.getCommentList())
        {
            String comment = data.substring(cmt.getStartPosition(), cmt.getStartPosition() + cmt.getLength());
            if (cmt.isLineComment())
            {
                String[] words = comment.split(" ");
                if (words.length >= 3)
                {
                    // First word is "//", 
                    // Second is "CraftBukkit", "Spigot", "Forge".., 
                    // Third is "start"/"end"
                    //Sometimes they miss spaces, so check if the beginning is smoshed
                    int idx = ((words[0].startsWith("//") && words[0].length() != 2) ? 1 : 2);
                    String command = words[idx];
                    if (command.equalsIgnoreCase("start"))
                    {
                        ret.add(cmt.getStartPosition());
                        if (inside) System.out.println("Unmatched newcode start: " + cmt.getStartPosition() + ": " + comment);
                        inside = true;
                    }
                    else if (command.equalsIgnoreCase("end"))
                    {
                        ret.add(cmt.getStartPosition());
                        if (!inside) System.out.println("Unmatched newcode end: " + cmt.getStartPosition() + ": " + comment);
                        inside = false;
                    }
                }
            }
            else if (cmt.isBlockComment())
            {
                String[] lines = comment.split("\r?\n");
                for (String line : lines)
                {
                    String[] words = line.trim().split(" ");
                    if (words.length >= 3)
                    {
                        // First word is "/*", 
                        // Second is "CraftBukkit", "Spigot", "Forge".., 
                        // Third is "start"/"end"
                        String command = words[2];
                        if (command.equalsIgnoreCase("start"))
                        {
                            ret.add(cmt.getStartPosition());
                            if (inside) System.out.println("Unmatched newcode start: " + cmt.getStartPosition() + ": " + comment);
                            inside = true;
                        }
                        else if (command.equalsIgnoreCase("end"))
                        {
                            ret.add(cmt.getStartPosition());
                            if (!inside) System.out.println("Unmatched newcode end: " + cmt.getStartPosition() + ": " + comment);
                            inside = false;
                        }
                    }   
                }
            }
                
        }
        
        int[] r = new int[ret.size()];
        for (int x = 0; x < ret.size(); x++)
        {
            r[x] = ret.get(x);
        }
        return r;
    }
}
