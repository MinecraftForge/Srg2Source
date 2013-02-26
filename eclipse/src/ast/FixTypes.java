package ast;

import org.eclipse.jdt.core.dom.*;
import java.util.List;

public class FixTypes implements Comparable<FixTypes>
{
    protected ASTNode node;
    protected int start  = 0;
    protected int length = 0;
    protected String newText = null;
    
    private FixTypes(ASTNode ast)
    {
        this.node = ast;
        start = node.getStartPosition();
        length = node.getLength();
    }
    
    @SuppressWarnings("unchecked")
    public static Modifier findAccessor(BodyDeclaration node)
    {
        for (IExtendedModifier m : ((List<IExtendedModifier>)node.modifiers()))
        {
            if (m instanceof Modifier)
            {
                Modifier mod = (Modifier)m;
                if (mod.isPrivate() || mod.isProtected())
                {
                    return mod;
                }
            }
        }
        return null;
    }

    @Override
    public int compareTo(FixTypes o){ return start - o.start; }
    public int getLength(){ return length; }
    public int getStart(){ return start; }
    
    public static class RemoveMethod extends FixTypes
    {
        public RemoveMethod(MethodDeclaration node)
        {
            super(node);
            newText = "/*\n" + node.toString() + "*/";
        }
    }

    public static class PublicMethod extends FixTypes
    {
        public PublicMethod(MethodDeclaration node)
        {
            super(node);
            
            Modifier target = FixTypes.findAccessor(node);
            newText = "public ";
            if (target == null)
            {
                start = node.getReturnType2().getStartPosition();
                length = 0;
            }
            else
            {
                start = target.getStartPosition();
                length = target.getLength() + 1;
            }
        }
    }
    
    public static class PublicField extends FixTypes
    {
        public PublicField(FieldDeclaration node)
        {
            super(node);
            
            Modifier target = FixTypes.findAccessor(node);
            newText = "public ";            
            if (target == null)
            {
                start = node.getType().getStartPosition();
                length = 0;
            }
            else
            {
                start = target.getStartPosition();
                length = target.getLength() + 1;
            }
        }
    }
    
    public static class BounceMethod extends FixTypes
    {
        public BounceMethod(TypeDeclaration node, String newName, String oldName, String[] args, Class<?> returnType)
        { 
            super(node);
            
            start = node.getStartPosition() + node.getLength() - 1;
            length = 0;            
            
            StringBuffer buf = new StringBuffer();
            buf.append("    public ").append(returnType.getName()).append(' ').append(newName).append('(');
            for (int x = 0; x < args.length; x++)
            {
                buf.append(args[x]).append(' ').append((char)('a' + x));
                if (x != args.length - 1) buf.append(", ");
            }
            
            buf.append("){\n        ");
            if (returnType != Void.TYPE) buf.append("return ");
            buf.append(oldName).append('(');
            for (int x = 0; x < args.length; x++)
            {
                buf.append((char)('a' + x));
                if (x != args.length - 1) buf.append(", ");
            }
            buf.append(");\n    }\n");
            
            newText = buf.toString();
        }
    }
}
