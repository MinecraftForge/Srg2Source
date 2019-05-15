package net.minecraftforge.srg2source;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;
import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.api.SourceVersion;

public class RangeExtractMain
{
    /*
     * TODO:
     *   Support Source Directories/Inputs on the classpath. Which is the sourcepathEntries argument in
     *   org.eclipse.jdt.core.dom.ASTParser.setEnvironment(String[], String[], String[], boolean)
     *   This would need patching to support InputSupplier's
     *
     *   Runtime detection of JDT patch, and re launch in TransformingClassloader if not detected.
     *
     *   Find a way to pass RangeExtractor instance to our JDT hook so we can run multiple batches at once.
     */


    public static void main(String[] args) throws IOException
    {
        OptionParser parser = new OptionParser();
        OptionSpec<File> libArg = parser.acceptsAll(Arrays.asList("e", "lib")).withRequiredArg().ofType(File.class);
        OptionSpec<File> inputArg = parser.acceptsAll(Arrays.asList("in", "input")).withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputArg = parser.acceptsAll(Arrays.asList("out", "output")).withRequiredArg().ofType(File.class).required();
        OptionSpec<Boolean> batch = parser.accepts("batch").withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        OptionSpec<SourceVersion> jversionArg = parser.acceptsAll(Arrays.asList("sc", "source-compatibility")).withRequiredArg().ofType(SourceVersion.class).defaultsTo(SourceVersion.JAVA_1_8)
            .withValuesConvertedBy(new ValueConverter<SourceVersion>() {
                @Override
                public SourceVersion convert(String value)
                {
                    return SourceVersion.parse(value);
                }

                @Override
                public Class<? extends SourceVersion> valueType()
                {
                    return SourceVersion.class;
                }

                @Override
                public String valuePattern()
                {
                    List<String> ret = new ArrayList<>();
                    for (SourceVersion v : SourceVersion.values())
                    {
                        ret.add(v.name());
                        ret.add(v.getSpec());
                    }
                    return ret.stream().collect(Collectors.joining(","));
                }
            });

        try
        {
            OptionSet options = parser.parse(args);
            System.out.println("Compat: " + options.valueOf(jversionArg));
            System.out.println("Output: " + options.valueOf(outputArg));
            System.out.println("Batch:  " + options.valueOf(batch));

            RangeExtractorBuilder builder = new RangeExtractorBuilder()
                .sourceCompatibility(options.valueOf(jversionArg))
                .output(options.valueOf(outputArg))
                .batch(options.valueOf(batch));

            if (options.has(libArg))
            {
                options.valuesOf(libArg).forEach(v -> {
                    System.out.println("Lib:    " + v);
                    builder.library(v);
                });
            }

            options.valuesOf(inputArg).forEach(v -> {
                System.out.println("Input:  " + v);
                builder.input(v);
            });

            builder.build().run();
        }
        catch (OptionException e)
        {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
