package dev.analyzer.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small local intent compiler. It never executes text as a command or calls an AI service. */
public final class TaskPlanner {
    public enum Action { TRAVEL, MINE, CRAFT, BUILD_SHELTER, EQUIP, ENCHANT }
    public enum Direction { NORTH, EAST, SOUTH, WEST }
    public record Step(String id, Action action, String label, String resource, int count, int x, int y, int z) {
        public Step {
            Objects.requireNonNull(id); Objects.requireNonNull(action); Objects.requireNonNull(label);
            resource = resource == null ? "" : resource;
            if (count < 0 || count > 4096) throw new IllegalArgumentException("Resource count must be between 0 and 4096.");
        }
    }
    public record Plan(String title, String description, List<Step> steps, List<String> prerequisites) {
        public Plan {
            Objects.requireNonNull(title); Objects.requireNonNull(description);
            steps = List.copyOf(steps); prerequisites = List.copyOf(prerequisites);
            if (steps.isEmpty()) throw new IllegalArgumentException("A plan needs at least one step.");
        }
    }
    public record Options(int originX, int originY, int originZ, int travelX, int travelY, int travelZ,
                          int escapeDistance, Direction direction) {
        public Options {
            Objects.requireNonNull(direction);
            coordinate(originX); coordinate(originZ); coordinate(travelX); coordinate(travelZ);
            if (originY < -2048 || originY > 2048 || travelY < -2048 || travelY > 2048)
                throw new IllegalArgumentException("Y must be between -2048 and 2048; the current dimension must support it.");
            if (escapeDistance < 128 || escapeDistance > 1_000_000)
                throw new IllegalArgumentException("Escape distance must be between 128 and 1,000,000 blocks.");
        }
    }
    private static final Pattern COORDINATES = Pattern.compile("(?:to|at|coordinates?)\\s+(-?\\d+)\\s*[, ]\\s*(-?\\d+)(?:\\s*[, ]\\s*(-?\\d+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESOURCE = Pattern.compile("(?:mine|gather|collect)\\s+(?:(\\d+)\\s+)?(logs?|wood|cobblestone|stone|raw[ _-]?iron|iron(?:[ _-]?ore)?|coal|diamonds?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCAPE_DISTANCE = Pattern.compile("escape\\s+(?:spawn\\s+)?(?:by\\s+)?(\\d+)\\s*(?:blocks?|m)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCAPE_DIRECTION = Pattern.compile("\\b(north|east|south|west)\\b", Pattern.CASE_INSENSITIVE);
    private TaskPlanner() { }

    public static Plan plan(String input, Options options) {
        Objects.requireNonNull(options);
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Describe a task or choose a preset.");
        if (input.length() > 512) throw new IllegalArgumentException("Keep the task under 512 characters.");
        String text = input.toLowerCase(Locale.ROOT).replace('\n', ' ').trim();
        boolean escape = text.contains("escape") || text.contains("leave spawn") || text.contains("get out of spawn");
        boolean shelter = text.contains("base") || text.contains("shelter") || text.contains("house");
        boolean enchant = text.contains("enchant") || text.contains("fully geared") || text.contains("fully kitted");
        boolean kit = enchant || text.contains("diamond kit") || text.contains("diamond armor") || text.contains("diamond armour") || text.contains("kitted out");
        boolean expedition = text.contains("expedition");
        if (expedition) { escape = true; shelter = true; kit = true; enchant = true; }
        List<Step> steps = new ArrayList<>();
        List<String> prerequisites = new ArrayList<>(List.of("Baritone for Fabric 1.21.4 must be installed.",
                "Stay connected to one world. Low health, hunger, missing materials and stalled progress pause the task.",
                "Conflicting action modules are paused for the task and restored when it stops."));
        int homeX = shelter && !escape ? options.travelX : options.originX;
        int homeY = shelter && !escape ? options.travelY : options.originY;
        int homeZ = shelter && !escape ? options.travelZ : options.originZ;
        if (escape) {
            int distance = options.escapeDistance;
            Direction direction = options.direction;
            Matcher requestedDistance = ESCAPE_DISTANCE.matcher(text), requestedDirection = ESCAPE_DIRECTION.matcher(text);
            if (requestedDistance.find()) {
                try { distance = Integer.parseInt(requestedDistance.group(1)); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Escape distance must be between 128 and 1,000,000 blocks."); }
                if (distance < 128 || distance > 1_000_000) throw new IllegalArgumentException("Escape distance must be between 128 and 1,000,000 blocks.");
            }
            if (requestedDirection.find()) direction = Direction.valueOf(requestedDirection.group(1).toUpperCase(Locale.ROOT));
            switch (direction) {
                case NORTH -> homeZ = coordinate((long) options.originZ - distance);
                case EAST -> homeX = coordinate((long) options.originX + distance);
                case SOUTH -> homeZ = coordinate((long) options.originZ + distance);
                case WEST -> homeX = coordinate((long) options.originX - distance);
            }
            steps.add(step("escape", Action.TRAVEL, "Travel " + distance + " blocks " + direction.name().toLowerCase(Locale.ROOT), "", 0, homeX, homeY, homeZ));
            prerequisites.add("Escape means reaching a coordinate offset from your current position, not a guarantee that a location is safe.");
        }
        Matcher coordinates = COORDINATES.matcher(text);
        boolean travel = !escape && (text.contains("travel") || text.contains("go to") || text.contains("walk to") || text.contains("move to"));
        if (travel || (!escape && shelter && coordinates.find())) {
            coordinates.reset();
            if (coordinates.find()) {
                homeX = parseCoordinate(coordinates.group(1));
                if (coordinates.group(3) == null) { homeY = options.travelY; homeZ = parseCoordinate(coordinates.group(2)); }
                else { homeY = Integer.parseInt(coordinates.group(2)); homeZ = parseCoordinate(coordinates.group(3)); }
                if (homeY < -2048 || homeY > 2048) throw new IllegalArgumentException("Target Y is outside supported world bounds.");
            } else { homeX = options.travelX; homeY = options.travelY; homeZ = options.travelZ; }
            steps.add(step("travel", Action.TRAVEL, "Travel to " + homeX + ", " + homeY + ", " + homeZ, "", 0, homeX, homeY, homeZ));
        }
        if (kit || shelter) {
            steps.add(step("logs", Action.MINE, "Gather 32 logs", "logs", 32, homeX, homeY, homeZ));
            steps.add(step("bootstrap", Action.CRAFT, "Craft a table, sticks and a wooden pickaxe", "bootstrap", 1, homeX, homeY, homeZ));
            int stoneCount = shelter ? 256 : 96;
            steps.add(step("stone", Action.MINE, "Gather " + stoneCount + " cobblestone", "cobblestone", stoneCount, homeX, homeY, homeZ));
            steps.add(step("stone-tools", Action.CRAFT, "Craft a stone pickaxe and furnace", "stone-tools", 1, homeX, homeY, homeZ));
        }
        if (kit) {
            steps.add(step("iron", Action.MINE, "Gather 8 raw iron", "raw_iron", 8, homeX, homeY, homeZ));
            steps.add(step("coal", Action.MINE, "Gather 16 coal for smelting", "coal", 16, homeX, homeY, homeZ));
            steps.add(step("iron-tools", Action.CRAFT, "Smelt iron and craft an iron pickaxe", "iron-tools", 1, homeX, homeY, homeZ));
            steps.add(step("diamond", Action.MINE, "Gather 29 diamonds for armor and tools", "diamond", 29, homeX, homeY, homeZ));
            steps.add(step("diamond-kit", Action.CRAFT, "Craft four diamond armor pieces, a pickaxe and a sword", "diamond-kit", 1, homeX, homeY, homeZ));
            steps.add(step("equip", Action.EQUIP, "Equip the verified diamond armor", "diamond-kit", 1, homeX, homeY, homeZ));
            prerequisites.add("Diamond mining needs reachable ore and tool durability. Inventory targets credit items you already own.");
        }
        if (shelter) {
            steps.add(step("home", Action.TRAVEL, "Return to the shelter site", "", 0, homeX, homeY, homeZ));
            steps.add(step("shelter", Action.BUILD_SHELTER, "Build a compact starter shelter", "cobblestone", 1, homeX, homeY, homeZ));
            prerequisites.add("The 168-block starter shelter needs a clear, solid site. The 256-cobblestone goal also covers tools, the furnace and a building margin.");
        }
        if (enchant) {
            steps.add(step("enchant", Action.ENCHANT, "Enchant and verify the four armor pieces", "diamond-armor", 4, homeX, homeY, homeZ));
            steps.add(step("restore-armor", Action.EQUIP, "Re-equip the verified enchanted armor", "diamond-kit", 1, homeX, homeY, homeZ));
            prerequisites.add("Enchanting needs an accessible table, lapis and XP; level-30 options also need 15 correctly placed bookshelves.");
            prerequisites.add("The armor quality goal is Protection IV + Unbreaking III on all four pieces. Vanilla enchantments are random; suitable books and an anvil may be required. Mending is not guaranteed.");
        }
        if (!kit && !shelter) {
            Matcher resource = RESOURCE.matcher(text);
            if (resource.find()) {
                int count = resource.group(1) == null ? 32 : Integer.parseInt(resource.group(1));
                if (count < 1 || count > 4096) throw new IllegalArgumentException("Gather between 1 and 4096 items per task.");
                String name = resource.group(2).replaceAll("[ _-]", "");
                String key = name.startsWith("diamond") ? "diamond" : name.startsWith("iron") || name.startsWith("rawiron") ? "raw_iron"
                        : name.equals("wood") || name.startsWith("log") ? "logs" : name.equals("stone") ? "cobblestone" : name;
                steps.add(step("resource", Action.MINE, "Gather " + count + " " + key.replace('_', ' '), key, count, homeX, homeY, homeZ));
                prerequisites.add("Have a suitable pickaxe for stone, iron or diamonds; the gather task does not bootstrap tools.");
            }
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("Try: escape spawn, build a base, get diamond armor, enchant my armor, go to X Y Z, or mine 32 diamonds.");
        String title = expedition || (escape && shelter && enchant) ? "Spawn-to-kit expedition" : enchant ? "Enchanted diamond kit"
                : kit ? "Diamond kit" : shelter ? "Starter shelter" : escape ? "Escape spawn" : steps.size() > 1 ? "Travel and gather" : steps.getFirst().label();
        return new Plan(title, "A local, reviewable plan using ordinary Minecraft actions and Baritone pathfinding.", steps, prerequisites);
    }

    private static Step step(String id, Action action, String label, String resource, int count, int x, int y, int z) {
        return new Step(id, action, label, resource, count, x, y, z);
    }
    private static int parseCoordinate(String value) {
        try { return coordinate(Long.parseLong(value)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Coordinate is not a whole number within the world border."); }
    }
    private static int coordinate(long value) {
        if (value < -29_999_000L || value > 29_999_000L) throw new IllegalArgumentException("Coordinates must stay within the Minecraft world border.");
        return (int) value;
    }
}
