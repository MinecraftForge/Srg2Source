/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.test;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import net.minecraftforge.srg2source.api.RangeApplierBuilder;
import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.api.SourceVersion;
import net.minecraftforge.srg2source.extract.RangeExtractor;
import net.minecraftforge.srg2source.util.TransformingUtil;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IRenamer;
import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.IPackage;

public class MinecraftTest {
    //Make a clone of https://github.com/MinecraftForge/MCPConfig
    //Run the command: gradlew [MC_VERSION]:projectJoinedApplyPatches
    //It will make all necessary files for this test.

    private static final Path MCP_ROOT = Paths.get("Z:/Projects/MCPConfig/");
    private static final String MC_VERSION = "1.21.4";
    private static final OutputStream NULL_OUTPUT = new OutputStream() { public void write(int b) throws IOException { } };

    /**
     * We have to patch the JDT to allow custom source providers. So we can process jar files instead of extracting them.
     * So we have to keep at arms length and use ModLauncher
     */
    @Test
    public void testMCP() throws Exception {
        if (!Files.exists(MCP_ROOT))
            return;

        var tcl = TransformingUtil.createTransformer();

        Class<?> cls = Class.forName(getClass().getName() + "$Redefined", true, tcl);

        long legacy  = (Long)cls.getDeclaredMethod("extract", boolean.class).invoke(cls.getConstructor(String.class).newInstance(MC_VERSION), true);
        long batched = (Long)cls.getDeclaredMethod("extract", boolean.class).invoke(cls.getConstructor(String.class).newInstance(MC_VERSION), false);
        long apply   = (Long)cls.getDeclaredMethod("apply")                 .invoke(cls.getConstructor(String.class).newInstance(MC_VERSION));

        System.out.println("Legacy  Time: " + legacy);
        System.out.println("Batched Time: " + batched);
        System.out.println("Apply   Time: " + apply);
    }

    public static class Redefined {
        private static final Pattern SRG_FINDER = Pattern.compile("((?:func_|m_)[0-9]+_[a-zA-Z_]*?|(?:field_|f_)[0-9]+_[a-zA-Z_]*?|p_[\\w]+_\\d+_)\\b");
        private final String mcver;
        private final Path root;

        public Redefined(String MCVersion) throws IOException {
            this.mcver = MCVersion;
            this.root = Paths.get("build/test/mcptest/" + mcver);
            if (!Files.exists(root))
                Files.createDirectories(root);

            Assertions.assertTrue(RangeExtractor.hasBeenASMPatched(), "Range Extractor not Patched!");
        }

        private Path mcpBuild(String path) {
            return MCP_ROOT.resolve("build/versions/" + mcver + '/' + path);
        }
        private Path mcpData(String path) {
            var ret = MCP_ROOT.resolve("versions/release/" + mcver + '/' + path);
            if (!Files.exists(ret)) {
                // Could be a pre-release
                int idx = mcver.indexOf('.', 2);
                if (idx == -1)
                    throw new IllegalStateException("Missing MCP Data");
                ret = MCP_ROOT.resolve("versions/pre/" + mcver.substring(0, idx) + '/' + mcver + '/' + path);
            }
            return ret;
        }

        public long extract(boolean forceOld) throws Exception {
            List<Path> libraries = gatherLibraries();
            Path src_srg = createSourceJar();
            Assertions.assertTrue(Files.exists(src_srg), "Missing SRG Source jar: " + src_srg);

            Path src_mcp = getMappedSrc(src_srg);

            try (PrintStream log = new PrintStream(NULL_OUTPUT /*Files.newOutputStream(root.resolve("extract_" + (forceOld ? "legacy" : "batched") + ".log"))*/)) {
                RangeExtractorBuilder builder = new RangeExtractorBuilder()
                    .input(ZipInputSupplier.create(src_mcp, StandardCharsets.UTF_8))
                    .batch(!forceOld)
                    .output(root.resolve("extract_" + (forceOld ? "legacy" : "batched") + ".txt"))
                    .sourceCompatibility(SourceVersion.JAVA_21)
                    .failOnError()
                    .logger(log);

                libraries.forEach(l -> {
                    System.out.println("Library: " + l);
                    builder.library(l.toFile());
                });


                long startTime = System.currentTimeMillis();
                builder.build().run();
                long duration = System.currentTimeMillis() - startTime;

                return duration;
            }
        }

        public long apply() throws Exception {
            Path src_srg = createSourceJar();
            Assertions.assertTrue(Files.exists(src_srg), "Missing SRG Source jar: " + src_srg);

            Path src_mcp = getMappedSrc(src_srg);
            Path mcp_to_srg = getMcpToSrg();
            Path exc = getMcpExc();

            try (PrintStream log = new PrintStream(NULL_OUTPUT /*Files.newOutputStream(root.resolve("apply.log"))*/)) {
                RangeApplierBuilder builder = new RangeApplierBuilder()
                    .input(ZipInputSupplier.create(src_mcp, StandardCharsets.UTF_8))
                    .output(root.resolve("apply_output.jar"))
                    .range(root.resolve("extract_batched.txt"))
                    .srg(mcp_to_srg)
                    .exc(exc)
                    .keepImports()
                    .logger(log);

                long startTime = System.currentTimeMillis();
                builder.build().run();
                long duration = System.currentTimeMillis() - startTime;

                try (ZipInputSupplier clean = ZipInputSupplier.create(src_mcp , StandardCharsets.UTF_8);
                     ZipInputSupplier zout = ZipInputSupplier.create(root.resolve("apply_output.jar"), StandardCharsets.UTF_8)) {
                    for (String path : clean.gatherAll(".java")) {
                        String c = new String(Util.readStream(clean.getInput(path)), StandardCharsets.UTF_8);
                        String o = new String(Util.readStream(clean.getInput(path)), StandardCharsets.UTF_8);
                        Assertions.assertEquals(c, o, "Output Mismatch: " + path);
                    }
                }
                return duration;
            }
        }

        // We want to have a recompile code, so we need to package MCPConfig's patched files.
        private Path createSourceJar() throws IOException {
            Path target = root.resolve("compileable.jar");
            if (Files.exists(target))
                return target;

            Map<String, String> env = new HashMap<>();
            env.put("create", "true");
            URI uri = URI.create("jar:file:/" + target.toAbsolutePath().toString().replace('\\', '/'));
            System.out.println("jar:file:/" + target.toAbsolutePath().toString().replace('\\', '/'));
            for (var src : new Path[] { mcpData("projects/joined/src/main/java/"), mcpData("projects/shared/") }) {
                if (!Files.exists(src))
                    continue;

                try (FileSystem zipfs = FileSystems.newFileSystem(uri, env);
                    Stream<Path> stream = Files.walk(src)) {
                    for (Path p : stream.filter(f -> !Files.isDirectory(f)).collect(Collectors.toList())) {
                        Path zfile = zipfs.getPath(src.relativize(p).toString());
                        if (!Files.exists(zfile.getParent()))
                            Files.createDirectories(zfile.getParent());
                        Files.copy(p, zfile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            return target;
        }

        private List<Path> gatherLibraries() throws IOException {
            Path cfg = mcpBuild("joined.fernflower.libs.txt");
            Assertions.assertTrue(Files.exists(cfg), "Could not find library list: " + cfg);

            List<Path> libs = Files.readAllLines(cfg).stream().filter(l -> l.startsWith("-e="))
                    .map(l -> Paths.get(l.substring(3))).collect(Collectors.toList());
            libs.forEach(l -> Assertions.assertTrue(Files.exists(l), "Missing Library: " + l));
            return libs;
        }

        //So, currently there is no real way to identify lambda arguments... so we need to figure that out.
        //For the time being we need to figure out how to NOT remap lambda arguments so the test will pass..
        private String makeName(String srg) {
            String[] pts = srg.split("_");
            switch (pts[0]) {
                case "func", "m": return "a_" + pts[1] + '_';
                case "field", "f": return "b_" + pts[1] + '_';
                case "p": return "c_" + pts[1] + '_' + pts[2] + '_';
            }
            throw new IllegalArgumentException("Unknown SRG: " + srg);
        }

        private Path getMappedSrc(Path src_srg) throws IOException {
            Path target = root.resolve("mapped.jar");
            //TODO: Check MD5's?
            if (Files.exists(target))
                return target;

            final Map<String, String> mappings = new HashMap<>();

            try (ZipFile input = new ZipFile(src_srg.toFile());
                 ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target.toFile())))) {
                input.stream().forEach(entry -> {
                    try {
                        ZipEntry new_entry = new ZipEntry(entry.getName());
                        new_entry.setTime(0);
                        output.putNextEntry(new_entry);
                        if (entry.getName().endsWith(".java")) {

                            BufferedReader reader = new BufferedReader(new InputStreamReader(input.getInputStream(entry)));
                            String line = null;
                            while (reader.ready()) {
                                if (line != null)
                                    output.write('\n');
                                line = reader.readLine();

                                StringBuffer buf = new StringBuffer(line.length());
                                Matcher matcher = SRG_FINDER.matcher(line);
                                while (matcher.find())
                                    matcher.appendReplacement(buf, mappings.computeIfAbsent(matcher.group(), this::makeName));
                                matcher.appendTail(buf);
                                output.write(buf.toString().getBytes(StandardCharsets.UTF_8));
                            }

                        }
                        output.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return target;
        }

        private Path getMcpToSrg() throws IOException {
            Path target = root.resolve("mapped_to_srg.tsrg");
            //TODO: Check MD5's?
            if (Files.exists(target))
                return target;

            Path source = mcpData("joined.tsrg");
            try (InputStream input = Files.newInputStream(source)) {
                IMappingFile.load(input).reverse().rename(new IRenamer() {
                    @Override
                    public  String rename(IPackage value) {
                        return value.getOriginal();
                    }

                    @Override
                    public  String rename(IClass value) {
                        return value.getOriginal();
                    }

                    @Override
                    public String rename(IField value) {
                        if (value.getOriginal().startsWith("field_") || value.getOriginal().startsWith("f_"))
                            return Redefined.this.makeName(value.getOriginal());
                        return value.getOriginal();
                    }

                    @Override
                    public String rename(IMethod value) {
                        if (value.getOriginal().startsWith("func_") || value.getOriginal().startsWith("m_"))
                            return Redefined.this.makeName(value.getOriginal());
                        return value.getOriginal();
                    }
                }).reverse().write(target, IMappingFile.Format.TSRG, false);
            }
            return target;
        }

        private Path getMcpExc() throws IOException {
            Path target = root.resolve("mapped.exc");
            //TODO: Check MD5's?
            if (Files.exists(target))
                return target;

            List<String> ret = new ArrayList<>();

            Path static_mtds = mcpBuild("data/static_methods.txt");
            if (Files.exists(static_mtds)) {
                Set<String> statics = Files.lines(static_mtds, StandardCharsets.UTF_8).map(l -> l.split("#")[0]).filter(l -> !l.trim().isEmpty()).collect(Collectors.toSet());

                try (InputStream input = Files.newInputStream(mcpData("joined.tsrg"))) {
                    IMappingFile srg = IMappingFile.load(input);
                    srg.getClasses().stream().flatMap(c -> c.getMethods().stream())
                    .filter(m -> m.getMapped().startsWith("func_") && !m.getDescriptor().contains("()")).forEach(m ->
                        ret.add(m.getParent().getMapped() + '.' + Redefined.this.makeName(m.getMapped()) + m.getMappedDescriptor() + "=|" +
                            buildArgs(m.getMapped(), m.getDescriptor(), statics.contains(m.getMapped())))
                    );
                }
            }

            Path ctrs = mcpData("constructors.txt");
            if (Files.exists(ctrs)) {
                Files.lines(ctrs, StandardCharsets.UTF_8).map(l -> l.split(" ")).forEach(pts ->
                    ret.add(pts[1] + ".<init>" + pts[2] + "=|" + buildArgs(pts[0], pts[2], false))
                );
            }

            try (PrintStream out = new PrintStream(new FileOutputStream(target.toFile()))) {
                ret.forEach(l -> out.print(l + "\n"));
            }

            return target;
        }

        private static String buildArgs(String name, String desc, boolean isStatic) {
            String prefix = "p_i" + name + "_";
            if (name.startsWith("func_")) {
                prefix = "p_" + name.split("_")[1] + "_";
            }
            List<String> ret = new ArrayList<String>();
            int idx = isStatic ? 0 : 1;
            for (Type arg : Type.getArgumentTypes(desc)) {
                ret.add(prefix + idx + '_');
                idx += arg.getSize();
            }

            return ret.stream().collect(Collectors.joining(","));
        }
    }
}
