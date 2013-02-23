package ast;

import org.eclipse.jdt.core.dom.ASTNode;

public class FixTypes implements Comparable<FixTypes>
{
    protected ASTNode node;
    private FixTypes(ASTNode ast)
    {
        this.node = ast;
    }

    @Override
    public int compareTo(FixTypes o)
    {
        return node.getStartPosition() - o.node.getStartPosition();
    }
    
    public int getLength()
    {
        return node.getLength();
    }
    
    public int getStart()
    {
        return node.getStartPosition();
    }
    
    public static class RemoveMethod extends FixTypes
    {
        public RemoveMethod(ASTNode node){ super(node); }
    }

    public static class PublicMethod extends FixTypes
    {
        public PublicMethod(ASTNode node){ super(node); }
    }
    
    public static class PublicField extends FixTypes
    {
        public PublicField(ASTNode node){ super(node); }
    }
    
    public static class BounceMethod extends FixTypes
    {
        public String newName;
        public String oldName;
        public String[] args;
        public BounceMethod(ASTNode node, String newName, String oldName, String[] args)
        { 
            super(node);
            this.newName = newName;
            this.oldName = oldName;
            this.args = args;
        }
        
        @Override
        public int compareTo(FixTypes o)
        {
            int mIdx = (node.getStartPosition() + node.getLength() - 1);
            int tIdx = o.node.getStartPosition() + (o instanceof BounceMethod ? o.node.getLength() - 1 : 0);
            return mIdx - tIdx;
        }
        
        public int getLength(){ return 0; }
        public int getStart(){ return node.getStartPosition() + node.getLength() - 1; }
    }
}
