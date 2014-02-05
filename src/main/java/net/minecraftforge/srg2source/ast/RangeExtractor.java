package net.minecraftforge.srg2source.ast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

@SuppressWarnings("unchecked")
public class RangeExtractor
{
    private static PrintWriter logger;

    public static void main(String[] args) throws IOException
    {
        if (args.length != 3)
        {
            System.out.println("Usage: RangeExtract [SourceDir] [LibDir] [OutFile]");
            System.exit(1);
        }

        File srcRoot = new File(args[0]);
        String[] libs; // classpath
        File outFile = new File(args[2]);

        if (args[1].equals("none") || args[1].isEmpty())
        {
            libs = gatherFiles(srcRoot.getAbsolutePath(), ".jar", false);
        }
        else
        {
            if (args[1].contains(File.pathSeparator))
            {
                libs = args[1].split(File.pathSeparator);
            }
            else
            {
                libs = gatherFiles(args[1], ".jar", false);
            }
        }

        logger = new PrintWriter(new BufferedWriter(new FileWriter(outFile)));

        log("Symbol range map extraction starting");

        String[] files = gatherFiles(srcRoot.getAbsolutePath(), ".java", true);
        log("Processing " + files.length + " files");

        if (files.length == 0)
        {
            logger.close();
            return;
        }

        boolean success = false;

        try
        {
            success = processFiles(files, srcRoot, logger, libs);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        logger.close();

        System.out.println("Srg2source batch mode finished - now exiting");
        if (!success)
        {
            System.exit(1);
        }
    }

    private static void log(String s)
    {
        log(s, logger);
    }

    private static void log(String s, PrintWriter out)
    {
        System.out.println(s);
        out.println(s);
    }

    /**
     * 
     * @param path Absolute directory path
     * @param filter *.java or some similair filter
     * @param relative whether or not the output paths should be relative
     * @return
     */
    public static String[] gatherFiles(String path, String filter, boolean relative)
    {
        ArrayList<String> names = new ArrayList<String>();
        for (File f : new File(path).listFiles())
        {
            if (f.isDirectory())
            {
                if (relative)
                    names.addAll(gatherFiles(f.getAbsolutePath(), path.length()+1, filter));
                else
                    names.addAll(Arrays.asList(gatherFiles(f.getAbsolutePath(), filter, relative)));
            }
            else if (f.getName().endsWith(filter))
            {
                if (relative)
                    names.add(f.getAbsolutePath().substring(path.length()+1).replace('\\', '/'));
                else
                    names.add(f.getAbsolutePath().replace('\\', '/'));
            }
        }
        return names.toArray(new String[names.size()]);
    }
    
    private static List<String> gatherFiles(String path, int cut, String filter)
    {
        ArrayList<String> names = new ArrayList<String>();
        for (File f : new File(path).listFiles())
        {
            if (f.isDirectory())
            {
                names.addAll(gatherFiles(f.getPath(), cut, filter));
            }
            else if (f.getName().endsWith(filter))
            {
                names.add(f.getAbsolutePath().substring(cut).replace('\\', '/'));
            }
        }
        return names;
    }

    /**
     * @param files Array of relative file paths, with '/' as the seperator
     * @param inStream Input stream of the file.
     * @param writer Writer for the output range map
     * @param libs Classpath entries
     * @return
     * @throws Exception
     */
    public static boolean processFiles(String[] files, File srcRoot, PrintWriter writer, String[] libs) throws Exception
    {
        for (String file : files)
        {
            InputStream stream = Files.newInputStreamSupplier(new File(srcRoot, file)).getInput();
            boolean worked = processFile(file, srcRoot.getAbsolutePath(), stream, writer, libs);

            if (!worked)
                return false;
        }
        return true;
    }

    public static CompilationUnit createUnit(String name, String data, String srcRoot, String[] libs) throws Exception
    {
        ASTParser parser = ASTParser.newParser(AST.JLS4);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        Hashtable<String, String> options = JavaCore.getDefaultOptions();
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_6);
        parser.setCompilerOptions(options);
        parser.setUnitName(name);
        parser.setEnvironment(libs, new String[] {srcRoot}, null, true);

        parser.setSource(data.toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }

    /**
     * @param path File path relative to the srcRoot. Should only contain '/'
     * @param inStream Input stream of the file.
     * @param writer Writer for the output range map
     * @param libs Classpath entries
     * @return
     * @throws Exception
     */
    public static boolean processFile(String path, String root, InputStream inStream, PrintWriter writer, String[] libs) throws Exception
    {
        SymbolRangeEmitter emitter = new SymbolRangeEmitter(path, writer);
        String data = new String(ByteStreams.toByteArray(inStream), Charset.forName("UTF-8")).replaceAll("\r", "");

        CompilationUnit cu = createUnit(path, data, root, libs);

        log("processing " + path, writer);

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

        List<AbstractTypeDeclaration> types = (List<AbstractTypeDeclaration>) cu.types();
        for (AbstractTypeDeclaration type : types)
        {
            if (!processAbstractClass(emitter, type, newCode, writer))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean processAbstractClass(SymbolRangeEmitter emitter, AbstractTypeDeclaration type, int[] newCode, PrintWriter writer)
    {
        if (type instanceof AnnotationTypeDeclaration)
        {
            log("Annotation!", writer);
        }
        else if (type instanceof EnumDeclaration)
        {
            log("ENUM!", writer);
        }
        else if (type instanceof TypeDeclaration)
        {
            if (!processClass(emitter, (TypeDeclaration) type, newCode, writer))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean processClass(SymbolRangeEmitter emitter, TypeDeclaration clazz, int[] newCode, PrintWriter writer)
    {
        String className = emitter.emitClassRange(clazz);

        if (clazz.getSuperclassType() != null)
        {
            emitter.emitTypeRange(clazz.getSuperclassType());
        }

        for (Type i : (List<Type>) clazz.superInterfaceTypes())
        {
            emitter.emitTypeRange(i);
        }

        // Methods and fields in this class (not 'all', which includes superclass)
        FieldDeclaration[] fields = clazz.getFields();
        for (FieldDeclaration field : fields)
        {
            emitter.emitTypeRange(field.getType());

            for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>) field.fragments())
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
        for (BodyDeclaration body : (List<BodyDeclaration>) clazz.bodyDeclarations())
        {
            if (body instanceof Initializer)
            {
                Initializer init = (Initializer) body;
                SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, className, newCode, "{}", "");
                if (!walker.walk(init.getBody()))
                {
                    return false;
                }
            }
        }

        //Inner classes
        for (BodyDeclaration body : (List<BodyDeclaration>) clazz.bodyDeclarations())
        {
            if (body instanceof AbstractTypeDeclaration)
            {
                if (!processAbstractClass(emitter, (AbstractTypeDeclaration) body, newCode, writer))
                {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean processMethod(SymbolRangeEmitter emitter, String className, MethodDeclaration method, int[] newCode)
    {
        String methodSignature = emitter.emitMethodRange(method);

        // Return type and throws list
        emitter.emitTypeRange(method.getReturnType2());
        for (Name exc : (List<Name>) method.thrownExceptions())
        {
            emitter.emitThrowRange(exc, (ITypeBinding) exc.resolveBinding());
        }

        List<SingleVariableDeclaration> params = (List<SingleVariableDeclaration>) method.parameters();
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
        for (Comment cmt : (List<Comment>) cu.getCommentList())
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
                        if (inside)
                            System.out.println("Unmatched newcode start: " + cmt.getStartPosition() + ": " + comment);
                        inside = true;
                    }
                    else if (command.equalsIgnoreCase("end"))
                    {
                        ret.add(cmt.getStartPosition());
                        if (!inside)
                            System.out.println("Unmatched newcode end: " + cmt.getStartPosition() + ": " + comment);
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
                            if (inside)
                                System.out.println("Unmatched newcode start: " + cmt.getStartPosition() + ": " + comment);
                            inside = true;
                        }
                        else if (command.equalsIgnoreCase("end"))
                        {
                            ret.add(cmt.getStartPosition());
                            if (!inside)
                                System.out.println("Unmatched newcode end: " + cmt.getStartPosition() + ": " + comment);
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
