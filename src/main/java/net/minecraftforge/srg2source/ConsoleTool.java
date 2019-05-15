package net.minecraftforge.srg2source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.srg2source.rangeapplier.RangeApplier;

public class ConsoleTool
{
    public static void main(String[] args) throws IOException
    {
        boolean apply = false, extract = false;
        Deque<String> que = new LinkedList<>();
        for(String arg : args)
            que.add(arg);

        List<String> _args = new ArrayList<String>();

        String arg;
        while ((arg = que.poll()) != null)
        {
            if ("--apply".equals(arg))
                apply = true;
            else if ("--extract".equals(arg))
                extract = true;
            else if ("--cfg".equals(arg))
            {
                String cfg = que.poll();
                if (cfg == null)
                    throw new IllegalArgumentException("Invalid --cfg entry, missing file path");
                Files.readAllLines(Paths.get(cfg)).forEach(que::add);
            }
            else if (arg.startsWith("--cfg="))
                Files.readAllLines(Paths.get(arg.substring(6))).forEach(que::add);
            else
                _args.add(arg);
        }

        if (apply && extract)
        {
            System.out.println("Must specify EITHER --apply or --extract, can not run both at once.");
        }
        else if (apply)
        {
            RangeApplier.main(_args.toArray(new String[_args.size()]));
        }
        else if (extract)
        {
            RangeExtractMain.main(_args.toArray(new String[_args.size()]));
        }
        else
        {
            System.out.println("Must specify either --apply or --extract");
        }
    }
}
