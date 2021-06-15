/*
 * Srg2Source
 * Copyright (c) 2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srg2source.extract;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.srg2source.api.InputSupplier;
import net.minecraftforge.srg2source.api.SourceVersion;
import net.minecraftforge.srg2source.range.RangeMap;
import net.minecraftforge.srg2source.range.RangeMapBuilder;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.ConfLogger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

public class RangeExtractor extends ConfLogger<RangeExtractor> {
    private static RangeExtractor INSTANCE = null;

    private PrintWriter output;
    private String sourceVersion;
    private boolean enableBatchedASTs = true;
    private final Set<File> libs = new LinkedHashSet<File>();
    private String[] libArray = null; //A cache of libs, so we don't have to re-build it over and over.

    private InputSupplier input;

    private Map<String, RangeMap> file_cache = new HashMap<>();
    private int cache_hits = 0;
    private boolean enableMixins = false;
    private boolean fatalMixins = false;
    private boolean logWarnings = false;
    private boolean enablePreview = false;

    public RangeExtractor(){}

    public void setOutput(PrintWriter value) {
        this.output = value;
    }

    public void setSourceCompatibility(SourceVersion value) {
        this.sourceVersion = value.getSpec();
    }

    public void setBatchASTs(boolean value) {
        this.enableBatchedASTs = value;
    }

    public void enableMixins() {
        this.enableMixins = true;
    }

    public void fatalMixins() {
        this.fatalMixins = true;
    }
    public boolean areMixinsFatal() {
        return this.fatalMixins;
    }
    public void logWarnings() {
        this.logWarnings = true;
    }
    public void enablePreview() {
        this.enablePreview = true;
    }

    public void addLibrary(File value) {
        String fileName = value.getPath().toLowerCase(Locale.ENGLISH);
        if (!value.exists()) {
            error("Missing Library: " + value.getAbsolutePath());
        } else if (value.isDirectory()) {
            libArray = null;
            libs.add(value); // Root directories, for dev time classes.
        } else if (fileName.endsWith(".jar") || fileName.endsWith(".jar")) {
            libArray = null;
            libs.add(value);
        } else
            log("Unsupposrted library path: " + value.getAbsolutePath());
    }

    public void setInput(InputSupplier supplier) {
        this.input = supplier;
    }

    public void loadCache(InputStream stream) throws IOException {
        this.file_cache = RangeMap.readAll(stream);
    }

    @Override //Log everything as a comment in case we merge the output and log as we used to do.
    public void log(String message) {
        super.log("# " + message);
    }

    /**
     * Generates the rangemap.
     */
    public boolean run() {
        log("Symbol range map extraction starting");

        String[] files = input.gatherAll(".java").stream()
                .map(f -> f.replaceAll("\\\\", "/")) // Normalize directory separators.
                .sorted()
                .toArray(String[]::new);
        log("Processing " + files.length + " files");

        if (files.length == 0) {
            // no files? well.. nothing to do then.
            cleanup();
            return true;
        }

        if (canBatchASTs())
            return batchGenerate(files);
        else
            return legacyGenerate(files);
    }

    private boolean legacyGenerate(String[] files) {
        try {
            for (String path : files) {
                Charset encoding = input.getEncoding(path);
                if (encoding == null)
                    encoding = StandardCharsets.UTF_8;

                try (InputStream stream = input.getInput(path)) {
                    String data = new String(Util.readStream(stream), encoding);
                    String md5 = Util.md5(data, encoding);
                    RangeMapBuilder builder = new RangeMapBuilder(this, path, md5);

                    log("startProcessing \"" + path + "\" md5: " + md5);

                    RangeMap cache = this.file_cache.get(path);
                    if (builder.loadCache(cache)) {
                        log("Cache Hit!");
                        RangeExtractor.this.cache_hits++;
                    } else {
                        ASTParser parser = createParser(input.getRoot(path));
                        parser.setUnitName(path);
                        parser.setSource(data.toCharArray());
                        CompilationUnit cu = (CompilationUnit)parser.createAST(null);
                        if (cu.getProblems() != null && cu.getProblems().length > 0)
                            Arrays.stream(cu.getProblems()).filter(p -> !p.isWarning()).forEach(p -> log("   Compile Error! " + p.toString()));

                        SymbolReferenceWalker walker = new SymbolReferenceWalker(this, builder, enableMixins);
                        walker.safeWalk(cu);
                    }

                    RangeMap range = builder.build();
                    if (output != null)
                        range.write(output, true);
                    log("endProcessing \"" + path + "\"");
                    log("");
                }
            }
        } catch (Exception e) {
            e.printStackTrace(getErrorLogger());
        }

        cleanup();
        return true;
    }

    private boolean batchGenerate(String[] files) {
        if (RangeExtractor.INSTANCE != null)
            throw new IllegalStateException("Can not do batched processing while another is running!");
        RangeExtractor.INSTANCE = this;

        //TODO: Check org.eclipse.jdt.internal.compiler.batch.FileSystem.getClasspath(String, String, boolean, AccessRuleSet, String, Map<String, String>, String)
        // That is where it loads sourceDirs as classpath entries. Try and hijack to include InputSuppliers?
        ASTParser parser = createParser(null);

        FileASTRequestor requestor = new FileASTRequestor() {
            @Override
            public void acceptAST(String path, CompilationUnit cu) {
                path = path.replace(File.separatorChar, '/');

                Charset encoding = input.getEncoding(path);
                if (encoding == null)
                    encoding = StandardCharsets.UTF_8;

                try (InputStream stream = input.getInput(path)) {
                    String data = new String(Util.readStream(stream), encoding);
                    String md5 = Util.md5(data, encoding);

                    RangeMapBuilder builder = new RangeMapBuilder(RangeExtractor.this, path, md5);

                    log("startProcessing \"" + path + "\" md5: " + md5);

                    RangeMap cache = RangeExtractor.this.file_cache.get(path);
                    if (builder.loadCache(cache)) {
                        log("Cache Hit!");
                        RangeExtractor.this.cache_hits++;
                    } else {
                        if (cu.getProblems() != null && cu.getProblems().length > 0)
                            Arrays.stream(cu.getProblems()).filter(p -> logWarnings || !p.isWarning()).forEach(p -> log("   Compile Error! " + p.toString()));

                        SymbolReferenceWalker walker = new SymbolReferenceWalker(RangeExtractor.this, builder, enableMixins);
                        walker.safeWalk(cu);
                    }

                    RangeMap range = builder.build();
                    if (output != null)
                        range.write(output, true);
                    log("endProcessing \"" + path + "\"");
                    log("");
                } catch (IOException e) {
                    e.printStackTrace(getErrorLogger());
                }
            }
        };

        IProgressMonitor monitor = new NullProgressMonitor();

        parser.createASTs(files, null, new String[0], requestor, monitor);

        cleanup();

        RangeExtractor.INSTANCE = null;
        return true;
    }

    private void cleanup() {
        try {
            input.close();
        } catch (IOException e) {
            e.printStackTrace(getErrorLogger());
        }

        if (output != null) {
            output.flush();
            output.close();
            output = null;
        }
    }

    private String[] getLibArray() {
        if (libArray == null)
            libArray = libs.stream().map(File::getAbsolutePath).toArray(String[]::new);
        return libArray;
    }

    public int getCacheHits() {
        return this.cache_hits;
    }

    public boolean canBatchASTs() {
        return hasBeenASMPatched() && enableBatchedASTs;
    }

    private ASTParser createParser(String srcRoot) {
        ASTParser parser = ASTParser.newParser(AST.JLS15);
        parser.setEnvironment(getLibArray(), srcRoot == null ? null : new String[] {srcRoot}, null, true);
        return setOptions(parser);
    }

    private ASTParser setOptions(ASTParser parser) {
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        Hashtable<String, String> options = JavaCore.getDefaultOptions();
        JavaCore.setComplianceOptions(sourceVersion, options);
        if (enablePreview)
            options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
        parser.setCompilerOptions(options);
        return parser;
    }

    //ASM redirect for JDT's Util.getFileCharContent(File, String) to allow us to use our inputs
    public static char[] getFileCharContent(String path, String encoding) {
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
    public static boolean hasBeenASMPatched() {
        return false;
    }
}
