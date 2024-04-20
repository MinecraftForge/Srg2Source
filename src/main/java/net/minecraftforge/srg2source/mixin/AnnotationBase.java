/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import org.eclipse.jdt.core.dom.ASTNode;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.srg2source.extract.MixinProcessor;
import net.minecraftforge.srg2source.extract.SymbolReferenceWalker;
import net.minecraftforge.srg2source.range.RangeMapBuilder;

public abstract class AnnotationBase implements IAnnotationProcessor {
    protected final MixinProcessor processor;
    protected final MixinAnnotation type;

    protected AnnotationBase(MixinProcessor processor, MixinAnnotation type) {
        this.processor = processor;
        this.type = type;
    }

    @Override
    public String getType() {
        return this.type.getType();
    }

    protected SymbolReferenceWalker getWalker() {
        return processor.getWalker();
    }

    protected RangeMapBuilder getBuilder() {
        return processor.getBuilder();
    }

    protected String getFilename() {
        return getBuilder().getFilename();
    }

    @Nullable
    protected MixinInfo getInfo(String owner) {
        return processor.getInfo(owner);
    }

    protected boolean error(ASTNode node, String message) {
        String error = "ERROR: " + getFilename() + " @ " + node.getStartPosition() + ": " + message;
        getWalker().error(error);
        if (getWalker().getExtractor().areMixinsFatal())
            throw new IllegalStateException(error);
        return true;
    }
}
