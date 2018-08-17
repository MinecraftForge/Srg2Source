package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

public class MinecraftTest
{
    // This must point to a Forge Dev directory.
    private static String ROOT_DIR = "Z:/Projects/Forge/Forge_110";
    private static String MC_VERSION = "1.10.2";
    private static String MAPPINGS = "20160518";

    @Test
    public void testMinecraft() throws IOException
    {
        File mc_src_clean  = new File(ROOT_DIR + "/build/localCache/decompiled-processed.zip");
        File mc_src_mapped = new File(ROOT_DIR + "/build/localCache/remapped-clean.zip");
        // After remapping mapped, it should equal src.
        if (!mc_src_clean.isFile() || !mc_src_mapped.exists())
            return; // Optional test, skip if not there.

        File RANGEMAP = new File("./MINECRAFT_TEST_RANGEMAP.txt");
        File REMAPPED = new File("./MINECRAFT_TEST_REMAPPED.zip");
        File REMAPLOG = new File("./MINECRAFT_TEST_REMAPPED.txt");
        File MAPPED   = new File("./MINECRAFT_TEST.jar");

        makeMappedJar(MAPPED, new File(ROOT_DIR + "/projects/Clean/bin/"));

        RangeExtractor extractor = new RangeExtractor(RangeExtractor.JAVA_1_8);
        extractor.setSrc(new ZipInputSupplier(mc_src_mapped));
        extractor.addLibs(MAPPED);

        for (String line : Files.readLines(new File(ROOT_DIR + "/projects/Clean/.classpath"), Charsets.UTF_8))
        {
            if (line.contains("kind=\"lib\" "))
            {
                String path = line.substring(line.indexOf(" path=\"") + 7);
                path = path.substring(0, path.indexOf("\""));
                extractor.addLibs(path);
            }
        }

        System.out.println("Libs:");
        for (File f : extractor.getLibs())
        {
            System.out.println("    " + f.getAbsolutePath());
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        boolean worked = extractor.generateRangeMap(new PrintWriter(bos));
        Assert.assertTrue("Failed to do work!" , worked);
        System.out.println("Saving rangemap to: " + RANGEMAP.getAbsolutePath());
        Files.write(bos.toByteArray(), RANGEMAP); // Save the rangemap for debugging, and for feeding into the applier

        //Now we remap it using the snapshot srgs and see if they match!
        File mappingRoot = new File(System.getProperty("user.home") + "/.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_snapshot_nodoc/" + MAPPINGS + "/" + MC_VERSION + "/srgs");// Why does FG not have mc version specific folders?

        ZipOutputSupplier out = new ZipOutputSupplier(REMAPPED);
        RangeApplier applier = new RangeApplier().readSrg(new File(mappingRoot, "mcp-srg.srg"));
        applier.readParamMap(Lists.newArrayList(new File(mappingRoot, "mcp.exc")));
        applier.setKeepImports(true);

        bos = new ByteArrayOutputStream();
        applier.setOutLogger(new PrintStream(bos));

        applier.remapSources(new ZipInputSupplier(mc_src_mapped), out, RANGEMAP, false);
        out.close();

        System.out.println("Saving rangemap log to: " + REMAPLOG.getAbsolutePath());
        Files.write(bos.toByteArray(), REMAPLOG);

        compareJars(REMAPPED, mc_src_clean);
    }

    private void makeMappedJar(File jar, File src) throws IOException
    {
        if (jar.exists())
            return; //Delete or return?

        System.out.println("Creating compiled zip:");
        int rootLen = src.getAbsolutePath().length();
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar));
        for (File f : Files.fileTraverser().depthFirstPreOrder(src))
        {
            if (f.isDirectory() || !f.getName().endsWith(".class"))
                continue;
            String name = f.getAbsolutePath().substring(rootLen + 1).replace('\\', '/');
            System.out.println("    " + name);
            zip.putNextEntry(new ZipEntry(name));
            zip.write(Files.toByteArray(f));
        }
        zip.close();
    }

    private void compareJars(File src, File target) throws IOException
    {
        System.out.println("Testing remapped zip:");
        ZipFile zsrc = new ZipFile(src);
        ZipFile ztarget = new ZipFile(target);
        for (Enumeration<? extends ZipEntry> e = zsrc.entries(); e.hasMoreElements();)
        {
            ZipEntry entry = e.nextElement();
            System.out.println("    " + entry.getName());
            String ssrc = CharStreams.toString(new InputStreamReader(zsrc.getInputStream(entry), Charsets.UTF_8)).replaceAll("\r\n", "\n");
            String starget = CharStreams.toString(new InputStreamReader(ztarget.getInputStream(entry), Charsets.UTF_8)).replaceAll("\r\n", "\n");
            //Because we dont have a new line at the end, but RangeApply add it. I should fix that...
            //"mcp/MethodsReturnNonnullByDefault.java" and generated package-info.java's
            if (!starget.endsWith("\n"))
                starget += '\n';
            Assert.assertEquals(ssrc, starget);
        }
        zsrc.close();
        ztarget.close();
    }
}
