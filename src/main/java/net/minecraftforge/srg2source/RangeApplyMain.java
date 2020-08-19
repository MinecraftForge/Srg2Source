package net.minecraftforge.srg2source;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
        OptionSpec<Path> inputArg = parser.acceptsAll(a("in", "input", "srcRoot")).withRequiredArg().ofType(Path.class).required();
        OptionSpec<Path> outArg = parser.acceptsAll(a("out", "output", "outDir")).withRequiredArg().ofType(Path.class).required();
        OptionSpec<Path> excArg = parser.acceptsAll(a("exc", "excFiles")).withRequiredArg().ofType(Path.class);
        OptionSpec<Path> mappingArg = parser.acceptsAll(a("map", "srg", "srgFiles")).withRequiredArg().ofType(Path.class).required();
        //TODO: Encoding arguments

        OptionSpec<File> rangeArg = parser.acceptsAll(a("rm", "range", "srcRangeMap")).withRequiredArg().ofType(File.class).required();
        OptionSpec<Boolean> importArg = parser.acceptsAll(a("keepImports")).withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        //OptionSpec<Boolean> annArg = parser.acceptsAll(a("annotate")).withOptionalArg().ofType(Boolean.class).defaultsTo(false);

        try
        {
            OptionSet options = parser.parse(args);

            if (options.has(helpArg)) {
                parser.printHelpOn(System.out);
                return;
            }

            File range = options.valueOf(rangeArg);
            Path output = options.valueOf(outArg);
            boolean keepImports = options.has(importArg) && options.valueOf(importArg);

            System.out.println("Range:   " + range);
            System.out.println("Output:  " + output);
            System.out.println("Imports: " + keepImports);

            RangeApplierBuilder builder = new RangeApplierBuilder()
                .range(range)
                .output(output);

            if (options.has(mappingArg))
            {
                options.valuesOf(mappingArg).forEach(v -> {
                    System.out.println("Map:     " + v);
                    builder.srg(v);
                });
            }

            options.valuesOf(inputArg).forEach(v -> {
                System.out.println("Input:   " + v);
                builder.input(v);
            });

            if (options.has(excArg))
            {
                options.valuesOf(excArg).forEach(v -> {
                    System.out.println("Exc:     " + v);
                    builder.exc(v);
                });
            }

            if (keepImports)
                builder.keepImports();
            else
                builder.trimImports();

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
