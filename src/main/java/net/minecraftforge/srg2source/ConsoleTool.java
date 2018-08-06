package net.minecraftforge.srg2source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;

public class ConsoleTool
{
    public static void main(String[] args) throws IOException
    {
        boolean apply = false, extract = false;
        List<String> _args = new ArrayList<String>();
        for (String s : args)
        {
            if ("--apply".equals(s))
                apply = true;
            else if ("--extract".equals(s))
                extract = true;
            else
                _args.add(s);
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
            RangeExtractor.main(_args.toArray(new String[_args.size()]));
        }
        else
        {
            System.out.println("Must specify either --apply or --extract");
        }
    }
}
