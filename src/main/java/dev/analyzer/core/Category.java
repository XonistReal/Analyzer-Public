package dev.analyzer.core;

/** The stable groups used by the module browser. */
public enum Category {
    VISUAL("Render"), WORLD("World"), HUD("HUD"), COMBAT("Combat"), MOVEMENT("Movement"),
    HVH("HVH"), PLAYER("Player"), COSMETICS("Cosmetics"), CLIENT("Client");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
