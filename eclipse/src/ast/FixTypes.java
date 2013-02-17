package ast;

import org.eclipse.jdt.core.dom.ASTNode;

public class FixTypes
{
    private ASTNode node;
    private FixTypes(ASTNode ast)
    {
        this.node = ast;
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
}
