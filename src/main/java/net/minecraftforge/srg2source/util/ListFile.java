package net.minecraftforge.srg2source.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("rawtypes")
public abstract class ListFile<T, ME extends ListFile> implements Iterable<T>
{
    protected List<T> lines;

    protected ListFile()
    {
        lines = new LinkedList<T>();
    }

    @Override
    public Iterator<T> iterator()
    {
        return lines.iterator();
    }

    public boolean isEmpty()
    {
        return lines.isEmpty();
    }

    public boolean add(T e)
    {
        return lines.add(e);
    }

    public boolean addAll(Collection<? extends T> c)
    {
        return lines.addAll(c);
    }

    public boolean contains(Object o)
    {
        return lines.contains(o);
    }

    public boolean containsAll(Collection<?> c)
    {
        return lines.containsAll(c);
    }

    public boolean retainAll(Collection<?> c)
    {
        return lines.retainAll(c);
    }

    public int size()
    {
        return lines.size();
    }

    @SuppressWarnings("unchecked")
    public T[] toArray()
    {
        return (T[]) lines.toArray();
    }

    public T[] toArray(T[] a)
    {
        return lines.toArray(a);
    }

    @SuppressWarnings("unchecked")
    public ME read(File file)
    {
        try
        {
            try (BufferedReader reader = new BufferedReader(new FileReader(file)))
            {
                reader.lines().forEach(line -> {
                    T thing = parseLine(line);
                    if (thing != null)
                        lines.add(thing);
                });
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return (ME) this;
    }
    /**
     * This may return null, and if it does, the
     * @return
     */
    protected abstract T parseLine(String line);
}
