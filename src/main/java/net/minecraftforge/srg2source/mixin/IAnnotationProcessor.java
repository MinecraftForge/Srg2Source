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
