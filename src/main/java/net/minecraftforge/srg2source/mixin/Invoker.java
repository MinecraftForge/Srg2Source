/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import net.minecraftforge.srg2source.extract.MixinProcessor;

/*
 * See Accessor, Invokers are almost identicle to Accessors except that they are designed to invoke methods specifically.
 * The method name can be either specified in the 'value' of the annotation.
 * Or it can be derived from the annotated method name.
 * If the annotated method starts with "call" or "invoke" followed by a capital letter. Then the target is the method name
 * minus the prefix, and with the first letter lowercased. Unless the entire Method name is uppercased.
 *
 * However, there is a special case for Invokers for constructors.
 * Since <init> is not a valid source level name, it must be specified in the annotation.
 * Or it can be the fully qualified name of the class to be constructed.
 */
public class Invoker extends Accessor {
    public Invoker(MixinProcessor processor) {
        super(processor, MixinAnnotation.INVOKER);
    }
}
