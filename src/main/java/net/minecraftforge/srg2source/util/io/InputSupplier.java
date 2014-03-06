package net.minecraftforge.srg2source.util.io;

import java.io.Closeable;
import java.io.InputStream;
import java.util.List;

public interface InputSupplier extends Closeable
{
    /**
     * The absolute path of the root entity of the given resource, be it a file or directory.
     * The passed resource may only be useful when there are resources from multiple roots.
     * @param resource 
     * @return
     */
    public String getRoot(String resource);
    
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
