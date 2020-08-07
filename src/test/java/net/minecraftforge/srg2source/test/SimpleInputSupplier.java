package net.minecraftforge.srg2source.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import net.minecraftforge.srg2source.api.InputSupplier;

public class SimpleInputSupplier implements InputSupplier {
    String resource = null;
    String clsName = null;

    public SimpleInputSupplier(String resource, String clsName) {
        this.resource = resource;
        this.clsName = clsName;
    }

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
}
