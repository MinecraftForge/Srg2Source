package net.minecraftforge.srg2source.range.entries;

import java.util.List;
import java.util.function.Consumer;

import net.minecraftforge.srg2source.util.Util;

public class ClassReference extends RangeEntry {

    public static ClassReference create(int start, int length, String text, String internal, boolean qualified) {
        return new ClassReference(start, length, text, internal, qualified);
    }

    static ClassReference read(int spec, int start, int length, String text, String data) {
        List<String> pts = Util.unquote(data, 2);
        if (pts.size() != 2)
            throw new IllegalArgumentException("Invalid Class reference: " + data);
        return new ClassReference(start, length, text, pts.get(1), Boolean.parseBoolean(pts.get(0)));
    }

    private final String internal;
    private final boolean qualified;

    protected ClassReference(int start, int length, String text, String internal, boolean qualified) {
        super(RangeEntry.Type.CLASS, start, length, text);
        this.internal = internal;
        this.qualified = qualified;
    }

    @Override
    protected String getExtraFields() {
        return "Internal: " + internal + ", Qualified: " + qualified;
    }

    @Override
    protected void writeInternal(Consumer<String> out) {
        out.accept(Util.quote(Boolean.toString(qualified), internal));
    }
}
