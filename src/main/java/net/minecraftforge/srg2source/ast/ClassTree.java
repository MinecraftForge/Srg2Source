package net.minecraftforge.srg2source.ast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jdt.core.dom.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassTree
{
    private HashMap<String, Class> classes = new HashMap<String, Class>();
    private boolean includeInterfaces = true;

    public ClassTree()
    {
    }
    
    public ClassTree(boolean includeInterfaces)
    {
        this.includeInterfaces = includeInterfaces;
    }

    public Class getClass(String name)
    {
        name = name.replace('.', '/');
        Class ret = classes.get(name);
        if (ret == null)
        {
            ret = new Class(name);
            ret.setInclueInterfaces(includeInterfaces);
            classes.put(name, ret);
        }
        return ret;
    }

    public void processLibrary(File file)
    {
        try
        {
            ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));

            ZipEntry entry = null;
            while ((entry = input.getNextEntry()) != null)
            {
                String name = entry.getName();

                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("."))
                {
                    continue; 
                }

                byte[] data = new byte[4096];
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                int len;
                while ((len = input.read(data)) != -1)
                {
                    buf.write(data, 0, len);
                }

                processClass(buf.toByteArray());
            }
            input.close();
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void processClass(byte[] data)
    {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        Class cls = getClass(classNode.name);
        if (cls.hasProcessed)
        {
            return; //Pre-processed class, shadowing a library
        }
        
        cls.access = classNode.access;
        cls.setParent(getClass(classNode.superName));

        for (String inter : (List<String>)classNode.interfaces)
        {
            cls.addInterface(getClass(inter));
        }

        for (FieldNode field : (List<FieldNode>)classNode.fields)
        {
            cls.addField(new Node(cls, field.name, field.access, field.desc, false));
        }

        for (MethodNode method : (List<MethodNode>)classNode.methods)
        {
            cls.addMethod(new Node(cls, method.name, method.access, method.desc, true));
        }

        cls.hasProcessed = true;
    }
    
    public boolean processClass(AbstractTypeDeclaration type)
    {
        if (type instanceof AnnotationTypeDeclaration)
        {
            //log("Annotation!");
        }
        else if (type instanceof EnumDeclaration)
        {
            //log("ENUM!");
        }
        else if (type instanceof TypeDeclaration)
        {
            processClass((TypeDeclaration)type);
        }
        return true;
    } 
    
    @SuppressWarnings("unchecked")
    public void processClass(TypeDeclaration type)
    {
        String className = ((ITypeBinding)type.getName().resolveBinding()).getQualifiedName();
        Class cls = getClass(className);
        
        if (cls.hasProcessed)
        {
            return; //don't reprocess shadowed classes?
        }
        
        cls.access = type.getModifiers();
        
        if (type.getSuperclassType() != null)
        {
            cls.setParent(getClass(cleanType(type.getSuperclassType())));
        }
        
        for (Type i : (List<Type>)type.superInterfaceTypes())
        {
            cls.addInterface(getClass(cleanType(i)));
        }
        
        FieldDeclaration[] fields = type.getFields();
        for (FieldDeclaration field : fields)
        {
            String desc = MethodSignatureHelper.getTypeSignature(field.getType().resolveBinding());
            int access = field.getModifiers();

            for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>)field.fragments())
            {
                cls.addField(new Node(cls, frag.resolveBinding().getName(), access, desc, false));
            }
        }

        for (MethodDeclaration method : type.getMethods())
        {
            IMethodBinding bind = method.resolveBinding();
            if (bind == null)
            {
                //log(method.toString());
                continue;
            }
            String desc = MethodSignatureHelper.getSignature(bind);
            cls.addMethod(new Node(cls, method.getName().toString(), method.getModifiers(), desc, true));
        }

        cls.hasProcessed = true;
        
        //Inner classes
        for (BodyDeclaration body : (List<BodyDeclaration>)type.bodyDeclarations())
        {
            if (body instanceof AbstractTypeDeclaration)
            {
                processClass((AbstractTypeDeclaration)body);
            }
        }
    }
    public static String cleanType(Type type)
    {
        if (type == null)
        {
            return null;
        }

        // Go deeper.. reaching inside arrays
        // We want to report e.g. java.lang.String, not java.lang.String[]
        if (type.isArrayType())
        {
            type = ((ArrayType)type).getElementType();
        }
        
        if (type.isPrimitiveType())
        {
            return type.toString().replace('.', '/');
        }
    
        if (type.isParameterizedType())
        {
            type = ((ParameterizedType)type).getType();
        }
        
        if (type.isWildcardType())
        {
            return "WILDCARD!?!?!?";
        }
        
        if (type.isSimpleType())
        {
            
            SimpleType stype = (SimpleType)type;
            ITypeBinding bind = stype.getName().resolveTypeBinding().getErasure();
            return bind.getQualifiedName().replace('.', '/');
        }
        else
        {
            System.out.println("ERROR Unknown Type: " + type + type.getClass() + " " + type.getStartPosition() + '|' + type.getStartPosition() + type.getLength());
            return type.toString();
        }
    }

    public static class Node implements Comparable<Node>
    {
        public Class owner;
        public final String name;
        public int access;
        public final String desc;
        private boolean strict = false;

        public Node(Class owner, String name, int access, String desc, boolean strict)
        {
            this(name, access, desc, strict);
            this.owner = owner;
        }

        private Node(String name, int access, String desc, boolean strict)
        {
            this.name = name;
            this.access = access;
            this.desc = desc;
            this.strict = strict;
            if (this instanceof Class)
            {
                this.owner = (Class)this;
            }
        }

        public String toString()
        {
            return String.format("%s %s", name, desc);
        }

        public String getFullDesc()
        {
            return String.format("%s/%s %s", owner.name, name, desc);
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof Node)
            {
                Node t = (Node)o;
                if (strict)
                    return t.strict && t.name.equals(name) && t.desc.equals(desc);
                else
                    return !t.strict && t.name.equals(name);
            }

            return false;
        }

        @Override
        public int hashCode()
        {
            return name.hashCode() ^ (strict ? desc.hashCode() : 0);
        }

        @Override
        public int compareTo(Node o)
        {
            return name.compareTo(o.name);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static class Class extends Node
    {
        protected boolean hasProcessed = false;
        private boolean includeInterfaces = true;

        private Class parent = null;
        private ArrayList<Class> interfaces = new ArrayList<Class>();
        private ArrayList<Class> children   = new ArrayList<Class>();
        private ArrayList<Node>  fields     = new ArrayList<Node>();
        private ArrayList<Node>  methods    = new ArrayList<Node>();
        

        public Class(String name)
        {
            super(name, 0, "", false);
        }
        
        public void setInclueInterfaces(boolean value)
        {
            includeInterfaces = value;
        }

        public Class setParent(Class cls)
        {
            parent = cls;
            parent.addChild(this);
            return this;
        }

        public Class getParent()
        {
            return parent;
        }

        private void addNode(ArrayList lst, Node fld)
        { 
            if (lst.contains(fld))
            {
                lst.remove(fld);
            }
            lst.add(fld); 
        }

        public void addInterface(Class cls)
        { 
            addNode(interfaces, cls);
            cls.addChild(this);
        }
        public void addField(Node fld)      { addNode(fields, fld);     }
        public void addChild(Class cls)     { addNode(children, cls);   }
        public void addMethod(Node mtd)
        {
            if (!mtd.name.equals("<clinit>"))
            {
                addNode(methods, mtd);
            }
        }

        private <T> ArrayList<T> sort(ArrayList lst)
        {
            Collections.sort(lst);
            return lst;
        }

        public ArrayList<Class> getChildren()  { return sort((ArrayList<Class>)children.clone());   }
        public ArrayList<Class> getInterfaces(){ return sort((ArrayList<Class>)interfaces.clone()); }
        public ArrayList<Node>  getFields()    { return sort((ArrayList<Node> )fields.clone());     }
        public ArrayList<Node>  getMethods()   { return sort((ArrayList<Node> )methods.clone());    }

        public boolean hasChildren()   { return children.size()   > 0; }
        public boolean hasInterfaces() { return interfaces.size() > 0; }
        public boolean hasFields()     { return fields.size()     > 0; }
        public boolean hasMethods()    { return methods.size()    > 0; }
        
        public Node getField(String name)
        {
            Node search = new Node(name, 0, "", false);
            return (fields.contains(search) ? fields.get(fields.indexOf(search)) : null);
        }

        public Node getMethod(String name, String desc)
        {
            Node search = new Node(name, 0, desc, true);
            return (methods.contains(search) ? methods.get(methods.indexOf(search)) : null);
        }

        public Node[] getMethods(String name)
        {
            ArrayList<Node> ret = new ArrayList<Node>();
            for (Node n : methods)
            {
                if (n.name.equals(name))
                {
                    ret.add(n);
                }
            }
            return ret.toArray(new Node[ret.size()]);
        }

        public Node getTopField(String name)
        {
            return getTop(name, null, true);
        }
        
        public Node getTopMethod(String name, String desc)
        {
            if (name.equals("<init>"))
            {
                return getMethod(name, desc);
            }
            else
            {
                return getTop(name, desc, true);
            }
        }

        private Node getTop(String name, String desc, boolean isBottom)
        {
            Node ret = null;
            if (parent != null)
            {
                ret = parent.getTop(name, desc, false);
                if (ret != null) return ret;
            }

            if (includeInterfaces)
            {
                for (Class cls : interfaces)
                {
                    ret = cls.getTop(name, desc, false);
                    if (ret != null) return ret;
                }
            }

            Node f = (desc == null ? getField(name) : getMethod(name, desc));
            if (f != null)
            {
                if (!Modifier.isPrivate(f.access) || isBottom) return f; 
            }
            return null;
        }

        public boolean isChild(Class child)
        {
            return (getChildren().contains(this) || getParent().isChild(child));
        }
    }
    
    public static void log(String s)
    {
        System.out.println(s);
    }
}
