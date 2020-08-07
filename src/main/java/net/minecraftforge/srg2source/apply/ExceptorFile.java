package net.minecraftforge.srg2source.apply;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.srg2source.apply.ExceptorFile.ExcLine;
import net.minecraftforge.srg2source.util.ListFile;

public class ExceptorFile extends ListFile<ExcLine, ExceptorFile> {
    static final Pattern  EXC_REGEX = Pattern.compile("^([^.]+)\\.([^(]+)(\\([^=]+)=([^|]*)\\|(.*)");

    public ExceptorFile() {}
    public ExceptorFile(File src) {
        read(src);
    }
    public ExceptorFile(Path src) {
        read(src);
    }

    //TODO: This makes a lot of assumptions about inner classes and enums, we should fix that.
    @Override
    protected ExcLine parseLine(String line) {
        Matcher match = EXC_REGEX.matcher(line);

        if (match.find()) {
            // check if its an inner or not..
            String className = match.group(1);
            String methodName = match.group(2);
            String methodSig = match.group(3);
            int index = className.lastIndexOf('$');

            if (index >= 0) {
                String parent = "L" + className.substring(0, index) + ";";
                if (methodName.equals("<init>") && methodSig.startsWith("(" + parent)) { // ! non-static inner class!
                    List<String> params = new ArrayList<String>(splitToList(match.group(5)));
                    params.remove(0);

                    super.add(new ExcLine(className, methodName, methodSig.replace("(" + parent, "("), splitToList(match.group(4)), params));
                }
            }

            if (methodName.equals("<init>") && methodSig.startsWith("(Ljava/lang/String;I")) { // Enums
                List<String> params = new ArrayList<String>(splitToList(match.group(5)));
                params.remove(0);
                params.remove(0);
                super.add(new ExcLine(className, methodName, methodSig.replace("(Ljava/lang/String;I", "("), splitToList(match.group(4)), params));
            }

            return new ExcLine(
                    match.group(1), match.group(2), match.group(3),
                    splitToList(match.group(4)),
                    splitToList(match.group(5)));
        } else
            return null;
    }

    private List<String> splitToList(String value) {
        return new ArrayList<>(Arrays.asList(value.split(",")));
    }

    public static final class ExcLine implements Cloneable, Serializable {
        private static final long serialVersionUID = 887701501978751668L;
        public final String       className;
        public final String       methodName;
        public final String       methodSig;
        public final List<String> exceptions;
        public final List<String> params;

        ExcLine(String className, String methodName, String methodSig, List<String> exceptions, List<String> params) {
            this.className = className.replace('$', '/');
            this.methodName = methodName.replace('$', '/');
            this.methodSig = methodSig;
            this.exceptions = exceptions;
            this.params = params;
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, exceptions, methodName, methodSig, params);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || (obj != null && getClass() == obj.getClass() && equals((ExcLine)obj));
        }

        private boolean equals(ExcLine other) {
            return
                Objects.equals(className, other.className) &&
                Objects.equals(exceptions, other.exceptions) &&
                Objects.equals(methodName, other.methodName) &&
                Objects.equals(methodSig, other.methodSig) &&
                Objects.equals(params, other.params);
        }
    }
}
