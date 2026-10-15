package dev.analyzer.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small local intent compiler. It never executes text as a command or calls an AI service. */
public final class TaskPlanner {
    public enum Action { TRAVEL, MINE, CRAFT, BUILD_SHELTER, EQUIP, ENCHANT, DIMENSION, BUILD_SCHEMATIC, SUPPLY_MATERIAL }
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
    private static final Pattern ESCAPE_DISTANCE = Pattern.compile("escape\\s+(?:spawn\\s+)?(?:by\\s+)?(\\d+)\\s*(?:blocks?|m)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCAPE_DIRECTION = Pattern.compile("\\b(north|east|south|west)\\b", Pattern.CASE_INSENSITIVE);
    private TaskPlanner() { }

    public static Plan plan(String input, Options options) {
        Objects.requireNonNull(options);
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Describe a task or choose a preset.");
        if (input.length() > 512) throw new IllegalArgumentException("Keep the task under 512 characters.");
        if (LocalTaskIntent.schematic(input).isPresent()) throw new IllegalArgumentException("Choose the actual schematic from Automation → Schematics → Browse, then review its materials and site. One selected file is built once.");
        if (Pattern.compile("(?:^|\\s)[/#]|\\b(?:don't|do not|without|except)\\b", Pattern.CASE_INSENSITIVE).matcher(input).find())
            throw new IllegalArgumentException("Use a positive task request such as ‘get just 37 diamonds’. Commands and exclusions are not executed as task instructions.");
        String text = input.toLowerCase(Locale.ROOT).replace('\n', ' ').trim();
        var parsed = LocalTaskIntent.parse(text);
        text = parsed.actionText();
        boolean escape = text.contains("escape") || text.contains("leave spawn") || text.contains("get out of spawn");
        boolean shelter = text.contains("base") || text.contains("shelter") || text.contains("house");
        boolean netherite = text.contains("netherite armor") || text.contains("netherite armour") || text.contains("netherite kit");
        boolean enchant = text.contains("enchant") || text.contains("fully geared") || text.contains("fully kitted");
        boolean kit = netherite || enchant || text.contains("diamond kit") || text.contains("diamond armor") || text.contains("diamond armour") || text.contains("kitted out");
        boolean expedition = text.contains("expedition");
        if (expedition) { escape = true; shelter = true; kit = true; enchant = true; }
        List<Step> steps = new ArrayList<>();
        List<String> prerequisites = new ArrayList<>(List.of("Analyzer's original navigator uses loaded terrain, ordinary movement and server-verified actions; no external pathfinding mod is required.",
                "Stay connected to the same server or save. Only reviewed portal stages permit expected dimension changes; other changes pause the task.",
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
        String dimension = requestedDimension(text);
        boolean travel = !escape && dimension.isEmpty() && (text.contains("travel") || text.contains("go to") || text.contains("walk to") || text.contains("move to"));
        if (!text.isBlank() && !escape && !shelter && !kit && !enchant && !travel && dimension.isEmpty())
            throw new IllegalArgumentException("I cannot turn ‘" + text + "’ into a supported local action yet. Review a material target, travel, an armor goal, or choose an actual schematic from Browse.");
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
            steps.add(step("iron", Action.MINE, "Gather " + (netherite ? 12 : 8) + " raw iron", "raw_iron", netherite ? 12 : 8, homeX, homeY, homeZ));
            steps.add(step("coal", Action.MINE, "Gather " + (netherite ? 24 : 16) + " coal for smelting", "coal", netherite ? 24 : 16, homeX, homeY, homeZ));
            steps.add(step("iron-tools", Action.CRAFT, "Smelt iron and craft an iron pickaxe", "iron-tools", 1, homeX, homeY, homeZ));
            int diamonds = netherite ? 50 : 29;
            steps.add(step("diamond", Action.MINE, "Gather " + diamonds + " diamonds for armor, tools" + (netherite ? " and template duplication" : ""), "diamond", diamonds, homeX, homeY, homeZ));
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
            steps.add(step("enchant-lapis", Action.MINE, "Gather 12 lapis lazuli for armor enchanting", "lapis_lazuli", 12, homeX, homeY, homeZ));
            steps.add(step("enchant", Action.ENCHANT, "Enchant and verify the four armor pieces", "diamond-armor", 4, homeX, homeY, homeZ));
            steps.add(step("restore-armor", Action.EQUIP, "Re-equip the verified enchanted armor", "diamond-kit", 1, homeX, homeY, homeZ));
            prerequisites.add("Enchanting needs an accessible table, lapis and XP; level-30 options also need 15 correctly placed bookshelves.");
            prerequisites.add("The armor quality goal is Protection IV + Unbreaking III on all four pieces. Vanilla enchantments are random; suitable books and an anvil may be required. Mending is not guaranteed.");
        }
        if (netherite) {
            steps.add(step("netherite-template", Action.CRAFT, "Verify an owned Netherite Upgrade template before the expedition", "netherite-template", 1, homeX, homeY, homeZ));
            steps.add(step("netherite-gold", Action.MINE, "Gather 16 raw gold for four netherite ingots", "raw_gold", 16, homeX, homeY, homeZ));
            steps.add(step("netherite-gold-smelt", Action.CRAFT, "Smelt 16 gold ingots", "smelt-gold", 16, homeX, homeY, homeZ));
            steps.add(step("netherite-obsidian", Action.MINE, "Gather 10 obsidian for a Nether portal if needed", "obsidian", 10, homeX, homeY, homeZ));
            steps.add(step("netherite-flint", Action.MINE, "Collect one real flint drop for portal ignition", "flint", 1, homeX, homeY, homeZ));
            steps.add(step("netherite-igniter", Action.CRAFT, "Craft flint and steel from owned flint and iron", "portal-kit", 1, homeX, homeY, homeZ));
            steps.add(step("netherite-portal", Action.DIMENSION, "Enter the Nether through a verified portal", "the_nether", 1, homeX, homeY, homeZ));
            steps.add(step("netherite-debris", Action.MINE, "Gather 16 ancient debris with a diamond-tier pickaxe", "ancient_debris", 16, homeX, homeY, homeZ));
            steps.add(step("netherite-netherrack", Action.MINE, "Gather 3 netherrack for template duplication", "netherrack", 3, homeX, homeY, homeZ));
            steps.add(step("netherite-return", Action.DIMENSION, "Return to the Overworld through a verified portal", "overworld", 1, homeX, homeY, homeZ));
            steps.add(step("netherite-scrap", Action.CRAFT, "Smelt 16 ancient debris into netherite scrap", "smelt-debris", 16, homeX, homeY, homeZ));
            steps.add(step("netherite-ingots", Action.CRAFT, "Craft four netherite ingots", "netherite-ingots", 4, homeX, homeY, homeZ));
            steps.add(step("netherite-armor", Action.CRAFT, "Duplicate missing templates and upgrade four armor pieces at a smithing table", "netherite-armor", 4, homeX, homeY, homeZ));
            steps.add(step("netherite-equip", Action.EQUIP, "Equip and verify four netherite armor pieces", "netherite-armor", 4, homeX, homeY, homeZ));
            prerequisites.add("One Netherite Upgrade template must already be owned. Bastion discovery and looting are not assumed; the first template stage stays incomplete until supplied.");
            prerequisites.add("Portal construction uses 10 owned obsidian, 13 cobblestone corner/stair supports and flint and steel, or crafts the igniter from an owned flint and iron ingot. Nether navigation, debris mining and portal use need reachable loaded terrain.");
            prerequisites.add("Four armor upgrades cost 16 scrap, 16 gold and four templates. Missing templates are duplicated with 7 diamonds + 1 netherrack each; real smithing output must be verified.");
        }
        List<LocalTaskIntent.Gather> requestedGathers = parsed.gathers();
        String gatheredDimension = "";
        for (int gatherIndex = 0; gatherIndex < requestedGathers.size(); gatherIndex++) {
                var resource = requestedGathers.get(gatherIndex); int count = resource.count(); String key = resource.resource();
                var spec = ResourceCatalog.get(key);
                String suffix = gatherIndex == 0 ? "" : "-" + gatherIndex;
                if (!spec.dimension().isEmpty() && !spec.dimension().equals(gatheredDimension)) {
                    steps.add(step("resource-dimension" + suffix, Action.DIMENSION, "Use " + dimensionLabel(spec.dimension()) + " for natural " + key.replace('_', ' ') + " deposits", spec.dimension(), 1, homeX, homeY, homeZ));
                    portalPrerequisites(prerequisites, spec.dimension());
                    gatheredDimension = spec.dimension();
                }
                steps.add(step("resource" + suffix, Action.MINE, "Have " + count + " " + key.replace('_', ' ') + " in inventory", key, count, homeX, homeY, homeZ));
        }
        if (!requestedGathers.isEmpty()) {
            prerequisites.add("Gather quantities are total inventory targets, not extra items: existing stock counts toward at least the reviewed amount. Extra owned items are kept, and real loot can exceed a target.");
            prerequisites.add("Missing or worn pickaxes trigger ordinary wood, stone, iron and diamond tool recovery. Inaccessible materials can pause this prerequisite queue; existing usable tools are credited.");
            prerequisites.add("Enough free inventory space and safe reachable sources are required. Sand and gravel are harvested only where falling blocks cannot create an unsafe ceiling collapse.");
        }
        if (!dimension.isEmpty() && !netherite) {
            if (dimension.equals("the_nether") && !kit && !shelter) {
                steps.add(step("portal-logs", Action.MINE, "Gather eight logs for portal preparation", "logs", 8, homeX, homeY, homeZ));
                steps.add(step("portal-bootstrap", Action.CRAFT, "Prepare a crafting table and mining tools", "bootstrap", 1, homeX, homeY, homeZ));
                steps.add(step("portal-obsidian", Action.MINE, "Gather 10 obsidian with a recovered diamond-tier pickaxe", "obsidian", 10, homeX, homeY, homeZ));
                steps.add(step("portal-furnace-material", Action.MINE, "Gather 21 cobblestone for a furnace and portal supports", "cobblestone", 21, homeX, homeY, homeZ));
                steps.add(step("portal-furnace", Action.CRAFT, "Prepare a furnace for the portal igniter", "stone-tools", 1, homeX, homeY, homeZ));
                steps.add(step("portal-iron", Action.MINE, "Gather one additional raw iron for portal ignition", "raw_iron", 1, homeX, homeY, homeZ));
                steps.add(step("portal-coal", Action.MINE, "Gather four coal for portal preparation", "coal", 4, homeX, homeY, homeZ));
                steps.add(step("portal-iron-smelt", Action.CRAFT, "Smelt the portal igniter's iron ingot", "smelt-extra-iron", 1, homeX, homeY, homeZ));
                steps.add(step("portal-flint", Action.MINE, "Collect one actual flint drop from gravel", "flint", 1, homeX, homeY, homeZ));
                steps.add(step("portal-igniter", Action.CRAFT, "Craft and verify flint and steel", "portal-kit", 1, homeX, homeY, homeZ));
                steps.add(step("portal-supports", Action.MINE, "Verify 13 cobblestone for the portal supports", "cobblestone", 13, homeX, homeY, homeZ));
                prerequisites.add("Portal preparation recovers missing pickaxes and gathers actual flint drops. Gravel can be placed and broken again; random drops and inaccessible terrain can still pause this goal.");
            }
            steps.add(step("dimension", Action.DIMENSION, "Travel to " + dimensionLabel(dimension) + " through a real portal", dimension, 1, homeX, homeY, homeZ));
            portalPrerequisites(prerequisites, dimension);
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("Try: escape spawn, build a base, get diamond/netherite armor, enchant my armor, go to the Nether, go to X Y Z, or mine 32 diamonds.");
        String title = netherite ? (enchant ? "Enchanted netherite armor" : "Netherite armor expedition") : expedition || (escape && shelter && enchant) ? "Spawn-to-kit expedition" : enchant ? "Enchanted diamond kit"
                : kit ? "Diamond kit" : shelter ? "Starter shelter" : escape ? "Escape spawn" : steps.size() > 1 ? "Travel and gather" : steps.getFirst().label();
        return new Plan(title, "A local, reviewable plan using ordinary Minecraft actions and Analyzer's native navigation.", steps, prerequisites);
    }

    private static String requestedDimension(String text) {
        if (!Pattern.compile("\\b(go|travel|enter|return|visit|walk|move)\\b").matcher(text).find()) return "";
        if (Pattern.compile("\\b(end|the_end)\\b").matcher(text).find()) return "the_end";
        if (Pattern.compile("\\b(nether|the_nether)\\b").matcher(text).find()) return "the_nether";
        if (text.contains("overworld")) return "overworld";
        return "";
    }
    private static String dimensionLabel(String dimension) { return switch (dimension) { case "the_nether" -> "the Nether"; case "the_end" -> "the End"; default -> "the Overworld"; }; }
    private static void portalPrerequisites(List<String> prerequisites, String dimension) {
        if (dimension.equals("the_end")) prerequisites.add("The End requires a reachable loaded stronghold portal or activated End portal. Owned eyes can fill a complete frame; no stronghold teleport or world scan is available.");
        else prerequisites.add("Use a reachable loaded Nether portal. A new frame needs 10 obsidian, 13 cobblestone support blocks, flint and steel, and a checked clear solid site; returning from the End requires a real exit portal.");
        prerequisites.add("The task accepts only its expected dimension transition. Disconnects, unrelated worlds and unexpected portal destinations pause for review.");
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
