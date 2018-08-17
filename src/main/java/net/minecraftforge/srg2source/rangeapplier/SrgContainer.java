package net.minecraftforge.srg2source.rangeapplier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import joptsimple.internal.Strings;

import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;

public class SrgContainer
{
    private static Pattern CLS_ENTRY = Pattern.compile("L([^;]+);");
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
        return readSrg(srg, false);
    }
    public SrgContainer readSrg(File srg, boolean reverse)
    {
        try
        {
            String currentClass = null;
            List<String> lines = Files.readLines(srg, StandardCharsets.UTF_8);
            lines = lines.stream().map(line -> line.split("#")[0]).filter(l -> !Strings.isNullOrEmpty(l.trim())).collect(Collectors.toList());

            for (String line : lines) //Gather class remaps so we can load TSRG
            {
                if (line.startsWith("\t") || (line.indexOf(':') != -1 && !line.startsWith("CL:")))
                    continue;
                if (line.indexOf(':') != -1)
                    line = line.substring(4);

                String[] args = line.split(" ");
                if (args.length == 2 && !args[0].endsWith("/"))
                    classMap.put(args[0], args[1]);
            }

            for (String line : lines)
            {
                int idx = line.indexOf(':');
                if (idx != -1 && line.startsWith("CL:"))
                    continue;

                String type = idx != -1 ? line.substring(0, idx) : null;
                if (idx != -1) line = line.substring(4);
                if (line.startsWith("\t")) {
                    if (currentClass == null)
                        throw new RuntimeException("Invalid TSRG line, missing current class: " + line);
                    line = currentClass + " " + line.substring(1);
                }

                String[] args = line.split(" ");
                if (type != null)
                {
                    if (type.equals("PK"))
                        packageMap.put(args[0], args[1]);
                    else if (type.equals("CL"))
                        ; // We already read classes above.
                    else if (type.equals("FD"))
                        fieldMap.put(args[0], args[1]);
                    else if (type.equals("MD"))
                        methodMap.put(new MethodData(args[0], args[1]), new MethodData(args[2], args[3]));
                    else
                        throw new RuntimeException("Invalid SRG file: " + srg + " Line: " + type + ": " + line);
                }
                else
                {
                  if (args.length == 2)
                  {
                      if (args[0].endsWith("/")) //Package
                          packageMap.put(args[0].substring(0, args[0].length() - 1), args[1].substring(0, args[1].length() - 1));
                  }
                  else if (args.length == 3)
                      fieldMap.put(args[0] + "/" + args[1], remapClass(args[0]) + "/" + args[2]);
                  else if (args.length == 4)
                      methodMap.put(new MethodData(args[0] + "/" + args[1], args[2]), new MethodData(remapClass(args[0]) + "/" + args[3], remapDesc(args[2])));
                  else
                      throw new RuntimeException("Invalid SRG file: " + srg + " Line: " + type + ": " + line);
                }
            }
        }
        catch (IOException e)
        {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        return this;
    }

    private String remapClass(String cls)
    {
        String ret = classMap.get(cls);
        if (ret != null)
            return ret;

        int idx = cls.lastIndexOf('$');
        if (idx != -1)
            ret = remapClass(cls.substring(0, idx)) + cls.substring(idx);
        else
            ret = cls;
        classMap.put(cls, ret);
        return cls;
    }

    private String remapDesc(String desc)
    {
        StringBuffer buf = new StringBuffer();
        Matcher matcher = CLS_ENTRY.matcher(desc);
        while (matcher.find()) {
            matcher.appendReplacement(buf, "L" + remapClass(matcher.group(1)) + ";");
        }
        matcher.appendTail(buf);
        return buf.toString();
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
