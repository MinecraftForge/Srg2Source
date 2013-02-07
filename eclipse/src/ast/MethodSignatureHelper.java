package ast;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class MethodSignatureHelper
{
    public static String getSignature(IMethodBinding method)
    {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (ITypeBinding param : method.getParameterTypes())
        {
            buf.append(getTypeSignature(param));
        }
        buf.append(')');
        
        buf.append(getTypeSignature(method.getReturnType()));
        
        return buf.toString();
    }
    
    private static String getTypeSignature(ITypeBinding type)
    {
        String ret = type.getErasure().getBinaryName().replace('.', '/');
        if (ret.indexOf('/') != -1 && !ret.endsWith(";"))
        {
            return "L" + ret + ";";
        }
        return ret;
    }
}
