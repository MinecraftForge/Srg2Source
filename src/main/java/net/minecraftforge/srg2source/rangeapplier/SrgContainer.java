package net.minecraftforge.srg2source.rangeapplier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import joptsimple.internal.Strings;

import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;

public class SrgContainer
{
    public final BiMap<String, String>   classMap, fieldMap, packageMap;
    public final BiMap<MethodData, MethodData> methodMap;

    public SrgContainer()
    {
        classMap = HashBiMap.create();
        packageMap = HashBiMap.create();
        fieldMap = HashBiMap.create();
        methodMap = HashBiMap.create();
    }

    public SrgContainer readSrg(File srg)
    {
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
                {
                    packageMap.put(args[0], args[1]);
                }
                else if (type.equals("CL"))
                {
                    classMap.put(args[0], args[1]);
                }
                else if (type.equals("FD"))
                {
                    fieldMap.put(args[0], args[1]);
                }
                else if (type.equals("MD"))
                {
                    methodMap.put(new MethodData(args[0], args[1]), new MethodData(args[2], args[3]));
                }
                else
                {
                    throw new RuntimeException("Invalid SRG file: " + srg);
                }
            }
        }
        catch (IOException e)
        {
            Throwables.propagate(e);
        }
        
        return this;
    }

    public SrgContainer readSrgs(Iterable<File> srgs)
    {
        for (File file : srgs)
        {
            readSrg(file);
        }
        
        return this;
    }
}
