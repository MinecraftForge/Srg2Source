package agaricus.applysrg;

public class RenamingField extends Renaming {
    public String className;
    public String newName;
    public String oldName;

    public RenamingField(String className, String newName, String oldName) {
        this.className = className;
        this.newName = newName;
        this.oldName = oldName;
    }

    public String toString() {
        return "Field "+className+" "+oldName+" -> "+newName;
    }
}
