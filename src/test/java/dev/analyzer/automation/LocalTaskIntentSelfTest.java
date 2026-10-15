package dev.analyzer.automation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Concrete local request contracts: quantities, item names, scope and actual schematic files. */
public final class LocalTaskIntentSelfTest {
    private static final TaskPlanner.Options OPTIONS = new TaskPlanner.Options(0, 64, 0, 0, 64, 0, 10000, TaskPlanner.Direction.NORTH);
    private static int assertions;
    public static void main(String[] args) throws Exception {
        for (String phrase : List.of("get me 600 sand", "Please, get me 600 sand, thanks.", "could you please collect exactly 600 blocks of sand?", "I would like you to gather sand 600", "I'd like you to get me 600 sand", "can you please go and get me 600 sand", "fetch six hundred sand", "600 sand", "sand x600", "have 600 sand")) {
            var plan = TaskPlanner.plan(phrase, OPTIONS);
            check(plan.steps().size() == 1 && plan.steps().getFirst().resource().equals("sand") && plan.steps().getFirst().count() == 600, "600 sand is one exact inventory target: " + phrase);
            check(plan.prerequisites().stream().anyMatch(text -> text.contains("total inventory targets") && text.contains("existing stock")), "The review states total rather than additional item semantics");
        }
        for (String phrase : List.of("get just 37 diamonds", "please bring me diamonds 37", "collect thirty-seven diamonds", "I need thirty seven diamonds")) {
            var plan = TaskPlanner.plan(phrase, OPTIONS); var last = plan.steps().getLast();
            check(last.action() == TaskPlanner.Action.MINE && last.resource().equals("diamond") && last.count() == 37, "Specific diamonds keep their requested count: " + phrase);
            check(plan.steps().stream().noneMatch(step -> step.action() == TaskPlanner.Action.CRAFT || step.action() == TaskPlanner.Action.EQUIP || step.action() == TaskPlanner.Action.ENCHANT), "Diamonds alone never request an armor kit");
        }
        for (String phrase : List.of("get me 600 sand for my base", "get just 37 diamonds for enchanting my armor", "collect 37 diamonds to make diamond armor")) {
            var plan = TaskPlanner.plan(phrase, OPTIONS);
            check(plan.steps().stream().noneMatch(step -> step.action() == TaskPlanner.Action.CRAFT || step.action() == TaskPlanner.Action.EQUIP || step.action() == TaskPlanner.Action.ENCHANT || step.action() == TaskPlanner.Action.BUILD_SHELTER), "A gather purpose cannot silently add a base or gear task: " + phrase);
            check(plan.steps().getLast().count() == (phrase.contains("600") ? 600 : 37), "Purpose text preserves the actual material quantity");
        }
        var multiple = TaskPlanner.plan("please get me 64 sand, 20 dirt and 37 diamonds then gather 12 gravel", OPTIONS);
        var mines = multiple.steps().stream().filter(step -> step.action() == TaskPlanner.Action.MINE).toList();
        check(mines.size() == 4 && mines.get(0).count() == 64 && mines.get(1).count() == 20 && mines.get(2).count() == 37 && mines.get(3).count() == 12, "Several material clauses preserve all quantities and order");
        check(multiple.steps().stream().map(TaskPlanner.Step::id).distinct().count() == multiple.steps().size(), "Multiple gather and dimension stages have unique IDs");
        for (String[] named : List.of(new String[]{"red sand", "red_sand"}, new String[]{"soil", "dirt"}, new String[]{"coarse dirt", "coarse_dirt"}, new String[]{"rooted dirt", "rooted_dirt"}, new String[]{"clay balls", "clay_ball"}, new String[]{"oak logs", "oak_log"}, new String[]{"jungle logs", "jungle_log"}, new String[]{"deepslate", "cobbled_deepslate"}, new String[]{"soul sand", "soul_sand"})) {
            var plan = TaskPlanner.plan("collect 19 " + named[0], OPTIONS);
            check(plan.steps().getLast().resource().equals(named[1]) && plan.steps().getLast().count() == 19, "Named material resolves its actual inventory item: " + named[0]);
        }
        check(TaskPlanner.plan("get 1,000 sand", OPTIONS).steps().getLast().count() == 1000, "Thousands separators are quantities, not extra clauses");
        check(TaskPlanner.plan("get one hundred and twenty five sand", OPTIONS).steps().getLast().count() == 125, "Common spelled-out quantities remain local and exact");
        check(TaskPlanner.plan("get four thousand ninety six sand", OPTIONS).steps().getLast().count() == 4096, "The maximum count works as number words");
        for (String phrase : List.of("Could you get me 512 ice?", "would you mind getting me 512 blocks of ice?", "grab eight stacks of ice", "I need ice 512", "collect 512 minecraft:ice", "could you fetch five hundred and twelve ice for me?")) {
            var plan = TaskPlanner.plan(phrase, OPTIONS);
            check(plan.steps().size() == 1 && plan.steps().getLast().resource().equals("ice") && plan.steps().getLast().count() == 512, "Ice is understood through quantity and material rather than a preset phrase: " + phrase);
            check(plan.prerequisites().stream().anyMatch(text -> text.contains("Silk Touch")), "Ice review identifies its real drop requirement");
        }
        check(TaskPlanner.plan("get half a stack of sand", OPTIONS).steps().getLast().count() == 32, "Half-stack quantities are interpreted");
        check(TaskPlanner.plan("get a stack of ice", OPTIONS).steps().getLast().count() == 64, "One stack is 64 blocks");
        check(TaskPlanner.plan("get two stacks of buckets", OPTIONS).steps().getLast().count() == 32, "Stack quantities respect the actual sixteen-bucket stack size");
        rejects(() -> TaskPlanner.plan("get half a stack of bows", OPTIONS), "Unstackable items cannot silently produce fractional or zero targets");
        rejects(() -> TaskPlanner.plan("get -1 stacks of ice", OPTIONS), "A negative stack quantity cannot become a positive request");
        check(TaskPlanner.plan("get 64 minecraft:packed_ice", OPTIONS).steps().getLast().resource().equals("packed_ice"), "Qualified item IDs preserve compound material names");
        var supplied = TaskPlanner.plan("fetch 16 hoppers and 8 buckets", OPTIONS).steps();
        check(supplied.size() == 2 && supplied.stream().allMatch(step -> step.action() == TaskPlanner.Action.SUPPLY_MATERIAL), "Known crafted items use the material dependency planner");
        check(BuildingMaterialCatalog.find("bucket").orElseThrow().ingredients().equals(List.of(new BuildingMaterialCatalog.Ingredient("iron_ingot", 3))), "One reusable bucket has a real three-iron recipe");
        ResourceCatalog.registerItemNames(java.util.Map.of("potato", "Potato", "nether_star", "Nether Star"));
        var registryItem = TaskPlanner.plan("could you bring me 12 potatoes", OPTIONS);
        check(registryItem.steps().getLast().resource().equals("minecraft:potato") && registryItem.steps().getLast().count() == 12, "Registry names recognize real items independently of acquisition support");
        check(registryItem.prerequisites().stream().anyMatch(text -> text.contains("not implemented")), "Recognized unavailable acquisition is explained as a capability requirement");
        check(TaskPlanner.plan("obtain 2 nether stars", OPTIONS).steps().getLast().resource().equals("minecraft:nether_star"), "Several registry item words are consumed as one actual item");
        var commaRoute = TaskPlanner.plan("please go to -1500, 72, 2400 and collect 37 diamonds", OPTIONS);
        check(commaRoute.steps().getFirst().x() == -1500 && commaRoute.steps().getFirst().y() == 72 && commaRoute.steps().getFirst().z() == 2400,
                "Comma-separated coordinates remain one travel clause before material-list parsing");
        for (String phrase : List.of("get 0 sand", "get -1 sand", "get 4097 sand", "get 999999999999999999999 sand", "get 1.5 sand", "get lots of sand", "get a bunch of diamonds", "get 600 more sand", "get 10 sand and 20 sand", "get 10 fictionaliums", "get 10 sand and farm villagers forever", "get twenty thirty sand", "get 65 stacks of ice"))
            rejects(() -> TaskPlanner.plan(phrase, OPTIONS), "Invalid, ambiguous or unsupported requests are rejected: " + phrase);
        check(LocalTaskIntent.schematic("only build one schematic").orElseThrow().name().isBlank(), "One schematic requests the actual selected file or chooser");
        check(LocalTaskIntent.schematic("please build the selected schematic").orElseThrow().name().isBlank(), "Selected schematic requests reuse an actual previous selection");
        check(LocalTaskIntent.schematic("build one schematic called Castle").orElseThrow().name().equals("castle"), "A named schematic retains its local name");
        check(LocalTaskIntent.schematic("build ‘Castle.litematic’".replace('‘', '\'').replace('’', '\'')).orElseThrow().name().equals("castle.litematic"), "An explicit filename keeps its extension");
        check(LocalTaskIntent.schematic("build one schematic named castle.schematic").orElseThrow().name().equals("castle.schematic"), "Schematica file extensions are not stripped as task words");
        check(LocalTaskIntent.schematic("get 37 diamonds").isEmpty(), "Material requests do not become schematic builds");
        rejects(() -> LocalTaskIntent.schematic("build two schematics"), "A plural build must be reviewed one file at a time");
        rejects(() -> LocalTaskIntent.schematic("get 32 diamonds and build one schematic"), "Schematic requests cannot silently drop another explicit goal");
        rejects(() -> TaskPlanner.plan("only build one schematic", OPTIONS), "Pure planner requires an actual selected schematic rather than inventing a starter shelter");
        lookupContracts();
        System.out.println("Local task intent passed " + assertions + " assertions");
    }
    private static void lookupContracts() throws Exception {
        Path game = Files.createTempDirectory("analyzer-local-request-");
        try {
            Path root = Files.createDirectories(game.resolve("schematics")); Path castle = Files.write(root.resolve("Castle.litematic"), new byte[]{1});
            check(LocalSchematicLookup.find(game, "castle").unique().orElseThrow().equals(castle.toRealPath()), "A name resolves the actual case-insensitive file");
            check(LocalSchematicLookup.find(game, "Castle.litematic").unique().isPresent(), "An exact existing filename is accepted");
            check(LocalSchematicLookup.find(game, "missing").matches().isEmpty(), "Missing names are never fabricated as local paths");
            Path nested = Files.createDirectories(root.resolve("cities")); Files.write(nested.resolve("Castle.schem"), new byte[]{2});
            var ambiguous = LocalSchematicLookup.find(game, "castle");
            check(ambiguous.matches().size() == 2 && ambiguous.unique().isEmpty(), "Duplicate names require an actual chooser rather than guessing a folder");
            check(LocalSchematicLookup.find(game, "cities/castle").unique().isPresent(), "An actual library-relative name can disambiguate a subfolder");
            try { LocalSchematicLookup.find(game, "../outside"); throw new AssertionError("A parent path must not escape the library"); }
            catch (java.io.IOException expected) { assertions++; }
            Files.write(root.resolve("Castle.txt"), new byte[]{3}); check(LocalSchematicLookup.find(game, "castle").matches().size() == 2, "Unsupported files do not become build candidates");
            check(new LocalSchematicLookup.Resolution(List.of(castle), true).unique().isEmpty(), "An incomplete bounded search cannot claim a unique name");
        } finally {
            try (var paths = Files.walk(game)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
    private static void rejects(Runnable action, String detail) { assertions++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(detail); }
}
