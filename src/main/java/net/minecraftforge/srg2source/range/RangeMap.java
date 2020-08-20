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

package net.minecraftforge.srg2source.range;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.minecraftforge.srg2source.range.entries.RangeEntry;
import net.minecraftforge.srg2source.util.Util;

public class RangeMap {
    private final int SPEC = 1;

    //TODO: Support output types:
    // Directory: every range file is split into it's own file. Would allow for easier navigation/debug
    // Zip: Same as directory, but also compressed, easier debugging, and lower file size
    public static Map<String, RangeMap> readAll(InputStream stream) throws IOException {
        Map<String, RangeMap> ret = new HashMap<>();
        List<String> lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
        for (int x = 0; x < lines.size(); x++) {
            String line = stripComment(lines.get(x)).trim();

            if (line.isEmpty())
                continue;

            if (line.startsWith("start ")) {
                List<String> pts = Util.unquote(line, 3);
                if (pts.size() != 4)
                    throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x));
                int spec = -1;
                try {
                    spec = Integer.parseInt(pts.get(1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x));
                }

                int end = x + 1;
                while (end < lines.size() && !"end".equals(stripComment(lines.get(end))))
                    end++;

                if (end == lines.size())
                    throw new IllegalArgumentException("Invalid RangeMap. Start on line #" + x + " with no end");

                if (spec == 1)
                    ret.put(pts.get(2), new RangeMap(spec, pts.get(2), pts.get(3), lines, x + 1, end));
                else
                    throw new IllegalArgumentException("Invalid RangeMap line #" + x + " Unknown Spec: " + lines.get(x));

                x = end;
            } else if ("end".equals(stripComment(line))) {
                throw new IllegalArgumentException("Invalid RangeMap. End on line #" + x + " with no start");
            }
        }
        return ret;
    }

    private final String filename;
    private final String hash;
    private final List<RangeEntry> entries;
    private final List<StructuralEntry> structures;

    private RangeMap(int spec, String filename, String hash, List<String> lines, int start, int end) {
        this.filename = filename;
        this.hash = hash;
        final List<RangeEntry> entries = new ArrayList<>();
        final List<StructuralEntry> structures = new ArrayList<>();
        this.entries = Collections.unmodifiableList(entries);
        this.structures = Collections.unmodifiableList(structures);

        for (int x = start; x < end; x++) {
            String line = stripComment(lines.get(x)).trim();
            if (line.isEmpty())
                continue;
            int idx = line.indexOf(' ');
            if (idx == -1)
                throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x));

            try {
                String type = line.substring(0, idx);
                if (type.endsWith("def")) //Structure
                    structures.add(StructuralEntry.read(spec, type.substring(0, type.length() - 3), line.substring(idx + 1)));
                else //entry
                    entries.add(RangeEntry.read(spec, type, line.substring(idx + 1)));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x), e);
            }
        }
    }

    RangeMap(String filename, String hash, List<RangeEntry> entries, List<StructuralEntry> structures) {
        this.filename = filename;
        this.hash = hash;
        this.entries = Collections.unmodifiableList(entries);
        this.structures = Collections.unmodifiableList(structures);
    }

    public String getFilename() {
        return this.filename;
    }

    public String getHash() {
        return this.hash;
    }

    public List<RangeEntry> getEntries() {
        return this.entries;
    }

    public List<StructuralEntry> getStructures() {
        return this.structures;
    }

    public void write(PrintWriter out, boolean pretty) {
        Writer writer = new Writer(out);
        writer.accept(Util.quote("start", Integer.toString(SPEC), filename, hash));

        Stack<StructuralEntry> stack = new Stack<>();
        Iterator<StructuralEntry> segments = structures.iterator();

        StructuralEntry last = null;
        StructuralEntry next = segments.hasNext() ? segments.next() : null;

        for (RangeEntry entry : entries) {
            if (pretty) {
                while (last != null) {
                    if (entry.getStart() < end(last))
                        break;
                    writer.tabs--;
                    writer.accept("# End " + last.getType().name());
                    last = stack.empty() ? null : stack.pop();
                }
            }

            if (next != null) {
                if (entry.getStart() > next.getStart()) {
                    next.write(writer);
                    if (pretty) {
                        writer.accept("# Start " + next.getType().name() + ' ' + next.getName() + (next.getDescriptor() == null ? "" : next.getDescriptor()));
                        writer.tabs++;
                        if (last != null)
                            stack.push(last);
                        last = next;
                        next = segments.hasNext() ? segments.next() : null;
                    }
                }
            }

            entry.write(writer);
        }

        //Grab all the trailing things that don't have entries inside them?
        //Should never be the case because we should have a entry for the name of the object at least, but hey why not.
        if (next != null) {
            next.write(writer);
            while (segments.hasNext())
                segments.next().write(writer);
        }

        if (pretty) {
            while (last != null) {
                writer.tabs--;
                writer.accept("# End " + last.getType().name());
                last = stack.empty() ? null : stack.pop();
            }
        }

        writer.tabs = 0;
        writer.accept("end");
    }

    private static class Writer implements Consumer<String> {
        private int tabs = 0;
        private final PrintWriter out;
        private Writer(PrintWriter out) {
            this.out = out;
        }

        @Override
        public void accept(String line) {
            for (int x = 0; x < tabs; x++)
                out.write("  ");
            out.print(line + '\n'); //Don't use println, as we want consistent line endings.
        }
    }

    private int end(IRange e) {
        return e.getStart() + e.getLength();
    }

    private static String stripComment(String line) {
        int idx = line.indexOf('#');
        return idx == -1 ? line : line.substring(0, idx);
    }
}
