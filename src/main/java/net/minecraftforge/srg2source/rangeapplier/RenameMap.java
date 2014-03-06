package net.minecraftforge.srg2source.rangeapplier;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import net.minecraftforge.srg2source.rangeapplier.ExceptorFile.ExcLine;
import net.minecraftforge.srg2source.rangeapplier.LocalVarFile.LocalVar;
import net.minecraftforge.srg2source.util.Util;

import com.google.common.collect.BiMap;

class RenameMap
{
    public Map<String, String> maps, imports;

    public RenameMap()
    {
        maps = new HashMap<String, String>();
        imports = new HashMap<String, String>();
    }

    /**
     * Generates values in the RenameMap with data from the provided SrgContainer.
     * @param srgs
     */
    public RenameMap readSrg(SrgContainer srgs)
    {
        // CB -> packaged MCP class/field/method
        for (Entry<String, String> e : srgs.classMap.entrySet()) // classes map
        {
            maps.put("class " + e.getKey(), Util.splitBaseName(e.getValue()));
            imports.put("class " + e.getKey(), Util.internalName2Source(e.getValue())); // when renaming class, need to import it, too

            // and dont forget packages
            maps.put("package " + Util.splitPackageName(e.getKey()), Util.internalName2Source(Util.splitPackageName(e.getValue())));
        }

        for (Entry<String, String> e : srgs.fieldMap.entrySet())
            // fields map
            maps.put("field " + e.getKey(), Util.splitBaseName(e.getValue()));

        for (Entry<MethodData, MethodData> e : srgs.methodMap.entrySet())
            // methods map
            maps.put("method " + e.getKey(), Util.splitBaseName(e.getValue().name));
        
        return this;
    }

    public Map<String, String> getQualified()
    {
        Map<String, String> out = new TreeMap<String, String>();
        out.putAll(maps); // shallow

        for (Entry<String, String> e : out.entrySet())
        {
            if (e.getKey().startsWith("class"))
            {
                // Replace with fully-qualified class name (usually imported, but now insert directly when referenced in source)
                out.put(e.getKey(), imports.get(e.getKey()));
            }
            else if (e.getKey().startsWith("package"))
            {
                // No package names in classes - removing existing qualifications
                //newRenameMap[key] = ""
                out.put(e.getKey(), "");
            }
        }

        return out;
    }

    public RenameMap readParamMap(SrgContainer srg, ExceptorFile primaryExc, ExceptorFile secondaryExc)
    {
        //inverted stuff
        BiMap<String, String> classMap = srg.classMap.inverse();
        BiMap<MethodData, MethodData> methodMap = srg.methodMap.inverse();

        // temp vars
        MethodData paramKey;

        // do primary unmapped EXC
        for (ExcLine line : primaryExc)
        {
            paramKey = line.getMethodData();

            if (line.methodName.equals("<init>"))
            {
                // constructor - remap to new name through class map, not method map
                String newClassName = line.className;

                if (!classMap.containsKey(line.className))
                    continue; // no mapping? ignore....
                else
                    newClassName = classMap.get(line.className);

                paramKey = new MethodData(newClassName + "/" + Util.splitBaseName(newClassName), Util.remapSig(line.methodSig, classMap));
            }
            else if (!methodMap.containsKey(paramKey))
                continue; // ignore if it has no mappigs..
            else
                paramKey = methodMap.get(paramKey); // get info from the methodMap

            // Parameters by number, p_XXXXX_X.. to par1. descriptions
            int i = 0;
            for (String val : line.params)
            {
                maps.put("param " + paramKey + " " + i, val);
                i++;
            }
        }
        // do secondary EXC
        if (secondaryExc == null)
            return this;
        for (ExcLine line : primaryExc)
        {
            paramKey = line.getMethodData();

            if (line.methodName.equals("<init>"))
            {
                // constructor - remap to new name through class map, not method map
                String newClassName = line.className;

                if (!classMap.containsKey(line.className))
                    continue; // no mapping? ignore....
                else
                    newClassName = classMap.get(line.className);

                paramKey = new MethodData(newClassName + "/" + Util.splitBaseName(newClassName), Util.remapSig(line.methodSig, classMap));
            }
            else if (!methodMap.containsKey(paramKey))
            {
                //                    String[] split = e.getKey().split(" "); // 0 = fullMethodName, 1 = methodSig
                //                    String className = splitPackageName(split[0]);
                //                    newFullMethodName = className + "/" + splitBaseName(split[0]);
                //                    newMethodSig = remapSig(split[1], classMap);
                paramKey = new MethodData(paramKey.name, Util.remapSig(line.methodSig, classMap));
            }
            else
                paramKey = methodMap.get(paramKey); // get info from the methodMap

            // Parameters by number, p_XXXXX_X.. to par1. descriptions
            int i = 0;
            for (String val : line.params)
            {
                maps.put("param " + paramKey + " " + i, val);
                i++;
            }
        }
        
        return this;
    }

    /**
     * Read existing local variable name from MCP range map to get local variable positional mapping
     * @throws IOException
     */
    public RenameMap readLocalVariableMap(LocalVarFile localVars, SrgContainer srg) throws IOException
    {
        //inverted stuff
        BiMap<String, String> classMap = srg.classMap.inverse();
        BiMap<MethodData, MethodData> methodMap = srg.methodMap.inverse();

        String className;
        MethodData val;

        for (LocalVar var : localVars)
        {

            String mcpClassName = Util.sourceName2Internal(var.mcpClassName);
            //Range map has MCP names, but we need to map from CB

            if (!classMap.containsKey(mcpClassName))
            {
                className = "net.minecraft.server." + Util.splitBaseName(mcpClassName); // fake it, assuming CB mc-dev will choose similar name to MCP
                System.out.println("WARNING: readLocalVariableMap: no CB class name for MCP class name " + mcpClassName + ", using " + className);
            }
            else
                className = classMap.get(mcpClassName);

            if (var.mcpMethodName.equals("{}"))
            {
                // Initializer - no name
                val = new MethodData("{}", "");
            }
            else if (var.mcpMethodName.equals(Util.splitBaseName(var.mcpMethodName)))
            {
                // Constructor - same name as class
                val = new MethodData(className + "/" + Util.splitBaseName(className), Util.remapSig(var.mcpMethodSig, classMap));
            }
            else
            {
                // Normal method
                MethodData key = new MethodData(mcpClassName + "/" + var.mcpMethodName, var.mcpMethodSig);

                if (!methodMap.containsKey(key))
                {
                    System.out.println("NOTICE: local variables available for " + key + " but no inverse method map; skipping");
                    // probably a changed signature
                    continue;
                }

                val = methodMap.get(key);
            }

            String key = "localvar " + val + " " + var.variableIndex;
            maps.put(key, var.expectedOldText); // existing name
        }
        
        return this;
    }
}
