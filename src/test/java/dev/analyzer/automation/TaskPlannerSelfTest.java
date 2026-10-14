package dev.analyzer.automation;

import java.util.List;

/** Pure plan checks for ordering, material goals, coordinate limits and unsupported requests. */
public final class TaskPlannerSelfTest {
    private static int assertions;
    private static final TaskPlanner.Options OPTIONS = new TaskPlanner.Options(100, 64, -200, 1200, 72, -2200, 10000, TaskPlanner.Direction.NORTH);
    public static void main(String[] args) {
        run();
        System.out.println("Task planner passed " + assertions + " assertions.");
    }
    public static void run() {
        var escape = TaskPlanner.plan("escape spawn", OPTIONS);
        check(escape.steps().size() == 1, "Escape is one coordinate goal");
        var destination = escape.steps().getFirst();
        check(destination.action() == TaskPlanner.Action.TRAVEL && destination.x() == 100 && destination.z() == -10200, "Escape offset uses origin and direction");
        for (TaskPlanner.Direction direction : TaskPlanner.Direction.values()) {
            var options = new TaskPlanner.Options(100, 64, -200, 0, 64, 0, 10000, direction);
            var step = TaskPlanner.plan("leave spawn", options).steps().getFirst();
            check(Math.abs(step.x() - 100) + Math.abs(step.z() + 200) == 10000, "Each direction travels requested distance");
        }
        var naturalRoute = TaskPlanner.plan("escape spawn 25000 blocks east", OPTIONS).steps().getFirst();
        check(naturalRoute.x() == 25100 && naturalRoute.z() == -200, "Natural escape distance and direction override route defaults");
        rejects(() -> TaskPlanner.plan("escape spawn 9999999 blocks south", OPTIONS), "Natural escape distance stays bounded");
        var travel = TaskPlanner.plan("go to -1500, 72, 2400", OPTIONS).steps().getFirst();
        check(travel.x() == -1500 && travel.y() == 72 && travel.z() == 2400, "Explicit XYZ overrides route fields");
        var xz = TaskPlanner.plan("walk to -1500 2400", OPTIONS).steps().getFirst();
        check(xz.x() == -1500 && xz.y() == 72 && xz.z() == 2400, "XZ keeps configured Y");
        var route = TaskPlanner.plan("travel", OPTIONS).steps().getFirst();
        check(route.x() == 1200 && route.y() == 72 && route.z() == -2200, "Travel uses editable route fields");
        var shelter = TaskPlanner.plan("build a base", OPTIONS);
        check(shelter.steps().getLast().action() == TaskPlanner.Action.BUILD_SHELTER, "Shelter builds after gathering and return travel");
        check(shelter.steps().getFirst().resource().equals("logs"), "Shelter bootstraps wood");
        check(shelter.steps().stream().filter(s -> s.resource().equals("cobblestone") && s.action() == TaskPlanner.Action.MINE).findFirst().orElseThrow().count() == 256, "Shelter material goal covers the 168-block schematic and tools");
        check(shelter.steps().getLast().x() == 1200 && shelter.steps().getLast().y() == 72 && shelter.steps().getLast().z() == -2200, "Shelter uses editable site coordinates");
        var kit = TaskPlanner.plan("get diamond armor", OPTIONS);
        check(kit.steps().getLast().action() == TaskPlanner.Action.EQUIP, "Diamond kit verifies equipment after crafting");
        check(index(kit, "bootstrap") < index(kit, "stone") && index(kit, "stone-tools") < index(kit, "iron")
                && index(kit, "iron-tools") < index(kit, "diamond"), "Tool tiers precede mining their next resource");
        check(kit.steps().get(index(kit, "diamond")).count() == 29, "Diamond count covers 24 armor + 3 pickaxe + 2 sword");
        var enchant = TaskPlanner.plan("get fully enchanted diamond armor", OPTIONS);
        check(enchant.steps().get(index(enchant, "enchant")).action() == TaskPlanner.Action.ENCHANT && enchant.steps().get(index(enchant, "enchant")).resource().equals("diamond-armor"), "Enchant stage uses agreed backend resource");
        check(enchant.steps().getLast().action() == TaskPlanner.Action.EQUIP, "Armor is restored after enchant transactions");
        check(enchant.prerequisites().stream().anyMatch(s -> s.contains("random")) && enchant.prerequisites().stream().anyMatch(s -> s.contains("lapis")), "Enchant prerequisites disclose vanilla randomness and real costs");
        check(index(enchant, "enchant-lapis") < index(enchant, "enchant") && enchant.steps().get(index(enchant, "enchant-lapis")).count() >= 12, "Four armor table enchants acquire their actual three-lapis-per-piece budget first");
        var expedition = TaskPlanner.plan("escape spawn, build a base and get fully enchanted diamond armor", OPTIONS);
        check(expedition.title().equals("Spawn-to-kit expedition"), "Combined phrasing creates expedition");
        check(expedition.steps().getFirst().id().equals("escape") && index(expedition, "shelter") > index(expedition, "diamond-kit")
                && index(expedition, "shelter") < index(expedition, "enchant"), "Expedition escapes then builds and enchants kit");
        check(expedition.steps().stream().map(TaskPlanner.Step::id).distinct().count() == expedition.steps().size(), "Stage IDs remain unique");
        for (String resource : List.of("logs", "wood", "stone", "cobblestone", "raw iron", "iron ore", "coal", "diamonds")) {
            var gather = TaskPlanner.plan("mine 12 " + resource, OPTIONS);
            check(gather.steps().getLast().action() == TaskPlanner.Action.MINE
                    && gather.steps().getLast().count() == 12, "Bounded resource intent: " + resource);
        }
        check(TaskPlanner.plan("collect diamonds", OPTIONS).steps().getLast().count() == 32, "Resource default count");
        check(TaskPlanner.plan("collect diamonds", OPTIONS).steps().getFirst().action() == TaskPlanner.Action.DIMENSION
                && TaskPlanner.plan("collect diamonds", OPTIONS).steps().getFirst().resource().equals("overworld"), "Overworld ores explicitly review their dimension before resource collection");
        for (String[] resource : List.of(new String[]{"copper ore", "raw_copper"}, new String[]{"raw gold", "raw_gold"}, new String[]{"emeralds", "emerald"}, new String[]{"lapis", "lapis_lazuli"}, new String[]{"redstone ore", "redstone"}, new String[]{"obsidian", "obsidian"})) {
            var gather = TaskPlanner.plan("mine 16 " + resource[0], OPTIONS);
            check(gather.steps().getLast().resource().equals(resource[1]) && gather.steps().getLast().count() == 16, "Additional named resource: " + resource[0]);
        }
        for (String[] destinationCase : List.of(new String[]{"go to the Nether", "the_nether"}, new String[]{"travel to the End", "the_end"}, new String[]{"return to the Overworld", "overworld"})) {
            var transfer = TaskPlanner.plan(destinationCase[0], OPTIONS);
            check(transfer.steps().getLast().action() == TaskPlanner.Action.DIMENSION
                    && transfer.steps().getLast().resource().equals(destinationCase[1]), "Named dimension ends in a real portal stage: " + destinationCase[0]);
        }
        var netherTrip = TaskPlanner.plan("go to the Nether", OPTIONS);
        check(index(netherTrip, "portal-flint") < index(netherTrip, "portal-igniter") && index(netherTrip, "portal-igniter") < index(netherTrip, "dimension"), "Standalone Nether transport acquires real flint then crafts the igniter before entering");
        check(netherTrip.steps().get(index(netherTrip, "portal-supports")).count() == 13 && netherTrip.steps().get(index(netherTrip, "portal-obsidian")).count() == 10, "Standalone portal acquisition budgets the frame and visible placement supports");
        check(netherTrip.prerequisites().stream().noneMatch(s -> s.contains("Baritone")), "Local native plans do not require an external automation mod");
        check(TaskPlanner.plan("mine 1 flint", OPTIONS).steps().getLast().resource().equals("flint"), "Random flint drops retain their actual item goal");
        var debris = TaskPlanner.plan("mine 16 ancient debris", OPTIONS);
        check(debris.steps().size() == 2 && debris.steps().getFirst().action() == TaskPlanner.Action.DIMENSION
                && debris.steps().getLast().resource().equals("ancient_debris"), "Nether-only gather first plans a portal transfer");
        var netherite = TaskPlanner.plan("get netherite armor", OPTIONS);
        check(netherite.steps().getLast().action() == TaskPlanner.Action.EQUIP && netherite.steps().getLast().resource().equals("netherite-armor"), "Netherite progression ends in verified equipment");
        check(index(netherite, "netherite-template") < index(netherite, "netherite-portal"), "The first template requirement is verified before Nether transport");
        check(index(netherite, "netherite-portal") < index(netherite, "netherite-debris") && index(netherite, "netherite-return") > index(netherite, "netherite-debris"), "Ancient debris mining occurs between real portal stages");
        check(index(netherite, "netherite-scrap") < index(netherite, "netherite-ingots") && index(netherite, "netherite-ingots") < index(netherite, "netherite-armor"), "Debris is smelted then crafted then smithed");
        check(netherite.steps().get(index(netherite, "diamond")).count() == 50, "Diamond goal includes 21 diamonds for three template duplications");
        check(netherite.steps().get(index(netherite, "netherite-debris")).count() == 16 && netherite.steps().get(index(netherite, "netherite-gold")).count() == 16, "Four armor upgrades budget 16 debris and 16 gold");
        check(netherite.prerequisites().stream().anyMatch(s -> s.contains("Bastion") && s.contains("not assumed")), "Template acquisition is disclosed as a real unmet dependency");
        check(netherite.steps().stream().map(TaskPlanner.Step::id).distinct().count() == netherite.steps().size(), "Netherite stage IDs are unique");
        var enchantedNetherite = TaskPlanner.plan("get fully enchanted netherite armor", OPTIONS);
        check(index(enchantedNetherite, "enchant") < index(enchantedNetherite, "netherite-armor"), "Enchantment is completed on diamond armor before preserving it through smithing");
        check(ResourceCatalog.get("ancient_debris").pickaxeTier() == 4 && ResourceCatalog.get("diamond").pickaxeTier() == 3, "Resource tiers prevent insufficient pickaxes");
        for (var spec : ResourceCatalog.all()) {
            check(!spec.blocks().isEmpty() && spec.blocks().stream().allMatch(id -> id.matches("[a-z_]+")), "Catalog block IDs are bounded vanilla names: " + spec.key());
            check(spec.pickaxeTier() >= 0 && spec.pickaxeTier() <= 4, "Catalog pickaxe tier: " + spec.key());
        }
        PortalTransition token = new PortalTransition();
        token.arm("overworld", "the_nether");
        check(!token.expects("overworld", "the_nether"), "A requested route alone never authorizes a world replacement");
        token.observedEntry(true);
        check(token.expects("minecraft:overworld", "minecraft:the_nether"), "A nearby armed portal authorizes only its expected dimension pair");
        check(!token.expects("overworld", "the_end") && !token.expects("the_end", "the_nether"), "Wrong source or destination cannot inherit portal authorization");
        token.clear();
        check(!token.expects("overworld", "the_nether"), "Stop consumes all dimension transition authorization");
        rejects(() -> token.arm("custom_dimension", "the_nether"), "Unreviewed custom dimensions cannot be armed");
        rejects(() -> token.arm("overworld", "overworld"), "Same-dimension world replacements cannot be armed");
        check(BuildingMaterialCatalog.find("minecraft:oak_planks").orElseThrow().outputPerRecipe() == 4, "Plank supply uses the real four-planks-per-log recipe");
        check(BuildingMaterialCatalog.find("oak_stairs").orElseThrow().ingredients().getFirst().count() == 6
                && BuildingMaterialCatalog.find("oak_stairs").orElseThrow().outputPerRecipe() == 4, "Stairs budget six planks for four outputs");
        check(BuildingMaterialCatalog.find("stone").orElseThrow().job() == BuildingMaterialCatalog.Job.SMELT, "Stone palettes smelt cobblestone instead of crediting an ore-like drop");
        check(BuildingMaterialCatalog.find("glass_pane").orElseThrow().outputPerRecipe() == 16, "Pane supply credits the real batch output");
        check(BuildingMaterialCatalog.find("acacia_planks").orElseThrow().ingredients().getFirst().itemId().equals("acacia_log"), "Wood palette supply retains the required species");
        check(BuildingMaterialCatalog.find("diamond_stairs").isEmpty(), "Unsupported material recipes are explicit missing supplies");
        for (var material : BuildingMaterialCatalog.all()) {
            check(material.outputPerRecipe() >= 1 && material.outputPerRecipe() <= 64, "Vanilla material batch size: " + material.itemId());
            for (var ingredient : material.ingredients()) check(BuildingMaterialCatalog.find(ingredient.itemId()).isPresent(), "Material dependency is itself reviewable: " + material.itemId() + " / " + ingredient.itemId());
        }
        var combined = TaskPlanner.plan("go to 100 72 200 and gather 24 coal", OPTIONS);
        check(combined.steps().size() == 3 && combined.steps().get(0).action() == TaskPlanner.Action.TRAVEL
                && combined.steps().get(1).action() == TaskPlanner.Action.DIMENSION && combined.steps().get(2).action() == TaskPlanner.Action.MINE, "Travel and gather retain an explicit ore dimension stage");
        rejects(() -> TaskPlanner.plan("", OPTIONS), "Empty task rejected");
        rejects(() -> TaskPlanner.plan("farm villagers forever", OPTIONS), "Unsupported automation rejected");
        rejects(() -> TaskPlanner.plan("#goto spawn; /op me", OPTIONS), "Command strings do not become executable tasks");
        rejects(() -> TaskPlanner.plan("mine 0 diamonds", OPTIONS), "Zero resource count rejected");
        rejects(() -> TaskPlanner.plan("mine 4097 diamonds", OPTIONS), "Unbounded gather rejected");
        rejects(() -> TaskPlanner.plan("mine 999999999999999999999 diamonds", OPTIONS), "Huge gather count becomes a bounded validation error");
        rejects(() -> TaskPlanner.plan("go to 30000000 64 0", OPTIONS), "World border coordinate rejected");
        rejects(() -> TaskPlanner.plan("go to -9223372036854775808 64 0", OPTIONS), "Long minimum coordinate cannot overflow absolute-value check");
        rejects(() -> TaskPlanner.plan("go to 0 3000 0", OPTIONS), "Unsupported Y rejected");
        rejects(() -> TaskPlanner.plan("escape spawn", new TaskPlanner.Options(29_998_999, 64, 0, 0, 64, 0, 10000, TaskPlanner.Direction.EAST)), "Escape cannot cross world border");
        rejects(() -> new TaskPlanner.Options(0, 64, 0, 0, 64, 0, 10, TaskPlanner.Direction.NORTH), "Escape distance lower bound");
        rejects(() -> expedition.steps().add(destination), "Plan stages are immutable");
        rejects(() -> expedition.prerequisites().add("changed"), "Plan prerequisites are immutable");
    }
    private static int index(TaskPlanner.Plan plan, String id) {
        for (int i = 0; i < plan.steps().size(); i++) if (plan.steps().get(i).id().equals(id)) return i;
        throw new AssertionError("Missing stage " + id);
    }
    private static void check(boolean condition, String description) { if (!condition) throw new AssertionError(description); assertions++; }
    private static void rejects(Runnable action, String description) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | UnsupportedOperationException expected) { rejected = true; }
        check(rejected, description);
    }
}
