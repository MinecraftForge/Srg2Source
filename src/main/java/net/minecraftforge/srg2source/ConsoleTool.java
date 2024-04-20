/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source;

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
import java.util.Set;
import java.util.stream.Collectors;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.ServiceRunner;
import net.minecraftforge.srg2source.extract.RangeExtractor;
import net.minecraftforge.srg2source.util.TransformingUtil;
import net.minecraftforge.srg2source.util.Util;

public class ConsoleTool {
    public static void main(String[] args) throws Exception {
        System.setProperty("osgi.nls.warnings", "ignore"); //Shutup Eclipse in our trimmed fat-jar.
        if (RangeExtractor.hasBeenASMPatched()) {
            Redefined.main(args);
        } else {
            var tcl = TransformingUtil.createTransformer();

            Class<?> cls = Class.forName(ConsoleTool.class.getName() + "$Redefined", true, tcl);

            cls.getDeclaredMethod("main", String[].class).invoke(null, new Object[] {args});
        }
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
