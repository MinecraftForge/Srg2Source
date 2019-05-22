package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.api.SourceVersion;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.Util;

public class SingleTests
{
    @Test
    public void testGenerics() throws IOException
    {
        testClass("GenericClasses");
    }
    @Test
    public void testLambda() throws IOException
    {
        String version = System.getProperty("java.version");
        int java_version = Integer.parseInt(version.split("\\.")[version.startsWith("1.") ? 1 : 0]);
        if (java_version >= 8)
            testClass("Lambda");
    }

    @Test
    public void testAnonClass() throws IOException
    {
        testClass("AnonClass");
    }

    @Test
    public void testInnerClass() throws IOException
    {
        testClass("InnerClass");
    }

    @Test
    public void testLocalClass() throws IOException
    {
        testClass("LocalClass");
    }

    @Test
    public void testPackageInfo() throws IOException
    {
        testClass("PackageInfo", "test.package-info");
    }

    @Test
    public void testCache() throws IOException
    {
        testClass("GenericClasses", "GenericClasses", true);
    }

    @Test
    public void testWhiteSpace() throws IOException
    {
        testClass("Whitespace", "core.package-info");
    }

    public void testClass(final String resource) throws IOException
    {
        testClass(resource, resource, false);
    }
    public void testClass(final String resource, final String clsName) throws IOException
    {
        testClass(resource, clsName, false);
    }

    public void testClass(final String resource, final String clsName, boolean loadCache) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        RangeExtractor extractor = new RangeExtractorBuilder()
            .sourceCompatibility(SourceVersion.JAVA_1_8)
            .input(new SimpleInputSupplier(resource, clsName))
            .output(new PrintWriter(bos))
            .build();


        if (loadCache)
            extractor.loadCache(getClass().getResourceAsStream("/" + resource + "_ret.txt"));

        boolean worked = extractor.run();

        Assert.assertTrue("Failed to do work!", worked);
        Assert.assertEquals(getFileContents(resource, "_ret.txt"), bos.toString().replaceAll("\r?\n", "\n").replaceAll("Cache Hit!\n", ""));
        if (loadCache)
            Assert.assertTrue("Cache Missed!", extractor.getCacheHits() == 1);

        testApply(resource, clsName);
    }

    private void testApply(final String resource, final String clsName) throws IOException
    {
        File srg = null;
        File map = null;
        try {
            URL url = getClass().getResource("/" + resource + "_srg.txt");
            if (url == null)
                return;
            srg = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new FileNotFoundException("/" + resource + "_srg.txt");
        }
        try {
            map = new File(getClass().getResource("/" + resource + "_ret.txt").toURI());
        } catch (URISyntaxException e) {
            throw new FileNotFoundException("/" + resource + "_ret.txt");
        }
        if (!srg.exists())
            return;

        MemoryOutputSupplier out = new MemoryOutputSupplier();
        RangeApplier applier = new RangeApplier();
        applier.readSrg(srg);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        applier.setLogger(new PrintStream(bos));
        applier.setInput(new SimpleInputSupplier(resource, clsName));
        applier.setOutput(out);
        applier.annotate(true);
        applier.readRangeMap(map);

        applier.run();
        Assert.assertEquals(getFileContents(resource, "_maped.txt"), out.get(0));
        Assert.assertEquals(getFileContents(resource, "_maped_ret.txt"), bos.toString().replaceAll("\r?\n", "\n"));
    }

    private String getFileContents(String resource, String suffix) throws IOException {
        File result = new File(getClass().getResource("/" + resource + suffix).getFile());
        return  new String(Util.readFile(result), StandardCharsets.UTF_8);
    }
}
