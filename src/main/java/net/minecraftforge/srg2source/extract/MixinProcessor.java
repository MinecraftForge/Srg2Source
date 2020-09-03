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

package net.minecraftforge.srg2source.extract;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import net.minecraftforge.srg2source.mixin.IAnnotationProcessor;
import net.minecraftforge.srg2source.mixin.MixinAnnotation;
import net.minecraftforge.srg2source.mixin.MixinInfo;
import net.minecraftforge.srg2source.mixin.MixinInfo.InterfaceInfo;
import net.minecraftforge.srg2source.mixin.MixinInfo.ShadowInfo;
import net.minecraftforge.srg2source.range.RangeMapBuilder;

/*
 * This is an attempt to add support for remapping projects that take advantage of the https://github.com/SpongePowered/Mixin project.
 * However, as there are many ways that code can be written for this and still be valid to the compiler. This does a best effort.
 *
 * Notably, when the annotations require string parameters. We support simple string literals, doing things like "java.lang." + "String" is not supported and will error.
 */

public class MixinProcessor {
    private final SymbolReferenceWalker walker;
    private final Map<String, MixinInfo> mixins = new HashMap<>();
    private final Map<MixinAnnotation, IAnnotationProcessor> annotations = new EnumMap<>(MixinAnnotation.class);

    MixinProcessor(SymbolReferenceWalker walker) {
        this.walker = walker;
    }

    boolean process(NormalAnnotation node, String name) {
        IAnnotationProcessor proc = getProcessor(name);
        return proc == null ? true : proc.process(node);
    }

    boolean process(SingleMemberAnnotation node, String name) {
        IAnnotationProcessor proc = getProcessor(name);
        return proc == null ? true : proc.process(node);
    }

    boolean process(MarkerAnnotation node, String name) {
        IAnnotationProcessor proc = getProcessor(name);
        return proc == null ? true : proc.process(node);
    }

    @Nullable
    public MixinInfo getInfo(String owner) {
        return this.mixins.get(owner);
    }

    public MixinInfo getOrCreateInfo(ITypeBinding owner) {
        String name = ExtractUtil.getInternalName(getBuilder().getFilename(), owner, null);
        return this.mixins.computeIfAbsent(name, k -> new MixinInfo(name, owner));
    }

    public SymbolReferenceWalker getWalker() {
        return this.walker;
    }

    public RangeMapBuilder getBuilder() {
        return this.walker.getBuilder();
    }

    @Nullable
    private IAnnotationProcessor getProcessor(String type) {
        MixinAnnotation ann = MixinAnnotation.getByType(type);
        if (ann == null)
            return null;
        return this.annotations.computeIfAbsent(ann, k -> k.newInstance(this));
    }


    String getFieldOwner(String owner, String name, String type) {
        MixinInfo info = this.mixins.get(owner);
        if (info == null)
            return owner;
        String ret = info.getShadedOwner(name, type);
        return ret == null ? owner : ret;
    }

   boolean processMethodReference(SimpleName node, IMethodBinding root, String owner, String name, String desc) {
       MixinInfo info = this.mixins.get(owner);
       if (info == null || info.getTarget() == null)
           return false;

       if (info.isOverwrite(name, desc)) {
           IMethodBinding mtd = ExtractUtil.findRoot(info.getTargetType(), name, desc);
           String towner = ExtractUtil.getInternalName("{unknown}", mtd.getDeclaringClass(), node);
           getBuilder().addMethodReference(node.getStartPosition(), node.getLength(), node.toString(), towner, name, desc);
           return true;
       }

       ShadowInfo shadow = info.getShadow(name, desc);
       if (shadow != null) {
           int offset = 0;
           if (shadow.getPrefix() != null && name.startsWith(shadow.getPrefix()))
               offset = shadow.getPrefix().length();

           getBuilder().addMethodReference(node.getStartPosition() + offset, node.getLength() - offset, node.toString().substring(offset), info.getTarget(), name.substring(offset), desc);
           return true;
       }

       for (InterfaceInfo iinfo : info.getInterfaces()) {
           String iowner = iinfo.findOwner(name, desc);
           if (iowner != null) {
               int offset = name.startsWith(iinfo.getPrefix()) ? iinfo.getPrefix().length() : 0;
               getBuilder().addMethodReference(node.getStartPosition() + offset, node.getLength() - offset, node.toString().substring(offset), iowner, name.substring(offset), desc);
               return true;
           }
       }

       return false;
   }
}
