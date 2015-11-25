package net.minecraftforge.srg2source.rangeapplier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraftforge.srg2source.util.Util;

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.Files;

class RangeMap
{
    private final Multimap<String, RangeEntry> rangeMap;
    private final HashMap<String, String> obfids = Maps.newHashMap();

    public RangeMap()
    {
        rangeMap = Multimaps.newMultimap(new HashMap<String, Collection<RangeEntry>>(), new Supplier<Collection<RangeEntry>>()
        {
            @Override
            public Collection<RangeEntry> get()
            {
                return new TreeSet<RangeEntry>();
            }
        });
    }
    
    public Set<String> keySet()
    {
        return rangeMap.keySet();
    }
    
    public Collection<RangeEntry> get(String key)
    {
       return rangeMap.get(key);
    }

    public RangeMap read(File file)
    {
        try
        {
            for (String line : Files.readLines(file, Charset.defaultCharset()))
            {
                line = line.trim();
                List<String> tokens = Splitter.on('|').splitToList(line);
                if (!tokens.get(0).equals("@"))
                    continue;
                String fileName = tokens.get(1);
                int startRange = Integer.parseInt(tokens.get(2));
                int endRange = Integer.parseInt(tokens.get(3));
                String expectedOldText = tokens.get(4);
                String kind = tokens.get(5);
                List<String> info = tokens.subList(6, tokens.size());

                String key;
                // Build unique identifier for symbol
                if (kind.equals("package"))
                {
                    String forClass = info.get(1);

                    // key = "package "+packageName # ignore old name (unique identifier == filename)
                    if (forClass.equals("(file)"))
                        forClass = Util.getTopLevelClassForFilename(fileName);
                    else
                        forClass = Util.sourceName2Internal(forClass); // . -> /

                    // 'forClass' == the class that == in this package; when the class is
                    // remapped to a different package, this range should be updated

                    key = "package " + forClass;
                }
                else if (kind.equals("class"))
                {
                    key = "class " + Util.sourceName2Internal(info.get(0));
                }
                else if (kind.equals("field"))
                {
                    key = "field " + Util.sourceName2Internal(info.get(0)) + "/" + info.get(1);
                    if (info.get(1).equals("__OBFID"))
                        obfids.put(info.get(0), info.get(2));
                }
                else if (kind.equals("method"))
                {
                    if (expectedOldText.contains("super") || expectedOldText.contains("this"))
                        continue; // hack: avoid erroneously replacing super/this calls
                    key = "method " + Util.sourceName2Internal(info.get(0)) + "/" + info.get(1) + " " + info.get(2);
                }
                else if (kind.equals("param"))
                {
                    key = "param " + Util.sourceName2Internal(info.get(0)) + "/" + info.get(1) + " " + info.get(2) + " " + info.get(4);  // ignore old name (positional)
                }
                else if (kind.equals("localvar"))
                {
                    key = "localvar " + Util.sourceName2Internal(info.get(0)) + "/" + info.get(1) + " " + info.get(2) + " " + info.get(4); // ignore old name (positional)
                }
                else
                {
                    throw new RuntimeException("Unknown kind: " + kind);
                }

                // (startRange, endRange, expectedOldText, key)
                rangeMap.put(fileName, new RangeEntry(startRange, endRange, expectedOldText, key));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return this;
    }

    public static class RangeEntry implements Comparable<RangeEntry>
    {
        public final int start, end;
        public final String expectedOldText, key;

        RangeEntry(int start, int end, String expectedOldText, String key)
        {
            super();
            this.start = start;
            this.end = end;
            this.expectedOldText = expectedOldText;
            this.key = key;
        }

        @Override
        public int compareTo(RangeEntry arg0)
        {
            if (start == arg0.start)
                return 0;

            return start > arg0.start ? 1 : -1;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + end;
            result = prime * result + ((expectedOldText == null) ? 0 : expectedOldText.hashCode());
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + start;
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            RangeEntry other = (RangeEntry) obj;
            if (end != other.end)
                return false;
            if (expectedOldText == null)
            {
                if (other.expectedOldText != null)
                    return false;
            }
            else if (!expectedOldText.equals(other.expectedOldText))
                return false;
            if (key == null)
            {
                if (other.key != null)
                    return false;
            }
            else if (!key.equals(other.key))
                return false;
            if (start != other.start)
                return false;
            return true;
        }

        @Override
        public String toString()
        {
            return "RangeEntry [start=" + start + ", end=" + end + ", expectedOldText=" + expectedOldText + ", key=" + key + "]";
        }
    }
}
