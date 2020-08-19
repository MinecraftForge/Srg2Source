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
