/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
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
