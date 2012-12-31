package agaricus.srg2source;

public class RenamingClass extends Renaming {
    public String oldName;
    public String newName;

    public RenamingClass(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    public String toString() {
        return "Class "+oldName+" -> "+newName;
    }
}
