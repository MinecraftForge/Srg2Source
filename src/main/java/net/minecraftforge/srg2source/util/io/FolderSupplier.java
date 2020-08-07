package net.minecraftforge.srg2source.util.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraftforge.srg2source.api.InputSupplier;
import net.minecraftforge.srg2source.api.OutputSupplier;

public class FolderSupplier implements InputSupplier, OutputSupplier {
    protected final File root;

    public FolderSupplier(File root) {
        if (!root.exists())
            root.mkdirs();
        this.root = root;
    }

    @Override
    public OutputStream getOutput(String relPath) {
        try {
            File out = new File(root, relPath);
            if (!out.exists()) {
                out.getParentFile().mkdirs();
                out.createNewFile();
            }
            return new FileOutputStream(new File(root, relPath));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public InputStream getInput(String relPath) {
        try {
            return new FileInputStream(new File(root, relPath));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter) {
        final Path proot = root.toPath();
        try {
            return Files.walk(proot).filter(Files::isRegularFile)
                    .map(proot::relativize).map(Path::toString)
                    .filter(p -> p.endsWith(endFilter))
                    .sorted().collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @Override
    public void close() throws IOException {
        // they are files.. what do you want me to do?
    }

    @Override
    public String getRoot(String resource) {
        return root.getAbsolutePath();
    }
}
