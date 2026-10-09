package dev.analyzer.core;

/** The stable groups used by the module browser. */
public enum Category {
    VISUAL("Visual"), WORLD("World"), HUD("HUD");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
