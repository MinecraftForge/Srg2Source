/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.api;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public interface InputSupplier extends Closeable {
    /**
     * The absolute path of the root entity of the given resource, be it a file or directory.
     * The passed resource may only be useful when there are resources from multiple roots.
     * @param resource The resource to find the root for
     * @return The absolute path of the root entity of the given resource, be it a file or directory.
     */
    @Nullable
    String getRoot(String resource);

    /**
     * Opens an input stream to the specified resource. You are expected to close this stream yourself.
     * Returns null if the resource does not exist.
     * @param relPath Relative path separated with '/' and having no preceding slash.
     * @return InputStream for the specified path
     */
    @Nullable
    InputStream getInput(String relPath);

    /**
     * Gathers all the names of all the resources with the given ending in their name.
     * These paths are guaranteed to be relative. This will never return null, and return an empty list instead.
     * @param endFilter Filter to match the end of files names
     * @return A list containing all files matching the filter
     */
    List<String> gatherAll(String endFilter);

    /**
     * Get the encoding to be used when processing this specified resource as code.
     * If you do not know this resource, return null.
     * Returning null will default to UTF_8.
     *
     * @param resouce The resource that we will be reading.
     * @return Encoding charset, or Null if unknown.
     */
    @Nullable
    default Charset getEncoding(String resource) {
        return null;
    }
}
