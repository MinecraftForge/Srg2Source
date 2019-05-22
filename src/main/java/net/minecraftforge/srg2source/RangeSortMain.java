package net.minecraftforge.srg2source;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.srg2source.rangeapplier.RangeMap;

public class RangeSortMain
{
    public static void main(String[] args) throws IOException
    {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputArg = parser.acceptsAll(Arrays.asList("in", "input")).withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputArg = parser.acceptsAll(Arrays.asList("out", "output")).withRequiredArg().ofType(File.class);

        try
        {
            OptionSet options = parser.parse(args);
            File input = options.valueOf(inputArg);
            File output = options.valueOf(outputArg);

            System.out.println("#Input:  " + input);
            System.out.println("#Output: " + output);

            try (PrintWriter out = output == null ? new PrintWriter(System.out) : new PrintWriter(output))
            {
                RangeMap range = new RangeMap().read(input);
                range.keySet().forEach(key -> range.get(key).forEach(out::println));
            }
        }
        catch (OptionException e)
        {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
