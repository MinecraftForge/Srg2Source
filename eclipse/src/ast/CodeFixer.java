package ast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

@SuppressWarnings("unchecked")
public class CodeFixer
{
    private static String SRC = null;
    private static String[] libs = null;
    private static ClassTree TREE = new ClassTree(false);
    private static SrgFile SRG;
    
    private static class SourceKey
    {
        String name;
        CompilationUnit cu;
        String data;
        ArrayList<TypeDeclaration> classes;
        
        SourceKey(String name, CompilationUnit cu, String data, ArrayList<TypeDeclaration> classes)
        {
            this.name = name;
            this.cu = cu;
            this.data = data;
            this.classes = classes;
        }
    }
    
    private static Method addURL;
    
    public static void main(String[] args) throws Exception
    {        
        SRC = new File(args[0]).getAbsolutePath();
        SRG = new SrgFile(new File(args[2])).read();
        if  (!args[1].equalsIgnoreCase("none") && args[1].length() != 0)
        {
            if (args[1].contains(File.pathSeparator))
            {
                libs = args[1].split(File.pathSeparator);
            }
            else
            {
                libs = RangeExtractor.gatherFiles(new File(args[1]).getAbsolutePath(), ".jar");
            }
        }
        else
        {
            libs = new String[0];
        }

        addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addURL.setAccessible(true);
        for (String s : libs)
        {
            addURL.invoke(CodeFixer.class.getClassLoader(), new File(s).toURI().toURL());
        }
        
        String[] files = RangeExtractor.gatherFiles(SRC, ".java");
        try
        {
            ArrayList<SourceKey> srcClasses = createTree(files);
            
            log("Gathering Fixes:");
            HashMap<String, ArrayList<FixTypes>> classFixes = findFixes(srcClasses);
            
            log("Applying Fixes:");
            for (String key : classFixes.keySet())
            {
                ArrayList<FixTypes> fixes = classFixes.get(key);
                Collections.sort(fixes);
                
                log("  " + key);
                
                SourceKey data = null;
                for (SourceKey s : srcClasses)
                {
                    if (s.name.equals(key))
                    {
                        data = s;
                        break;
                    }
                }
                
                if (data == null)
                {
                    log("    Could not find sourcekey for fixes: " + key);
                    System.exit(1);
                }
                
                int offset = 0;
                String src = data.data;
                
                for (FixTypes fix : fixes)
                {
                    System.out.println("    Fix: " + fix + " " + offset);
                    
                    String pre = src.substring(0, fix.getStart() + offset);
                    String post = src.substring(fix.getStart() + fix.getLength() + offset);
                    
                    src = pre + fix.newText + post; 
                    offset += (fix.newText.length() - fix.getLength());
                }
                
                String outFile = SRC + "/" + key + ".java";
                try
                {
                    FileWriter out = new FileWriter(outFile);
                    out.write(src);
                    out.close();
                }
                catch (IOException e)
                {
                    System.out.println("Exception " + e.toString());
                }
                System.out.println("");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private static ArrayList<SourceKey> createTree(String[] files)
    {
        ArrayList<SourceKey> ret = new ArrayList<SourceKey>();
        try
        {            
            log("Processing Source Tree:");
            for (String file : files)
            {
                String data = FileUtils.readFileToString(new File(file), Charset.forName("UTF-8")).replaceAll("\r", "");
                String name = file.replace('\\', '/').substring(SRC.length() + 1);
                log("    Processing " + name);
                CompilationUnit cu = RangeExtractor.createUnit(name, data, SRC, libs);

                ArrayList<TypeDeclaration> classes = new ArrayList<TypeDeclaration>();
                List<AbstractTypeDeclaration> types = (List<AbstractTypeDeclaration>)cu.types();
                for (AbstractTypeDeclaration type : types)
                {
                    TREE.processClass(type);
                    if (type instanceof TypeDeclaration)
                    {
                        classes.add((TypeDeclaration)type);
                    }
                }
                ret.add(new SourceKey(name.substring(0, name.length() - 5), cu, data.trim(), classes));
            }
            
            for (String lib : libs)
            {
                //log("Processing Tree: " + lib);
                TREE.processLibrary(new File(lib));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        
        return ret;
    }
    
    @SuppressWarnings("rawtypes")
    private static HashMap<String, ArrayList<FixTypes>> findFixes(ArrayList<SourceKey> files) throws ClassNotFoundException
    {
        HashMap<String, ArrayList<FixTypes>> ret = new HashMap<String, ArrayList<FixTypes>>();
        for (SourceKey src : files)
        {
            System.out.println("    Processing " + src.name);
            ArrayList<IProblem> errors = new ArrayList<IProblem>();
            HashMap<String, ArrayList<IProblem>> duplicates = new HashMap<String, ArrayList<IProblem>>();
            
            for (IProblem p : src.cu.getProblems())
            {
                if (p.isError())
                {
                    int id = (p.getID() & IProblem.IgnoreCategoriesMask);
                    if (id == 169) continue; //Screw you switch errors that arnt errrors
                    
                    if (id == 355) //Duplicate methods
                    {
                        String name = p.getArguments()[0];
                        if (!duplicates.containsKey(name)) duplicates.put(name, new ArrayList<IProblem>());
                        duplicates.get(name).add(p);
                    }
                    else if (id == 101) //Non-visible method
                    {
                        if (!gatherMethod(ret, getClass(p.getArguments()[0], files), p.getArguments()[1], p.getArguments()[2]))
                        {
                            log("Could not find class: " + p.getArguments()[0]);
                            log(p.toString());
                            System.exit(1);
                        }
                    }
                    else if (id == 71) //Non-visible field
                    {
                        String find = p.getArguments()[0];
                        TypeDeclaration cls = getClass(p.getArguments()[1], files);
                        if (cls == null)
                        {
                            log("Could not find class for field " + p.toString());
                            System.exit(1);
                        }
                        boolean exit = false;
                        for (FieldDeclaration field : cls.getFields())
                        {
                            for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>)field.fragments())
                            {
                                String name = frag.resolveBinding().getName();
                                if (find.equals(name))
                                {
                                    String clsName = cls.resolveBinding().getQualifiedName().replace('.', '/');
                                    if (!ret.containsKey(clsName)) ret.put(clsName, new ArrayList<FixTypes>());
                                    ret.get(clsName).add(new FixTypes.PublicField(field));
                                    exit = true;
                                    break;
                                }
                            }
                            if (exit) break;
                        }
                    }
                    else if (id == 400) //Missing Method
                    {
                        String name  = p.getArguments()[0];
                        String tmp   = p.getArguments()[1];
                        String owner = p.getArguments()[2];
                        String impl  = p.getArguments()[3];
                        String[] args = (tmp.length() == 0 ? new String[0] : tmp.split(", "));
                        Class cls = Class.forName(owner, false, CodeFixer.class.getClassLoader());
                        String signature = null;
                        Class<?> returnType = null; 
                        
                        for (Method m : cls.getMethods())
                        {
                            if (m.getName().equals(name))
                            {
                                Class[] types = m.getParameterTypes();
                                if (types.length == args.length)
                                {
                                    boolean same = true;
                                    for (int x = 0; x < args.length; x++)
                                    {
                                        if (!args[x].equals(types[x].getName().toString()))
                                        {
                                            same = false;
                                            break;
                                        }
                                    }
                                    if (same)
                                    {
                                        signature = MethodSignatureHelper.getSignature(m);
                                        returnType = m.getReturnType();
                                    }
                                }   
                            }
                            if (signature != null) break;
                        }
                        
                        SrgFile.Class icls = SRG.getClass2(impl.replace('.', '/'));
                        if (icls == null)
                        {
                            System.out.println("Could not find class in SRG " + impl);
                            System.exit(1);
                        }
                        
                        SrgFile.Node mtd = icls.methods1.get(name + signature);
                        
                        ClassTree.Class treeNode = TREE.getClass(impl);
                        treeNode = treeNode.getParent();
                        if (treeNode == null)
                        {
                            System.out.println("Could not find missing method, and parent was null: " + impl + "." + name + signature);
                            System.exit(1);
                        }
                        
                        while(mtd == null && treeNode != null)
                        {
                            icls = SRG.getClass2(treeNode.name);
                            treeNode = treeNode.getParent();
                            if (icls != null)
                            {
                                mtd = icls.methods1.get(name + signature);
                            }
                        }
                        
                        if (mtd == null)
                        {
                            System.out.println("Could not find bounce rename " + icls.name + '/' + name + signature);
                            System.exit(1);
                        }
                        
                        String clsName = impl.replace('.', '/');
                        if (!ret.containsKey(clsName)) ret.put(clsName, new ArrayList<FixTypes>());
                        ret.get(clsName).add(new FixTypes.BounceMethod(getClass(impl, files), name, mtd.rename, args, returnType));
                    }
                    else
                    {
                        errors.add(p);
                    }
                }
            }
            
            gatherDuplicateFixes(duplicates, ret, src);
            
            if (errors.size() > 0)
            {
                System.out.println(src.name);
                for (IProblem p : errors)
                {
                    System.out.println("    " + p);
                    //for (String s : p.getArguments()) System.out.println("        " + s);
                }
            }
            
        }
        return ret;
    }
    
    private static TypeDeclaration getClass(String name, ArrayList<SourceKey> keys)
    {
        for (SourceKey src : keys)
        {
            for (TypeDeclaration t : src.classes)
            {
                if (t.resolveBinding().getBinaryName().equals(name))
                {
                    return t;
                }
            }
        }
        return null;
    }
    
    private static void gatherDuplicateFixes(HashMap<String, ArrayList<IProblem>> duplicates, HashMap<String, ArrayList<FixTypes>> ret, SourceKey src)
    {
        for (Map.Entry<String, ArrayList<IProblem>> ent : duplicates.entrySet())
        {
            IProblem p = ent.getValue().get(0);
            TypeDeclaration cls = null;
            for (TypeDeclaration t : src.classes)
            {
                if (t.getStartPosition() <= p.getSourceStart() && (t.getStartPosition() + t.getLength()) >= p.getSourceEnd())
                {
                    cls = t;
                    break;
                }
            }
            if (cls == null)
            {
                System.out.println("WTF! COULD NOT FIND DUPLICATE CLASS");
                System.exit(1);
            }
            
            for (MethodDeclaration mtd : cls.getMethods())
            {
                if (mtd.getName().toString().equals(ent.getKey()))
                {
                    //Resolving will fail on duplicate methods.. HOPE that the real 
                    //one is the first one, only real case is the synthetic method 
                    //in Mooshroom, which this works
                    if (mtd.resolveBinding() == null) 
                    {
                        String clsName = cls.resolveBinding().getQualifiedName().replace('.', '/');
                        if (!ret.containsKey(clsName)) ret.put(clsName, new ArrayList<FixTypes>());
                        ret.get(clsName).add(new FixTypes.RemoveMethod(mtd));
                    }
                }
            }
        }
    }
    
    private static boolean gatherMethod(HashMap<String, ArrayList<FixTypes>> ret, TypeDeclaration cls, String name, String args)
    {
        if (cls == null)
        {
            return false;
        }
        for (MethodDeclaration mtd : cls.getMethods())
        {
            if (mtd.getName().toString().equals(name))
            {
                String[] pts = (args.length() > 0 ? args.split(", ") : new String[0]);
                List<SingleVariableDeclaration> pars = mtd.parameters();
                if (pars.size() == pts.length)
                {
                    boolean same = true;
                    for (int x = 0; x < pts.length; x++)
                    {
                        String clean = ClassTree.cleanType(pars.get(x).getType()).replace('/', '.');
                        if (!clean.equals(pts[x]))
                        {
                            same = false;
                            break;
                        }
                    }
                    if (same)
                    {
                        String clsName = cls.resolveBinding().getQualifiedName().replace('.', '/');
                        if (!ret.containsKey(clsName)) ret.put(clsName, new ArrayList<FixTypes>());
                        ret.get(clsName).add(new FixTypes.PublicMethod(mtd));
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public static void log(String s)
    {
        System.out.println(s);
    }  
}
