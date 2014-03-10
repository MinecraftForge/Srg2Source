package net.minecraftforge.srg2source.rangeapplier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.srg2source.rangeapplier.ExceptorFile.ExcLine;
import net.minecraftforge.srg2source.util.ListFile;

import com.google.common.base.Splitter;

class ExceptorFile extends ListFile<ExcLine, ExceptorFile>
{
    static final Pattern  EXC_REGEX = Pattern.compile("^([^.]+)\\.([^(]+)(\\([^=]+)=([^|]*)\\|(.*)");
    static final Splitter SPLIT     = Splitter.on(",").omitEmptyStrings().trimResults();

    public ExceptorFile()
    {
        super();
    }

    @Override
    protected ExcLine parseLine(String line)
    {
        Matcher match = EXC_REGEX.matcher(line);

        if (match.find())
        {
            // check if its an inner or not..
            String className = match.group(1);
            String methodName = match.group(2);
            String methodSig = match.group(3);
            int index = className.lastIndexOf('$');

            if (index >= 0)
            {
                String parent = "L" + className.substring(0, index) + ";";
                if (methodName.equals("<init>") && methodSig.startsWith("(" + parent)) // ! non-static inner class!
                {
                    List<String> params = new ArrayList<String>(SPLIT.splitToList(match.group(5)));
                    params.remove(0);
                    
                    super.add(new ExcLine(className, methodName, methodSig.replace("(" + parent, "("), SPLIT.splitToList(match.group(4)), params));
                }
            }

            if (methodName.equals("<init>") && methodSig.startsWith("(Ljava/lang/String;I")) // Enums
            {
                List<String> params = new ArrayList<String>(SPLIT.splitToList(match.group(5)));
                params.remove(0);
                params.remove(0);
                super.add(new ExcLine(className, methodName, methodSig.replace("(Ljava/lang/String;I", "("), SPLIT.splitToList(match.group(4)), params));
            }

            return new ExcLine(
                    match.group(1), match.group(2), match.group(3),
                    SPLIT.splitToList(match.group(4)),
                    SPLIT.splitToList(match.group(5)));
        }
        else
            return null;
    }

    public static final class ExcLine implements Cloneable, Serializable
    {
        private static final long serialVersionUID = 887701501978751668L;
        public final String       className;
        public final String       methodName;
        public final String       methodSig;
        public final List<String> exceptions;
        public final List<String> params;

        ExcLine(String className, String methodName, String methodSig, List<String> exceptions, List<String> params)
        {
            super();
            this.className = className.replace('$', '/');
            this.methodName = methodName.replace('$', '/');
            this.methodSig = methodSig;
            this.exceptions = exceptions;
            this.params = params;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((className == null) ? 0 : className.hashCode());
            result = prime * result + ((exceptions == null) ? 0 : exceptions.hashCode());
            result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
            result = prime * result + ((methodSig == null) ? 0 : methodSig.hashCode());
            result = prime * result + ((params == null) ? 0 : params.hashCode());
            return result;
        }

        public MethodData getMethodData()
        {
            return new MethodData(className + "/" + methodName, methodSig);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ExcLine other = (ExcLine) obj;
            if (className == null)
            {
                if (other.className != null)
                    return false;
            }
            else if (!className.equals(other.className))
                return false;
            if (exceptions == null)
            {
                if (other.exceptions != null)
                    return false;
            }
            else if (!exceptions.equals(other.exceptions))
                return false;
            if (methodName == null)
            {
                if (other.methodName != null)
                    return false;
            }
            else if (!methodName.equals(other.methodName))
                return false;
            if (methodSig == null)
            {
                if (other.methodSig != null)
                    return false;
            }
            else if (!methodSig.equals(other.methodSig))
                return false;
            if (params == null)
            {
                if (other.params != null)
                    return false;
            }
            else if (!params.equals(other.params))
                return false;
            return true;
        }
    }
}
