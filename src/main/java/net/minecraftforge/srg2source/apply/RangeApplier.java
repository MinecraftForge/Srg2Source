/*
 * Srg2Source
 * Copyright (c) 2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srg2source.apply;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraftforge.srg2source.api.InputSupplier;
import net.minecraftforge.srg2source.api.OutputSupplier;
import net.minecraftforge.srg2source.range.RangeMap;
import net.minecraftforge.srg2source.range.entries.ClassLiteral;
import net.minecraftforge.srg2source.range.entries.ClassReference;
import net.minecraftforge.srg2source.range.entries.FieldLiteral;
import net.minecraftforge.srg2source.range.entries.FieldReference;
import net.minecraftforge.srg2source.range.entries.LocalVariableReference;
import net.minecraftforge.srg2source.range.entries.MethodLiteral;
import net.minecraftforge.srg2source.range.entries.MethodReference;
import net.minecraftforge.srg2source.range.entries.ParameterReference;
import net.minecraftforge.srg2source.range.entries.RangeEntry;
import net.minecraftforge.srg2source.util.Util;
import net.minecraftforge.srg2source.util.io.ConfLogger;
import net.minecraftforge.srgutils.IMappingFile;

@SuppressWarnings("unused")
public class RangeApplier extends ConfLogger<RangeApplier> {
    private static Pattern IMPORT = Pattern.compile("import\\s+((?<static>static)\\s+)?(?<class>[A-Za-z][A-Za-z0-9_\\.]*\\*?);.*");

    private List<IMappingFile> srgs = new ArrayList<>();
    private Map<String, String> clsSrc2Internal = new HashMap<>();
    private Map<String, ExceptorClass> excs = Collections.emptyMap();
    private boolean keepImports = false; // Keep imports that are not referenced anywhere in code.
    private InputSupplier input = null;
    private OutputSupplier output = null;
    private Map<String, RangeMap> range = new HashMap<>();
    private ClassMeta meta = null;
    private Map<String, String> guessLambdas = null;
    private boolean guessLocals = false;
    private boolean sortImports = false;

    public void readSrg(Path srg) {
        try (InputStream in = Files.newInputStream(srg)) {
            IMappingFile map = IMappingFile.load(in);
            srgs.add(map); //TODO: Add merge function to SrgUtils?

            map.getClasses().forEach(cls -> {
                clsSrc2Internal.put(cls.getOriginal().replace('/', '.').replace('$', '.'), cls.getOriginal());

                if (guessLambdas != null) {
                    cls.getMethods().stream()
                    .filter(Objects::nonNull)
                    .flatMap(mtd -> mtd.getParameters().stream())
                    .filter(Objects::nonNull)
                    .forEach(p -> this.guessLambdas.put(p.getOriginal(), p.getMapped()));
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read SRG: " + srg, e);
        }
    }

    public void readExc(Path value) {
        readExc(value, StandardCharsets.UTF_8);
    }

    public void readExc(Path value, Charset encoding) {
        try {
            this.excs = ExceptorClass.create(value, encoding, this.excs);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read EXC: " + value, e);
        }
    }

    public void setGuessLambdas(boolean value) {
        if (!value) {
            this.guessLambdas = null;
        } else {
            this.guessLambdas = new HashMap<>();
            this.srgs.stream()
                .flatMap(srg -> srg.getClasses().stream())
                .flatMap(cls -> cls.getMethods().stream())
                .flatMap(mtd -> mtd.getParameters().stream())
                .forEach(p -> this.guessLambdas.put(p.getOriginal(), p.getMapped()));
        }
    }

    public void setGuessLocals(boolean value) {
        this.guessLocals = value;
    }

    public void setSortImports(boolean value) {
        this.sortImports = value;
    }

    public void setInput(InputSupplier value) {
        this.input = value;
    }

    public void setOutput(OutputSupplier value) {
        this.output = value;
    }

    public void readRangeMap(File value) {
        try (InputStream in = Files.newInputStream(value.toPath())) {
            this.range.putAll(RangeMap.readAll(in));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid range map: " + value);
        }
    }

    public void readRangeMap(Path value) {
        try (InputStream in = Files.newInputStream(value)) {
            this.range.putAll(RangeMap.readAll(in));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid range map: " + value);
        }
    }

    public void keepImports(boolean value) {
        this.keepImports = value;
    }

    public void run() throws IOException {
        if (input == null)
            throw new IllegalStateException("Missing Range Apply input");
        if (output == null)
            throw new IllegalStateException("Missing Range Apply output");
        if (range == null)
            throw new IllegalStateException("Missing Range Apply range");

        meta = ClassMeta.create(this, range);

        List<String> paths = new ArrayList<>(range.keySet());
        Collections.sort(paths);

        log("Processing " + paths.size() + " files");

        for (String filePath : paths) {
            log("Start Processing: " + filePath);
            InputStream stream = input.getInput(filePath);

            //no stream? what?
            if (stream == null) {
                // yeah.. nope.
                log("Data not found: " + filePath);
                continue;
            }
            Charset encoding = input.getEncoding(filePath);
            if (encoding == null)
                encoding = StandardCharsets.UTF_8;

            String data = new String(Util.readStream(stream), encoding);
            stream.close();

            // process
            List<String> out = processJavaSourceFile(filePath, data, range.get(filePath), meta);
            filePath = out.get(0);
            data = out.get(1);

            // write.
            if (data != null) {
                OutputStream outStream = output.getOutput(filePath);
                if (outStream == null)
                    throw new IllegalStateException("Could not get output stream form: " + filePath);
                outStream.write(data.getBytes(encoding));
                outStream.close();
            }

            log("End  Processing: " + filePath);
            log("");
        }

        output.close();
    }

    private List<String> processJavaSourceFile(String fileName, String data, RangeMap rangeList, ClassMeta meta) throws IOException {
        StringBuilder outData = new StringBuilder();
        outData.append(data);

        Set<String> importsToAdd = new TreeSet<>();
        int shift = 0;

        // Existing package/class name (with package, internal) derived from filename
        String oldTopLevelClassFullName = Util.getTopLevelClassForFilename(fileName);
        int idx = oldTopLevelClassFullName.lastIndexOf('/');
        String oldTopLevelClassPackage = idx == -1 ? null                     : oldTopLevelClassFullName.substring(0, idx);
        String oldTopLevelClassName    = idx == -1 ? oldTopLevelClassFullName : oldTopLevelClassFullName.substring(idx + 1);

        // New package/class name through mapping
        String newTopLevelClassFullName = mapClass(oldTopLevelClassFullName);
        idx = newTopLevelClassFullName.lastIndexOf('/');
        String newTopLevelClassPackage = idx == -1 ? null                     : newTopLevelClassFullName.substring(0, idx);
        String newTopLevelClassName    = idx == -1 ? newTopLevelClassFullName : newTopLevelClassFullName.substring(idx + 1);

        //String newTopLevelQualifiedName = ((newTopLevelClassPackage == null ? "" : newTopLevelClassPackage + '/') + newTopLevelClassName).replace('\\', '/');

        // TODO: Track what code object we're in so we have more context?
        for (RangeEntry info : rangeList.getEntries()) {
            int start = info.getStart();
            int end = start + info.getLength();
            String expectedOldText = info.getText();
            String oldName = outData.substring(start + shift, end + shift);

            if (!oldName.equals(expectedOldText))
                throw new RuntimeException("Rename sanity check failed: expected '" + expectedOldText +
                        "' at [" + start + "," + end + "] (shifted " + shift + " [" + (start + shift) + "," + (end + shift) + "]) " +
                        "in " + fileName + ", but found '" + oldName + "'\n" +
                        "Regenerate symbol map on latest sources or start with fresh source and try again");

            String newName = null;
            switch (info.getType()) {
                case PACKAGE: // This should be OUR package reference, other packages are expressed as qualified class entries.
                    newName = newTopLevelClassPackage.replace('/', '.'); //TODO: Support remapping to no package, thus removing this entirely. Problem is this doesn't reference the "package" text itself.
                    break;
                case CLASS: {
                    ClassReference ref = (ClassReference)info;
                    //TODO: I am unsure how we should handle mappings that change the inner class level of a class.
                    // Right now, the outer class is it's own ClassReference entry. So we have no way to figure out if we need to qualify/import it...
                    String fullname = mapClass(ref.getClassName());
                    idx = fullname.lastIndexOf('/');
                    String packagename = idx == -1 ? null : fullname.substring(0, idx);
                    String simplename = fullname.substring(idx + 1);
                    idx = simplename.lastIndexOf('$');

                    if (idx != -1) {
                        if (oldName.indexOf('.') != -1)
                            throw new IllegalStateException("Invalid Class mapping. Quialified inner class: Mapped: " + fullname + " " + info);
                        packagename = null;
                        simplename = simplename.substring(idx + 1);
                    }

                    if (ref.isQualified() && oldName.indexOf('.') > 0) { // Top Level Includes package
                        newName = fullname.replace('/', '.').replace('$', '.');
                    } else {
                        newName = simplename;
                        if (!ref.isQualified()) {
                            trackImport(importsToAdd, newTopLevelClassFullName,
                                newTopLevelClassFullName, //TODO: pass in inner class names so we can check super?
                                fullname);
                        }
                    }
                    break;
                }
                case FIELD: {
                    FieldReference ref = (FieldReference)info;
                    newName = mapField(ref.getOwner(), ref.getName());
                    break;
                }
                case METHOD: {
                    MethodReference ref = (MethodReference)info;
                    newName = mapMethod(ref.getOwner(), ref.getName(), ref.getDescriptor());
                    break;
                }
                case PARAMETER: {
                    ParameterReference ref = (ParameterReference)info;
                    newName = mapParam(ref.getOwner(), ref.getName(), ref.getDescriptor(), ref.getIndex(), oldName);
                    break;
                }
                case CLASS_LITERAL: {
                    ClassLiteral ref = (ClassLiteral)info;
                    newName = '"' + mapClass(ref.getClassName()) + '"';
                    //Convert this to names used in code. Can either be internal, or source.
                    //It can also technically be some other encoded format...
                    //Should we care about changing text names/escape characters?
                    if (ref.getText().indexOf('.') != -1)
                        newName = newName.replace('/', '.'); // Source names.
                    break;
                }
                case FIELD_LITERAL: {
                    FieldLiteral ref = (FieldLiteral)info;
                    newName = '"' + mapField(ref.getOwner(), ref.getName()) + '"';
                    break;
                }
                case METHOD_LITERAL: {
                    MethodLiteral ref = (MethodLiteral)info;
                    newName = '"' + mapMethod(ref.getOwner(), ref.getName(), ref.getDescriptor()) + '"';
                    break;
                }
                case LOCAL_VARIABLE: {
                    LocalVariableReference ref = (LocalVariableReference)info;
                    newName = mapLocal(ref.getOwner(), ref.getName(), ref.getDescriptor(), ref.getIndex(), ref.getVarType(), oldName);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown RangeEntry type: " + info);
            }

            if (oldName.equals(newName))
                continue; //No rename? Skip the rest.

            log("Rename " + info + " Shift[" + shift + "] " + oldName + " -> " + newName);

            // Rename algorithm:
            // 1. textually replace text at specified range with new text
            // 2. shift future ranges by difference in text length
            //data = data.substring(0, info.start + shift) + newName + data.substring(end + shift);
            outData.replace(start + shift, end + shift, newName);
            shift += (newName.length() - oldName.length());
        }

        // Lastly, update imports - this == separate from symbol range manipulation above
        String outString = updateImports(outData, importsToAdd);

        // rename?
        fileName = fileName.replace('\\', '/');
        String newFileName = newTopLevelClassFullName + ".java";

        if (newFileName.charAt(0) != '/' && fileName.charAt(0) == '/')
            newFileName = '/' + newFileName;

        if (!fileName.equals(newFileName)) {
            log("Rename file " + fileName + " -> " + newFileName);
            fileName = newFileName;
        }

        return Arrays.asList(fileName, outString);
    }

    private static void trackImport(Set<String> imports, String topLevel, String self, String reference) {
        if (reference.startsWith(topLevel)) return; //This is a inner class, nested unknown amounts deep.... Just assume it's qualified correctly in code.

        int idx = topLevel.lastIndexOf('/');
        String tpkg = idx == -1 ? "" : topLevel.substring(0, idx);
        idx = reference.lastIndexOf('/');
        String rpkg = idx == -1 ? "" : reference.substring(0, idx);

        if (tpkg.equals(rpkg)) return; // We are in the same package, no import needed

        //This needs to be made better by taking into account inheritance, but I don't know of a simple way to hack inheritance into this,
        //so I think we're gunna have to live with some false positives. We just have to be careful when patching.
        imports.add(reference.replace('/', '.').replace('$', '.'));
    }

    /*
     * Parse the existing imports and find out where to add ours
     * TODO: Make RangeExtract pull import segments and remap in line? Can we support more layouts?
     * Imports syntax CAN be very complicated, we only support the most common layout:
     * import\w+[static]\w+(ClassName);
     * We can not support comments before the import.. anyone wanna try it?
     */
    private String updateImports(StringBuilder data, Set<String> newImports) {
        int lastIndex = 0;
        int nextIndex = getNextIndex(data.indexOf("\n"), data.length(), lastIndex);

        boolean addedNewImports = false;
        boolean sawImports = false;
        int packageLine = -1;
        String newline = data.indexOf("\r\n") != -1 ? "\r\n" : "\n";

        while (nextIndex > -1) {
            String line = data.substring(lastIndex, nextIndex);
            if (line.endsWith("\r"))
                line = line.substring(0, line.length() - 1);
            int comment = line.indexOf("//");

            while ((comment == -1 ? line : line.substring(0, comment)).trim().isEmpty()) {
                lastIndex += line.length() == 0 ? 1 : line.length();
                nextIndex = getNextIndex(data.indexOf("\n", lastIndex), data.length(), lastIndex);
                if (nextIndex == -1) //EOF
                    break;
                line = data.substring(lastIndex, nextIndex);
                if (line.endsWith("\r"))
                    line = line.substring(0, line.length() - 1);
                comment = line.indexOf("//");
            }

            if (nextIndex == -1) //EOF
                break;

            if (line.startsWith("package "))
                packageLine = nextIndex + 1;
            else if (line.startsWith("import")) {
                sawImports = true;

                Matcher match = IMPORT.matcher(line);
                if (!match.matches()) {
                    error("Error: Invalid import line: " + line); //Do we want to error out?
                    lastIndex = nextIndex + 1;
                    nextIndex = getNextIndex(data.indexOf("\n", nextIndex + 1), data.length(), nextIndex + 1);
                    continue;
                }

                boolean isStatic = match.group("static") != null;
                String old = match.group("class");
                int cStart = match.start("class");
                int cEnd = match.end("class");
                boolean wildMatch = false;

                if (isStatic) {
                    int idx = old.lastIndexOf('.');
                    cEnd -= old.length() - idx;
                    String name = old.substring(idx + 1);
                    old = old.substring(0, idx);

                    if ("*".equals(name)) {
                        ; //Wildcard, but we just want to rename the class
                    } else {
                        error("Warning: Static Method/Field Imports not supported: " + line); //Do we want to error out?
                        lastIndex = nextIndex;
                        nextIndex = getNextIndex(data.indexOf("\n", nextIndex + 1), data.length(), nextIndex + 1);
                    }
                } else if (old.endsWith(".*")) {
                    Set<String> remove = new HashSet<>();
                    String starter = old.substring(0, old.length() - 1);
                    for (String imp : newImports) {
                        String impStart = imp.substring(0, imp.lastIndexOf('.') + 1);
                        if (impStart.equals(starter)) {
                            remove.add(imp);
                            wildMatch = true;
                        }
                    }
                    newImports.removeAll(remove);

                    old = old.substring(0, old.length() - 2);
                    cEnd -= 2;
                }

                String newClass = mapClass(clsSrc2Internal.getOrDefault(old, old)).replace('/', '.').replace('$', '.');

                //log("Import: " + newClass);

                if (!wildMatch && !newImports.remove(newClass) && !isStatic) { // New file doesn't need the import, so delete the line.
                    if (this.keepImports)
                        lastIndex = nextIndex + 1;
                    else {
                        data.delete(lastIndex, nextIndex + 1);
                        if (data.substring(lastIndex > 3 ? lastIndex - 3 : 0, lastIndex).endsWith('\n' + newline) &&
                            data.substring(lastIndex, lastIndex + newline.length()).equals(newline)) { // Collapse double empty lines
                            data.delete(lastIndex - newline.length(), lastIndex);
                            lastIndex -= newline.length();
                        }
                    }
                    nextIndex = getNextIndex(data.indexOf("\n", lastIndex), data.length(), lastIndex);
                    continue;
                }

                if (!old.equals(newClass)) { // Got renamed
                    data.replace(lastIndex + cStart, lastIndex + cEnd, newClass);
                    nextIndex = nextIndex - (old.length() - newClass.length());
                }
            } else if (sawImports && !addedNewImports) {
                filterImports(newImports);

                if (newImports.size() > 0) {
                    // Add our new imports right after the last import
                    CharSequence sub = data.subSequence(lastIndex, data.length()); // grab the rest of the string.
                    data.setLength(lastIndex); // cut off the build there

                    newImports.stream().sorted().forEach(imp -> data.append("import ").append(imp).append(";\n"));

                    if (newImports.size() > 0)
                        data.append(newline);

                    int change = data.length() - lastIndex; // get changed size
                    lastIndex = data.length(); // reset the end to the actual end..
                    nextIndex += change; // shift nextIndex accordingly..

                    data.append(sub); // add on the rest if the string again
                }

                addedNewImports = true;
                break; //We've added out imports lets exit.
            }

            // next line.
            lastIndex = nextIndex + 1; // +1 to skip the \n at the end of the line there
            nextIndex = getNextIndex(data.indexOf("\n", lastIndex), data.length(), lastIndex); // another +1 because otherwise it would just return lastIndex
        }

        // got through the whole file without seeing or adding any imports???
        if (!addedNewImports) {
            filterImports(newImports);

            if (newImports.size() > 0) {
                //If we saw the package line, add to it after that.
                //If not prepend to the start of the file
                int index = packageLine == -1 ? 0 : packageLine;

                CharSequence sub = data.subSequence(index, data.length()); // grab the rest of the string.
                data.setLength(index); // cut off the build there

                newImports.stream().sorted().forEach(imp -> data.append(newline).append("import ").append(imp).append(";"));

                if (newImports.size() > 0)
                    data.append('\n');

                data.append(sub); // add on the rest if the string again
            }
        }

        if (sortImports && (!newImports.isEmpty() || sawImports)) {
            int startIndex = data.indexOf("import ");
            int endIndex = data.indexOf("\n", startIndex);
            nextIndex = endIndex;
            if (startIndex != -1) {
                String line;

                while (endIndex != -1) {
                    nextIndex = data.indexOf("\n", endIndex + 1);
                    line = data.substring(endIndex + 1, nextIndex == -1 ? data.length() : nextIndex);
                    if (line.startsWith("import ") || line.replaceAll("\r?\n", "").trim().isEmpty())
                        endIndex = nextIndex;
                    else
                        break;
                }
                if (endIndex == -1)
                    endIndex = data.length();

                while (data.charAt(endIndex-1) == '\n' || data.charAt(endIndex-1) == '\r')
                    endIndex--;

                String importData = data.substring(startIndex, endIndex);
                String imports = Stream.of(importData.split("\r?\n"))
                    .filter(i -> !i.trim().isEmpty())
                    .map(i -> {
                        i = i.substring(7, i.length() - 1);
                        int idx = i.lastIndexOf('.');
                        return new String[] { i.substring(0, idx), i.substring(idx + 1) };
                    })
                    .sorted((o1, o2) -> o1[0].equals(o2[0]) ? o1[1].compareTo(o2[1]) : o1[0].compareTo(o2[0]))
                    .map(i -> "import " + i[0] + '.' + i[1] + ';')
                    .collect(Collectors.joining(newline));
                data.replace(startIndex, endIndex, imports);
            }
        }

        return data.toString();
    }

    private int getNextIndex(int newLine, int dataLength, int oldIndex) {
        if (newLine == -1 && dataLength > oldIndex)
            return dataLength;
        return newLine;
    }

    private void filterImports(Set<String> newImports) {
        Iterator<String> itr  = newImports.iterator();
        while (itr.hasNext()) {
            if (itr.next().startsWith("java.lang.")) //java.lang classes can be referenced without imports
                itr.remove();                        //We remove them here to allow for them to exist in src
                                                     //But we will never ADD them
        }

        if (newImports.size() > 0) {
            log("Adding " + newImports.size() + " imports");
            for (String imp : newImports) {
                log("        " + imp);
                //log("        " + HashCode.fromBytes(imp.getBytes()).toString());
            }
        }
    }

    // TODO: Decide how I want to manage multiple srg files? Chain them? Merge them?
    //Current usecase is Forge adding extra SRG lines. But honestly that shouldn't happen anymore.
    String mapClass(String name) {
        for (IMappingFile srg : srgs) {
            IMappingFile.IClass cls = srg.getClass(name);
            if (cls != null)
                return cls.getMapped();
        }
        return name;
    }

    String mapField(String owner, String name) {
        for (IMappingFile srg : srgs) {
            IMappingFile.IClass cls = srg.getClass(owner);
            if (cls != null) {
                String newName = cls.remapField(name);
                if (newName != name) // This is intentional instance equality. As remap methods return the same instance of not found.
                    return newName;
            }
        }
        return name;
    }

    String mapMethod(String owner, String name, String desc) {
        if ("<init>".equals(name)) {
            String newName = mapClass(owner);
            int idx = newName.lastIndexOf('$');
            idx = idx != -1 ? idx : newName.lastIndexOf('/');
            return idx == -1 ? newName : newName.substring(idx + 1);
        }

        for (IMappingFile srg : srgs) {
            IMappingFile.IClass cls = srg.getClass(owner);
            if (cls != null) {
                String newName = cls.remapMethod(name, desc);
                if (newName != name) // This is intentional instance equality. As remap methods return the same instance of not found.
                    return newName;
            }
        }

        //There was no mapping for this specific method, so lets see if this is something in the metadata
        return meta == null ? name : meta.mapMethod(owner, name, desc);
    }

    private String mapParam(String owner, String name, String desc, int index, String old) {
        ExceptorClass exc = this.excs.get(owner);
        String ret = exc == null ? null : exc.mapParam(name, desc, index, old);
        if (ret == null) {
            for (IMappingFile srg : srgs) {
                IMappingFile.IClass cls = srg.getClass(owner);
                if (cls != null) {
                    IMappingFile.IMethod mtd = cls.getMethod(name, desc);
                    if (mtd != null) {
                        String mapped = mtd.remapParameter(index, old);
                        if (mapped != old) {
                            ret = mapped;
                            break;
                        }
                    }
                }
            }
        }
        if (ret == null && this.guessLambdas != null && name.startsWith("lambda$"))
            ret = this.guessLambdas.get(old);

        return ret == null ? old : ret;
    }

    // Guess JAD style local variables, and some Fernflower quarks
    private String mapLocal(String owner, String name, String desc, int index, String type, String old) {
        if (!guessLocals || type.indexOf(';') == -1)
            return old;

        String prefix = "";

        // Arrays are 'a' + type name
        if (type.charAt(0) == '[') {
            prefix = "a";
            type = type.substring(type.indexOf('L') + 1, type.length() - 1);
        } else
            type = type.substring(1, type.length() - 1);


        // Typically it's the simple class name, including the outer class if the type is an inner
        int idx = type.lastIndexOf('/');
        String expected = prefix + (idx == -1 ? type : type.substring(idx + 1)).toLowerCase(Locale.ENGLISH);

        String newClass = mapClass(type);
        if (newClass.equals(type))
            return old;

        idx = newClass.lastIndexOf('/');
        if (idx != -1)
            newClass = newClass.substring(idx + 1);

        if (!old.startsWith(expected)) {
            idx = expected.lastIndexOf('$');
            int nidx = newClass.lastIndexOf('$');
            if (idx == -1 || nidx == -1)
                return old;

            String inner = prefix + expected.substring(idx + 1);
            String outer = expected.substring(0, idx);

            // Sometimes it's JUST the inner class name
            if (old.startsWith(inner)) {
                newClass = newClass.substring(nidx + 1);
                expected = inner;
            //I don't know why this happens, but BlockModelDefinition has a local var named the outer class instead of inner..
            } else if (old.startsWith(outer)) {
                newClass = newClass.substring(0, nidx);
                expected = outer;
            } else
                return old;
        }

        return prefix + newClass.toLowerCase(Locale.ENGLISH) + old.substring(expected.length());
    }
}
