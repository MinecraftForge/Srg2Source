package net.minecraftforge.srg2source.util.io;

import java.io.Closeable;
import java.io.InputStream;
import java.util.List;

public interface InputSupplier extends Closeable
{
    /**
     * Opens an input stream to the specified resource. You are expected to close this stream yourself.
     * Returns null if the resource does not exist.
     * @param relPath Reative path seperated with '/' and having no preceding slash.
     * @return
     */
    public InputStream getInput(String relPath);
    
    /**
     * Gathers all the names of all the resources with the given ending in their name.
     * These paths are gaurnateed to be relative. This will never reeturn null, and return an emtpy list instead.
     * @param endFilter
     * @return
     */
    public List<String> gatherAll(String endFilter);
}
