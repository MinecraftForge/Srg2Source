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

package net.minecraftforge.srg2source.util.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.srg2source.api.OutputSupplier;

public class ZipOutputSupplier implements OutputSupplier {
    private final ZipOutputStream zout;
    private EntryOutStream tempOut;

    public ZipOutputSupplier(File out) throws IOException {
        out = out.getAbsoluteFile(); //Make sure we know the parent or else getParentFile nulls
        if (!out.exists()) {
            out.getParentFile().mkdirs();
            out.createNewFile();
        }
        zout = new ZipOutputStream(new FileOutputStream(out));
    }

    public ZipOutputSupplier(Path out) throws IOException {
        if (!Files.exists(out)) {
            Path parent = out.toAbsolutePath().getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);
        }
        zout = new ZipOutputStream(Files.newOutputStream(out));
    }

    public ZipOutputSupplier(ZipOutputStream stream) {
        zout = stream;
    }

    @Override
    public void close() throws IOException {
        zout.flush();
        zout.close();
    }

    @Override
    public OutputStream getOutput(String relPath, boolean override) {
        if (tempOut != null)
            throw new IllegalStateException("You must close the previous stream before getting a new one!");

        try {
            zout.putNextEntry(new ZipEntry(relPath));
        } catch (IOException e) {
            return null;
        }

        return new EntryOutStream();
    }

    private class EntryOutStream extends OutputStream {
        @Override
        public void write(int paramInt) throws IOException {
            zout.write(paramInt);
        }

        @Override
        public void close() throws IOException {
            zout.closeEntry();
        }
    }
}
