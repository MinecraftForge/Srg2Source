/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.test;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraftforge.srg2source.api.RangeExtractorBuilder;

public abstract class MixinTests extends SimpleTestBase {
    @Override protected String getPrefix() { return "Mixins/" + getClass().getSimpleName(); }
    @Override protected List<String> getLibraries(){ return Arrays.asList("org.spongepowered:mixin:0.8"); }
    @Override protected RangeExtractorBuilder customize(RangeExtractorBuilder builder) { return builder.enableMixins().fatalMixins(); };

    public static class Mixin extends MixinTests {
        @Test public void testHardTargetSingle() { testClass("HardTargetSingle"); }
        @Test public void testHardTargetMulti()  { testClass("HardTargetMulti");  }
        @Test public void testSoftTargetSingle() { testClass("SoftTargetSingle"); }
        @Test public void testSoftTargetMulti()  { testClass("SoftTargetMulti");  }
    }

    public static class Shadow extends MixinTests {
        @Test public void testShadowField()  { testClass("ShadowField"); }
        @Test public void testShadowMethod() { testClass("ShadowMethod"); }
    }

    public static class Accessor extends MixinTests {
        @Test public void testFieldGetterImply() { testClass("FieldGetterImply"); }
        @Test public void testFieldSetterImply() { testClass("FieldSetterImply"); }
        @Test public void testFieldGetterNamed() { testClass("FieldGetterNamed"); }
        @Test public void testFieldSetterNamed() { testClass("FieldSetterNamed"); }

        @Test public void testMethodProxyImply() { testClass("MethodProxyImply"); }
        @Test public void testMethodProxyNamed() { testClass("MethodProxyNamed"); }

        @Test public void testFactoryImply()     { testClass("FactoryImply");     }
        @Test public void testFactoryNamed()     { testClass("FactoryNamed");     }
    }

    public static class Implements extends MixinTests {
        @Test public void testSimpleImplements() { testClass("SimpleImplements"); }
    }

    public static class Overwrite extends MixinTests {
        @Test public void testOverwriteImply() { testClass("OverwriteImply"); }
    }
}
