package net.minecraftforge.srg2source.api;

import java.io.Closeable;
import java.io.OutputStream;

import javax.annotation.Nullable;

public interface OutputSupplier extends Closeable {
    /**
     * Opens an output stream to the specified resource. The resource will be created if it does not already exist. You are expected to close this stream yourself.
     * @param relPath Relative path separated with '/' and having no preceding slash.
     * @return An output we can stream data to
     */
    @Nullable
    public OutputStream getOutput(String relPath);
}
