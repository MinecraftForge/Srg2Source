package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.InputSupplier;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class GenericTest
{
    @Test
    public void testGenerics() throws IOException
    {
        RangeExtractor extractor = new RangeExtractor();
        extractor.setSrc(new InputSupplier(){
            @Override public void close() throws IOException{}
            @Override public String getRoot(String resource) { return ""; }
            @Override
            public InputStream getInput(String relPath)
            {
                try
                {
                    return getClass().getResourceAsStream(relPath);
                }
                catch (Exception e)
                {
                    return null;
                }
            }

            @Override
            public List<String> gatherAll(String endFilter)
            {
                return Arrays.asList("/GenericClasses.txt");
            }
        });

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(bos);

        boolean worked = extractor.generateRangeMap(writer);
        Assert.assertTrue("Failed to do work!" , worked);
        Assert.assertEquals(Files.toString(new File(getClass().getResource("/GenericClasses_ret.txt").getFile()), Charsets.UTF_8), bos.toString());

    }
}
