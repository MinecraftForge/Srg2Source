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
