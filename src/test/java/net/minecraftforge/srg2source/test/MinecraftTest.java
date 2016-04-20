package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

public class MinecraftTest
{
    // This must point to a 1.9.0 Forge Dev directory.
    private static String ROOT_DIR = "Z:/Projects/Forge/Forge_19";
    @Test
    public void testMinecraft() throws IOException
    {
        File mc_src = new File(ROOT_DIR + "/build/localCache/decompiled-processed.zip");
        if (!mc_src.isFile())
            return; // Optional test, skip if not there.
        File zip = new File(ROOT_DIR + "/build/localCache/deobfuscated.zip");
        File jar = new File(ROOT_DIR + "/build/localCache/deobfuscated.jar");
        if (!jar.exists())
            Files.copy(zip, jar);

        /* Disabled for now until I write a simple bytecode remapper so I can create the compile lib
        //Now we need to load the SRG up...
        List<String> srg = Files.readLines(new File(System.getProperty("user.home") + "/.gradle/caches/minecraft/de/oceanlabs/mcp/mcp/1.9/joined.srg"), Charsets.UTF_8);
        //Lets make some random mappings
        final Map<String, String> mappings = Maps.newHashMap();
        for (String line : srg)
        {
            String pts[] = line.split(" ");
            if ("FD:".equals(pts[0]))
            {
                String name = pts[2].substring(pts[2].lastIndexOf('/') + 1);
                if (name.startsWith("field_"))
                    mappings.put(name, "fmap" + name.substring(5));
            }
            else if ("MD:".equals(pts[0]))
            {
                String name = pts[3].substring(pts[3].lastIndexOf('/') + 1);
                if (name.startsWith("func_"))
                    mappings.put(name, "mmap" + name.substring(4));
            }
        }

        InputSupplier remapped = new ZipInputSupplier(mc_src)
        {
            @Override
            public InputStream getInput(String relPath)
            {
                try
                {
                    String data = new String(ByteStreams.toByteArray(super.getInput(relPath)), Charsets.UTF_8);
                    for (Entry<String, String> e : mappings.entrySet())
                    {
                        data = data.replaceAll(e.getKey(), e.getValue());
                    }
                    return ByteSource.wrap(data.getBytes()).openStream();
                }
                catch (Exception e)
                {
                    Throwables.propagate(e);
                }
                return null;
            }
        };
        */

        RangeExtractor extractor = new RangeExtractor(RangeExtractor.JAVA_1_8);
        extractor.setSrc(new ZipInputSupplier(mc_src));
        extractor.addLibs(jar);

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
        System.out.println(bos.toString());
        //Assert.assertEquals(Files.toString(new File(getClass().getResource("/" + resource + "_ret.txt").getFile()), Charsets.UTF_8), bos.toString());

    }

}
