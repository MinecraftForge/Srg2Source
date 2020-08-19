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
