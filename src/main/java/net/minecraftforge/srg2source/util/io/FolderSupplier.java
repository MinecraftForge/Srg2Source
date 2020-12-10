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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraftforge.srg2source.api.InputSupplier;
import net.minecraftforge.srg2source.api.OutputSupplier;

public class FolderSupplier implements InputSupplier, OutputSupplier {
    public static FolderSupplier create(Path root, @Nullable Charset encoding) throws IOException {
        if (!Files.exists(root))
            Files.createDirectories(root);
        return new FolderSupplier(root, encoding);
    }

    private final Path root;
    private final String sroot;
    @Nullable
    private final Charset encoding;

    protected FolderSupplier(Path root, @Nullable Charset encoding) {
        this.root = root;
        this.sroot = root.toAbsolutePath().toString();
        this.encoding = encoding;
    }

    @Override
    @Nullable
    public OutputStream getOutput(String relPath, boolean override) {
        try {
            Path target = root.resolve(relPath);
            if (!Files.exists(target)) {
                Path parent = target.getParent();
                if (!Files.exists(parent))
                    Files.createDirectories(parent);
            }
            if (override)
                return Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            else
                return Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    @Nullable
    public InputStream getInput(String relPath) {
        try {
            Path target = root.resolve(relPath);
            if (!Files.exists(target))
                return null;
            return Files.newInputStream(target, StandardOpenOption.READ);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter) {
        try {
            return Files.walk(root).filter(Files::isRegularFile)
                    .map(root::relativize).map(Path::toString)
                    .filter(p -> p.endsWith(endFilter))
                    .sorted().collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @Override
    public void close() throws IOException {
        // they are files.. what do you want me to do?
    }

    @Override
    @Nullable
    public String getRoot(String resource) {
        return sroot;
    }

    @Override
    @Nullable
    public Charset getEncoding(String resource) {
        return encoding;
    }
}
