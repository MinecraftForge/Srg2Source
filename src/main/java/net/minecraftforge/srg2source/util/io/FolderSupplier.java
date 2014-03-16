package net.minecraftforge.srg2source.util.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import com.google.common.io.Files;

public class FolderSupplier implements InputSupplier, OutputSupplier
{
    private final File root;

    public FolderSupplier(File root)
    {
        if (!root.exists())
        {
            root.mkdirs();
        }
        this.root = root;
    }

    @Override
    public OutputStream getOutput(String relPath)
    {
        try
        {
            File out = new File(root, relPath);
            if (!out.exists())
            {
                out.getParentFile().mkdirs();
                out.createNewFile();
            }
            return Files.newOutputStreamSupplier(new File(root, relPath)).getOutput();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    @Override
    public InputStream getInput(String relPath)
    {
        try
        {
            return Files.newInputStreamSupplier(new File(root, relPath)).getInput();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        LinkedList<String> out = new LinkedList<String>();
        Stack<File> dirStack = new Stack<File>();
        dirStack.push(root);
        
        int rootCut = root.getAbsolutePath().length() + 1; // +1 for the slash

        while(dirStack.size() > 0)
        {
            for (File f : dirStack.pop().listFiles())
            {
                if (f.isDirectory())
                    dirStack.push(f);
                else if (f.getPath().endsWith(endFilter))
                    out.add(f.getAbsolutePath().substring(rootCut));
            }
        }
        
        return out;
    }

    @Override
    public void close() throws IOException
    {
        // they are files.. what do you want me to do?
    }

    @Override
    public String getRoot(String resource)
    {
        return root.getAbsolutePath();
    }
}
