package net.minecraftforge.srg2source;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import joptsimple.internal.Strings;
import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;

public class SrgUtil
{
    /**
     * Get map from full descriptive method name + signature -> list of descriptive parameter names
     */
    public static Map<String, List<String>> readParameterMap(File mcpConfDir, File excFile, boolean applyMap)
    {
        // verify the exc file...
        if (excFile == null)
        {
            excFile = new File(mcpConfDir, "packaged.exc"); // FML/MCP
            if (!excFile.exists())
                excFile = new File(mcpConfDir, "joined.exc"); // vanilla MCP
        }

        BiMap<String, String> methodCsv = readDescriptiveMethodNames(mcpConfDir);
        BiMap<String, String> paramCsv = readDescriptiveParameterNames(mcpConfDir);

        Map<String, List<String>> paramMap = new HashMap<String, List<String>>();

        List<ExcLine> exc = readExc(excFile);
        String methodName, fullMethodName;
        for (ExcLine line : exc)
        {
            if (line.methodNumber.equals("<init>"))
            {
                // constructor
                // methodName = className.split("/")[-1]
                // methodName = line.className.substring(line.className.lastIndexOf("/"));
                methodName = "<init>";
            }
            else if (applyMap && methodCsv.containsKey(line.methodNumber))
            {
                // descriptive name..
                methodName = methodCsv.get(line.methodNumber);
            }
            else
                // no one named this method
                methodName = line.methodNumber;

            fullMethodName = line.className + "/" + methodName;

            // Parameters by number, p_XXXXX_X.. to par1. descriptions
            ArrayList<String> paramNames = new ArrayList<String>(line.params.size());
            for (String param : line.params)
            {
                if (applyMap && paramCsv.containsKey(param))
                    paramNames.add(paramCsv.get(param));
                else
                    paramNames.add(param);
            }

            paramMap.put(fullMethodName + " " + line.methodSig, paramNames);
        }

        return paramMap;
    }

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
                String className = splitPackageName(split[0], "/");
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
                    newClassName = classMap.get(classMap);
                }
                newFullMethodName = newClassName + "/" + splitBaseName(newClassName, "/");
                newMethodSig = remapSig(split[1], classMap);
            }
            else if (methodMap.containsKey(e.getKey()))
            {
                // not in method map - probably client-only method
                //removed.add(e.getKey());
                if (!keepMissing)
                    continue;

                String[] split = e.getKey().split(" "); // 0 = fullMethodName, 1 = methodSig
                String className = splitPackageName(split[0], "/");
                newFullMethodName = className + "/" + splitBaseName(split[0], "/");
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

    public static String remapSig(String sig, Map<String, String> classMap)
    {
        StringBuffer buffer = new StringBuffer(sig.length());

        Matcher matcher = SIG_REGEX.matcher(sig);
        String className;
        while (matcher.find())
        {
            className = matcher.group(1);
            String repl = "L" + (classMap.containsKey(className) ? classMap.get(className) : className) + ";";
            matcher.appendReplacement(buffer, repl);
        }

        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * Mapping from parameter number (p_####) to name in source (par#X..)
     */
    public static BiMap<String, String> readDescriptiveParameterNames(File mcpConfDir)
    {
        return readCSVMap(new File(mcpConfDir, "params.csv"));
    }

    /**
     * Method numbers (func_####) to descriptive name in source
     */
    public static BiMap<String, String> readDescriptiveMethodNames(File mcpConfDir)
    {
        return readCSVMap(new File(mcpConfDir, "methods.csv"));
    }

    /**
     * Class name to package, from FML/MCP's repackaging
     */
    public static BiMap<String, String> readClassPackageMap(File mcpConfDir)
    {
        return readCSVMap(new File(mcpConfDir, "methods.csv"));
    }

    /**
     * Read MCP's comma-separated-values files into key->value map
     */
    public static BiMap<String, String> readCSVMap(File file)
    {
        try
        {
            Reader reader = Files.newReader(file, Charset.defaultCharset());
            CSVReader csv = new CSVReader(Files.newReader(file, Charset.defaultCharset()), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false);

            BiMap<String, String> map = HashBiMap.create();
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
     * read lots of srgs, as one.
     * @param srgs a array of maps in this order: packagesMap, classMap, fieldMap, methodMap, methodSigMap
     */
    @SuppressWarnings("unchecked")
    public static BiMap<String, String>[] readMultipleSrgs(List<File> srgs)
    {
        HashBiMap<String, String>[] maps = new HashBiMap[] {
                HashBiMap.create(),
                HashBiMap.create(),
                HashBiMap.create(),
                HashBiMap.create(),
                HashBiMap.create()
        };

        for (File file : srgs)
        {
            BiMap<String, String>[] srg = readSrg(file);
            for (int i = 0; i < maps.length; i++)
                maps[i].putAll(srg[i]);
        }

        return maps;
    }

    /**
     * reads an SRG
     * @param srgs a array of maps in this order: packagesMap, classMap, fieldMap, methodMap, methodSigMap
     */
    @SuppressWarnings("unchecked")
    public static BiMap<String, String>[] readSrg(File srg)
    {
        final HashBiMap<String, String> packages = HashBiMap.create();
        final HashBiMap<String, String> classes = HashBiMap.create();
        final HashBiMap<String, String> fields = HashBiMap.create();
        final HashBiMap<String, String> methods = HashBiMap.create();
        final HashBiMap<String, String> methodSigs = HashBiMap.create();

        try
        {
            for (String line : Files.readLines(srg, Charset.defaultCharset()))
            {
                if (Strings.isNullOrEmpty(line) || line.startsWith("#"))
                    continue;

                String type = line.substring(0, 2);
                line = line.substring(4);
                String[] args = line.split(" ");

                if (type.equals("PK"))
                    packages.put(args[0], args[1]);
                else if (type.equals("CL"))
                    classes.put(args[0], args[1]);
                else if (type.equals("FD"))
                    fields.put(args[0], args[1]);
                else if (type.equals("MD"))
                {
                    String fullMethod = args[0] + " " + args[1];
                    methods.put(fullMethod, args[2]);
                    methodSigs.put(fullMethod, args[3]); //fundamentally the same signature, but with types replaced (alternative to remapSig(inSig))
                }
                else
                    throw new RuntimeException("Invalid SRG file: " + srg);
            }
        }
        catch (IOException e)
        {
            Throwables.propagate(e);
        }

        return new HashBiMap[] { packages, classes, fields, methods, methodSigs };
    }

    public static String splitBaseName(String className, String seperator)
    {
        return className.substring(className.lastIndexOf(seperator) + 1);
    }

    public static String splitPackageName(String className, String seperator)
    {
        return className.substring(0, className.lastIndexOf(seperator));
    }

    public static String internalName2Source(String internalName)
    {
        if (internalName == null)
            return null;
        else
            return internalName.replace('/', '.');
    }

    public static String sourceName2Internal(String sourceName)
    {
        if (sourceName == null)
            return null;
        else
            return sourceName.replace('.', '/');
    }

    /**
     * Invert a method + method signature map, undoing the mapping
     */
    public static HashMap<String, String>[] invertMethodMap(Map<String, String> inMethodMap, Map<String, String> inSigMap)
    {
        @SuppressWarnings("unchecked")
        HashMap<String, String>[] maps = new HashMap[] {
                new HashMap<String, String>(),
                new HashMap<String, String>()
        };

        if (inMethodMap.size() != inSigMap.size())
            throw new RuntimeException("Map size is niot the same!");

        for (String inKey : inMethodMap.keySet())
        {
            String[] inSplit = inKey.split(" ");
            String outKey = inMethodMap.get(inKey) + " " + inSigMap.get(inKey);
            maps[0].put(outKey, inSplit[0]);
            maps[1].put(outKey, inSplit[2]);
        }

        return maps;
    }

    public static List<ExcLine> readExc(File exc)
    {
        LinkedList<ExcLine> out = new LinkedList<ExcLine>();

        try
        {
            for (String line : Files.readLines(exc, Charset.forName("UTF-8")))
            {
                Matcher match = ExcLine.EXC_REGEX.matcher(line);

                if (match.find())
                {
                    out.add(new ExcLine(
                            match.group(1), match.group(2), match.group(3),
                            Arrays.asList(match.group(4).split(",")),
                            Arrays.asList(match.group(4).split(","))
                            ));
                }
            }
        }
        catch (IOException e)
        {
            Throwables.propagate(e);
        }

        return out;
    }

    private static class ExcLine implements Cloneable, Serializable
    {
        private static final long serialVersionUID = 887701501978751668L;
        public final String       className;
        public final String       methodNumber;
        public final String       methodSig;
        public final List<String> exceptions;
        public final List<String> params;

        static final Pattern      EXC_REGEX        = Pattern.compile("^([^.]+)\\.([^(]+)(\\([^=]+)=([^|]*)\\|(.*)");

        public ExcLine(String className, String methodName, String methodSig, List<String> exceptions, List<String> params)
        {
            super();
            this.className = className;
            this.methodNumber = methodName;
            this.methodSig = methodSig;
            this.exceptions = exceptions;
            this.params = params;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((className == null) ? 0 : className.hashCode());
            result = prime * result + ((exceptions == null) ? 0 : exceptions.hashCode());
            result = prime * result + ((methodNumber == null) ? 0 : methodNumber.hashCode());
            result = prime * result + ((methodSig == null) ? 0 : methodSig.hashCode());
            result = prime * result + ((params == null) ? 0 : params.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ExcLine other = (ExcLine) obj;
            if (className == null)
            {
                if (other.className != null)
                    return false;
            }
            else if (!className.equals(other.className))
                return false;
            if (exceptions == null)
            {
                if (other.exceptions != null)
                    return false;
            }
            else if (!exceptions.equals(other.exceptions))
                return false;
            if (methodNumber == null)
            {
                if (other.methodNumber != null)
                    return false;
            }
            else if (!methodNumber.equals(other.methodNumber))
                return false;
            if (methodSig == null)
            {
                if (other.methodSig != null)
                    return false;
            }
            else if (!methodSig.equals(other.methodSig))
                return false;
            if (params == null)
            {
                if (other.params != null)
                    return false;
            }
            else if (!params.equals(other.params))
                return false;
            return true;
        }
    }
}
