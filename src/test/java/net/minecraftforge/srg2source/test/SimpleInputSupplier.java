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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import net.minecraftforge.srg2source.api.InputSupplier;

public class SimpleInputSupplier implements InputSupplier {
    String resource = null;
    String clsName = null;

    public SimpleInputSupplier(String resource, String clsName) {
        this.resource = resource;
        this.clsName = clsName;
    }

    @Override public void close() throws IOException{}
    @Override public String getRoot(String resource) { return ""; }
    @Override
    public InputStream getInput(String relPath)
    {
        try
        {
            return getClass().getResourceAsStream("/" + resource + ".txt");
        }
        catch (Exception e)
        {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        return Arrays.asList("/" + clsName.replace('.', '/') + ".java");
    }
}
