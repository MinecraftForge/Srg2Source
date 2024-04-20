/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
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

import net.minecraftforge.srg2source.range.entries.MetaEntry;
import net.minecraftforge.srg2source.range.entries.RangeEntry;
import net.minecraftforge.srg2source.range.entries.StructuralEntry;
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
    private final List<MetaEntry> meta;

    private RangeMap(int spec, String filename, String hash, List<String> lines, int start, int end) {
        this.filename = filename;
        this.hash = hash;
        final List<RangeEntry> entries = new ArrayList<>();
        final List<StructuralEntry> structures = new ArrayList<>();
        final List<MetaEntry> meta = new ArrayList<>();
        this.entries = Collections.unmodifiableList(entries);
        this.structures = Collections.unmodifiableList(structures);
        this.meta = Collections.unmodifiableList(meta);

        for (int x = start; x < end; x++) {
            String line = stripComment(lines.get(x)).trim();
            if (line.isEmpty())
                continue;
            int idx = line.indexOf(' ');
            if (idx == -1)
                throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x));

            try {
                String type = line.substring(0, idx);
                if ("meta".equals(type))
                    meta.add(MetaEntry.read(spec, line.substring(idx + 1)));
                else if (type.endsWith("def")) //Structure
                    structures.add(StructuralEntry.read(spec, type.substring(0, type.length() - 3), line.substring(idx + 1)));
                else //entry
                    entries.add(RangeEntry.read(spec, type, line.substring(idx + 1)));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid RangeMap line #" + x + ": " + lines.get(x), e);
            }
        }
    }

    RangeMap(String filename, String hash, List<RangeEntry> entries, List<StructuralEntry> structures, List<MetaEntry> meta) {
        this.filename = filename;
        this.hash = hash;
        this.entries = Collections.unmodifiableList(entries);
        this.structures = Collections.unmodifiableList(structures);
        this.meta = Collections.unmodifiableList(meta);
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
