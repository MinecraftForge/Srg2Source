package net.minecraftforge.srg2source.asm;

import java.util.Collections;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.minecraftforge.srg2source.extract.RangeExtractor;

/*
 * Eclipse's JDT does not support non file based source loocations.
 * We have to patch the line that loads the characters from the file to redirect into our input suppliers.
 * We are specifically patching this line: https://github.com/eclipse/eclipse.jdt.core/blob/master/org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnitResolver.java#L1013
 * From: contents = Util.getFileCharContent(new File(sourceUnitPath), encoding);
 * To: contents = RangeExtractor.getFileCharContent(sourceUnitPath, encoding);
 */
public class CompilationUnitResolverTransfomer implements ITransformer<ClassNode> {
    private static final String RESOLVE_METHOD = "resolve([Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Lorg/eclipse/jdt/core/dom/FileASTRequestor;ILjava/util/Map;I)V";
    private static final String GET_CONTENTS = "org/eclipse/jdt/internal/compiler/util/Util.getFileCharContent(Ljava/io/File;Ljava/lang/String;)[C";
    private static final String HOOK_OWNER = Type.getInternalName(RangeExtractor.class);
    private static final String HOOK_DESC = Type.getMethodDescriptor(Type.getType(char[].class), Type.getType(String.class), Type.getType(String.class));

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        System.out.println("Tansforming: " + input.name);

        MethodNode resolve = input.methods.stream().filter(e -> RESOLVE_METHOD.equals(e.name + e.desc)).findFirst().orElse(null);
        if (resolve == null)
            throw new IllegalStateException("Could not find resolve target on " + input.name + " JDT Mismatch?: " + RESOLVE_METHOD);

        for (int x = 0; x < resolve.instructions.size(); x++) {
            if (resolve.instructions.get(x).getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode mtd = (MethodInsnNode)resolve.instructions.get(x);
                if (GET_CONTENTS.equals(mtd.owner + "." + mtd.name + mtd.desc)) {
                    if (
                            resolve.instructions.get(x - 5).getOpcode() == Opcodes.NEW &&
                            resolve.instructions.get(x - 4).getOpcode() == Opcodes.DUP &&
                            resolve.instructions.get(x - 3).getOpcode() == Opcodes.ALOAD &&
                            resolve.instructions.get(x - 2).getOpcode() == Opcodes.INVOKESPECIAL &&
                            resolve.instructions.get(x - 1).getOpcode() == Opcodes.ALOAD
                        ) {
                        resolve.instructions.set(resolve.instructions.get(x - 5), new InsnNode(Opcodes.NOP)); // NEW File
                        resolve.instructions.set(resolve.instructions.get(x - 4), new InsnNode(Opcodes.NOP)); // DUP
                        resolve.instructions.set(resolve.instructions.get(x - 2), new InsnNode(Opcodes.NOP)); // INVOKESTATIC <init>
                        mtd.owner = HOOK_OWNER;
                        mtd.desc = HOOK_DESC;
                        System.out.println("Patched " + input.name);
                    } else {
                        throw new IllegalStateException("Found Util.getFileCharContents call, with unexpected context");
                    }
                }
            }
        }

        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target> targets() {
        return Collections.singleton(Target.targetClass("org.eclipse.jdt.core.dom.CompilationUnitResolver"));
    }
}
