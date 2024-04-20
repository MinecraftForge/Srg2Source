/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.test;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

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
