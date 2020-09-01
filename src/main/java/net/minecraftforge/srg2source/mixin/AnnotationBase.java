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

import javax.annotation.Nullable;

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

    protected MixinInfo getInfo(String owner, String error) {
        MixinInfo ret = getInfo(owner);
        if (ret == null)
            throw new IllegalStateException(error);
        return ret;
    }
}
