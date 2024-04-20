/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.mixin;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraftforge.srg2source.extract.MixinProcessor;

public enum MixinAnnotation {
    ACCESSOR   ("gen/Accessor",         Accessor::new),
    INVOKER    ("gen/Invoker",          Invoker::new),
    INTERFACE  ("Interface",            Interface::new),
    /*
     *  All these remaining types use the 'selector' system of Mixin, which is super complex
     *  and I have no good idea right now how to manage them.
     */
    //AT         ("injection/At",         At::new),
    //INJECT     ("injection/Inject",     Inject::new),
    //AT_CODE    ("injection/InjectionPoint$AtCode", AtCode::new),
    //MODIFY_ARG ("injection/ModifyArg",  ModifyArg::new),
    //MODIFY_ARGS("injection/ModifyArgs", ModifyArgs::new),
    //MODIFY_CONSTANT("injection/ModifyConstant", ModifyConstant::new),
    //MODIFY_VARIABLE("injection/ModifyVariable", ModifyVariable::new),
    //REDIRECT   ("injection/Redirect",   Redirect::new),
    MIXIN      ("Mixin",                Mixin::new),
    OVERWRITE  ("Overwrite",            Overwrite::new),
    SHADOW     ("Shadow",               Shadow::new),

    /* These annotations are metadata thing that we don't care about during remapping.
     * We should probably write tests for these for completeness, but I don't think they actually do anything.
    COERCE     ("injection/Coerce",     Coerce::new),
    CONSTANT   ("injection/Constant",   Constant::new),
    DEBUG      ("Debug",                Debug::new),
    DYNAMIC    ("Dynamic",              Dynamic::new),
    FINAL      ("Final",                Final::new),
    GROUP      ("injection/Group",      Group::new),
    IMPLEMENTS ("Implements",           Implements::new), - This one just wraps a bunch of @Interface
    INTRINSIC  ("Intrinsic",            Intrinsic:new),
    MUTABLE    ("Mutable",              Mutable::new),
    UNIQUE     ("Unique",               Unique::new),
    SLICE      ("injection/Slice",      Slice::new),
    SURROGATE  ("injection/Surrogate",  Surrogate::new),
    */

    /* These are markers for things that do not exist on the class path, so there is no way for us to resolve.
     * We just have to hope the user knows what they are doing
    PSUEDO       ("Psuedo",             Psuedo::new),
    SOFT_OVERRIDE("SoftOverride",       SoftOverride::new),
     */

    /* Pretty sure these are meta and not public facing
    ANNOTATION_TYPE("injection/struct/InjectionInfo$AnnotationType", Annotationtype::new),
    HANDLER_PREFIX ("injection/struct/InjectionInfo$HandlerPrefix",  HandlerPrefix::new),
    MIXIN_INNER    ("transformer/meta/MixinInner",                   MixinInner::new),
    MIXIN_MERGED   ("transformer/meta/MixinMerged",                  MixinMerged::new),
    MIXIN_PROXY    ("transformer/meta/MixinProxy",                   MixinProxy::new),
    MIXIN_RENAMED  ("transformer/meta/MixinRenamed",                 MixinRenamed::new),
    */
    ;

    private static final Map<String, MixinAnnotation> values = Arrays.asList(values()).stream().collect(Collectors.toMap(e -> e.getType(), e -> e));
    private final String type;
    private final Function<MixinProcessor, IAnnotationProcessor> factory;

    private MixinAnnotation(String type, Function<MixinProcessor, IAnnotationProcessor> factory) {
        this.type = "org/spongepowered/asm/mixin/" + type;
        this.factory = factory;
    }

    @Nullable
    public static MixinAnnotation getByType(String type) {
        return values.get(type);
    }

    public String getType() {
        return this.type;
    }

    public IAnnotationProcessor newInstance(MixinProcessor processor) {
        return this.factory.apply(processor);
    }
}
