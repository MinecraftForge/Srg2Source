/*
 * Srg2Source
 * Copyright (c) 2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srg2source.api;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import javax.annotation.Nullable;

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
