/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

//TODO: Eclipse complains about unused messages. Find a way to make it shut up.
@CompileStatic
abstract class PatchJDTClasses extends DefaultTask {
    public static final String COMPILATION_UNIT_RESOLVER = 'org/eclipse/jdt/core/dom/CompilationUnitResolver'
    public static final String RANGE_EXTRACTOR = 'net/minecraftforge/srg2source/extract/RangeExtractor'
    public static final String RESOLVE_METHOD = 'resolve([Ljava/lang/String[Ljava/lang/String[Ljava/lang/StringLorg/eclipse/jdt/core/dom/FileASTRequestorILjava/util/MapI)V'
    public static final String GET_CONTENTS = 'org/eclipse/jdt/internal/compiler/util/Util.getFileCharContent(Ljava/io/FileLjava/lang/String)[C'
    public static final String HOOK_DESC_RESOLVE = '(Ljava/lang/StringLjava/lang/String)[C'

    abstract @Input SetProperty<String> getTargets()
    abstract @InputFiles @Classpath ConfigurableFileCollection getLibraries()
    abstract @OutputFile RegularFileProperty getOutput()

    @TaskAction
    void patchClass() {
        final targets = this.targets.get().collect()
        try (var zout = new ZipOutputStream(new FileOutputStream(this.output.get().asFile))) {
            for (var lib in this.libraries.files.findAll { !it.directory }) {
                try (var zin = new ZipFile(lib)) {
                    def remove = []
                    for (var target in targets) {
                        var entry = zin.getEntry(target + '.class')
                        if (entry === null) return

                        var node = new ClassNode()
                        var reader = new ClassReader(zin.getInputStream(entry))
                        reader.accept(node, 0)

                        //CompilationUnitResolver allows batch compiling, the problem is it is hardcoded to read the contents from a File.
                        //So we patch this call to redirect to us, so we can get the contents from our InputSupplier
                        if (COMPILATION_UNIT_RESOLVER == target) {
                            this.logger.lifecycle('Transforming: {} From: {}', target, lib)
                            var resolve = node.methods.find { RESOLVE_METHOD.equals(it.name + it.desc) }
                            if (resolve == null)
                                throw new RuntimeException('Failed to patch ' + target + ': Could not find method ' + RESOLVE_METHOD)

                            for (int x = 0; x < resolve.instructions.size(); x++) {
                                def insn = resolve.instructions.get(x)
                                if (insn.type != AbstractInsnNode.METHOD_INSN) continue

                                insn = insn as MethodInsnNode
                                if (GET_CONTENTS != "${insn.owner}.${insn.name}${insn.desc}") continue

                                if (
                                    resolve.instructions.get(x - 5).opcode === Opcodes.NEW &&
                                        resolve.instructions.get(x - 4).opcode === Opcodes.DUP &&
                                        resolve.instructions.get(x - 3).opcode === Opcodes.ALOAD &&
                                        resolve.instructions.get(x - 2).opcode === Opcodes.INVOKESPECIAL &&
                                        resolve.instructions.get(x - 1).opcode === Opcodes.ALOAD
                                ) {
                                    resolve.instructions.set(resolve.instructions.get(x - 5), new InsnNode(Opcodes.NOP))
                                    // NEW File
                                    resolve.instructions.set(resolve.instructions.get(x - 4), new InsnNode(Opcodes.NOP))
                                    // DUP
                                    resolve.instructions.set(resolve.instructions.get(x - 2), new InsnNode(Opcodes.NOP))
                                    // INVOKESTATIC <init>
                                    insn.owner = RANGE_EXTRACTOR
                                    insn.desc = HOOK_DESC_RESOLVE
                                    //logger.lifecycle('Patched {}', node.name)
                                    remove.add(target)
                                } else {
                                    throw new IllegalStateException('Found Util.getFileCharContents call, with unexpected context')
                                }
                            }
                        } else if (RANGE_EXTRACTOR == target) {
                            this.logger.lifecycle('Tansforming: {} From: {}', target, lib)
                            def marker = node.methods.find { 'hasBeenASMPatched()Z' == it.name + it.desc }
                            if (marker == null)
                                throw new RuntimeException('Failed to patch ' + target + ': Could not find method hasBeenASMPatched()Z')

                            marker.instructions.clear()
                            marker.instructions.add(new InsnNode(Opcodes.ICONST_1))
                            marker.instructions.add(new InsnNode(Opcodes.IRETURN))
                            //logger.lifecycle('Patched: {}', node.name)
                            remove.add(target)
                        }

                        def writer = new ClassWriter(0)
                        node.accept(writer)

                        def nentry = new ZipEntry(entry.name)
                        nentry.time = 0
                        zout.putNextEntry(nentry)
                        zout.write(writer.toByteArray())
                        zout.closeEntry()
                    }
                    targets.removeAll(remove)
                }
            }

            if (!targets.empty)
                throw new IllegalStateException('Patching class failed: ' + targets)
        }
    }
}
