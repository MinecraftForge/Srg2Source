package net.minecraftforge.srg2source.util.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.io.ByteStreams;

public class ZipInputSupplier implements InputSupplier
{
    private final HashMap<String, byte[]> data = new HashMap<String, byte[]>();

    public void readZip(File zip) throws IOException
    {
        // begin reading jar
        final ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        ZipEntry entry;

        while ((entry = zin.getNextEntry()) != null)
            data.put(entry.getName(), ByteStreams.toByteArray(zin));

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
            return ByteStreams.newInputStreamSupplier(data.get(relPath)).getInput();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        return Arrays.asList(data.keySet().toArray(new String[data.size()]));
    }

}
