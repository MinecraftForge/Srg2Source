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

public class MixinTests extends SimpleTestBase {
    @Override protected String getPrefix() { return "Mixins"; }
    @Override protected List<String> getLibraries(){ return Arrays.asList("org.spongepowered:mixin:0.8"); }

    public static class Mixin extends MixinTests {
        @Override protected String getPrefix() { return super.getPrefix() + "/Mixin"; }

        @Test public void testHardTargetSingle() { testClass("HardTargetSingle"); }
        @Test public void testHardTargetMulti()  { testClass("HardTargetMulti");  }
        @Test public void testSoftTargetSingle() { testClass("SoftTargetSingle"); }
        @Test public void testSoftTargetMulti()  { testClass("SoftTargetMulti");  }
    }

    public static class Shadow extends MixinTests {
        @Override protected String getPrefix() { return super.getPrefix() + "/Shadow"; }

        @Test public void testShadowField()  { testClass("ShadowField"); }
        @Test public void testShadowMethod() { testClass("ShadowMethod"); }
    }
}
