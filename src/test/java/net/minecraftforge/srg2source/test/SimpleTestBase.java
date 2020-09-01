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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.Assert;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import net.minecraftforge.srg2source.api.RangeApplierBuilder;
import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.api.SourceVersion;
import net.minecraftforge.srg2source.apply.RangeApplier;
import net.minecraftforge.srg2source.extract.RangeExtractor;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.FolderSupplier;

public abstract class SimpleTestBase {
    private static final String FORGE_MAVEN = "https://files.minecraftforge.net/maven/";

    protected abstract String getPrefix();
    protected abstract List<String> getLibraries();

    private Path getRoot() {
        URL url = this.getClass().getResource("/test.marker");
        Assert.assertNotNull("Could not find test.marker", url);
        try {
            return new File(url.toURI()).getParentFile().toPath();
        } catch (URISyntaxException e) {
            return new File(url.getPath()).getParentFile().toPath();
        }
    }

    protected void testClass(final String name) {
        final Path root = getRoot().resolve(getPrefix()).resolve(name);

        Assert.assertTrue("Unknown test: " + root.toAbsolutePath(), Files.exists(root));

        List<File> libraries = gatherLibraries(root, getRoot().resolve("libraries"));

        Path original = root.resolve("original");
        Path mapped = root.resolve("mapped");
        testExtract(original, root.resolve("original.range"), libraries);
        if (Files.exists(mapped)) {
            Path range = root.resolve("mapped.range");
            testExtract(mapped, range, libraries);
            testApply(original, range, mapped, root.resolve("mapped.tsrg"));
        }
    }

    //TODO: Make libraries a Path if JDT supports it?
    private List<File> gatherLibraries(Path root, Path libs) {
        final List<String> ids = new ArrayList<>();

        final Path info = root.resolve("libs.txt");
        if (Files.exists(info)) {
            try {
                Files.lines(info).forEach(ids::add);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + info.toAbsolutePath(), e);
            }
        }
        ids.addAll(getLibraries());
        if (ids.isEmpty())
            return Collections.emptyList();

        final List<Path> ret = new ArrayList<>();
        for (String line : ids) {
            if (line.contains(":")) {
                String[] pts = line.split(":");
                if (pts.length != 3)
                    throw new IllegalStateException("Invalid Library Line: " + line);
                String path = pts[0].replace('.', '/') + '/' + pts[1] + '/' + pts[2] + '/' + pts[1] + '-' + pts[2] + ".jar";

                Path target = libs.resolve(path);
                if (!Files.exists(target)) {
                    try {
                        if (!downloadFile(new URL(FORGE_MAVEN + path), target, false))
                            throw new IllegalStateException("Could not download: " + FORGE_MAVEN + path);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not download: " + FORGE_MAVEN + path, e);
                    }
                }
                ret.add(target);
            } else { //Raw file? Die for now...
                throw new IllegalStateException("Invalid Library Line: " + line);
            }
        }
        return ret.stream().map(Path::toFile).collect(Collectors.toList());
    }

    private void testExtract(Path src, Path range, List<File> libs) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteArrayOutputStream logs = new ByteArrayOutputStream();

        RangeExtractor extractor = new RangeExtractorBuilder()
            .sourceCompatibility(SourceVersion.JAVA_1_8)
            .input(new TestFolderSupplier(src))
            .logger(new PrintStream(logs))
            .output(new PrintWriter(data))
            .build();

        libs.forEach(extractor::addLibrary);

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

    private static boolean downloadFile(URL url, Path output, boolean deleteOn404) {
        String proto = url.getProtocol().toLowerCase();

        try {
            if ("http".equals(proto) || "https".equals(proto)) {
                HttpURLConnection con = connectHttpWithRedirects(url);
                int responseCode = con.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return downloadFile(con, output);
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND && deleteOn404) {
                    delete(output);
                }
            } else {
                URLConnection con = url.openConnection();
                con.connect();
                return downloadFile(con, output);
            }
        } catch (FileNotFoundException e) {
            if (deleteOn404)
                delete(output);
        } catch (IOException e) {
            //Invalid URLs/File paths will cause FileNotFound or 404 errors.
            //As well as any errors during download.
            //So delete the output if it exists as it's invalid, and return false
            delete(output);
            throw new RuntimeException(e);
        }

        return false;
    }

    private static boolean downloadFile(URLConnection con, Path output) throws IOException {
        try {
            InputStream stream = con.getInputStream();
            int len = con.getContentLength();
            int read = -1;

            Path parent = output.toAbsolutePath().getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);

            try (OutputStream out = Files.newOutputStream(output)) {
                read = Util.transferTo(stream, out);
            }

            if (read != len) {
                delete(output);
                throw new IOException("Failed to read all of data from " + con.getURL() + " got " + read + " expected " + len);
            }

            return true;
        } catch (IOException e) {
            delete(output);
            throw e;
        }
    }

    private static void delete(Path target) {
        try {
            if (Files.exists(target))
                Files.delete(target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpURLConnection connectHttpWithRedirects(URL url) throws IOException {
        return connectHttpWithRedirects(url, (setupCon) -> {});
    }

    private static HttpURLConnection connectHttpWithRedirects(URL url, Consumer<HttpURLConnection> setup) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        setup.accept(con);
        con.connect();
        if ("http".equalsIgnoreCase(url.getProtocol())) {
            int responseCode = con.getResponseCode();
            switch (responseCode) {
                case HttpURLConnection.HTTP_MOVED_TEMP:
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_SEE_OTHER:
                    String newLocation = con.getHeaderField("Location");
                    URL newUrl = new URL(newLocation);
                    if ("https".equalsIgnoreCase(newUrl.getProtocol())) {
                        // Escalate from http to https.
                        // This is not done automatically by HttpURLConnection.setInstanceFollowRedirects
                        // See https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4959149
                        return connectHttpWithRedirects(newUrl, setup);
                    }
                    break;
            }
        }
        return con;
    }
}
