package net.minecraftforge.srg2source.ast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.ConfLogger;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

@SuppressWarnings("unchecked")
public class RangeExtractor extends ConfLogger<RangeExtractor>
{
    public static final String JAVA_1_6 = JavaCore.VERSION_1_6;
    public static final String JAVA_1_7 = JavaCore.VERSION_1_7;
    public static final String JAVA_1_8 = JavaCore.VERSION_1_8;

    private PrintWriter outFile;
    private final Set<File> libs = new HashSet<File>();
    private InputSupplier src;
    private ASTParser parser = null;
    private String java_version = JAVA_1_6;
    private Map<String, FileCache> file_cache = Maps.newHashMap();
    private int cache_hits =0;

    public RangeExtractor()
    {
        this(JAVA_1_6);
    }

    public RangeExtractor(String javaVersion)
    {
        this.java_version = javaVersion;
    }

    public static void main(String[] args) throws IOException
    {
        if (args.length < 3)
        {
            System.out.println("Usage: RangeExtract <SourceDir> <LibDir> <OutFile> [JavaVersion]");
            return;
        }

        File src = new File(args[0]);

        String javaVersion = args.length > 3 ? args[3] : JAVA_1_6;
        RangeExtractor extractor = new RangeExtractor(javaVersion);
        extractor.setSrcRoot(new File(args[0]));

        if (args[1].equals("none") || args[1].isEmpty())
            extractor.addLibs(src);
        else
            extractor.addLibs(args[1]);

        boolean worked = extractor.generateRangeMap(new File(args[2]));

        System.out.println("Srg2source batch mode finished - now exiting " + (worked ? 0 : 1));
        System.exit(worked ? 0 : 1);
    }

    /**
     * Generates the rangemap.
     * @param out The file where the RangeMap will be put out.
     * @return FALSE if it failed for some reason, TRUE otherwise.
     */
    public boolean generateRangeMap(File out)
    {
        try
        {
            if (!out.exists())
            {
                Files.createParentDirs(out);
                out.createNewFile();
            }

            // setup printwriter
            outFile = new PrintWriter(new BufferedWriter(new FileWriter(out)));
        }
        catch (Exception e)
        {
            // some issue making the output thing.
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return generateRangeMap(outFile);
    }

    /**
     * Generates the rangemap.
     * @param writer The write to print the RangeMap to.
     * @return FALSE if it failed for some reason, TRUE otherwise.
     */
    public boolean generateRangeMap(PrintWriter writer)
    {
        this.outFile = writer;

        log("Symbol range map extraction starting");

        List<String> tmp = src.gatherAll(".java");
        Collections.sort(tmp);
        String[] files = tmp.toArray(new String[tmp.size()]);
        log("Processing " + files.length + " files");

        if (files.length == 0)
        {
            // no files? well.. nothing to do then.
            cleanup();
            return true;
        }

        try
        {

            for (String path : files)
            {
                //path = path.replace('\\',  '/');
                //if (!path.equals("net/minecraft/block/BlockHopper.java")) continue;
                InputStream stream = src.getInput(path);

                // do stuff
                {
                    SymbolRangeEmitter emitter = new SymbolRangeEmitter(path, outFile);
                    byte[] bytes = ByteStreams.toByteArray(stream);
                    String data = new String(bytes, Charsets.UTF_8).replaceAll("\r", "");
                    @SuppressWarnings("deprecation")
                    String md5 = Hashing.md5().hashString(data, Charsets.UTF_8).toString();

                    log("startProcessing \"" + path + "\" md5: " + md5);

                    FileCache cache = this.file_cache.get(path);
                    if (cache != null && cache.path.equals(path) && cache.md5.equals(md5))
                    {
                        log("Cache Hit!");
                        this.cache_hits++;
                        for (String line : cache.lines)
                            emitter.log(line);
                    }
                    else
                    {
                        CompilationUnit cu = Util.createUnit(getParser(src.getRoot(path)), java_version, path, data);

                        if (cu.getProblems() != null && cu.getProblems().length > 0)
                        {
                            for (IProblem prob : cu.getProblems())
                            {
                                if (prob.isWarning())
                                    continue;
                                log("    Compile Error! " + prob.toString());
                            }
                        }

                        int[] newCode = getNewCodeRanges(cu, data);

                        SymbolReferenceWalker walker = new SymbolReferenceWalker(emitter, null, newCode);
                        walker.walk(cu);
                    }

                    log("endProcessing \"" + path + "\"");
                    log("");
                }

                stream.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace(errorLogger);
        }

        cleanup();
        return true;
    }

    private void cleanup()
    {
        outFile.close();
        outFile = null;
    }

    @Override
    protected void log(String s)
    {
        if (outFile != null)
            outFile.println(s);
        outLogger.println(s);
    }

    private int[] getNewCodeRanges(CompilationUnit cu, String data)
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
                            log("Unmatched newcode start: " + cmt.getStartPosition() + ": " + comment);
                        inside = true;
                    }
                    else if (command.equalsIgnoreCase("end"))
                    {
                        ret.add(cmt.getStartPosition());
                        if (!inside)
                            log("Unmatched newcode end: " + cmt.getStartPosition() + ": " + comment);
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
                                log("Unmatched newcode start: " + cmt.getStartPosition() + ": " + comment);
                            inside = true;
                        }
                        else if (command.equalsIgnoreCase("end"))
                        {
                            ret.add(cmt.getStartPosition());
                            if (!inside)
                                log("Unmatched newcode end: " + cmt.getStartPosition() + ": " + comment);
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

    public InputSupplier getSrc()
    {
        return src;
    }

    public RangeExtractor setSrcRoot(File srcRoot)
    {
        if (srcRoot.isDirectory())
            src = new FolderSupplier(srcRoot);

        return this;
    }

    public RangeExtractor setSrc(InputSupplier supplier)
    {
        src = supplier;
        return this;
    }

    private String[] libArray = null;
    /**
     * @param lib Either a directory or a file
     */
    public RangeExtractor addLibs(File lib)
    {
        if (lib.isDirectory())
        {
            libArray = null;
            libs.add(lib); // Root directories, for dev time classes.
            for (File f : lib.listFiles())
                addLibsRecursive(f); // Recursively scan for jar files, to keep backwards compatibility with specifying a 'libs folder'
        }
        else if (lib.getPath().endsWith(".jar"))
        {
            libArray = null;
            libs.add(lib);
        }
        else
        {
            log("Unsupposrted library path: " + lib.getAbsolutePath());
        }

        return this;
    }

    private void addLibsRecursive(File lib)
    {
        if (lib.isDirectory())
        {
            for (File f : lib.listFiles())
                addLibsRecursive(f);
        }
        else if (lib.getPath().endsWith(".jar"))
        {
            libArray = null;
            libs.add(lib);
        }
    }

    /**
     * @param path Either a directory or a file
     */
    public RangeExtractor addLibs(String path)
    {
        if (path.contains(File.pathSeparator))
            for (String f : Splitter.on(File.pathSeparatorChar).splitToList(path))
                addLibs(new File(f));
        else
            addLibs(new File(path));

        return this;
    }

    public Set<File> getLibs()
    {
        return libs;
    }

    private String src_root_cache = "";
    private ASTParser getParser(String root)
    {
        //Apparently this breaks shit, no clue why..
        //if (parser != null && src_root_cache.equals(root))
        //    return parser;

        // convert libs list
        if (libArray == null)
        {
            libArray = new String[libs.size()];
            int i = 0;
            for (File f : libs)
                libArray[i++] = f.getAbsolutePath();
        }

        src_root_cache = root;
        parser = Util.createParser(java_version, src_root_cache, libArray);
        return parser;
    }

    public void loadCache(InputStream stream) throws IOException
    {
        FileCache file = null;
        for (String line : CharStreams.readLines(new InputStreamReader(stream)))
        {
            if (line.startsWith("startProcessing"))
            {
                line = line.substring("startProcessing \"".length());
                file = new FileCache();
                file.path = line.substring(0, line.indexOf('"'));
                file.md5 = line.substring(file.path.length() + 7);
            }
            else if (line.startsWith("endProcessing"))
            {
                if (file == null)
                    continue; // End without start?

                line = line.substring("endProcessing \"".length());
                String path = line.substring(0, line.indexOf('"'));

                if (path.equals(file.path))
                    this.file_cache.put(path, file);

                file = null;
            }
            else if ("Cache Hit!".equals(line))
            {
                // Nom this up so we dont get it repeating.
            }
            else if (file != null)
            {
                file.lines.add(line);
            }
        }
    }

    public int getCacheHits()
    {
        return this.cache_hits;
    }

    private static final class FileCache
    {
        private String path;
        private String md5;
        private List<String> lines = Lists.newArrayList();
    }
}
