/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.srg2source.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraftforge.srg2source.ConsoleTool;

public class Util {
    /**
     * Get the top-level class required to be declared in a file by its given name, if in the main tree
     * This is an internal name, including slashes for packages components
     */
    public static String getTopLevelClassForFilename(String filename) {
        filename = filename.replace('\\', '/');
        if (filename.startsWith("/"))
            filename = filename.substring(1);


        int lastSlash = filename.lastIndexOf('/');
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > lastSlash)
            filename = filename.substring(0, lastDot);

        return filename;
    }

    public static int transferTo(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024];
        int total = 0;
        int cnt;

        while ((cnt = input.read(buf)) > 0) {
            output.write(buf, 0, cnt);
            total += cnt;
        }

        return total;
    }

    public static byte[] readStream(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transferTo(input, output);
        return output.toByteArray();
    }

    public static String md5(String data, Charset encoding) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data.getBytes(encoding));
            return hex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String hex(byte[] data) {
        return IntStream.range(0, data.length).collect(StringBuilder::new, (sb,i)->new Formatter(sb).format("%02x", data[i] & 0xFF), StringBuilder::append).toString();
    }

    /*
     * Quotes a string if it either has a space, or it has a " as the first character
     */
    public static String quote(String data) {
        if (data.indexOf(' ') != -1 || data.indexOf('"') == 0)
            data = '"' + data.replaceAll("\"", "\\\\\"") + '"';
        return data;
    }

    public static String quote(String... data) {
        return Arrays.stream(data).map(Util::quote).collect(Collectors.joining(" "));
    }

    /*
     * Will attempt to remove the specified number of quoted strings from the input.
     * The resulting list will have no more then `count` entries.
     */
    public static List<String> unquote(String data, int count) {
        List<String> ret = new ArrayList<>();
        for (int x = 0; x < count; x++) {
            if (data.charAt(0) != '"') {
                int idx = data.indexOf(' ');
                if (idx == -1)
                    break;
                ret.add(data.substring(0, idx));
                data = data.substring(idx + 1);
            } else {
                int idx = data.indexOf('"', 1);
                while (idx != -1 && data.charAt(idx - 1) == '\\')
                    idx = data.indexOf('"', idx + 1);

                if (idx == -1 || data.charAt(idx -1) == '\\')
                    throw new IllegalArgumentException("Improperly quoted string: " + data);

                idx = data.indexOf(' ', idx);
                ret.add(data.substring(1, idx - 1).replace("\\\"", "\""));
                if (idx == -1) {
                    data = null;
                    break;
                } else
                    data = data.substring(idx + 1);
            }
        }
        if (data != null)
            ret.add(data);
        return ret;
    }

    public static Path getClassRoot(String cls) {
        var url = ConsoleTool.class.getResource("/" + cls.replace('.', '/') + ".class");
        if (url == null)
            return null;
        String path = url.toString().substring(0, url.toString().length() - cls.length() - 6);
        if ("jar".equals(url.getProtocol()) && path.endsWith("!/"))
            path = path.substring(4, path.length() - 2);
        if (path.startsWith("file:"))
            path = path.substring(6);
        return Paths.get(path);
    }
}
