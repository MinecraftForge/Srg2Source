/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.api;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraftforge.srg2source.extract.RangeExtractor;
import net.minecraftforge.srg2source.util.io.ChainedInputSupplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

public class RangeExtractorBuilder {
    private SourceVersion sourceVersion = SourceVersion.JAVA_1_8;
    private PrintStream logStd = System.out;
    private PrintStream logErr = System.err;
    private PrintWriter output = null;
    private boolean batch = true;
    private List<File> libraries = new ArrayList<>();
    private List<InputSupplier> inputs = new ArrayList<>();
    private File cache = null;
    private boolean enableMixins = false;
    private boolean fatalMixins = false;
    private boolean logWarnings = false;
    private boolean enablePreview = false;
    private boolean failOnError = false;

    public RangeExtractorBuilder sourceCompatibility(SourceVersion value) {
        this.sourceVersion = value;
        return this;
    }

    public RangeExtractorBuilder logger(File value) {
        try {
            return logger(new PrintStream(value));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RangeExtractorBuilder logger(Path value) {
        try {
            return logger(new PrintStream(Files.newOutputStream(value)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RangeExtractorBuilder logger(PrintStream value) {
        this.logStd = value;
        return this;
    }

    public RangeExtractorBuilder errorLogger(PrintStream value) {
        this.logErr = value;
        return this;
    }

    public RangeExtractorBuilder output(File value) {
        return output(value, StandardCharsets.UTF_8);
    }

    public RangeExtractorBuilder output(File value, Charset encoding) {
        try {
            if (!value.exists()) {
                File parent = value.getCanonicalFile().getParentFile();
                if (!parent.exists())
                    parent.mkdirs();
                value.createNewFile();
            }

            return output(new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(value), encoding))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RangeExtractorBuilder output(Path value) {
        return output(value, StandardCharsets.UTF_8);
    }

    public RangeExtractorBuilder output(Path value, Charset encoding) {
        try {
            if (!Files.exists(value)) {
                Path parent = value.toAbsolutePath().getParent();
                if (!Files.exists(parent))
                    Files.createDirectories(parent);
                Files.createFile(value);
            }
            return output(new PrintWriter(Files.newBufferedWriter(value, encoding)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RangeExtractorBuilder output(PrintWriter value) {
        if (output != null)
            output.close();
        output = value;
        return this;
    }

    public RangeExtractorBuilder batch() {
        return this.batch(true);
    }

    public RangeExtractorBuilder batch(boolean value) {
        this.batch = true;
        return this;
    }

    public RangeExtractorBuilder library(File value) {
        this.libraries.add(value);
        return this;
    }


    public RangeExtractorBuilder input(Path value) {
        return input(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("resource")
    public RangeExtractorBuilder input(Path value, @Nullable Charset encoding) {
        if (value == null || !Files.exists(value))
            throw new IllegalArgumentException("Invalid input value: " + value);

        String filename = value.getFileName().toString().toLowerCase(Locale.ENGLISH);
        try {
            if (Files.isDirectory(value))
                inputs.add(FolderSupplier.create(value, encoding));
            else if (filename.endsWith(".jar") || filename.endsWith(".zip")) {
                try {
                    inputs.add(ZipInputSupplier.create(value, encoding));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else
                throw new IllegalArgumentException("Invalid input value: " + value);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid input: " + value, e);
        }

        return this;
    }

    public RangeExtractorBuilder input(InputSupplier value) {
        this.inputs.add(value);
        return this;
    }

    public RangeExtractorBuilder cache(File value) {
        this.cache = value;
        return this;
    }

    public RangeExtractorBuilder enableMixins() {
        this.enableMixins = true;
        return this;
    }

    public RangeExtractorBuilder fatalMixins() {
        this.fatalMixins = true;
        return this;
    }

    public RangeExtractorBuilder logWarnings() {
        this.logWarnings = true;
        return this;
    }

    public RangeExtractorBuilder enablePreview() {
        this.enablePreview = true;
        return this;
    }

    public RangeExtractorBuilder failOnError() {
        this.failOnError = true;
        return this;
    }

    public RangeExtractor build() {
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

        if (this.enableMixins)
            ret.enableMixins();
        if (this.fatalMixins)
            ret.fatalMixins();
        if (this.logWarnings)
            ret.logWarnings();
        if (this.enablePreview)
            ret.enablePreview();
        if (this.failOnError)
            ret.failOnError();

        if (this.cache != null) {
            try (InputStream fin = new FileInputStream(this.cache)) {
                ret.loadCache(fin);
            } catch (IOException e) {
                System.out.println("Error Loading Caching: " + this.cache);
                e.printStackTrace();
            }
        }

        return ret;
    }
}
