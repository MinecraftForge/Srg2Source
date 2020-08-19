/*
 * Srg2Source
 * Copyright (c) 2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srg2source.api;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.apply.RangeApplier;
import net.minecraftforge.srg2source.util.io.ChainedInputSupplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

public class RangeApplierBuilder {
    private PrintStream logStd = System.out;
    private PrintStream logErr = System.err;
    private List<InputSupplier> inputs = new ArrayList<>();
    private OutputSupplier output = null;
    private Consumer<RangeApplier> range = null;
    private List<Consumer<RangeApplier>> srgs = new ArrayList<>();
    private List<Consumer<RangeApplier>> excs = new ArrayList<>();
    private boolean keepImports = false;
    private boolean annotate = false;

    public RangeApplierBuilder logger(PrintStream value) {
        this.logStd = value;
        return this;
    }

    public RangeApplierBuilder errorLogger(PrintStream value) {
        this.logErr = value;
        return this;
    }

    public RangeApplierBuilder output(Path value) {
        try {
            if (Files.isDirectory(value))
                this.output = FolderSupplier.create(value, null);
            else
                this.output = new ZipOutputSupplier(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid output: " + value, e);
        }
        return this;
    }

    public RangeApplierBuilder srg(Path value) {
        this.srgs.add(a -> a.readSrg(value));
        return this;
    }


    public RangeApplierBuilder exc(Path value) {
        this.excs.add(a -> a.readExc(value));
        return this;
    }

    public RangeApplierBuilder input(Path value) {
        return input(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("resource")
    public RangeApplierBuilder input(Path value, Charset encoding) {
        if (value == null || !Files.exists(value))
            throw new IllegalArgumentException("Invalid input value: " + value);

        String filename = value.getFileName().toString().toLowerCase(Locale.ENGLISH);
        try {
            if (Files.isDirectory(value))
                inputs.add(FolderSupplier.create(value, encoding));
            else if (filename.endsWith(".jar") || filename.endsWith(".zip")) {
                inputs.add(ZipInputSupplier.create(value, encoding));
            } else
                throw new IllegalArgumentException("Invalid input value: " + value);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid input: " + value, e);
        }

        return this;
    }

    public RangeApplierBuilder input(InputSupplier value) {
        this.inputs.add(value);
        return this;
    }

    public RangeApplierBuilder range(File value) {
        this.range = a -> a.readRangeMap(value);
        return this;
    }

    public RangeApplierBuilder range(Path value) {
        this.range = a -> a.readRangeMap(value);
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

    public RangeApplier build() {
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

        ret.setOutput(output);
        range.accept(ret);

        srgs.forEach(e -> e.accept(ret));
        excs.forEach(e -> e.accept(ret));

        ret.annotate(annotate);
        ret.keepImports(keepImports);

        return ret;
    }
}
