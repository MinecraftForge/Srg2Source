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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.junit.Assert;
import org.junit.Test;

import net.minecraftforge.srg2source.extract.SymbolReferenceWalker;

public class WalkerTest {
    private static boolean methodFilter(Method mtd) {
        int mods = mtd.getModifiers();
        return "visit".equals(mtd.getName()) && !mtd.isSynthetic() &&
               !Modifier.isStatic(mods) &&
               (Modifier.isPublic(mods) || !Modifier.isProtected(mods)) &&
               mtd.getParameterCount() == 1;
    }

    private static String toJava(Method mtd) {
        return "@Override " +
            (Modifier.isPublic(mtd.getModifiers()) ? "public" : "protected") +
            " boolean visit(" +
            mtd.getParameterTypes()[0].getSimpleName() +
            " node) { return true; }";
    }

    private static Set<String> getMethods(Class<?> cls) {
        return Arrays.stream(cls.getDeclaredMethods()).filter(WalkerTest::methodFilter).map(WalkerTest::toJava).collect(Collectors.toSet());
    }

    /**
     * Make sure that our visitor knows about all visit methods.
     * This is mainly a reminder to me and future developers to
     * make sure that we notice changes in the visitor when updating the library.
     */
    @Test
    public void testWalkerOverrides() {
        Set<String> needed = getMethods(ASTVisitor.class);
        Set<String> has = getMethods(new SymbolReferenceWalker(null, null, false).getVisitor().getClass());
        needed.removeAll(has);
        String code = needed.stream().sorted().collect(Collectors.joining("\n"));
        System.out.println(code);
        Assert.assertTrue("Missing Overrides:\n" + code, needed.isEmpty());
    }

}
