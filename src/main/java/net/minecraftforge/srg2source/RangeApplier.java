package net.minecraftforge.srg2source;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.internal.Strings;

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.Files;

public class RangeApplier
{

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException
    {
        // configure parser
        OptionParser parser = new OptionParser();
        {
            parser.acceptsAll(Arrays.asList("h", "help")).isForHelp();
            parser.accepts("srcRoot", "Source root directory to rename").withRequiredArg().ofType(File.class).isRequired(); // var=srcRoot
            parser.accepts("srcRangeMap", "Source range map generated by srg2source").withRequiredArg().ofType(File.class).isRequired(); // var=srcRangeMap
            parser.accepts("srgFiles", "Symbol map file(s), separated by ' '").withRequiredArg().ofType(File.class).isRequired(); // var=srgFiles
            parser.accepts("git", "Command to invoke git"); // var=git default="git"
            parser.accepts("lvRangeMap", "Original source range map generated by srg2source, for renaming local variables"); // var=lvRangeMap)  # TODO: csv instead?
            parser.accepts("mcpConfDir", "MCP configuration directory, for renaming parameters").withRequiredArg().ofType(File.class); // var=mcpConfDir
            parser.accepts("excFiles", "Parameter map file(s), separated by ' '").withRequiredArg().ofType(File.class); // var=excFiles
            parser.accepts("no-rewriteFiles", "Disable rewriting files with new symbol mappings"); //, var="rewriteFiles", default=True
            parser.accepts("no-renameFiles", "Disable renaming files with new filenames"); // var="renameFiles", default=True
            parser.accepts("no-dumpRenameMap", "Disable dumping symbol rename map before renaming"); // var="dumpRenameMap", default=True
            parser.accepts("dumpRangeMap", "Enable dumping the ordered range map and quit"); // var=dumpRangeMap, default=False
        }

        OptionSet options = parser.parse(args);

        //options.valuesOf("srgFiles");
        File srcRoot = (File) options.valueOf("srcRoot");
        boolean rewriteFiles = !options.has("no-rewriteFiles");
        boolean renameFiles = !options.has("no-renameFiles");
        boolean dumpRenameMap = !options.has("no-dumpRenameMap");
        boolean dumpRangeMap = options.has("dumpRangeMap");

        System.out.println("Reading rename maps...");
        Map<String, String>[] renameMaps = getRenameMaps(
                (List<File>) options.valueOf("srgFiles"), (File) options.valueOf("mcpConfDir"), (File) options.valueOf("lvRangeMap"),
                dumpRenameMap, srcRoot, (List<File>) options.valueOf("excFiles")
                );
        //        renameMap, importMap = getRenameMaps(options.srgFiles.split(","), options.mcpConfDir, options.lvRangeMap, options.dumpRenameMap, options.srcRoot, options.excFiles.split(","))
        System.out.println("Qualifying rename maps...");
        Map<String, String> qualifiedRenameMap = qualifyClassRenameMaps(renameMaps[0], renameMaps[1]);
        System.out.println("Reading range map...");
        Multimap<String, ImmutableList<String>> rangeMapByFile = readRangeMap((File) options.valueOf("srcRangeMap"), srcRoot);

        if (dumpRangeMap)
        {
            for (String key : rangeMapByFile.keySet())
            {
                for (ImmutableList<String> info : rangeMapByFile.get(key))
                {
                    System.out.println(info);
                }
            }
            System.exit(0);
        }

        System.out.println("Processing files...");

        for (String key : rangeMapByFile.keySet())
        {
            if (key.startsWith("jline"))
                continue;
            else if (key.startsWith("net/minecraft"))
            {
                processJavaSourceFile(srcRoot, key, rangeMapByFile.get(key), renameMaps[0], renameMaps[1], false, rewriteFiles, renameFiles);
            }
            else
            {
                processJavaSourceFile(srcRoot, key, rangeMapByFile.get(key), qualifiedRenameMap, new HashMap<String, String>(), false, rewriteFiles, renameFiles);
            }
        }
    }

    /**
     * Transform a rename map to use fully-qualified, removing need for imports
     */
    private static Map<String, String> qualifyClassRenameMaps(Map<String, String> renameMap, Map<String, String> importMap)
    {
        Map<String, String> out = new HashMap<String, String>();
        out.putAll(renameMap); // shallow copy OK

        for (Entry<String, String> e : out.entrySet())
        {
            if (e.getKey().startsWith("class"))
            {
                // Replace with fully-qualified class name (usually imported, but now insert directly when referenced in source)
                out.put(e.getKey(), importMap.get(e.getKey()));
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

    /**
     * Read ApplySrg2Source symbol range map into a dictionary
     * Keyed by filename -> list of (range start, end, expectedOldText, key)
     */
    public static Multimap<String, ImmutableList<String>> readRangeMap(File file, File srcRoot)
    {
        Multimap<String, ImmutableList<String>> rangeMap = Multimaps.newMultimap(new TreeMap<String, Collection<ImmutableList<String>>>(), new Supplier<Collection<ImmutableList<String>>>()
        {
            @Override
            public Collection<ImmutableList<String>> get()
            {
                return new LinkedList<ImmutableList<String>>();
            }
        });

        try
        {
            for (String line : Files.readLines(file, Charset.defaultCharset()))
            {
                List<String> tokens = Splitter.on('|').splitToList(line);
                if (!tokens.get(0).equals("@"))
                    continue;
                String absFilename = tokens.get(1);
                String startRange = tokens.get(2);
                String endRange = tokens.get(3);
                String expectedOldText = tokens.get(4);
                String kind = tokens.get(5);
                String filename = getProjectRelativePath(absFilename, srcRoot);
                List<String> info = tokens.subList(6, tokens.size());

                String key;
                // Build unique identifier for symbol
                if (kind.equals("package"))
                {
                    String forClass = info.get(1);

                    // key = "package "+packageName # ignore old name (unique identifier == filename)
                    if (forClass.equals("(file)"))
                        forClass = getTopLevelClassForFilename(filename);
                    else
                        forClass = SrgUtil.sourceName2Internal(forClass); // . -> /

                    // 'forClass' == the class that == in this package; when the class is
                    // remapped to a different package, this range should be updated

                    key = "package" + forClass;
                }
                else if (kind.equals("class"))
                {
                    key = "class" + SrgUtil.sourceName2Internal(info.get(0));
                }
                else if (kind.equals("field"))
                {
                    key = "field " + SrgUtil.sourceName2Internal(info.get(0)) + "/" + info.get(1);
                }
                else if (kind.equals("method"))
                {
                    if (expectedOldText.contains("super") || expectedOldText.contains("this"))
                        continue; // hack: avoid erroneously replacing super/this calls
                    key = "method " + SrgUtil.sourceName2Internal(info.get(0)) + "/" + info.get(1) + " " + info.get(2);
                }
                else if (kind.equals("param"))
                {
                    key = "param " + SrgUtil.sourceName2Internal(info.get(0)) + "/" + info.get(1) + " " + info.get(2) + " " + info.get(4);  // ignore old name (positional)
                }
                else if (kind.equals("localvar"))
                {
                    key = "localvar " + SrgUtil.sourceName2Internal(info.get(0)) + "/" + info.get(1) + " " + info.get(2) + " " + info.get(4); // ignore old name (positional)
                }
                else
                {
                    throw new RuntimeException("Unknown kind: " + kind);
                }

                // (startRange, endRange, expectedOldText, key)
                rangeMap.put(filename, ImmutableList.of(startRange, endRange, expectedOldText, key));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return rangeMap;
    }

    /**
     * Get all rename maps, keyed by globally unique symbol identifier, values are new names
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String>[] getRenameMaps(List<File> srgs, File mcpConfDir, File lvRangeMapFile, boolean dumpRenameMap, File srcRoot, List<File> excFiles) throws IOException
    {
        Map<String, String> maps = new TreeMap<String, String>();
        Map<String, String> importMaps = new HashMap<String, String>();

        // in order: packages classes fields methods sigs
        BiMap<String, String>[] srg = SrgUtil.readMultipleSrgs(srgs);

        // CB -> packaged MCP class/field/method
        for (Entry<String, String> e : srg[1].entrySet()) // classes map
        {
            maps.put("class " + e.getKey(), SrgUtil.splitBaseName(e.getValue(), "/"));
            importMaps.put("class " + e.getKey(), SrgUtil.internalName2Source(e.getValue())); // when renaming class, need to import it, too
        }

        for (Entry<String, String> e : srg[2].entrySet())
            // fields map
            maps.put("field " + e.getKey(), SrgUtil.splitBaseName(e.getValue(), "/"));

        for (Entry<String, String> e : srg[3].entrySet())
            // methods map
            maps.put("method " + e.getKey(), SrgUtil.splitBaseName(e.getValue(), "/"));

        // CB class -> MCP package name
        for (Entry<String, String> e : srg[1].entrySet())
            maps.put("package src/main/java" + e.getKey() + ".java", SrgUtil.internalName2Source(SrgUtil.splitPackageName(e.getValue(), "/")));

        // Read parameter map from MCP.. it comes from MCP with MCP namings, so have to remap to CB
        Map<String, String>[] invertedMethodMaps = SrgUtil.invertMethodMap(srg[3], srg[4]);

        // init some stuff
        Map<String, List<String>> mcpParamMap, cbParamMap;
        if (mcpConfDir != null)
        {
            mcpParamMap = SrgUtil.readParameterMap(mcpConfDir, null, false);
            // cbParamMap removedParamMap
            cbParamMap = SrgUtil.remapParameterMap(mcpParamMap, invertedMethodMaps[0], invertedMethodMaps[1], srg[1].inverse(), false);
            if (excFiles != null)
            {
                for (File file : excFiles)
                {
                    Map<String, List<String>> tmp = SrgUtil.readParameterMap(mcpConfDir, file, false);
                    Map<String, List<String>> tmpClean = SrgUtil.remapParameterMap(tmp, invertedMethodMaps[0], invertedMethodMaps[1], srg[1].inverse(), true);
                    // pprint(tmp_clean)
                    cbParamMap.putAll(tmpClean);
                }
            }
            // removedParamMap = methods in FML/MCP repackaged+joined but not CB = client-only methods
        }
        else
        {
            mcpParamMap = new HashMap<String, List<String>>();
            cbParamMap = new HashMap<String, List<String>>();
        }

        for (Entry<String, List<String>> e : cbParamMap.entrySet())
        {
            int i = 0;
            for (String val : e.getValue())
            {
                maps.put("param " + e.getKey() + " " + i, val);
                i++;
            }
        }

        // Local variable map - position in source -> name; derived from MCP rangemap
        if (lvRangeMapFile != null)
        {
            readLocalVariableMap(lvRangeMapFile, maps, srg[1].inverse(), invertedMethodMaps[0], invertedMethodMaps[1], srcRoot);
        }

        if (dumpRenameMap)
        {
            for (Entry<String, String> e : maps.entrySet())
            {
                System.out.println("RENAME MAP: " + e.getKey() + " -> " + e.getValue());
            }
        }

        return new Map[] { maps, importMaps };
    }

    /**
     * Read existing local variable name from MCP range map to get local variable positional mapping
     * @throws IOException
     */
    private static void readLocalVariableMap(File lvFile, Map<String, String> renameMap, BiMap<String, String> invClassMap, Map<String, String> invMethodMap, Map<String, String> invMethodSigMap, File srcRoot) throws IOException
    {
        for (String line : Files.readLines(lvFile, Charset.defaultCharset()))
        {
            // @ absFilename startRangeStr endRangeStr expectedOldText kind
            List<String> tokens = Splitter.on('|').splitToList(line.trim());

            if (!tokens.get(0).equals("@"))
                continue;

            // mcpClassName, mcpMethodName, mcpMethodSignature, variableName, variableIndex
            List<String> info = tokens.subList(6, tokens.size());
            String className, methodName, methodSig;

            if (!tokens.get(5).equals("localvar"))
                continue;

            String mcpClassName = SrgUtil.sourceName2Internal(info.get(0));
            //Range map has MCP names, but we need to map from CB

            if (!invClassMap.containsKey(mcpClassName))
            {
                className = "net.minecraft.server." + SrgUtil.splitBaseName(mcpClassName, "/"); // fake it, assuming CB mc-dev will choose similar name to MCP
                System.out.println("WARNING: readLocalVariableMap: no CB class name for MCP class name " + mcpClassName + ", using " + className);
            }
            else
                className = invClassMap.get(mcpClassName);

            if (info.get(1).equals("{}"))
            {
                // Initializer - no name
                methodName = className + "{}";
                methodSig = "";
            }
            else if (info.get(1).equals(SrgUtil.splitBaseName(info.get(1), "/")))
            {
                // Constructor - same name as class
                methodName = className + "/" + SrgUtil.splitBaseName(className, "/");
                methodSig = SrgUtil.remapSig(info.get(2), invClassMap);
            }
            else
            {
                // Normal method
                String key = mcpClassName + "/" + info.get(1) + " " + info.get(2);
                if (!invMethodMap.containsKey(key))
                {
                    System.out.println("NOTICE: local variables available for " + key + " but no inverse method map; skipping");
                    // probably a changed signature
                    continue;
                }

                methodName = invMethodMap.get(key);
                methodSig = invMethodSigMap.get(key);
            }

            String key = "localvar " + methodName + " " + methodSig + " " + info.get(4);
            renameMap.put(key, tokens.get(4)); // existing name
        }
    }

    /**
     * Rename symbols in source code
     * @throws IOException
     */
    private static void processJavaSourceFile(File srcRoot, String fileName, Collection<ImmutableList<String>> rangeList, Map<String, String> renameMap, Map<String, String> importMap, boolean shouldAnnotate, boolean rewrite, boolean rename) throws IOException
    {
        //      def processJavaSourceFile(srcRoot, filename, rangeList, renameMap, importMap, shouldAnnotate, options):

        File file = new File(srcRoot, fileName);
        String data = Files.toString(file, Charset.defaultCharset());

        if (data.contains("\r"))
        {
            // BlockJukebox == the only file with CRLF line endings in NMS.. and.. IntelliJ IDEA treats offsets 
            // as line endings being one character, whether LF or CR+LF. So remove the extraneous character or
            // offsets will be all off :.
            //  Yes I have a pull request on this: https://github.com/Bukkit/CraftBukkit/pull/985
            System.out.println("Warning: " + fileName + " has CRLF line endings; consider switching to LF");
            data = data.replace("\r", "");
        }

        Set<String> importsToAdd = new TreeSet<String>();
        int shift = 0;

        // Existing package/class name (with package, internal) derived from filename
        String oldTopLevelClassFullName = getTopLevelClassForFilename(fileName);
        String oldTopLevelClassPackage = SrgUtil.splitPackageName(oldTopLevelClassFullName, "/");
        String oldTopLevelClassName = SrgUtil.splitBaseName(oldTopLevelClassFullName, "/");

        // New package/class name through mapping
        String newTopLevelClassPackage = SrgUtil.sourceName2Internal(renameMap.get("package " + oldTopLevelClassFullName));
        String newTopLevelClassName = renameMap.get("class " + oldTopLevelClassFullName);
        if (newTopLevelClassPackage != null && newTopLevelClassName == null)
            throw new RuntimeException("filename " + fileName + " found package " + oldTopLevelClassPackage + "->" + newTopLevelClassPackage + " but no class map for " + newTopLevelClassName);
        if (newTopLevelClassPackage == null && newTopLevelClassName != null)
            throw new RuntimeException("filename " + fileName + " found class map " + oldTopLevelClassName + "->" + newTopLevelClassName + " but no package map for " + oldTopLevelClassPackage);

        // start,end,expectedOldText,key
        for (ImmutableList<String> info : rangeList)
        {
            int start = Integer.parseInt(info.get(0));
            int end = Integer.parseInt(info.get(1));
            String expectedOldText = info.get(2);
            String key = info.get(3);

            if (renameMap.containsKey(key) && renameMap.get(key).isEmpty())
            {
                // Replacing a symbol with no text = removing a symbol
                if (!key.startsWith("package "))
                    throw new RuntimeException("unable to remove non-package symbol " + key);

                // Remove that pesky extra period after qualified package names
                // this removes a pexky period how???  --Abrar
                end += 1;
                expectedOldText += ".";
            }

            String oldName = data.substring(start + shift, end + shift);

            if (!oldName.equals(expectedOldText))
                throw new RuntimeException("Rename sanity check failed: expected '" + expectedOldText + "' at [" + start + "," + end + "] (shifted " + shift + " to [" + (shift + start) + "," + (shift + end) + "]) in " + fileName + ", but found '" + oldName + "'\nRegenerate symbol map on latest sources or start with fresh source and try again");

            String newName = getNewName(key, oldName, renameMap, shouldAnnotate);
            if (newName == null)
            {
                if (key.split(" ")[1].contains("net/minecraft"))
                    System.out.println("No rename for " + key);
                continue;
            }

            System.out.println("Rename" + key + "[" + (start + shift) + "," + (end + shift) + "]" + "::" + oldName + "->" + newName);

            if (importMap.containsKey(key))
            {
              // This rename requires adding an import, if it crosses packages
              String importPackage = SrgUtil.splitPackageName(SrgUtil.sourceName2Internal(importMap.get(key)), "/");
              if (!importPackage.equals(newTopLevelClassPackage))
                  importsToAdd.add(importMap.get(key));
            }
        // Rename algorithm: 
        // 1. textually replace text at specified range with new text
        // 2. shift future ranges by difference in text length
        data = data.substring(0,start+shift) + newName + data.substring(end+shift);
        shift += (newName.length() - oldName.length());
      }

      // Lastly, update imports - this == separate from symbol range manipulation above
      data = updateImports(data, importsToAdd, importMap);

      if (rewrite)
      {
          System.out.println("Writing "+file);
          Files.write(data, file, Charset.defaultCharset());
      }
      
      if (rename)
      {
          if (newTopLevelClassPackage != null) // rename if package changed
          {
            String newFileName = (newTopLevelClassPackage + "/" + newTopLevelClassName + ".java").replace('\\', '/');
            File newPath = new File(srcRoot, newFileName);

            System.out.println("Rename file "+fileName+" -> "+newFileName);

            if (!fileName.equals(newFileName))
            {
              // Create any missing directories in new path
//              dirComponents = os.path.dirname(newPath).split(SEP)
//              for i in range(2,len(dirComponents)+1):
//                  intermediateDir = os.path.sep.join(dirComponents[0:i])
//                  if not os.path.exists(intermediateDir):
//                      os.mkdir(intermediateDir)
                newPath.getParentFile().mkdirs();

              //os.rename(path, newPath)
                Files.copy(file, newPath);
                file.delete();
//              cmd = [options.git, 'mv', filename, newFilename]
//              if not run_command(cmd, cwd=options.srcRoot):
//                  sys.exit(1)
            }
          }
      }
    }
    
    /**
     * Add new import statements to source
     */
    private static String updateImports(String data, Set<String> newImports, Map<String, String> importMap)
    {
        String[] lines = data.split("\n");
        // Parse the existing imports and find out where to add ours
        // This doesn't use Psi.. but the syntax is easy enough to parse here
        LinkedList<String> newLines = new LinkedList<String>();
        boolean addedNewImports = false;
        
        LinkedList<String> newImportLines = new LinkedList<String>();
        for (String imp : newImports)
        {
            newImportLines.add("import " + imp + ";");
        }

        boolean sawImports = false;

        for (String line : lines)
        {
            if (line.startsWith("import "))
            {
                sawImports = true;
                
                if (line.startsWith("import net.minecraft."))
                {
                    // If no import map, *remove* NMS imports (OBC rewritten with fully-qualified names)
                    if (importMap.isEmpty())
                        continue;
                    
                    // Rewrite NMS imports
                    String oldClass = line.replace("import ", "").replace(";", "");
                    System.out.println("Import: " + oldClass);
                    
                    String newClass;
                    if (oldClass.equals("net.minecraft.server.*"))
                        // wildcard NMS imports (CraftWorld, CraftEntity, CraftPlayer).. bad idea
                        continue;
                    else
                    {
                        if (importMap.containsKey("class " + SrgUtil.sourceName2Internal(oldClass)))
                            newClass = importMap.get("class " + SrgUtil.sourceName2Internal(oldClass));
                        else
                            newClass = SrgUtil.sourceName2Internal(oldClass);
                    }
                    
                    String newLine = "import "+newClass + ";";
                    
                  if (!newImportLines.contains(newLine))  // if not already added
                      newLines.add(newLine);
                }
                else
                    newLines.add(line);
            }
            else
            {
              if (sawImports && !addedNewImports)
              {
                  // Add our new imports right after the last import
                  System.out.println("Adding "+newImports.size()+" imports");
                  newLines.addAll(newImportLines);
                  addedNewImports = true;
              }
              newLines.add(line);
            }
        }

      if (!addedNewImports)
      {
          LinkedList<String> tmp = new LinkedList<String>();
          tmp.addAll(newLines.subList(0, 2));
          tmp.addAll(newImportLines);
          tmp.addAll(newLines.subList(2, newLines.size()));
          //newLines = newLines[0:2] + newImportLines + newLines[2:]
          newLines = tmp;
      }

      String newData = Strings.join(newLines, "\n");

      // Warning: ugly hack ahead
      // The symbol range map extractor is supposed to emit package reference ranges, which we can 
      // update with the correct new package names. However, it has a bug where the package ranges
      // are not always emitted on fully-qualified names. For example: (net.minecraft.server.X)Y - a
      // cast - will fail to recognize the net.minecraft.server package, so it won't be processed by us.
      // This leads to some qualified names in the original source to becoming "overqualified", that is,
      // net.minecraft.server.net.minecraft.X; the NMS class is replaced with its fully-qualified name
      // (in non-NMS source, where we want it to always be fully-qualified): original package name isn't replaced.
      // Occurs in OBC source which uses fully-qualified NMS names already, and NMS source which (unnecessarily)
      // uses fully-qualified NMS names, too. Attempted to fix this problem for longer than I should.. 
      // maybe someone smarter can figure it out -- but until then, in the interest of expediency, I present 
      // this ugly workaround, replacing the overqualified names after-the-fact.
      // Fortunately, this pattern is easy enough to reliably detect and replace textually!
      newData = newData.replace("net.minecraft.server.net", "net");  // OBC overqualified symbols
      newData = newData.replace("net.minecraft.server.Block", "Block"); // NMS overqualified symbols
      // ..and qualified inner classes, only one.... last ugly hack, I promise :P
      newData = newData.replace("net.minecraft.block.BlockSapling/*was:BlockSapling*/.net.minecraft.block.BlockSapling.TreeGenerator", "net.minecraft.block.BlockSapling.TreeGenerator");
      newData = newData.replace("net.minecraft.block.BlockSapling.net.minecraft.block.BlockSapling.TreeGenerator", "net.minecraft.block.BlockSapling.TreeGenerator");

      return newData;
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
    private static String getTopLevelClassForFilename(String filename)
    {
        //withoutExt, ext = os.path.splitext(filename)
        String noExt = Files.getNameWithoutExtension(filename);
        noExt = noExt.replace('\\', '/'); // ya never know windows...
        // expect project-relative pathname, standard Maven structure
        //assert parts[0] == "src", "unexpected filename '%s', not in src" % (filename,)
        //assert parts[1] in ("main", "test"), "unexpected filename '%s', not in src/{test,main}" % (filename,)
        //assert parts[2] == "java", "unexpected filename '%s', not in src/{test,main}/java" % (filename,)

        // return "/".join(parts[0:])  # "internal" fully-qualified class name, separated by /
        return noExt;
    }
    
    private static String getNewName(String key, String oldName, Map<String, String> renameMap, boolean shouldAnnotate)
    {
        String newName;
        if (!renameMap.containsKey(key))
        {
          String constructorClassName = getConstructor(key);
          if (constructorClassName != null)
          {
              // Constructors are not in the method map (from .srg, and can't be derived
              // exclusively from the class map since we don't know all the parameters).. so we
              // have to synthesize a rename from the class map here. Ugh..but, it works.
              System.out.println("FOUND CONSTR " +  key + " " + constructorClassName);
              if (renameMap.containsKey("class "+constructorClassName))
                  // Rename constructor to new class name
                  newName = SrgUtil.splitBaseName(renameMap.get("class "+constructorClassName), "/");
              else
                  return null;
          }
          else
              // Not renaming this
              return null;
        }
        else
            newName = renameMap.get(key);
        
        if (shouldAnnotate)
            newName += "/* was "+oldName + "*/";
        
        return newName;
    }
    
    /**
     *  Check whether a unique identifier method key is a constructor, if so return full class name for remapping, else null
     */
    private static String getConstructor(String key)
    {
            String[] tokens = key.split(" ", 2);  // TODO: switch to non-conflicting separator..types can have spaces :(
            if (!tokens[0].equals("method"))
                return null;
            System.out.println(Arrays.toString(tokens));
            //kind, fullMethodName, methodSig = tokens
            if (tokens[2].charAt(tokens[2].length()-1) != 'V') // constructors marked with 'V' return type signature in ApplySrg2Source and MCP
                return null;
            String fullClassName = SrgUtil.splitPackageName(tokens[1], "/");
            String methodName = SrgUtil.splitBaseName(tokens[1], "/");

            String className = SrgUtil.splitBaseName(fullClassName, "/");

            if (className.equals(methodName)) // constructor has same name as class
                return fullClassName;
            else
                return null;
    }
}