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

package net.minecraftforge.srg2source;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServiceDecorator;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.ITransformationService;
import net.minecraftforge.srg2source.asm.TransformationService;
import net.minecraftforge.srg2source.extract.RangeExtractor;

public class ConsoleTool {
    public static void main(String[] args) throws Exception {
        System.setProperty("osgi.nls.warnings", "ignore"); //Shutup Eclipse in our trimmed fat-jar.
        if (RangeExtractor.hasBeenASMPatched()) {
            Redefined.main(args);
        } else {
            TransformStore transformStore = new TransformStore();
            Constructor<TransformationServiceDecorator> ctr = TransformationServiceDecorator.class.getDeclaredConstructor(ITransformationService.class);
            ctr.setAccessible(true);
            TransformationServiceDecorator sd = ctr.newInstance(new TransformationService());
            sd.gatherTransformers(transformStore);
            Path[] targetPaths = new Path[] {
                getClassRoot("org/eclipse/jdt/core/dom/CompilationUnitResolver"),
                getClassRoot(ConsoleTool.class.getName()),
                getClassRoot(RangeExtractor.class.getName())
            };
            TransformingClassLoader tcl = new TransformingClassLoader(transformStore, new LaunchPluginHandler(), targetPaths);

            Class<?> cls = Class.forName(ConsoleTool.class.getName() + "$Redefined", true, tcl);

            cls.getDeclaredMethod("main", String[].class).invoke(null, new Object[] {args});
        }
    }

    private static Path getClassRoot(String cls) {
        URL url = ConsoleTool.class.getResource("/" + cls.replace('.', '/') + ".class");
        if (url == null)
            return null;
        String path = url.toString().substring(0, url.toString().length() - cls.length() - 6);
        if ("jar".equals(url.getProtocol()) && path.endsWith("!/"))
            path = path.substring(4, path.length() - 2);
        if (path.startsWith("file:"))
            path = path.substring(6);
        return Paths.get(path);
    }

    @SuppressWarnings("unused")
    public static class Redefined {
        public static void main(String[] args) throws Exception {
            Task target = null;
            Map<String, Task> tasks = new HashMap<>();
            for (Task t : Task.values())
                tasks.put("--" + t.name().toLowerCase(Locale.ENGLISH), t);

            Deque<String> que = new LinkedList<>();
            for(String arg : args)
                que.add(arg);

            List<String> _args = new ArrayList<String>();

            String arg;
            while ((arg = que.poll()) != null) {
                if (tasks.containsKey(arg.toLowerCase(Locale.ENGLISH))) {
                    if (target != null)
                        throw new IllegalArgumentException("Only one task allowed at a time, trued to run " + arg + " when " + target + " already set");
                    target = tasks.get(arg.toLowerCase(Locale.ENGLISH));
                } else if ("--cfg".equals(arg)) {
                    String cfg = que.poll();
                    if (cfg == null)
                        throw new IllegalArgumentException("Invalid --cfg entry, missing file path");
                    Files.readAllLines(Paths.get(cfg)).forEach(que::add);
                }
                else if (arg.startsWith("--cfg="))
                    Files.readAllLines(Paths.get(arg.substring(6))).forEach(que::add);
                else
                    _args.add(arg);
            }

            if (target == null)
                System.out.println("Must specify a task to run: " + tasks.keySet().stream().collect(Collectors.joining(", ")));
            else
                target.task.accept(_args.toArray(new String[_args.size()]));
        }
    }

    private static enum Task {
        APPLY(RangeApplyMain::main),
        EXTRACT(RangeExtractMain::main);

        private Consumer<String[]> task;
        private Task(Consumer<String[]> task) {
            this.task = task;
        }
    }

    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t) throws Exception;
    }
}
