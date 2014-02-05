package net.minecraftforge.srg2source.util;

import java.io.File;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            repl = repl.replace("$", "\\$");
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
        return className.substring(className.lastIndexOf('/') + 1);
    }

    /**
     * @return The everything up till last name in the string. Names are seperated by '/'
     */
    public static String splitPackageName(String className)
    {
        return className.substring(0, className.lastIndexOf('/'));
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

    /**
     * Converts names seperated with '.' to seperated with '/'
     */
    public static String sourceName2Internal(String sourceName)
    {
        if (sourceName == null)
            return null;
        else
            return sourceName.replace('.', '/');
    }

    /**
     * Get filename relative to project at srcRoot, instead of an absolute path
     */
    private static String getProjectRelativePath(String absFile, File srcRoot)
    {
        // if absFilename[0] != "/": return absFilename # so much for absolute
        return absFile.replace(srcRoot.getAbsolutePath() + "/", "");
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
}
