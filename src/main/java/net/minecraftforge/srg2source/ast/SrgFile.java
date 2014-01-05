package net.minecraftforge.srg2source.ast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class SrgFile
{
    private File source;
    private HashMap<String, Class> classes = new HashMap<String, Class>();
    private HashMap<String, Class> rclasses = new HashMap<String, Class>();
    
    public SrgFile(File source)
    {
        this.source = source;
    }

    public Class getClass(String name)
    {
        return classes.get(name);
    }
    public Class getClass2(String name)
    {
        return rclasses.get(name);
    }
    
    public Class getClass(String name, String rename)
    {
        if (!classes.containsKey(name))
        {
            Class cls = new Class(this, name, rename);
            classes.put(name, cls);
            rclasses.put(rename, cls);
        }
        return classes.get(name);
    }
    
    public SrgFile read() throws IOException
    {
        BufferedReader reader = new BufferedReader(new FileReader(source));
        
        String line = null;
        while ((line = reader.readLine()) != null)
        {
            String[] pts = line.split(" ");
            if (pts[0].equals("CL:"))
            {
                getClass(pts[1], pts[2]);
            }
            else if (pts[0].equals("FD:"))
            {
                String cls1  = pts[1].substring(0, pts[1].lastIndexOf('/'));
                String cls2  = pts[2].substring(0, pts[2].lastIndexOf('/'));
                String name1 = pts[1].substring(cls1.length() + 1);
                String name2 = pts[2].substring(cls2.length() + 1);
                Class cls = getClass(cls1, cls2);
                cls.fields1.put(name1, new Node(cls, name1, null, name2, null));
                cls.fields2.put(name2, new Node(cls, name2, null, name1, null));
            }
            else if (pts[0].equals("MD:"))
            {
                String cls1 = pts[1].substring(0, pts[1].lastIndexOf('/'));
                String cls2 = pts[3].substring(0, pts[3].lastIndexOf('/'));
                String name1 = pts[1].substring(cls1.length() + 1);
                String name2 = pts[3].substring(cls2.length() + 1);
                Class cls = getClass(cls1, cls2);
                cls.methods1.put(name1+pts[2], new Node(cls, name1, pts[2], name2, pts[4]));
                cls.methods2.put(name2+pts[4], new Node(cls, name2, pts[4], name1, pts[2]));
                //System.out.println(name1+pts[2]);
            }
        }
        
        reader.close();
        return this;
    }

    public static class Node implements Comparable<Node>
    {
        public Class owner;
        public final String name;
        public final String desc;
        public final String rename;
        public final String renameDesc;

        public Node(Class owner, String name, String desc, String rename, String renameDesc)
        {
            this.name = name;
            this.desc = desc;
            this.renameDesc = renameDesc;
            this.owner = owner;
            this.rename = rename;
        }

        public String toString(){ return String.format("%s %s", name, desc); }
        public String getFullDesc(){ return String.format("%s/%s %s", owner.name, name, desc); }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof Node)
            {
                Node t = (Node)o;
                if (desc != null)
                    return t.desc != null && t.name.equals(name) && t.desc.equals(desc);
                else
                    return t.desc == null && t.name.equals(name);
            }

            return false;
        }

        @Override
        public int hashCode(){ return name.hashCode() ^ (desc != null ? desc.hashCode() : 0); }
        @Override
        public int compareTo(Node o){ return name.compareTo(o.name); }
    }
    
    public static class Class
    {
        public SrgFile file;
        public String name;
        public String rename;
        public HashMap<String, Node> fields1  = new HashMap<String, Node>();
        public HashMap<String, Node> fields2  = new HashMap<String, Node>();
        public HashMap<String, Node> methods1 = new HashMap<String, Node>();
        public HashMap<String, Node> methods2 = new HashMap<String, Node>();

        public Class(SrgFile file, String name, String rename)
        {
            this.file = file;
            this.name = name;
            this.rename = rename;
        }
    }
}
