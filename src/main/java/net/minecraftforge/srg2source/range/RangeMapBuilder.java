package net.minecraftforge.srg2source.range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.srg2source.range.entries.ClassReference;
import net.minecraftforge.srg2source.range.entries.FieldReference;
import net.minecraftforge.srg2source.range.entries.MethodReference;
import net.minecraftforge.srg2source.range.entries.PackageReference;
import net.minecraftforge.srg2source.range.entries.ParameterReference;
import net.minecraftforge.srg2source.range.entries.RangeEntry;
import net.minecraftforge.srg2source.util.io.ConfLogger;

public class RangeMapBuilder extends ConfLogger<RangeMapBuilder> {
    private final List<RangeEntry> entries = new ArrayList<>();
    private final List<StructuralEntry> structures = new ArrayList<>();

    private final ConfLogger<?> logger;
    private final String filename;
    private final String hash;

    public RangeMapBuilder(ConfLogger<?> logger, String filename, String hash) {
        this.logger = logger;
        this.filename = filename;
        this.hash = hash;
    }

    public String getFilename() {
        return this.filename;
    }

    public boolean loadCache(RangeMap cache) {
        if (cache == null || !filename.equals(cache.getFilename()) || !hash.equals(cache.getHash()))
            return false;
        return false;
    }

    public RangeMap build() {
        // These should be sorted already, as we should encounter them in source order.
        // But lets sort them anyways
        Collections.sort(entries, (a, b) -> a.getStart() - b.getStart());
        Collections.sort(structures, (a, b) -> a.getStart() - b.getStart());
        checkOverlaps(entries);
        return new RangeMap(filename, hash, entries, structures);
    }

    private void checkOverlaps(List<? extends IRange> lst) {
        if (lst.isEmpty())
            return;

        IRange last = lst.get(0);
        for (int x = 1; x < lst.size(); x++) {
            IRange next = lst.get(x);
            if (last.getStart() + last.getLength() >= next.getStart()) {
                logger.error("Overlap: " + last);
                logger.error("         " + next);
            }
            last = next;
        }
    }


    private void addCode(RangeEntry entry) {
        entries.add(entry);
    }
    @SuppressWarnings("unused")
    private void LaddCode(RangeEntry entry) {
        log(entry.toString());
        addCode(entry);
    }
    private void addStructure(StructuralEntry entry) {
        structures.add(entry);
    }
    @SuppressWarnings("unused")
    private void LaddStructure(StructuralEntry entry) {
        log(entry.toString());
        addStructure(entry);
    }

    public void addAnnotationDeclaration(int start, int length, String name) {
        addStructure(StructuralEntry.createAnnotation(start, length, name));
    }

    public void addClassDeclaration(int start, int length, String name) {
        addStructure(StructuralEntry.createClass(start, length, name));
    }

    public void addEnumDeclaration(int start, int length, String name) {
        addStructure(StructuralEntry.createEnum(start, length, name));
    }

    public void addMethodDeclaration(int start, int length, String name, String desc) {
        addStructure(StructuralEntry.createMethod(start, length, name, desc));
    }

    public void addInterfaceDeclaration(int start, int length, String name) {
        addStructure(StructuralEntry.createInterface(start, length, name));
    }

    public void addPackageReference(int start, int length, String name) {
        addCode(PackageReference.create(start, length, name));
    }

    public void addClassReference(int start, int length, String text, String internal, boolean qualified) {
        addCode(ClassReference.create(start, length, text, internal, qualified));
    }

    public void addFieldReference(int start, int length, String text, String owner) {
        addCode(FieldReference.create(start, length, text, owner));
    }

    public void addMethodReference(int start, int length, String text, String owner, String name, String desc) {
        addCode(MethodReference.create(start, length, text, owner, name, desc));
    }

    public void addParameterReference(int start, int length, String text, String owner, String name, String desc, int index) {
        addCode(ParameterReference.create(start, length, text, owner, name, desc, index));
    }
}
