package agaricus.applysrg;

public class RenamingMethod extends Renaming {
    public String className;
    public String oldName;
    public String signature;
    public String newName;

    public RenamingMethod(String className, String oldName, String signature, String newName) {
        this.className = className;
        this.oldName = oldName;
        this.signature = signature;
        this.newName = newName;
    }


    public String toString() {
        return "Method "+className+" "+oldName+" "+signature+" -> "+newName;
    }
}
