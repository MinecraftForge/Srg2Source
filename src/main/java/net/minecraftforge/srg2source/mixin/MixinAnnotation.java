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

package net.minecraftforge.srg2source.mixin;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraftforge.srg2source.extract.MixinProcessor;

public enum MixinAnnotation {
    //DEBUG      ("Debug",                Debug::new),
    //DYNAMIC    ("Dynamic",              Dynamic::new),
    //FINAL      ("Final",                Final::new),
    //ACCESSOR   ("gen/Accessor",         Accessor::new),
    //INVOKER    ("gen/Invoker",          Invoker::new),
    //IMPLEMENTS ("Implements",           Implements::new),
    //AT         ("injection/At",         At::new),
    //COERCE     ("injection/Coerce",     Coerce::new),
    //CONSTANT   ("injection/Constant",   Constant::new),
    //GROUP      ("injection/Group",      Group::new),
    //INJECT     ("injection/Inject",     Inject::new),
    //AT_CODE    ("injection/InjectionPoint$AtCode", AtCode::new),
    //MODIFY_ARG ("injection/ModifyArg",  ModifyArg::new),
    //MODIFY_ARGS("injection/ModifyArgs", ModifyArgs::new),
    //MODIFY_CONSTANT("injection/ModifyConstant", ModifyConstant::new),
    //MODIFY_VARIABLE("injection/ModifyVariable", ModifyVariable::new),
    //REDIRECT   ("injection/Redirect",   Redirect::new),
    //SLICE      ("injection/Slice",      Slice::new),
    //SURROGATE  ("injection/Surrogate",  Surrogate::new),
    //INTERFACE  ("Interface",            Interface::new),
    //INTRINSIC  ("Intrinsic",            Intrinsic:new),
    MIXIN      ("Mixin",                Mixin::new),
    //MUTABLE    ("Mutable",              Mutable::new),
    //OVERWRITE  ("Overwrite",            Overwrite::new),
    //PSUEDO     ("Psuedo",               Psuedo::new),
    SHADOW     ("Shadow",               Shadow::new),
    //SOFT_OVERRIDE("SoftOverride",       SoftOverride::new),
    //UNIQUE    ("Unique",                Unique::new),

    //Pretty sure these are meta and not public facing
    //ANNOTATION_TYPE("injection/struct/InjectionInfo$AnnotationType", Annotationtype::new),
    //HANDLER_PREFIX ("injection/struct/InjectionInfo$HandlerPrefix",  HandlerPrefix::new),
    //MIXIN_INNER    ("transformer/meta/MixinInner",                   MixinInner::new),
    //MIXIN_MERGED   ("transformer/meta/MixinMerged",                  MixinMerged::new),
    //MIXIN_PROXY    ("transformer/meta/MixinProxy",                   MixinProxy::new),
    //MIXIN_RENAMED  ("transformer/meta/MixinRenamed",                 MixinRenamed::new),
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
