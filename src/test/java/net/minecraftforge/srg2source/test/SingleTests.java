package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.InputSupplier;

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
        testClass("Lambda");
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
        RangeExtractor extractor = new RangeExtractor(RangeExtractor.JAVA_1_8);
        if (loadCache)
        {
            extractor.loadCache(getClass().getResourceAsStream("/" + resource + "_ret.txt"));
        }
        extractor.setSrc(new InputSupplier(){
            @Override public void close() throws IOException{}
            @Override public String getRoot(String resource) { return ""; }
            @Override
            public InputStream getInput(String relPath)
            {
                try
                {
                    return getClass().getResourceAsStream("/" + resource + ".txt");
                }
                catch (Exception e)
                {
                    return null;
                }
            }

            @Override
            public List<String> gatherAll(String endFilter)
            {
                return Arrays.asList("/" + clsName.replace('.', '/') + ".java");
            }
        });

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(bos);

        boolean worked = extractor.generateRangeMap(writer);
        Assert.assertTrue("Failed to do work!", worked);
        Assert.assertEquals(Files.toString(new File(getClass().getResource("/" + resource + "_ret.txt").getFile()), Charsets.UTF_8), bos.toString().replaceAll("Cache Hit!\r?\n", ""));
        if (loadCache)
            Assert.assertTrue("Cache Missed!", extractor.getCacheHits() == 1);

    }
}
