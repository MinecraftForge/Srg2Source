package net.minecraftforge.srg2source.test;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Test;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServiceDecorator;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.ITransformationService;
import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.asm.TransformationService;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

public class MinecraftTest
{
    //Make a clone of https://github.com/MinecraftForge/MCPConfig
    //Run the command: gradlew [MC_VERSION]:projectJoinedReset
    //It will make all necessary files for this test.

    private static final Path MCP_ROOT = Paths.get("Z:/Projects/MCP/MCPConfig/");
    private static final String MC_VERSION = "1.13.2";
    private static final String MAPPING_VERSION = "20190513-1.13.2";

    @Test
    public void testMCP() throws Exception {
        if (!Files.exists(MCP_ROOT))
            return;

        TransformStore transformStore = new TransformStore();
        Constructor<TransformationServiceDecorator> ctr = TransformationServiceDecorator.class.getDeclaredConstructor(ITransformationService.class);
        ctr.setAccessible(true);
        TransformationServiceDecorator sd = ctr.newInstance(new TransformationService());
        sd.gatherTransformers(transformStore);
        Path[] targetPaths = new Path[] {
            getClassRoot("org/eclipse/jdt/core/dom/CompilationUnitResolver"),
            getClassRoot(getClass().getName()),
            getClassRoot(RangeExtractor.class.getName())
        };
        TransformingClassLoader tcl = new TransformingClassLoader(transformStore, new LaunchPluginHandler(), targetPaths);

        Class<?> cls = Class.forName(getClass().getName() + "$Redefined", true, tcl);
        cls.getDeclaredMethod("run", boolean.class).invoke(cls.getConstructor().newInstance(), false);

    }

    private Path getClassRoot(String cls) {
        URL url = this.getClass().getResource("/" + cls.replace('.', '/') + ".class");
        if (url == null)
            return null;
        String path = url.toString().substring(0, url.toString().length() - cls.length() - 6);
        if ("jar".equals(url.getProtocol()) && path.endsWith("!/"))
            path = path.substring(4, path.length() - 2);
        if (path.startsWith("file:"))
            path = path.substring(6);
        return Paths.get(path);
    }

    public static class Redefined {
        private static final Pattern SRG_FINDER = Pattern.compile("func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_\\b");

        public void run(boolean forceOld) throws Exception {
            List<Path> libraries = gatherLibraries();
            File mapping_file = getMappingFile();
            Map<String, String> mappings = getMappings(mapping_file);
            Path src_srg = MCP_ROOT.resolve("build/versions/" + MC_VERSION + "/" + MC_VERSION + ".joined.decomp.jar");
            Assert.assertTrue("Missing SRG Source jar: " + src_srg, Files.exists(src_srg));
            Path src_mcp = getMappedSrc(src_srg, mappings);

            RangeExtractorBuilder builder = new RangeExtractorBuilder()
                .input(new ZipInputSupplier(src_mcp.toFile()))
                .batch(!forceOld)
                .output(new File("build/test/mcptest/extract_" + (forceOld ? "legacy" : "batched") + ".txt"));

            libraries.forEach(l -> {
                System.out.println("Library: " + l);
                builder.library(l.toFile());
            });


            long startTime = System.currentTimeMillis();
            builder.build().run();
            long duration = System.currentTimeMillis() - startTime;

            System.out.println((forceOld ? "legacy" : "batched") + " Time: " + duration);
        }

        private List<Path> gatherLibraries() throws IOException {
            Path cfg = MCP_ROOT.resolve("build/versions/" + MC_VERSION + "/" + MC_VERSION + ".joined.fernflower.libs.txt");
            Assert.assertTrue("Could not find library list: " + cfg, Files.exists(cfg));

            List<Path> libs = Files.readAllLines(cfg).stream().filter(l -> l.startsWith("-e="))
                    .map(l -> Paths.get(l.substring(3))).collect(Collectors.toList());
            libs.forEach(l -> Assert.assertTrue("Missing Library: " + l, Files.exists(l)));
            return libs;
        }

        private File getMappingFile() throws IOException {
            File target = new File("build/test/mcptest/mcp_snapshot-" + MAPPING_VERSION + ".zip");
            if (target.exists())
                return target;

            if (!target.getParentFile().exists())
                target.getParentFile().mkdirs();

            URL url = new URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/" + MAPPING_VERSION + "/mcp_snapshot-" + MAPPING_VERSION + ".zip");
            try (InputStream source = url.openStream()) {
                Files.copy(source, target.toPath());
            }

            return target;
        }

        private Map<String, String> getMappings(File mapping_file) throws IOException {
            Map<String, String> mapping = new HashMap<>();
            try (ZipFile zip = new ZipFile(mapping_file)) {
                zip.stream().filter(e -> !e.isDirectory() && e.getName().endsWith(".csv")).forEach(e -> {
                    CsvReader reader = new CsvReader();
                    reader.setContainsHeader(true);
                    try {
                        CsvContainer csv = reader.read(new InputStreamReader(zip.getInputStream(e)));
                        csv.getRows().forEach(row -> {
                            String srg = row.getField("searge") == null ? row.getField("param") : row.getField("searge");
                            mapping.put(srg, row.getField("name"));
                        });
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                });
            }
            return mapping;
        }

        private Path getMappedSrc(Path src_srg, Map<String, String> mappings) throws IOException {
            Path target = Paths.get("build/test/mcptest/" + MC_VERSION + ".mapped." + MAPPING_VERSION + ".jar");
            //TODO: Check MD5's?
            if (Files.exists(target))
                return target;

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
                                    matcher.appendReplacement(buf, mappings.computeIfAbsent(matcher.group(), k -> k));
                                matcher.appendTail(buf);
                                output.write(buf.toString().getBytes(StandardCharsets.UTF_8));
                            }

                        } else
                            Util.transferTo(input.getInputStream(entry), output);
                        output.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return target;
        }
    }
}
