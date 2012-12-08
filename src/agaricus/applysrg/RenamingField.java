package agaricus.applysrg;

public class RenamingField extends Renaming {
    public String className;
    public String oldName;
    public String newName;

    public RenamingField(String className, String oldName, String newName) {
        this.className = className;
        this.oldName = oldName;
        this.newName = newName;
    }

    public String toString() {
        return "Field "+className+" "+oldName+" -> "+newName;
    }
}
