package net.minecraftforge.srg2source.util.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipOutputSupplier implements OutputSupplier
{
    private final ZipOutputStream zout;
    private EntryOutStream tempOut;

    public ZipOutputSupplier(File out) throws IOException
    {
        if (!out.exists())
        {
            out.getParentFile().mkdirs();
            out.createNewFile();
        }
        zout = new ZipOutputStream(new FileOutputStream(out));
    }

    public ZipOutputSupplier(ZipOutputStream stream)
    {
        zout = stream;
    }

    @Override
    public void close() throws IOException
    {
        zout.flush();
        zout.close();
    }

    @Override
    public OutputStream getOutput(String relPath)
    {
        if (tempOut != null)
            throw new IllegalStateException("You must close the previous stream before getting a new one!");
        
        try
        {
            zout.putNextEntry(new ZipEntry(relPath));
        }
        catch (IOException e)
        {
            return null;
        }
        
        return new EntryOutStream();
    }

    private class EntryOutStream extends OutputStream
    {

        @Override
        public void write(int paramInt) throws IOException
        {
            zout.write(paramInt);
        }

        @Override
        public void close() throws IOException
        {
            zout.closeEntry();
        }
    }
}
