package dev.analyzer.core;

import java.util.List;
import java.util.Objects;

/** Immutable, typed setting metadata. Every write goes through normalization. */
public final class Setting<T> {
    public enum Kind { BOOLEAN, NUMBER, CHOICE, TEXT }

    private final String id;
    private final String label;
    private final Kind kind;
    private final Class<T> valueType;
    private final T defaultValue;
    private final double min;
    private final double max;
    private final double step;
    private final List<String> choices;

    private Setting(String id, String label, Kind kind, Class<T> valueType, T defaultValue,
                    double min, double max, double step, List<String> choices) {
        if (id == null || !id.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid setting id: " + id);
        }
        this.id = id;
        this.label = Objects.requireNonNull(label, "label");
        this.kind = kind;
        this.valueType = valueType;
        this.min = min;
        this.max = max;
        this.step = step;
        this.choices = List.copyOf(choices);
        this.defaultValue = normalize(defaultValue);
    }

    public static Setting<Boolean> bool(String id, String label, boolean defaultValue) {
        return new Setting<>(id, label, Kind.BOOLEAN, Boolean.class, defaultValue,
                Double.NaN, Double.NaN, Double.NaN, List.of());
    }

    public static Setting<Double> number(String id, String label, double defaultValue,
                                         double min, double max, double step) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max
                || !Double.isFinite(step) || step <= 0) {
            throw new IllegalArgumentException("Invalid numeric bounds for " + id);
        }
        return new Setting<>(id, label, Kind.NUMBER, Double.class, defaultValue,
                min, max, step, List.of());
    }

    public static Setting<String> choice(String id, String label, String defaultValue,
                                         String... choices) {
        List<String> options = List.of(choices);
        if (options.isEmpty() || options.stream().distinct().count() != options.size()) {
            throw new IllegalArgumentException("Choices must be nonempty and unique: " + id);
        }
        return new Setting<>(id, label, Kind.CHOICE, String.class, defaultValue,
                Double.NaN, Double.NaN, Double.NaN, options);
    }

    /** A short, unformatted local alias. Spaces around input are ignored; unsafe glyphs are rejected. */
    public static Setting<String> text(String id, String label, String defaultValue, int maxLength) {
        if (maxLength < 1 || maxLength > 128) throw new IllegalArgumentException("Invalid text limit: " + id);
        return new Setting<>(id, label, Kind.TEXT, String.class, defaultValue, 1, maxLength, Double.NaN, List.of());
    }

    /** A local filesystem selection, edited by its dedicated folder browser rather than alias entry. */
    public static Setting<String> filePath(String id, String label) {
        return new Setting<>(id, label, Kind.TEXT, String.class, "", 0, 4096, Double.NaN, List.of());
    }

    /** Rejects wrong types and invalid choices; clamps and snaps finite numbers. */
    public T normalize(Object value) {
        Object normalized;
        switch (kind) {
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw invalid(value);
                }
                normalized = value;
            }
            case NUMBER -> {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    throw invalid(value);
                }
                double clamped = Math.clamp(number.doubleValue(), min, max);
                double snapped = min + Math.round((clamped - min) / step) * step;
                // Remove floating-point residue without imposing a display format.
                normalized = Math.clamp(Math.round(snapped * 1_000_000d) / 1_000_000d, min, max);
            }
            case CHOICE -> {
                if (!(value instanceof String text) || !choices.contains(text)) {
                    throw invalid(value);
                }
                normalized = value;
            }
            case TEXT -> {
                if (!(value instanceof String text)) throw invalid(value);
                String stripped = text.strip();
                if (stripped.length() < min || stripped.length() > max
                        || (min == 0 ? stripped.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)
                                     : !stripped.matches("[A-Za-z0-9_]+"))) throw invalid(value);
                normalized = stripped;
            }
            default -> throw new IllegalStateException("Unhandled setting kind: " + kind);
        }
        return valueType.cast(normalized);
    }

    private IllegalArgumentException invalid(Object value) {
        return new IllegalArgumentException("Invalid value for " + id + ": " + value);
    }

    public String id() { return id; }
    public String label() { return label; }
    public Kind kind() { return kind; }
    public Class<T> valueType() { return valueType; }
    public T defaultValue() { return defaultValue; }
    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public List<String> choices() { return choices; }
}
