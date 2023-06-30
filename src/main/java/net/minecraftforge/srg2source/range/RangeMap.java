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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.minecraftforge.srg2source.range.entries.MetaEntry;
import net.minecraftforge.srg2source.range.entries.RangeEntry;
import net.minecraftforge.srg2source.range.entries.StructuralEntry;
import net.minecraftforge.srg2source.util.Util;

public class RangeMap {
    private static final int SPEC = 1;
    private static final char TAB_CHAR = ' ';
    private static final int TAB_SIZE = 2;

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
    private final StructuralEntry root;
    private final List<MetaEntry> meta;

    private RangeMap(int spec, String filename, String hash, List<String> lines, int start, int end) {
        this.filename = filename;
        this.hash = hash;
        this.root = StructuralEntry.createRoot();
        final List<MetaEntry> meta = new ArrayList<>();
        this.meta = Collections.unmodifiableList(meta);

        Stack<StructuralEntry> stack = new Stack<>();
        stack.push(this.root);

        int lastDepth = 0;
        for (int x = start; x < end; x++) {
            int depth = 0;
            for (Character ch : lines.get(x).toCharArray()) {
                if (ch.equals(RangeMap.TAB_CHAR)) depth++; else break;
            }
            depth /= RangeMap.TAB_SIZE; // depth is correlated with stack size, not indent

            String line = stripComment(lines.get(x)).trim();
            if (line.isEmpty())
                continue;

            // Depth changed, remove last structure from stack
            while (depth < lastDepth) {
                stack.pop();
                lastDepth--;
            }

            int idx = line.indexOf(' ');
            if (idx == -1)
                throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x));

            try {
                String type = line.substring(0, idx);
                if ("meta".equals(type))
                    meta.add(MetaEntry.read(spec, line.substring(idx + 1)));
                else {
                    if (type.endsWith("def")) { // structure
                        StructuralEntry parent = stack.peek();
                        StructuralEntry structure = StructuralEntry.read(spec, type.substring(0, type.length() - 3), parent, line.substring(idx + 1));
                        // Store structure in parent structure
                        parent.addStructure(structure);
                        // and push new actual processed structure on stack
                        stack.push(structure);
                    } else { // entry
                        RangeEntry entry = RangeEntry.read(spec, type, line.substring(idx + 1));
                        // Store entry in parent structure
                        stack.peek().addEntry(entry);
                    }
                    lastDepth = depth;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x), e);
            }
        }
    }

    RangeMap(String filename, String hash, StructuralEntry root, List<MetaEntry> meta) {
        this.filename = filename;
        this.hash = hash;
        this.root = root;
        this.meta = Collections.unmodifiableList(meta);
    }

    public String getFilename() {
        return this.filename;
    }

    public String getHash() {
        return this.hash;
    }

    public StructuralEntry getRoot() {
        return this.root;
    }

    public List<MetaEntry> getMeta() {
        return this.meta;
    }

    public void write(PrintWriter out, boolean pretty) {
        Writer writer = new Writer(out);
        writer.accept(Util.quote("start", Integer.toString(SPEC), filename, hash));

        if (!meta.isEmpty()) {
            if (pretty) {
                writer.accept("# Start Meta");
                writer.tabs++;
            }

            for (MetaEntry entry : this.meta)
                entry.write(writer);

            if (pretty) {
                writer.tabs--;
                writer.accept("# End Meta");
            }
        }

        // Get ROOT elements as we want only root children
        List<IRange> rootElements = new ArrayList<>();
        rootElements.addAll(this.root.getEntries(false));
        rootElements.addAll(this.root.getStructures(false));

        // Start printing root without tab
        for (IRange element : rootElements.stream()
                .sorted(Comparator.comparing(IRange::getStart))
                .collect(Collectors.toList())) {
            printElement(writer, element, 0);
        }

        writer.tabs = 0;
        writer.accept("end");
    }

    public static void printElement(Writer writer, IRange entry, int tabs) {
        if (entry instanceof StructuralEntry){
            StructuralEntry structure = (StructuralEntry) entry;

            List<IRange> elements = new ArrayList<>();
            elements.addAll(structure.getEntries(false));
            elements.addAll(structure.getStructures(false));

            writer.tabs = tabs;
            structure.write(writer); // print structure def
            writer.accept("# Start " + structure.getType().name() + ' ' + structure.getName() + (structure.getDescriptor() == null ? "" : structure.getDescriptor()));

            for (IRange element : elements.stream()
                    .sorted(Comparator.comparing(IRange::getStart))
                    .collect(Collectors.toList())) {
                printElement(writer, element, tabs + 1);
            }

            writer.tabs = tabs;
            writer.accept("# End " + structure.getType().name());
        } else
        if (entry instanceof RangeEntry) {
            RangeEntry rentry = (RangeEntry) entry;
            writer.tabs = tabs;
            rentry.write(writer);
        }
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
                out.write(String.valueOf(RangeMap.TAB_CHAR).repeat(RangeMap.TAB_SIZE));
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
