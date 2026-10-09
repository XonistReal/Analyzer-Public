package dev.analyzer.render;

/** Render-state snapshot; no ItemEntity is mutated or retained. */
public interface ItemCosmeticState {
    boolean analyzer$grounded();
    boolean analyzer$water();
    float analyzer$angle();
    void analyzer$itemPhysics(boolean grounded, boolean water, float angle);
}
