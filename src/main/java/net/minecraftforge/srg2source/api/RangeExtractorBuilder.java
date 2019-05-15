package net.minecraftforge.srg2source.api;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.ChainedInputSupplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

public class RangeExtractorBuilder
{
    private SourceVersion sourceVersion = SourceVersion.JAVA_1_8;
    private PrintStream logStd = System.out;
    private PrintStream logErr = System.err;
    private PrintWriter output = null;
    private boolean batch = true;
    private List<File> libraries = new ArrayList<>();
    private List<InputSupplier> inputs = new ArrayList<>();
    private File cache = null;

    public RangeExtractorBuilder sourceCompatibility(SourceVersion value)
    {
        this.sourceVersion = value;
        return this;
    }

    public RangeExtractorBuilder logger(PrintStream value)
    {
        this.logStd = value;
        return this;
    }

    public RangeExtractorBuilder errorLogger(PrintStream value)
    {
        this.logErr = value;
        return this;
    }

    public RangeExtractorBuilder output(File value)
    {
        try
        {
            if (!value.exists())
            {
                File parent;
                    parent = value.getCanonicalFile().getParentFile();
                if (!parent.exists())
                    parent.mkdirs();
                value.createNewFile();
            }

            return output(new PrintWriter(new BufferedWriter(new FileWriter(value))));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public RangeExtractorBuilder output(PrintWriter value)
    {
        if (output != null)
            output.close();
        output = value;
        return this;
    }

    public RangeExtractorBuilder batch()
    {
        return this.batch(true);
    }

    public RangeExtractorBuilder batch(boolean value)
    {
        this.batch = true;
        return this;
    }

    public RangeExtractorBuilder library(File value)
    {
        this.libraries.add(value);
        return this;
    }

    public RangeExtractorBuilder input(File value)
    {
        if (value == null || !value.exists())
            throw new IllegalArgumentException("Invalid input value: " + value);

        String filename = value.getName().toLowerCase(Locale.ENGLISH);
        if (value.isDirectory())
            inputs.add(new FolderSupplier(value));
        else if (filename.endsWith(".jar") || filename.endsWith(".zip"))
        {
            try
            {
                inputs.add(new ZipInputSupplier(value));
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
            throw new IllegalArgumentException("Invalid input value: " + value);

        return this;
    }

    public RangeExtractorBuilder input(InputSupplier value)
    {
        this.inputs.add(value);
        return this;
    }

    public RangeExtractorBuilder cache(File value)
    {
        this.cache = value;
        return this;
    }


    public RangeExtractor build()
    {
        RangeExtractor ret = new RangeExtractor();
        ret.setLogger(logStd);
        ret.setErrorLogger(logErr);

        if (output != null)
            ret.setOutput(output);
        ret.setSourceCompatibility(sourceVersion);
        ret.setBatchASTs(batch);

        libraries.forEach(ret::addLibrary);

        if (this.inputs.size() == 1)
            ret.setInput(this.inputs.get(0));
        else
            ret.setInput(new ChainedInputSupplier(this.inputs));

        if (this.cache != null)
        {
            try (InputStream fin = new FileInputStream(this.cache))
            {
                ret.loadCache(fin);
            }
            catch (IOException e)
            {
                System.out.println("Error Loading Caching: " + this.cache);
                e.printStackTrace();
            }
        }

        return ret;
    }
}
