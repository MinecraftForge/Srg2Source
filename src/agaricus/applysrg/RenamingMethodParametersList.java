package agaricus.applysrg;

public class RenamingMethodParametersList extends Renaming {
    public String className;
    public String methodName;
    public String methodSignature;
    public String[] newParameterNames;

    public RenamingMethodParametersList(String className, String methodName, String methodSignature, String[] newParameterNames) {
        this.className = className;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
        this.newParameterNames = newParameterNames;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("MethodParametersList "+className+" "+methodName+" "+methodSignature+" ");
        for (String newParameterName: newParameterNames) {
            sb.append(newParameterName);
            sb.append(' ');
        }
        return sb.toString();
    }
}
