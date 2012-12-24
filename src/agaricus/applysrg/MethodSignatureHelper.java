package agaricus.applysrg;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;

public class MethodSignatureHelper {
    // Routines for converting PsiMethod signatures until I can find a better way

    /* Get Java type signature code string from a PsiMethod
See http://help.eclipse.org/indigo/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fjdt%2Fcore%2FSignature.html
TODO: is there an existing routine this can be replaced with?
 */
    public static String makeTypeSignatureString(PsiMethod method) {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        PsiType[] argTypes = signature.getParameterTypes();
        StringBuilder buf = new StringBuilder();

        buf.append("(");

        for(PsiType argType: argTypes) {
            buf.append(getTypeCodeString(argType));
        }

        buf.append(")");

        PsiType returnType = method.getReturnType();

        if (returnType == null) {
            // Constructors have no return type
            // Using void as a syntactic placeholder here - TODO - something better? but MCP .exc uses V, too
            buf.append("V");
        } else {
            buf.append(getTypeCodeString(returnType));
        }

        return buf.toString();
    }

    public static String getTypeCodeString(PsiType type)
    {
        if (type instanceof PsiPrimitiveType) {
            PsiPrimitiveType ptype = (PsiPrimitiveType)type;

            if (ptype == PsiType.BYTE) return "B";
            if (ptype == PsiType.CHAR) return "C";
            if (ptype == PsiType.DOUBLE) return "D";
            if (ptype == PsiType.FLOAT) return "F";
            if (ptype == PsiType.INT) return "I";
            if (ptype == PsiType.LONG) return "J";
            if (ptype == PsiType.SHORT) return "S";
            if (ptype == PsiType.VOID) return "V";
            if (ptype == PsiType.BOOLEAN) return "Z";
        }

        if (type instanceof PsiArrayType) {
            PsiArrayType atype = (PsiArrayType)type;
            return "[" + getTypeCodeString(atype.getComponentType());
        }

        // not supported here:
        // T type variable
        // <> optional arguments
        // * wildcard
        // + wildcard extends X
        // - wildcard super X
        // ! capture-of, | intersection type, Q unresolved type


        return "L" + type.getCanonicalText().replaceAll("\\.", "/") + ";";
    }
}
