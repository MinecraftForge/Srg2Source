package net.minecraftforge.srg2source.util.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.srg2source.util.Util;

public class ZipInputSupplier implements InputSupplier
{
    protected final HashMap<String, byte[]> data = new HashMap<String, byte[]>();
    protected String root;

    public ZipInputSupplier(){}
    public ZipInputSupplier(File zip) throws IOException
    {
        readZip(zip);
    }

    public void readZip(File zip) throws IOException
    {
        root = zip.getCanonicalPath();

        // begin reading jar
        final ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        ZipEntry entry;

        while ((entry = zin.getNextEntry()) != null)
            data.put(entry.getName(), Util.readStream(zin));

        zin.close();
    }

    @Override
    public void close() throws IOException
    {
        // useless here, since we read the zip ahead of time.
        // I wonder when this might actually be useful..
    }

    @Override
    public InputStream getInput(String relPath)
    {
        try
        {
            return new ByteArrayInputStream(data.get(relPath));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        LinkedList<String> out = new LinkedList<String>();

        for (String key : data.keySet())
            if (key.endsWith(endFilter))
                out.add(key);

        return out;
    }

    @Override
    public String getRoot(String resource)
    {
        return root;
    }

}
