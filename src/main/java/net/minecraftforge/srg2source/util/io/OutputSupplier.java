package net.minecraftforge.srg2source.util.io;

import java.io.Closeable;
import java.io.OutputStream;

public interface OutputSupplier extends Closeable
{
    /**
     * Opens an output stream to the specified resource. The resource will be created if it does not already exist. You are expected to close this stream yourself.
     * @param relPath Reative path seperated with '/' and having no preceding slash.
     * @return
     */
    public OutputStream getOutput(String relPath);
}
