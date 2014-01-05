package net.minecraftforge.srg2source.ast;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import java.lang.reflect.Method;

public class MethodSignatureHelper
{
    @SuppressWarnings("rawtypes")
    public static String getSignature(Method method)
    {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (Class param : method.getParameterTypes())
        {
            buf.append(getTypeSignature(param));
        }
        buf.append(')');
        
        buf.append(getTypeSignature(method.getReturnType()));
        
        return buf.toString();
    }
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
    
    public static String getTypeSignature(ITypeBinding type)
    {
        String ret = type.getErasure().getBinaryName().replace('.', '/');
        if (ret.indexOf('/') != -1 && !ret.endsWith(";"))
        {
            return "L" + ret + ";";
        }
        return ret;
    }
    
    @SuppressWarnings("rawtypes")
    public static String getTypeSignature(Class type)
    {
        if (type.isArray()) return '[' + getTypeSignature(type.getComponentType());
        if (type.isPrimitive())
        {
            if (type == Boolean  .TYPE) return "Z";
            if (type == Character.TYPE) return "C";
            if (type == Byte     .TYPE) return "B";
            if (type == Short    .TYPE) return "S";
            if (type == Integer  .TYPE) return "I";
            if (type == Long     .TYPE) return "J";
            if (type == Float    .TYPE) return "F";
            if (type == Double   .TYPE) return "D";
            if (type == Void     .TYPE) return "V";
            return "WTFUX: " + type.toString();
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }
}
