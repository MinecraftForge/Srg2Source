package net.minecraftforge.srg2source.api;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.io.ChainedInputSupplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

public class RangeApplierBuilder
{
    private PrintStream logStd = System.out;
    private PrintStream logErr = System.err;
    private List<InputSupplier> inputs = new ArrayList<>();
    private File output = null;
    private File range = null;
    private List<File> srgs = new ArrayList<>();
    private List<File> excs = new ArrayList<>();
    private boolean keepImports = false;
    private boolean annotate = false;
    private File rangeOld = null;

    public RangeApplierBuilder logger(PrintStream value)
    {
        this.logStd = value;
        return this;
    }

    public RangeApplierBuilder errorLogger(PrintStream value)
    {
        this.logErr = value;
        return this;
    }

    public RangeApplierBuilder output(File value)
    {
        this.output = value;
        return this;
    }

    public RangeApplierBuilder srg(File value)
    {
        this.srgs.add(value);
        return this;
    }

    public RangeApplierBuilder exc(File value)
    {
        this.excs.add(value);
        return this;
    }

    public RangeApplierBuilder input(File value)
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

    public RangeApplierBuilder input(InputSupplier value)
    {
        this.inputs.add(value);
        return this;
    }

    public RangeApplierBuilder range(File value) {
        this.range = value;
        return this;
    }

    public RangeApplierBuilder trimImports() {
        this.keepImports = false;
        return this;
    }

    public RangeApplierBuilder keepImports() {
        this.keepImports = true;
        return this;
    }

    public RangeApplierBuilder annotate(boolean value) {
        this.annotate = value;
        return this;
    }

    public RangeApplierBuilder lvrange(File value) {
        this.rangeOld = value;
        return this;
    }

    public RangeApplier build()
    {
        if (output == null)
            throw new IllegalStateException("Builder State Exception: Missing Output");
        if (range == null)
            throw new IllegalArgumentException("Builder State Exception: Missing Range Map");

        RangeApplier ret = new RangeApplier();
        ret.setLogger(logStd);
        ret.setErrorLogger(logErr);

        if (this.inputs.size() == 1)
            ret.setInput(this.inputs.get(0));
        else
            ret.setInput(new ChainedInputSupplier(this.inputs));

        if (output.isDirectory())
            ret.setOutput(new FolderSupplier(output));
        else {
            try {
                ret.setOutput(new ZipOutputSupplier(output));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        ret.readRangeMap(range);

        srgs.forEach(ret::readSrg);
        excs.forEach(ret::readExc);

        ret.annotate(annotate);
        ret.keepImports(keepImports);

        if (rangeOld != null)
            ret.readLvRangeMap(rangeOld);

        return ret;
    }
}
