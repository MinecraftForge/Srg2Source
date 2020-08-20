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

package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import net.minecraftforge.srg2source.api.RangeApplierBuilder;
import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.api.SourceVersion;
import net.minecraftforge.srg2source.apply.RangeApplier;
import net.minecraftforge.srg2source.extract.RangeExtractor;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.FolderSupplier;

public class SingleTests {

    //@Test public void testLambda()         { testClass("Lambda");         }
    @Test public void testGenerics()       { testClass("GenericClasses"); }
    @Test public void testAnonClass()      { testClass("AnonClass"     ); }
    @Test public void testInnerClass()     { testClass("InnerClass"    ); }
    @Test public void testLocalClass()     { testClass("LocalClass"    ); }
    @Test public void testImportSpaces()   { testClass("ImportSpaces"  ); }
    @Test public void testNestedGenerics() { testClass("NestedGenerics"); }
    @Test public void testPackageInfo()    { testClass("PackageInfo"   ); }
    //@Test public void testCache()          { testClass("GenericClasses"); }
    @Test public void testWhiteSpace()     { testClass("Whitespace"    ); }

    private Path getRoot() {
        URL url = this.getClass().getResource("/test.marker");
        Assert.assertNotNull("Could not find test.marker", url);
        try {
            return new File(url.toURI()).getParentFile().toPath();
        } catch (URISyntaxException e) {
            return new File(url.getPath()).getParentFile().toPath();
        }
    }

    public void testClass(final String name) {
        final Path root = getRoot().resolve(name);

        Assert.assertTrue("Unknown test: " + root.toAbsolutePath(), Files.exists(root));

        List<Path> libraries = gatherLibraries(root, getRoot().resolve("libraries"));

        Path original = root.resolve("original");
        Path mapped = root.resolve("mapped");
        testExtract(original, root.resolve("original.range"), libraries);
        if (Files.exists(mapped)) {
            Path range = root.resolve("mapped.range");
            testExtract(mapped, range, libraries);
            testApply(original, range, mapped, root.resolve("mapped.tsrg"));
        }
    }

    private List<Path> gatherLibraries(Path root, Path libs) {
        final Path info = root.resolve("libs.txt");
        if (!Files.exists(info))
            return Collections.emptyList();
        try {
            List<Path> ret = Files.lines(info).map(l -> libs.resolve(l)).collect(Collectors.toList());
            for (Path f : ret) {
                if (!Files.exists(f))
                    throw new IllegalStateException("Missing Library: " + f);
            }
            return ret;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + info.toAbsolutePath(), e);
        }
    }

    private void testExtract(Path src, Path range, List<Path> libs) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteArrayOutputStream logs = new ByteArrayOutputStream();

        RangeExtractor extractor = new RangeExtractorBuilder()
            .sourceCompatibility(SourceVersion.JAVA_1_8)
            .input(new TestFolderSupplier(src))
            .logger(new PrintStream(logs))
            .output(new PrintWriter(data))
            .build();

        boolean worked = extractor.run();
        @SuppressWarnings("unused")
        String log = logs.toString();

        Assert.assertTrue("Failed to do work!", worked);
        Assert.assertEquals(getFileContents(range), data.toString());
    }

    private void testApply(Path original, Path range, Path mapped, Path srg) {
        try (FileSystem imfs = Jimfs.newFileSystem(Configuration.unix())) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Path out = imfs.getPath("/");
            RangeApplier applier = new RangeApplierBuilder()
                .logger(new PrintStream(bos))
                .input(new TestFolderSupplier(mapped))
                .output(out)
                .annotate(true)
                .range(range)
                .build();

            if (Files.exists(srg))
                applier.readSrg(srg);

            applier.run();

            compareDirs(original, out);
            //Compare log?
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void compareDirs(Path expected, Path actual) throws IOException {
        Set<String> lstExpected = Files.walk(expected).filter(Files::isRegularFile).map(p -> expected.relativize(p).toString().replace('\\', '/').replace(".txt", ".java")).collect(Collectors.toSet());
        Set<String> lstActual = Files.walk(actual).filter(Files::isRegularFile).map(p -> actual.relativize(p).toString().replace('\\', '/')).collect(Collectors.toSet());
        Assert.assertEquals("File listing differ", lstExpected, lstActual);
        Files.walk(actual).filter(Files::isRegularFile).forEach(p -> {
            String relative = actual.relativize(p).toString().replace(".java", ".txt");
            Assert.assertEquals("Files differ: " + relative, getFileContents(expected.resolve(relative)), getFileContents(p));
        });
    }

    private String getFileContents(Path file) {
        try {
            return new String(readFile(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file.toAbsolutePath(), e);
        }
    }

    private static byte[] readFile(Path input) throws IOException {
        try (InputStream stream = Files.newInputStream(input)) {
            return Util.readStream(stream);
        }
    }

    private static class TestFolderSupplier extends FolderSupplier {
        public TestFolderSupplier(Path root) {
            super(root, StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getInput(String path) {
            if (path.endsWith(".java"))
                return super.getInput(path.substring(0, path.length() - 4) + "txt");
            return super.getInput(path);
        }

        @Override
        public List<String> gatherAll(String endFilter) {
            if (!".java".equals(endFilter))
                return super.gatherAll(endFilter);

            return super.gatherAll(".txt").stream().map(f -> f.substring(0, f.length() - 4) + ".java").collect(Collectors.toList());
        }
    }
}
