/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.test;

import net.minecraftforge.srg2source.api.SourceVersion;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class Java16Tests extends SimpleTestBase {
    @Override protected String getPrefix() { return ""; }
    @Override protected List<String> getLibraries(){ return Collections.emptyList(); }
    @Override protected void testClass(String name) { super.testClass(name, SourceVersion.JAVA_16); }

    @Test public void testRecordSimple()   { testClass("RecordSimple"); }
    @Test public void testPatternMatch()   { testClass("PatternMatch"); }
    @Test public void testRecordCompact()  { testClass("RecordCompact"); }
    @Test public void testRecordCanonical() { testClass("RecordCanonical"); }
    @Test public void testRecordComplex()  { testClass("RecordComplex"); }
}
