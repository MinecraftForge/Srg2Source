/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.srg2source.range.entries.ClassLiteral;
import net.minecraftforge.srg2source.range.entries.ClassReference;
import net.minecraftforge.srg2source.range.entries.FieldLiteral;
import net.minecraftforge.srg2source.range.entries.FieldReference;
import net.minecraftforge.srg2source.range.entries.LocalVariableReference;
import net.minecraftforge.srg2source.range.entries.MetaEntry;
import net.minecraftforge.srg2source.range.entries.MethodLiteral;
import net.minecraftforge.srg2source.range.entries.MethodReference;
import net.minecraftforge.srg2source.range.entries.MixinAccessorMeta;
import net.minecraftforge.srg2source.range.entries.PackageReference;
import net.minecraftforge.srg2source.range.entries.ParameterReference;
import net.minecraftforge.srg2source.range.entries.RangeEntry;
import net.minecraftforge.srg2source.range.entries.StructuralEntry;
import net.minecraftforge.srg2source.util.io.ConfLogger;

public class RangeMapBuilder extends ConfLogger<RangeMapBuilder> {
    private final List<RangeEntry> entries = new ArrayList<>();
    private final List<StructuralEntry> structures = new ArrayList<>();
    private final List<MetaEntry> meta = new ArrayList<>();

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
        return new RangeMap(filename, hash, entries, structures, meta);
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


    // Structure Elements
    private void addStructure(StructuralEntry entry) {
        structures.add(entry);
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

    public void addRecordDeclaration(int start, int length, String name) {
        addStructure(StructuralEntry.createRecord(start, length, name));
    }

    public void addMethodDeclaration(int start, int length, String name, String desc) {
        addStructure(StructuralEntry.createMethod(start, length, name, desc));
    }

    public void addInterfaceDeclaration(int start, int length, String name) {
        addStructure(StructuralEntry.createInterface(start, length, name));
    }

    // Code Elements
    private void addCode(RangeEntry entry) {
        entries.add(entry);
    }

    public void addPackageReference(int start, int length, String name) {
        addCode(PackageReference.create(start, length, name));
    }

    public void addClassReference(int start, int length, String text, String internal, boolean qualified) {
        addCode(ClassReference.create(start, length, text, internal, qualified));
    }

    public void addClassLiteral(int start, int length, String text, String internal) {
        addCode(ClassLiteral.create(start, length, text, internal));
    }

    public void addFieldReference(int start, int length, String text, String owner) {
        addCode(FieldReference.create(start, length, text, owner));
    }

    public void addFieldLiteral(int start, int length, String text, String owner, String name) {
        addCode(FieldLiteral.create(start, length, text, owner, name));
    }

    public void addMethodReference(int start, int length, String text, String owner, String name, String desc) {
        addCode(MethodReference.create(start, length, text, owner, name, desc));
    }

    public void addMethodLiteral(int start, int length, String text, String owner, String name, String desc) {
        addCode(MethodLiteral.create(start, length, text, owner, name, desc));
    }

    public void addParameterReference(int start, int length, String text, String owner, String name, String desc, int index) {
        addCode(ParameterReference.create(start, length, text, owner, name, desc, index));
    }

    public void addLocalVariableReference(int start, int length, String text, String owner, String name, String desc, int index, String type) {
        addCode(LocalVariableReference.create(start, length, text, owner, name, desc, index, type));
    }

    // Meta Elements
    private void addMeta(MetaEntry entry) {
        meta.add(entry);
    }

    public void addMixinAccessor(String owner, String name, String desc, String targetOwner, String targetName, String targetDesc, String prefix) {
        addMeta(MixinAccessorMeta.create(owner, name, desc, targetOwner, targetName, targetDesc, prefix));
    }
}
