package dev.analyzer.automation;

import java.util.*;

/** Separates a captured machine's live simulation from its reproducible construction settings. */
public final class SchematicStatePolicy {
    private SchematicStatePolicy() { }
    private static final Set<String> SIMULATION = Set.of("powered", "lit", "age", "moisture", "signal_fire", "unstable",
            "extended", "triggered", "crafting", "power", "locked", "enabled", "occupied", "disarmed", "attached");
    public static boolean fluid(String block) { return block.equals("minecraft:water") || block.equals("minecraft:lava"); }
    public static boolean derived(String block) { return block.equals("minecraft:piston_head") || block.equals("minecraft:moving_piston"); }
    public static boolean simulation(String block, String property) {
        return SIMULATION.contains(property) || property.equals("level") && !fluid(block)
                || property.equals("instrument") && block.equals("minecraft:note_block");
    }
    public static SchematicData.State construction(SchematicData.State snapshot) {
        if (derived(snapshot.name())) return new SchematicData.State("minecraft:air", Map.of());
        var properties = new TreeMap<>(snapshot.properties());
        properties.keySet().removeIf(property -> simulation(snapshot.name(), property));
        return new SchematicData.State(snapshot.name(), properties);
    }
    public static boolean adjustable(String block, String property) {
        return property.equals("delay") && block.equals("minecraft:repeater")
                || property.equals("mode") && block.equals("minecraft:comparator")
                || property.equals("note") && block.equals("minecraft:note_block")
                || property.equals("open") && (block.endsWith("_door") || block.endsWith("_trapdoor") || block.endsWith("_fence_gate"));
    }
    /** Only these intermediate differences can be resolved by stacking, normal use or neighbor updates. */
    public static boolean placementPropertyCompatible(String block, String property, String actual, String desired) {
        if (Objects.equals(actual, desired) || simulation(block, property) || adjustable(block, property)) return true;
        if (Set.of("north", "south", "east", "west", "shape", "distance", "snowy").contains(property)) return true;
        if (property.equals("waterlogged") && actual.equals("false") && desired.equals("true")) return true;
        if (property.equals("type") && Set.of("left", "right", "single").contains(desired)) return true;
        if (property.equals("type") && desired.equals("double") && Set.of("top", "bottom").contains(actual)) return true;
        return Set.of("layers", "candles", "pickles", "eggs").contains(property) && Integer.parseInt(actual) < Integer.parseInt(desired);
    }
    /** Never equate persistent orientation/shape/waterlogging with a convenient default. */
    public static boolean persistentEqual(SchematicData.State actual, SchematicData.State desired) {
        if (!actual.name().equals(desired.name())) return false;
        for (var entry : desired.properties().entrySet()) {
            if (simulation(desired.name(), entry.getKey())) continue;
            if (fluid(desired.name()) && entry.getKey().equals("level")) {
                String observed = actual.properties().get("level");
                if (observed == null || entry.getValue().equals("0") != observed.equals("0")) return false;
            } else if (!entry.getValue().equals(actual.properties().get(entry.getKey()))) return false;
        }
        return true;
    }
    public static List<String> constructionNotes(SchematicData data) {
        var changed = new TreeMap<String, Integer>(); int derived = 0, flowing = 0, water = 0, lava = 0, logged = 0;
        for (int y = 0; y < data.height(); y++) for (int z = 0; z < data.length(); z++) for (int x = 0; x < data.width(); x++) {
            var state = data.stateAt(x, y, z); if (state == null || state.air()) continue;
            if (derived(state.name())) { derived++; continue; }
            for (String property : state.properties().keySet()) if (simulation(state.name(), property)) changed.merge(property, 1, Integer::sum);
            if (fluid(state.name())) {
                if (!state.properties().getOrDefault("level", "0").equals("0")) flowing++;
                else if (state.name().equals("minecraft:water")) water++; else lava++;
            }
            if (state.properties().getOrDefault("waterlogged", "false").equals("true")) logged++;
        }
        var notes = new ArrayList<String>();
        notes.add("Construction review: the original file, palette and ghost stay unchanged. Facing, axis, slab/stair half, rotation, connections and waterlogging must match the observed world; completion is checked against construction properties.");
        if (!changed.isEmpty()) notes.add("Captured live machine states are not placement instructions: " + changed + ". Power, growth, compost level, light and piston motion are controlled by the server; this is a construction plan, not restoration of a running farm's instantaneous state.");
        if (derived > 0) notes.add(derived + " captured piston-head/moving-piston cells are generated by their real piston bases and are not separate obtainable materials or forced placements.");
        if (water + lava + logged > 0) notes.add("Fluids: " + water + " water sources, " + lava + " lava sources and " + logged + " waterlogged blocks. One reusable bucket is sufficient; it is refilled from observed reachable sources outside the build. Dry containment is built first.");
        if (flowing > 0) notes.add(flowing + " flowing-fluid cells must result from the real source blocks; flowing water/lava is never placed as invented source blocks. Exact flowing levels may change with server simulation.");
        return List.copyOf(notes);
    }
}
