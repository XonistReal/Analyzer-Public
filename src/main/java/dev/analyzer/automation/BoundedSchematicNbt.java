package dev.analyzer.automation;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Small read-only NBT decoder with limits applied before allocating arrays or descending. */
final class BoundedSchematicNbt {
    private static final int MAX_DEPTH = 32, MAX_STRING_BYTES = 8192, MAX_ELEMENTS = 4_194_304;
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private final DataInputStream input;
    private int elements;
    private BoundedSchematicNbt(InputStream input) { this.input = new DataInputStream(new LimitedInput(input)); }
    static Map<String, Object> read(InputStream raw) throws IOException {
        PushbackInputStream peek = new PushbackInputStream(raw, 2);
        int first = peek.read(), second = peek.read();
        if (second >= 0) peek.unread(second);
        if (first >= 0) peek.unread(first);
        InputStream decoded = first == 0x1f && second == 0x8b ? new GZIPInputStream(peek) : peek;
        try (decoded) {
            BoundedSchematicNbt reader = new BoundedSchematicNbt(decoded);
            if (reader.input.readUnsignedByte() != 10) throw new IOException("Schematic NBT root must be a compound");
            reader.string();
            Map<String, Object> root = reader.compound(0);
            if (reader.input.read() != -1) throw new IOException("Unexpected trailing schematic NBT bytes");
            return root;
        }
    }
    private String string() throws IOException {
        int length = input.readUnsignedShort();
        if (length > MAX_STRING_BYTES) throw new IOException("Schematic NBT string exceeds 8 KiB");
        byte[] bytes = new byte[length + 2]; bytes[0] = (byte)(length >>> 8); bytes[1] = (byte)length;
        input.readFully(bytes, 2, length);
        return new DataInputStream(new ByteArrayInputStream(bytes)).readUTF();
    }
    private int count() throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_ELEMENTS - elements) throw new IOException("Schematic NBT exceeds the element limit");
        elements += length; return length;
    }
    private Map<String, Object> compound(int depth) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        while (true) {
            int type = input.readUnsignedByte(); if (type == 0) return values;
            if (++elements > MAX_ELEMENTS) throw new IOException("Schematic NBT exceeds the element limit");
            String name = string();
            if (values.containsKey(name)) throw new IOException("Duplicate schematic NBT field: " + name);
            values.put(name, value(type, depth + 1));
        }
    }
    private Object value(int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Schematic NBT nesting exceeds 32 levels");
        return switch (type) {
            case 1 -> input.readByte(); case 2 -> input.readShort(); case 3 -> input.readInt(); case 4 -> input.readLong();
            case 5 -> input.readFloat(); case 6 -> input.readDouble(); case 8 -> string(); case 10 -> compound(depth);
            case 7 -> { byte[] array = new byte[count()]; input.readFully(array); yield array; }
            case 11 -> { int[] array = new int[count()]; for (int i = 0; i < array.length; i++) array[i] = input.readInt(); yield array; }
            case 12 -> { long[] array = new long[count()]; for (int i = 0; i < array.length; i++) array[i] = input.readLong(); yield array; }
            case 9 -> {
                int elementType = input.readUnsignedByte(), size = count();
                if (elementType > 12 || (elementType == 0 && size != 0)) throw new IOException("Invalid schematic NBT list type");
                List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) list.add(value(elementType, depth + 1));
                yield list;
            }
            default -> throw new IOException("Invalid schematic NBT tag type: " + type);
        };
    }
    private static final class LimitedInput extends FilterInputStream {
        private long remaining = MAX_BYTES;
        LimitedInput(InputStream input) { super(input); }
        @Override public int read() throws IOException {
            int value = super.read(); if (value >= 0 && --remaining < 0) throw new IOException("Schematic decompression exceeds 128 MiB"); return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, (int)Math.min(length, Math.max(1, remaining + 1)));
            if (count > 0 && (remaining -= count) < 0) throw new IOException("Schematic decompression exceeds 128 MiB"); return count;
        }
    }
}
