package net.minecraftforge.srg2source;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.srg2source.api.RangeApplierBuilder;

public class RangeApplyMain
{
    public static void main(String[] args) throws IOException
    {
        OptionParser parser = new OptionParser();
        OptionSpec<?> helpArg = parser.acceptsAll(a("h", "help")).forHelp();
        OptionSpec<File> inputArg = parser.acceptsAll(a("in", "input", "srcRoot")).withRequiredArg().ofType(File.class).required();
        OptionSpec<File> rangeArg = parser.acceptsAll(a("rm", "range", "srcRangeMap")).withRequiredArg().ofType(File.class).required();
        OptionSpec<File> mappingArg = parser.acceptsAll(a("map", "srg", "srgFiles")).withRequiredArg().ofType(File.class).required();
        OptionSpec<File> excArg = parser.acceptsAll(a("exc", "excFiles")).withRequiredArg().ofType(File.class);
        OptionSpec<File> outArg = parser.acceptsAll(a("out", "output", "outDir")).withRequiredArg().ofType(File.class).required();
        //OptionSpec<Boolean> importArg = parser.acceptsAll(a("keepImports")).withOptionalArg().ofType(Boolean.class).defaultsTo(false);
        //OptionSpec<Boolean> annArg = parser.acceptsAll(a("annotate")).withOptionalArg().ofType(Boolean.class).defaultsTo(false);

        //Old stuff, we should kill off
        OptionSpec<File> lvRangeArg = parser.acceptsAll(a("lvRangeMap")).withRequiredArg().ofType(File.class);
        //OptionSpec<File> mcpDirArg = parser.acceptsAll(a("mcpConfDir")).withRequiredArg().ofType(File.class);

        try
        {
            OptionSet options = parser.parse(args);

            if (options.has(helpArg)) {
                parser.printHelpOn(System.out);
                return;
            }

            File range = options.valueOf(rangeArg);
            File output = options.valueOf(outArg);
            File lvRange = options.valueOf(lvRangeArg);

            System.out.println("Range:   " + range);
            System.out.println("Output:  " + output);
            System.out.println("LVRange: " + lvRange);

            RangeApplierBuilder builder = new RangeApplierBuilder()
                .range(range)
                .output(output)
                .lvrange(lvRange);

            if (options.has(mappingArg))
            {
                options.valuesOf(mappingArg).forEach(v -> {
                    System.out.println("Map:    " + v);
                    builder.srg(v);
                });
            }

            options.valuesOf(inputArg).forEach(v -> {
                System.out.println("Input:  " + v);
                builder.input(v);
            });

            if (options.has(excArg))
            {
                options.valuesOf(excArg).forEach(v -> {
                    System.out.println("Exc:    " + v);
                    builder.exc(v);
                });
            }

            builder.build().run();
        }
        catch (OptionException e)
        {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private static List<String> a(String... values) {
        return Arrays.asList(values);
    }
}
