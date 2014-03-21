package net.minecraftforge.srg2source.util;

import java.io.File;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class Util
{
    /**
     * Remap a parameter map's method signatures (keeping the parameter names intact)
     * Returns new map, and list of methods not found in mapping and were removed
     * @return
     */
    public static HashMap<String, List<String>> remapParameterMap(Map<String, List<String>> paramMap, Map<String, String> methodMap, Map<String, String> methodSigMap, Map<String, String> classMap, boolean keepMissing)
    {
        HashMap<String, List<String>> newParamMap = new HashMap<String, List<String>>();
        //LinkedList<String> removed = new LinkedList<String>();

        for (Entry<String, List<String>> e : paramMap.entrySet())
        {
            String newFullMethodName;
            String newMethodSig;
            if (e.getKey().contains("<init>"))
            {
                // constructor - remap to new name through class map, not method map
                String[] split = e.getKey().split(" "); // 0 = fullMethodName, 1 = methodSig
                String className = splitPackageName(split[0]);
                String newClassName = className;
                if (!classMap.containsKey(className))
                {
                    // not in class map - probably client-only class
                    //removed.add(e.getKey());
                    if (!keepMissing)
                        continue;
                }
                else
                {
                    newClassName = classMap.get(className);
                }
                newFullMethodName = newClassName + "/" + splitBaseName(newClassName);
                newMethodSig = remapSig(split[1], classMap);
            }
            else if (!methodMap.containsKey(e.getKey()))
            {
                // not in method map - probably client-only method
                //removed.add(e.getKey());
                if (!keepMissing)
                    continue;

                String[] split = e.getKey().split(" "); // 0 = fullMethodName, 1 = methodSig
                String className = splitPackageName(split[0]);
                newFullMethodName = className + "/" + splitBaseName(split[0]);
                newMethodSig = remapSig(split[1], classMap);
            }
            else
            {
                newFullMethodName = methodMap.get(e.getKey());
                newMethodSig = methodSigMap.get(e.getKey());
            }

            newParamMap.put(newFullMethodName + " " + newMethodSig, e.getValue());
        }

        return newParamMap;
    }

    private static final Pattern SIG_REGEX = Pattern.compile("L([^;]+);");

    /**
     * Remaps the classes references in a method signature
     */
    public static String remapSig(String sig, Map<String, String> classMap)
    {
        StringBuffer buffer = new StringBuffer(sig.length());

        Matcher matcher = SIG_REGEX.matcher(sig);
        String className;
        while (matcher.find())
        {
            className = matcher.group(1);
            String repl = "L" + (classMap.containsKey(className) ? classMap.get(className) : className) + ";";

            int idx = repl.lastIndexOf('$');
            if (repl.indexOf('$') != -1)
            {
                String ending = repl.substring(idx + 1, repl.length() - 1);
                if (ending.matches("\\d+"))
                    repl = "Ljava/lang/Object;";
                else
                    repl = repl.replace("$", "\\$");
            }
            matcher.appendReplacement(buffer, repl);
        }

        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * Mapping from parameter number (p_####) to name in source (par#X..)
     */
    public static Map<String, String> readDescriptiveParameterNames(File mcpConfDir)
    {
        return readCSVMap(new File(mcpConfDir, "params.csv"));
    }

    /**
     * Method numbers (func_####) to descriptive name in source
     */
    public static Map<String, String> readDescriptiveMethodNames(File mcpConfDir)
    {
        return readCSVMap(new File(mcpConfDir, "methods.csv"));
    }

    /**
     * Class name to package, from FML/MCP's repackaging
     */
    public static Map<String, String> readClassPackageMap(File mcpConfDir)
    {
        return readCSVMap(new File(mcpConfDir, "methods.csv"));
    }

    /**
     * Read MCP's comma-separated-values files into key->value map
     */
    public static Map<String, String> readCSVMap(File file)
    {
        try
        {
            Reader reader = Files.newReader(file, Charset.defaultCharset());
            CSVReader csv = new CSVReader(Files.newReader(file, Charset.defaultCharset()), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false);

            Map<String, String> map = new HashMap<String, String>();
            for (String[] s : csv.readAll())
            {
                map.put(s[0], s[1]);
            }

            reader.close();
            csv.close();

            return map;
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
            return null;
        }
    }

    /**
     * @return The last name in the string. Names are seperated by '/'
     */
    public static String splitBaseName(String className)
    {
        if (className == null)
            return null;
        else
            return className.substring(className.lastIndexOf('/') + 1);
    }
    
    /**
     * Returns the last few names in the string.
     * @param num the number of extra names to include
     * @return The last few names in the string. Seperated by '.'
     */
    public static String splitBaseName(String qualName, int num)
    {
        char c = '.';
        int index = qualName.lastIndexOf(c);
        
        while (index >= 0 && num > 0)
        {
            index = qualName.lastIndexOf(c, index-1);
            num--;
        }
        
        if (index < 0)
            return qualName;
        
        return qualName.substring(index+1);
    }

    /**
     * @return The everything up till last name in the string. Names are seperated by '/'
     */
    public static String splitPackageName(String className)
    {
        if(className == null)
            return null;
        
        int index = className.lastIndexOf('/');
        if (index == -1)
            return null;
        else
            return className.substring(0, index);
    }

    /**
     * Converts names seperated with '/' to seperated with '.'
     */
    public static String internalName2Source(String internalName)
    {
        if (internalName == null)
            return null;
        else
            return internalName.replace('/', '.');
    }
    
    public static int countChar(String str, char c)
    {
        int index = str.indexOf(c);
        int num = 0;
        
        while (index >= 0)
        {
            num++;
            index = str.indexOf(c, index+1);
        }
        
        return num;
    }

    /**
     * Converts names seperated with '.' to seperated with '/'
     */
    public static String sourceName2Internal(String sourceName)
    {
        return sourceName2Internal(sourceName, true);
    }
    
    /**
     * Converts names seperated with '.' to seperated with '/'
     */
    public static String sourceName2Internal(String sourceName, boolean inner)
    {
        if (sourceName == null)
            return null;
        else if (inner)
            return sourceName.replace('.', '/').replace('$', '/');
        else
            return sourceName.replace('.', '/');
    }

    /**
     * Get the top-level class required to be declared in a file by its given name, if in the main tree
     * This is an internal name, including slashes for packages components
     */
    public static String getTopLevelClassForFilename(String filename)
    {
        //withoutExt, ext = os.path.splitext(filename)

        //String noExt = Files.getNameWithoutExtension(filename);
        //noExt = noExt.replace('\\', '/'); // ya never know windows...

        // expect project-relative pathname, standard Maven structure
        //assert parts[0] == "src", "unexpected filename '%s', not in src" % (filename,)
        //assert parts[1] in ("main", "test"), "unexpected filename '%s', not in src/{test,main}" % (filename,)
        //assert parts[2] == "java", "unexpected filename '%s', not in src/{test,main}/java" % (filename,)

        // return "/".join(parts[0:])  # "internal" fully-qualified class name, separated by /

        //return noExt;

        return filename.replace("." + Files.getFileExtension(filename), "").replace('\\', '/');
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

    private static ASTParser parser = ASTParser.newParser(AST.JLS4);
    @SuppressWarnings("unchecked")
    public static CompilationUnit createUnit(String name, String data, String srcRoot, String[] libs) throws Exception
    {
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
}
