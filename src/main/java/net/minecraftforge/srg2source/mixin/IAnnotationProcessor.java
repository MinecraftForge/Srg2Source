/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;

public interface IAnnotationProcessor {
    String getType();
    default boolean process(NormalAnnotation node) {
        throw new IllegalArgumentException('@' + getType() + " can not be a normal annotation, it is expected to have no value");
    }

    default boolean process(SingleMemberAnnotation node) {
        throw new IllegalArgumentException('@' + getType() + " can not be a sinlge member annotation, it has no known \"value\" method");
    }

    default boolean process(MarkerAnnotation node) {
        throw new IllegalArgumentException('@' + getType() + " can not be a marker annotation, it has to have a value of some kind");
    }
}
