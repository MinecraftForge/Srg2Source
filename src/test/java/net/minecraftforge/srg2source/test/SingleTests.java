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

import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SingleTests extends SimpleTestBase {
    @Override protected String getPrefix() { return ""; }
    @Override protected List<String> getLibraries(){ return Collections.emptyList(); }

    //@Test public void testLambda()         { testClass("Lambda");         }
    @Test public void testGenerics()       { testClass("GenericClasses"); }
    @Test public void testAnonClass()      { testClass("AnonClass"     ); }
    @Test public void testInnerClass()     { testClass("InnerClass"    ); }
    @Test public void testLocalClass()     { testClass("LocalClass"    ); }
    @Test public void testImportSpaces()   { testClass("ImportSpaces"  ); }
    @Test public void testNestedGenerics() { testClass("NestedGenerics"); }
    @Test public void testPackageInfo()    { testClass("PackageInfo"   ); }
    //@Test public void testCache()          { testClass("GenericClasses"); }
    @Test public void testWhiteSpace()     { testClass("Whitespace"    ); }
}
