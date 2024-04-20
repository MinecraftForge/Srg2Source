/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.util;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.HashSet;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Environment;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.ModuleLayerHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServiceDecorator;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.TypesafeMap;
import net.minecraftforge.securemodules.SecureModuleFinder;
import net.minecraftforge.srg2source.ConsoleTool;
import net.minecraftforge.srg2source.asm.TransformationService;

public class TransformingUtil {
    public static ClassLoader createTransformer() {
        var transformStore = new TransformStore();
        var sd = ctr(c(TransformationServiceDecorator.class, ITransformationService.class), new TransformationService());
        sd.gatherTransformers(transformStore);
        var layerHandler = ctr(c(ModuleLayerHandler.class));

        var lph = new LaunchPluginHandler(layerHandler);

        var environment = ctr(c(Environment.class, Launcher.class), new Object[] { null });
        new TypesafeMap(IEnvironment.class);

        List<Path> paths = new ArrayList<>();
        var test = getRoot("test.marker");
        if (test != null) {
            // We are in a testing environment, lets psudo-build the fat jar.
            paths.add(test);
            var classpath = getRoots("META-INF/MANIFEST.MF");
            var eclipse = getClassRoot("org/eclipse/jdt/core/dom/CompilationUnitResolver");

            var jars = new HashMap<Path, Set<String>>();
            for (var path : classpath) {
                if (Files.isDirectory(path) || path.equals(eclipse) || path.equals(test))
                    continue; // All eclipse libs are in a jar

                jars.put(path, gatherPackages(path));
            }

            paths.add(eclipse);
            Set<String> seen = gatherPackages(eclipse);
            var queue = new ArrayDeque<>(seen);

            // Join all jars that share packages, because the module classloader is based on packages.
            while (!queue.isEmpty() && !jars.isEmpty()) {
                var pkg = queue.pop();
                for (var itr = jars.entrySet().iterator(); itr.hasNext(); ) {
                    var entry = itr.next();
                    if (!entry.getValue().contains(pkg))
                        continue;

                    itr.remove();
                    paths.add(entry.getKey());
                    for (var npkg : entry.getValue()) {
                        if (seen.add(npkg))
                            queue.add(npkg);
                    }
                }
            }
        }
        paths.add(getClassRoot(ConsoleTool.class.getName())); // Last Wins so add main S2S first


        var finder = SecureModuleFinder.of(SecureJar.from(paths.toArray(Path[]::new)));

        var configuration = ModuleLayer.boot().configuration()
            .resolveAndBind(finder, ModuleFinder.ofSystem(), Set.of("net.minecraftforge.srg2source"));

        return ctr(c(TransformingClassLoader.class,
                String.class, ClassLoader.class, Configuration.class, List.class, List.class,
                TransformStore.class, LaunchPluginHandler.class, Environment.class
            ),
            "TRANSFORMER", TransformingUtil.class.getClassLoader(), configuration, List.of(ModuleLayer.boot()), List.of(),
            transformStore, lph, environment
        );
    }

    private static <T> Constructor<T> c(Class<T> cls, Class<?>... args) {
        try {
            return cls.getDeclaredConstructor(args);
        } catch (NoSuchMethodException | SecurityException e) {
            return sneak(e);
        }
    }

    private static <T> T ctr(Constructor<T> ctr, Object... args) {
        ctr.setAccessible(true);
        try {
            return ctr.newInstance(args);
        } catch (Exception e) {
            return sneak(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R sneak(Throwable e) throws E {
        throw (E)e;
    }

    private static Path getClassRoot(String cls) {
        return getRoot(cls.replace('.', '/') + ".class");
    }

    private static Path getRoot(String resource) {
        return toPath(resource, ConsoleTool.class.getResource('/' + resource));
    }

    private static List<Path> getRoots(String resource) {
        return ConsoleTool.class.getClassLoader()
            .resources(resource)
            .map(url -> toPath(resource, url))
            .filter(n -> n != null)
            .toList();
    }

    private static Path toPath(String resource, URL url) {
        if (url == null)
            return null;

        String path = url.toString().substring(0, url.toString().length() - resource.length());

        if ("jar".equals(url.getProtocol()) && path.endsWith("!/"))
            path = path.substring(4, path.length() - 2);

        if (path.startsWith("file:"))
            path = path.substring(6);
        return Paths.get(path);
    }

    private static Set<String> gatherPackages(Path path) {
        var ret = new HashSet<String>();
        try (var fis = Files.newInputStream(path);
             var zip = new ZipInputStream(fis)) {
            ZipEntry entry = null;
            while ((entry = zip.getNextEntry()) != null) {
                var file = entry.getName();
                if (entry.isDirectory() || file.startsWith("META-INF"))
                    continue;
                int idx = file.lastIndexOf('/');
                if (!file.endsWith(".class") || idx == -1)
                    continue;
                ret.add(file.substring(0, idx).replace('/', '.'));
            }
        } catch (IOException e) {
            return sneak(e);
        }

        return ret;
    }
}
