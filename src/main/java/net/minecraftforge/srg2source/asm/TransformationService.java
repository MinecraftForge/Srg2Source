/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.asm;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;

public class TransformationService implements ITransformationService {
    @Override
    public String name() {
        return "JDTPatcher";
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {}

    @SuppressWarnings("rawtypes")
    @Override
    public List<ITransformer> transformers() {
        return Arrays.asList(new CompilationUnitResolverTransfomer(), new RangeExtractorTransformer());
    }
}
