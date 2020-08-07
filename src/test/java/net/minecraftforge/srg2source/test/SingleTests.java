package net.minecraftforge.srg2source.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.api.SourceVersion;
import net.minecraftforge.srg2source.extract.RangeExtractor;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.FolderSupplier;

public class SingleTests {

    //@Test public void testLambda()         { testClass("Lambda");         }
    //@Test public void testGenerics()       { testClass("GenericClasses"); }
    //@Test public void testAnonClass()      { testClass("AnonClass"     ); }
    //@Test public void testInnerClass()     { testClass("InnerClass"    ); }
    //@Test public void testLocalClass()     { testClass("LocalClass"    ); }
    //@Test public void testImportSpaces()   { testClass("ImportSpaces"  ); }
    @Test public void testNestedGenerics() { testClass("NestedGenerics"); }
    //@Test public void testPackageInfo()    { testClass("PackageInfo"   ); }
    //@Test public void testCache()          { testClass("GenericClasses"); }
    //@Test public void testWhiteSpace()     { testClass("Whitespace"    ); }

    private File getRoot() {
        URL url = this.getClass().getResource("/test.marker");
        Assert.assertNotNull("Could not find test.marker", url);
        try {
            return new File(url.toURI()).getParentFile();
        } catch (URISyntaxException e) {
            return new File(url.getPath()).getParentFile();
        }
    }

    public void testClass(final String name) {
        final File root = new File(getRoot(), name);

        Assert.assertTrue("Unknown test: " + root.getAbsolutePath(), root.exists());

        List<File> libraries = gatherLibraries(root, new File(getRoot(), "libraries"));

        testExtract(new File(root, "original"), new File(root, "original.range"), libraries);
    }

    private List<File> gatherLibraries(File root, File libs) {
        final File info = new File(root, "libs.txt");
        if (!info.exists())
            return Collections.emptyList();
        try {
            List<File> ret = Files.lines(info.toPath()).map(l -> new File(libs, l)).collect(Collectors.toList());
            for (File f : ret) {
                if (!f.exists())
                    throw new IllegalStateException("Missing Library: " + f);
            }
            return ret;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + info.getAbsolutePath(), e);
        }
    }

    private void testExtract(File src, File range, List<File> libs) {
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
        if (range.exists())
            Assert.assertEquals(getFileContents(range), data.toString());

        //testApply(resource, clsName);
    }

    /*
    private void testApply(final String resource, final String clsName) throws IOException {
        File srg = null;
        File map = null;
        try {
            URL url = getClass().getResource("/" + resource + "_srg.txt");
            if (url == null)
                return;
            srg = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new FileNotFoundException("/" + resource + "_srg.txt");
        }
        try {
            map = new File(getClass().getResource("/" + resource + "_ret.txt").toURI());
        } catch (URISyntaxException e) {
            throw new FileNotFoundException("/" + resource + "_ret.txt");
        }
        if (!srg.exists())
            return;

        MemoryOutputSupplier out = new MemoryOutputSupplier();
        RangeApplier applier = new RangeApplier();
        applier.readSrg(srg);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        applier.setLogger(new PrintStream(bos));
        applier.setInput(new SimpleInputSupplier(resource, clsName));
        applier.setOutput(out);
        applier.annotate(true);
        applier.readRangeMap(map);

        applier.run();
        Assert.assertEquals(getFileContents(resource, "_maped.txt"), out.get(0));
        Assert.assertEquals(getFileContents(resource, "_maped_ret.txt"), bos.toString().replaceAll("\r?\n", "\n"));
    }
    */

    private String getFileContents(File file) {
        try {
            return new String(readFile(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file.getAbsolutePath(), e);
        }
    }

    private static byte[] readFile(File input) throws IOException {
        try (InputStream stream = new FileInputStream(input)) {
            return Util.readStream(stream);
        }
    }

    private static class TestFolderSupplier extends FolderSupplier {
        public TestFolderSupplier(File root) {
            super(root);
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
