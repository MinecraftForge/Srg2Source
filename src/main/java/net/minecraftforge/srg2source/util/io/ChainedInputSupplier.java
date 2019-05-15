package net.minecraftforge.srg2source.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ChainedInputSupplier implements InputSupplier
{
    private List<InputSupplier> children;

    public ChainedInputSupplier(InputSupplier... children)
    {
        this.children = Arrays.asList(children);
    }

    public ChainedInputSupplier(Collection<InputSupplier> children)
    {
        this.children = new ArrayList<>(children);
    }

    @Override
    public void close() throws IOException
    {
        for (InputSupplier child : children)
            child.close();
    }

    @Override
    public String getRoot(String resource)
    {
        String ret = null;
        for (InputSupplier child : children)
        {
            ret = child.getRoot(resource);
            if (ret != null)
                break;
        }
        return ret;
    }

    @Override
    public InputStream getInput(String resource)
    {
        InputStream ret = null;
        for (InputSupplier child : children)
        {
            ret = child.getInput(resource);
            if (ret != null)
                break;
        }
        return ret;
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        Set<String> ret = new HashSet<>();
        children.forEach(c -> ret.addAll(c.gatherAll(endFilter)));
        return ret.stream().collect(Collectors.toList());
    }

    @Override
    public Charset getEncoding(String resource)
    {
        Charset ret = null;
        for (InputSupplier child : children)
        {
            ret = child.getEncoding(resource);
            if (ret != null)
                break;
        }
        return ret;
    }

}
