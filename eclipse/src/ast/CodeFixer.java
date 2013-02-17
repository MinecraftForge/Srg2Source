package ast;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;

@SuppressWarnings("unchecked")
public class CodeFixer
{
    private static String SRC = null;
    private static String[] libs = null;
    private static ClassTree TREE = new ClassTree(false);
    
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
    
    public static void main(String[] args)
    {        
        SRC = new File(args[0]).getAbsolutePath();
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
        
        String[] files = RangeExtractor.gatherFiles(SRC, ".java");
        try
        {
            ArrayList<SourceKey> srcClasses = createTree(files);
            
            log("Processing Source:");
            HashMap<String, ArrayList<FixTypes>> fixes = findFixes(srcClasses);
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
    
    private static HashMap<String, ArrayList<FixTypes>> findFixes(ArrayList<SourceKey> files)
    {
        HashMap<String, ArrayList<FixTypes>> ret = new HashMap<String, ArrayList<FixTypes>>();
        for (SourceKey src : files)
        {
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
                                    String clsName = cls.getName().getFullyQualifiedName().replace('.', '/');
                                    if (!ret.containsKey(clsName)) ret.put(clsName, new ArrayList<FixTypes>());
                                    ret.get(clsName).add(new FixTypes.PublicField(field));
                                    exit = true;
                                    break;
                                }
                            }
                            if (exit) break;
                        }
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
                        String clsName = cls.getName().getFullyQualifiedName().replace('.', '/');
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
        MethodDeclaration mtd = null;
        for (MethodDeclaration m : cls.getMethods())
        {
            if (m.getName().toString().equals(name))
            {
                String[] pts = (args.length() > 0 ? args.split(", ") : new String[0]);
                List<SingleVariableDeclaration> pars = m.parameters();
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
                        String clsName = cls.getName().getFullyQualifiedName().replace('.', '/');
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
