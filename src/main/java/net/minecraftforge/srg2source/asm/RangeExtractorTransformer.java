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

package net.minecraftforge.srg2source.asm;

import java.util.Collections;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;

public class RangeExtractorTransformer implements ITransformer<ClassNode> {
    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        System.out.println("Tansforming: " + input.name);

        MethodNode resolve = input.methods.stream().filter(e -> "hasBeenASMPatched".equals(e.name) && "()Z".equals(e.desc)).findFirst().orElse(null);
        if (resolve == null)
            throw new IllegalStateException("Could not find hasBeenASMPatched()Z target on " + input.name + " Broken Transformer");

        resolve.instructions.clear();
        resolve.instructions.add(new InsnNode(Opcodes.ICONST_1));
        resolve.instructions.add(new InsnNode(Opcodes.IRETURN));

        System.out.println("Patched " + input.name);

        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target> targets() {
        return Collections.singleton(Target.targetClass("net.minecraftforge.srg2source.extract.RangeExtractor"));
    }
}
