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

package net.minecraftforge.srg2source.apply;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class ExceptorClass {
    public static Map<String, ExceptorClass> create(Path path) throws IOException {
        return create(path, StandardCharsets.UTF_8);
    }
    public static Map<String, ExceptorClass> create(Path path, Charset encoding) throws IOException {
        return create(path, encoding, null);
    }
    public static Map<String, ExceptorClass> create(Path path, Charset encoding, Map<String, ExceptorClass> parent) throws IOException {
        Map<String, Map<String, String[]>> params = new HashMap<>();
        Map<String, Map<String, String[]>> exceptions = new HashMap<>();
        Set<String> known = new HashSet<>();

        parent.forEach((k,v) -> {
            if (v.params != null)
                params.put(k, new HashMap<>(v.params));
            if (v.exceptions != null)
                exceptions.put(k, new HashMap<>(v.exceptions));
            known.add(k);
        });

        try (InputStream stream = Files.newInputStream(path)) {
            List<String> lines = new BufferedReader(new InputStreamReader(stream, encoding)).lines().collect(Collectors.toList());
            for (int x = 0; x < lines.size(); x++) {
                String line = stripComment(lines.get(x)).trim();

                if (line.isEmpty())
                    continue;

                int idx = line.indexOf('=');
                if (idx == -1)
                    throw new IllegalArgumentException("Invalid Exceptor line #" + x + ": " + lines.get(x));

                String key = line.substring(0, idx);
                String value = line.substring(idx + 1);

                idx = key.indexOf('.');
                if (idx == -1 || value.isEmpty())
                    throw new IllegalArgumentException("Invalid Exceptor line #" + x + ": " + lines.get(x));
                String cls = key.substring(0, idx);
                String mtd = key.substring(idx + 1);

                idx = value.indexOf('|');
                String excps = idx == -1 ? value : value.substring(0, idx);
                String args  = idx == -1 ? ""    : value.substring(idx + 1);

                if (!excps.isEmpty())
                    exceptions.computeIfAbsent(cls, k -> new HashMap<>()).put(mtd, excps.split(","));
                if (!args.isEmpty())
                    params.computeIfAbsent(cls, k -> new HashMap<>()).put(mtd, args.split(","));
                known.add(cls);
            }
        }

        Map<String, ExceptorClass> ret = new HashMap<>();
        for (String cls : known)
            ret.put(cls, new ExceptorClass(params.get(cls), exceptions.get(cls)));

        return ret;
    }

    private static String stripComment(String line) {
        int idx = line.indexOf('#');
        return idx == -1 ? line : line.substring(0, idx);
    }

    @Nullable
    private final Map<String, String[]> params;
    @Nullable
    private final Map<String, String[]> exceptions;

    private ExceptorClass(Map<String, String[]> params, Map<String, String[]> exceptions) {
        if (params != null && !params.isEmpty())
            this.params = Collections.unmodifiableMap(params);
        else
            this.params = null;
        if (exceptions != null && !exceptions.isEmpty())
            this.exceptions = Collections.unmodifiableMap(exceptions);
        else
            this.exceptions = null;
    }


    public String mapParam(String name, String desc, int index, String old) {
        String[] args = this.params.get(name + desc);
        if (args == null || index >= args.length)
            return old;
        return args[index];
    }
}
