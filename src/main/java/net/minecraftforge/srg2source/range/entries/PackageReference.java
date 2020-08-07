package net.minecraftforge.srg2source.range.entries;

import java.util.function.Consumer;

public class PackageReference extends RangeEntry {
    public static PackageReference create(int start, int length, String name) {
        return new PackageReference(start, length, name);
    }

    static PackageReference read(int spec, int start, int length, String text, String data) {
        return new PackageReference(start, length, text);
    }

    protected PackageReference(int start, int length, String text) {
        super(Type.PACKAGE, start, length, text);
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(null);
    }
}
