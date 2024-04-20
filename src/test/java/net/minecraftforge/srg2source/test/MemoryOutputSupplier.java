/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.srg2source.api.OutputSupplier;

public class MemoryOutputSupplier implements OutputSupplier {
    private List<String> byIndex = new ArrayList<String>();
    private Map<String, byte[]> data = new HashMap<String, byte[]>();

    @Override public void close() throws IOException {}

    @Override
    public OutputStream getOutput(final String relPath)
    {
        return new ByteArrayOutputStream()
        {
            public void close() throws IOException {
                MemoryOutputSupplier.this.data.put(relPath, this.toByteArray());
                MemoryOutputSupplier.this.byIndex.add(relPath);
            }
        };
    }

    public String get(int idx)
    {
        return new String(data.get(byIndex.get(idx)));
    }

}
