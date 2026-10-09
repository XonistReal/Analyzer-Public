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
        var expedition = TaskPlanner.plan("escape spawn, build a base and get fully enchanted diamond armor", OPTIONS);
        check(expedition.title().equals("Spawn-to-kit expedition"), "Combined phrasing creates expedition");
        check(expedition.steps().getFirst().id().equals("escape") && index(expedition, "shelter") > index(expedition, "diamond-kit")
                && index(expedition, "shelter") < index(expedition, "enchant"), "Expedition escapes then builds and enchants kit");
        check(expedition.steps().stream().map(TaskPlanner.Step::id).distinct().count() == expedition.steps().size(), "Stage IDs remain unique");
        for (String resource : List.of("logs", "wood", "stone", "cobblestone", "raw iron", "iron ore", "coal", "diamonds")) {
            var gather = TaskPlanner.plan("mine 12 " + resource, OPTIONS);
            check(gather.steps().size() == 1 && gather.steps().getFirst().action() == TaskPlanner.Action.MINE
                    && gather.steps().getFirst().count() == 12, "Bounded resource intent: " + resource);
        }
        check(TaskPlanner.plan("collect diamonds", OPTIONS).steps().getFirst().count() == 32, "Resource default count");
        var combined = TaskPlanner.plan("go to 100 72 200 and gather 24 coal", OPTIONS);
        check(combined.steps().size() == 2 && combined.steps().get(0).action() == TaskPlanner.Action.TRAVEL
                && combined.steps().get(1).action() == TaskPlanner.Action.MINE, "Travel and gather combine in order");
        rejects(() -> TaskPlanner.plan("", OPTIONS), "Empty task rejected");
        rejects(() -> TaskPlanner.plan("farm villagers forever", OPTIONS), "Unsupported automation rejected");
        rejects(() -> TaskPlanner.plan("#goto spawn; /op me", OPTIONS), "Command strings do not become executable tasks");
        rejects(() -> TaskPlanner.plan("mine 0 diamonds", OPTIONS), "Zero resource count rejected");
        rejects(() -> TaskPlanner.plan("mine 4097 diamonds", OPTIONS), "Unbounded gather rejected");
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
