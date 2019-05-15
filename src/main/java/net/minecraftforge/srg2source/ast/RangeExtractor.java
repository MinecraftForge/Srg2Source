package net.minecraftforge.srg2source.ast;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.srg2source.api.SourceVersion;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.ConfLogger;
import net.minecraftforge.srg2source.util.io.InputSupplier;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

@SuppressWarnings("unchecked")
public class RangeExtractor extends ConfLogger<RangeExtractor>
{
    private static RangeExtractor INSTANCE = null;

    private PrintWriter output;
    private String sourceVersion;
    private boolean enableBatchedASTs = true;
    private final Set<File> libs = new LinkedHashSet<File>();
    private String[] libArray = null; //A cache of libs, so we don't have to re-build it over and over.

    private InputSupplier input;

    private Map<String, FileCache> file_cache = new HashMap<>();
    private int cache_hits =0;

    private boolean enableNewRanges = false;

    public RangeExtractor(){}

    public void setOutput(PrintWriter value)
    {
        this.output = value;
    }

    public void setSourceCompatibility(SourceVersion value)
    {
        this.sourceVersion = value.getSpec();
    }

    public void setBatchASTs(boolean value)
    {
        this.enableBatchedASTs = value;
    }

    public void addLibrary(File value)
    {
        String fileName = value.getPath().toLowerCase(Locale.ENGLISH);
        if (value.isDirectory())
        {
            libArray = null;
            libs.add(value); // Root directories, for dev time classes.
        }
        else if (fileName.endsWith(".jar") || fileName.endsWith(".jar"))
        {
            libArray = null;
            libs.add(value);
        }
        else
            log("Unsupposrted library path: " + value.getAbsolutePath());
    }

    public void setInput(InputSupplier supplier)
    {
        this.input = supplier;
    }

    public void loadCache(InputStream stream) throws IOException
    {
        FileCache file = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line = null;
            while ((line = reader.readLine()) != null)
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
    }

    /**
     * Generates the rangemap.
     */
    public boolean run()
    {
        log("Symbol range map extraction starting");

        List<String> tmp = input.gatherAll(".java");
        Collections.sort(tmp);
        String[] files = tmp.toArray(new String[tmp.size()]);
        log("Processing " + files.length + " files");

        if (files.length == 0)
        {
            // no files? well.. nothing to do then.
            cleanup();
            return true;
        }

        if (canBatchASTs())
            return batchGenerate(files);
        else
            return legacyGenerate(files);
    }

    private boolean legacyGenerate(String[] files)
    {
        try
        {
            for (String path : files)
            {
                Charset encoding = input.getEncoding(path);
                if (encoding == null)
                    encoding = StandardCharsets.UTF_8;

                try (InputStream stream = input.getInput(path))
                {
                    SymbolRangeEmitter emitter = new SymbolRangeEmitter(path, output);
                    String data = new String(Util.readStream(stream), encoding).replaceAll("\r", "");
                    String md5 = Util.md5(data, encoding);

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
                        ASTParser parser = Util.createParser(sourceVersion, input.getRoot(path), getLibArray());
                        CompilationUnit cu = Util.createUnit(parser, sourceVersion, path, data.toCharArray());

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
            }
        }
        catch (Exception e)
        {
            e.printStackTrace(errorLogger);
        }

        cleanup();
        return true;
    }

    private boolean batchGenerate(String[] files)
    {
        if (RangeExtractor.INSTANCE != null)
            throw new IllegalStateException("Can not do batched processing while another is running!");
        RangeExtractor.INSTANCE = this;

        //TODO: Check org.eclipse.jdt.internal.compiler.batch.FileSystem.getClasspath(String, String, boolean, AccessRuleSet, String, Map<String, String>, String)
        // That is where it loads sourceDirs as classpath entries. Try and hijack to include InputSuppliers?
        ASTParser parser = Util.createParser(sourceVersion, getLibArray());

        FileASTRequestor requestor = new FileASTRequestor()
        {
            @Override
            public void acceptAST(String path, CompilationUnit cu)
            {
                path = path.replace(File.separatorChar, '/');

                Charset encoding = input.getEncoding(path);
                if (encoding == null)
                    encoding = StandardCharsets.UTF_8;

                try (InputStream stream = input.getInput(path)) {

                    SymbolRangeEmitter emitter = new SymbolRangeEmitter(path, output);
                    String data = new String(Util.readStream(stream), encoding).replaceAll("\r", "");
                    String md5 = Util.md5(data, encoding);

                    log("startProcessing \"" + path + "\" md5: " + md5);

                    FileCache cache = RangeExtractor.this.file_cache.get(path);
                    if (cache != null && cache.path.equals(path) && cache.md5.equals(md5))
                    {
                        log("Cache Hit!");
                        RangeExtractor.this.cache_hits++;
                        for (String line : cache.lines)
                            emitter.log(line);
                    }
                    else
                    {
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
                } catch (IOException e) {
                    if (output != null)
                        e.printStackTrace(output);
                    else
                        e.printStackTrace();
                }
            }
        };

        IProgressMonitor monitor = new NullProgressMonitor()
        {
        };

        parser.createASTs(files, null, new String[0], requestor, monitor);

        cleanup();

        RangeExtractor.INSTANCE = null;
        return true;
    }

    private void cleanup()
    {
        try
        {
            input.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if (output != null)
        {
            output.flush();
            output.close();
            output = null;
        }
    }

    @Override
    protected void log(String s)
    {
        if (output != null)
            output.println(s);
        super.log(s);
    }

    private int[] getNewCodeRanges(CompilationUnit cu, String data)
    {
        if (!enableNewRanges)
            return new int[0];

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

    private String[] getLibArray()
    {
        if (libArray == null)
            libArray = libs.stream().map(File::getAbsolutePath).toArray(String[]::new);
        return libArray;
    }

    public int getCacheHits()
    {
        return this.cache_hits;
    }

    public boolean canBatchASTs()
    {
        return hasBeenASMPatched() && enableBatchedASTs;
    }

    private static final class FileCache
    {
        private String path;
        private String md5;
        private List<String> lines = new ArrayList<>();
    }


    //ASM redirect for JDT's Util.getFileCharContent(File, String) to allow us to use our inputs
    public static char[] getFileCharContent(String path, String encoding)
    {
        RangeExtractor range = RangeExtractor.INSTANCE; //TODO: Find a way to make this non-static

        Charset charset = range.input.getEncoding(path);
        encoding = charset == null ? StandardCharsets.UTF_8.name() : charset.name();

        try(InputStream input = RangeExtractor.INSTANCE.input.getInput(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding));
        ) {
            CharArrayWriter writer = new CharArrayWriter();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf, 0, 1024)) > 0) {
                writer.write(buf, 0, len);
            }
            return writer.toCharArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This is a marker function that should always return false in code.
     * But if the ASM transformation has run, which is needed for JDT batching,
     * it will also transform this to return true.
     *
     * Welcome to the world of magic ASM hacks!
     */
    public static boolean hasBeenASMPatched()
    {
        return false;
    }

}
