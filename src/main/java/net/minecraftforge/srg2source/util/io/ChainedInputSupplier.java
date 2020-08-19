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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraftforge.srg2source.api.InputSupplier;

public class ChainedInputSupplier implements InputSupplier {
    private List<InputSupplier> children;

    public ChainedInputSupplier(InputSupplier... children) {
        this.children = Arrays.asList(children);
    }

    public ChainedInputSupplier(Collection<InputSupplier> children) {
        this.children = new ArrayList<>(children);
    }

    @Override
    public void close() throws IOException {
        for (InputSupplier child : children)
            child.close();
    }

    @Override
    public String getRoot(String resource) {
        return children.stream().map(c -> c.getRoot(resource)).filter(r -> r != null).findFirst().orElse(null);
    }

    @Override
    public InputStream getInput(String resource) {
        return children.stream().map(c -> c.getInput(resource)).filter(r -> r != null).findFirst().orElse(null);
    }

    @Override
    public List<String> gatherAll(String endFilter) {
        return children.stream().flatMap(c -> c.gatherAll(endFilter).stream()).distinct().collect(Collectors.toList());
    }

    @Override
    public Charset getEncoding(String resource) {
        return children.stream().map(c -> c.getEncoding(resource)).filter(r -> r != null).findFirst().orElse(null);
    }
}
