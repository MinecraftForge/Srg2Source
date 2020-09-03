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

package net.minecraftforge.srg2source.test;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

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
