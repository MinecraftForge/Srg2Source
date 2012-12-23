package agaricus.applysrg;

import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class SrgLoader {
    static void loadSrg(VirtualFile file,
                        List<RenamingClass> classes,
                        List<RenamingField> fields,
                        List<RenamingMethod> methods,
                        List<RenamingMethodParametersList> parameters) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(file.getInputStream());
        BufferedReader reader = new BufferedReader(inputStreamReader);

        do {
            String line = reader.readLine();
            if (line == null) break;

            String[] tokens = line.split(" ");
            if (tokens.length < 3) continue;

            String kind = tokens[0];
            if (kind.equals("CL:")) {
                String oldName = getPackageComponent(tokens[1]) + "." + getNameComponent(tokens[1]);
                String newName = getNameComponent(tokens[2]);

                classes.add(new RenamingClass(oldName, newName));
            } else if (kind.equals("FD:")) {
                String className = getPackageComponent(tokens[1]);
                String oldName = getNameComponent(tokens[1]);

                String newName = getNameComponent(tokens[2]);
                fields.add(new RenamingField(className, oldName, newName));
            } else if (kind.equals("MD:")) {
                String className = getPackageComponent(tokens[1]);
                String oldName = getNameComponent(tokens[1]);

                String oldSignature = tokens[2];
                String newName = getNameComponent(tokens[3]);
                //String newSignature = tokens[4]; // unused, changes types but otherwise ignored

                methods.add(new RenamingMethod(className, oldName, oldSignature, newName));

            // Method parameter renaming - new to ApplySrg2Source
            } else if (kind.equals("PA:")) {
                String className = getPackageComponent(tokens[1]);
                String methodName = getNameComponent(tokens[1]);

                String methodSignature = tokens[2];

                String[] newParameterNames = Arrays.copyOfRange(tokens, 3, tokens.length);


                parameters.add(new RenamingMethodParametersList(className, methodName, methodSignature, newParameterNames));
            }
        } while (true);
    }

    /** Get last component of a slash-separated name, the symbol name
     *
     * @param fullName
     * @return Name, for example, "a/b/c" will return "c"
     */
    public static String getNameComponent(String fullName) {
        String[] parts = fullName.split("/");

        return parts[parts.length - 1];
    }

    /** Get the package components of a slash-separated name
     *
     * @param fullName
     * @return Path, for example, "a/b/c" will return "a.b"
     */
    public static String getPackageComponent(String fullName) {
        String[] parts = fullName.split("/");

        return StringUtils.join(Arrays.copyOfRange(parts, 0, parts.length - 1), ".");
    }
}
