package dev.analyzer.automation;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.Locale;

/** Optional API-only integration. The typed adapter is loaded only after the Fabric mod guard. */
public final class BaritoneBridge {
    public enum State { UNAVAILABLE, IDLE, RUNNING, PAUSED, COMPLETE, FAILED, INTERRUPTED }
    public record Status(State state, String process, String message, double progress, boolean owned) {
        public boolean active() { return state == State.RUNNING || state == State.PAUSED; }
        public boolean complete() { return state == State.COMPLETE; }
        public boolean failed() { return state == State.FAILED || state == State.INTERRUPTED; }
        public boolean paused() { return state == State.PAUSED; }
    }
    public static final int SHELTER_MATERIAL_COUNT = 168;
    private Adapter adapter;
    private boolean initializationFailed;
    private Status status = new Status(State.UNAVAILABLE, "", "Install the official Baritone Fabric API 1.13.1 mod", 0, false);

    public boolean available() {
        if (!FabricLoader.getInstance().isModLoaded("baritone") || initializationFailed) return false;
        if (adapter == null) {
            try {
                adapter = new Adapter(this);
                status = new Status(State.IDLE, "", "Baritone ready", 0, false);
            } catch (RuntimeException | LinkageError ex) {
                initializationFailed = true;
                status = new Status(State.UNAVAILABLE, "", "Baritone API could not initialize: " + ex.getClass().getSimpleName(), 0, false);
            }
        }
        return adapter != null;
    }
    public Status status() { available(); return status; }
    public void tick() { if (available()) adapter.tick(); }
    public boolean travelXZ(int x, int z) { return available() && adapter.travel(x, null, z); }
    public boolean travelXYZ(int x, int y, int z) { return available() && adapter.travel(x, y, z); }
    /** Quantity means the total matching inventory count, as in the public Baritone mine API. */
    public boolean mineByName(int quantity, String... names) { return available() && adapter.mine(quantity, names); }
    public boolean buildShelter(BlockPos origin, String materialId) { return available() && adapter.build(origin, materialId); }
    public boolean pause() { return available() && adapter.pause(); }
    public boolean resume() { return available() && adapter.resume(); }
    public boolean cancel() { return available() && adapter.cancel("Analyzer task canceled"); }
    public void clearStatus() {
        if (available() && !adapter.owned()) status = new Status(State.IDLE, "", "Baritone ready", 0, false);
    }

    private static final class Adapter {
        private enum Kind { TRAVEL, MINE, BUILD }
        private final BaritoneBridge bridge;
        private final MinecraftClient mc = MinecraftClient.getInstance();
        private final baritone.api.IBaritone engine;
        private final PauseProcess pauser;
        private baritone.api.process.IBaritoneProcess owner;
        private baritone.api.pathing.goals.Goal goal;
        private baritone.api.utils.BlockOptionalMetaLookup mineFilter;
        private ShelterSchematic shelter;
        private BlockPos origin;
        private Object world;
        private Kind kind;
        private int quantity, ticks, failures;
        private double initialDistance;
        private String signature = "", title = "", pathEvent = "";
        private boolean paused;

        Adapter(BaritoneBridge bridge) {
            this.bridge = bridge;
            engine = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
            if (engine == null) throw new IllegalStateException("No primary Baritone");
            pauser = new PauseProcess();
            engine.getPathingControlManager().registerProcess(pauser);
            engine.getGameEventHandler().registerEventListener(new baritone.api.event.listener.AbstractGameEventListener() {
                @Override public void onPathEvent(baritone.api.event.events.PathEvent event) {
                    if (owner == null) return;
                    pathEvent = event.name();
                    if (event == baritone.api.event.events.PathEvent.CALC_FAILED) failures++;
                }
                @Override public void onSendChatMessage(baritone.api.event.events.ChatEvent event) {
                    if (owner == null) return;
                    String message = event.getMessage().strip();
                    var settings = baritone.api.BaritoneAPI.getSettings();
                    String prefix = settings.prefix.value;
                    boolean prefixed = settings.prefixControl.value && !prefix.isEmpty() && message.startsWith(prefix);
                    String commandText = prefixed ? message.substring(prefix.length()).strip() : message;
                    String head = commandText.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                    boolean direct = (settings.chatControl.value || settings.chatControlAnyway.value)
                            && engine.getCommandManager().getCommand(head) != null;
                    // Looking up help or an ETA does not turn an owned moving task into an orphan.
                    boolean observation = java.util.Set.of("help", "?", "version", "eta", "proc", "paused", "find", "render",
                            "gc", "saveall", "repack", "rescan", "modified", "mod", "baritone", "modifiedsettings",
                            "set", "setting", "settings", "reset", "sethome").contains(head);
                    if ((prefixed || direct) && !observation) yieldToManual("Manual Baritone command took control");
                }
            });
        }

        private boolean owned() { return owner != null; }
        private java.util.List<baritone.api.process.IBaritoneProcess> processes() {
            return java.util.List.of(engine.getCustomGoalProcess(), engine.getMineProcess(), engine.getBuilderProcess(),
                    engine.getFollowProcess(), engine.getExploreProcess(), engine.getFarmProcess(),
                    engine.getGetToBlockProcess(), engine.getElytraProcess());
        }
        private boolean anotherProcessActive() {
            for (var process : processes()) if (process != owner && process.isActive()) return true;
            var controlling = engine.getPathingControlManager().mostRecentInControl().orElse(null);
            return controlling != null && controlling != owner && controlling != pauser && controlling.isActive();
        }
        private boolean canStart() {
            if (mc.player == null || mc.world == null || mc.player.isDead()) return fail("Join a world with a living player first");
            if (owner != null) return fail("Pause or cancel the current Analyzer task before starting another");
            if (anotherProcessActive() || engine.getPathingBehavior().isPathing() || engine.getPathingBehavior().getInProgress().isPresent())
                return fail("Baritone is already running a manual task; Analyzer did not replace it");
            return true;
        }
        private boolean fail(String message) {
            bridge.status = owner == null ? new Status(State.FAILED, "", message, 0, false)
                    : new Status(paused ? State.PAUSED : State.RUNNING, title, message, bridge.status.progress(), true);
            return false;
        }
        private void begin(Kind kind, baritone.api.process.IBaritoneProcess process, String title) {
            this.kind = kind; owner = process; this.title = title; world = mc.world;
            signature = process.isActive() ? process.displayName() : "";
            ticks = failures = 0; paused = false; pathEvent = "";
            bridge.status = new Status(State.RUNNING, title, "Baritone is calculating a path", 0, true);
        }
        private boolean travel(int x, Integer y, int z) {
            if (Math.abs((long) x) > 29_999_984 || Math.abs((long) z) > 29_999_984) return fail("Travel coordinates are outside the world border limit");
            if (!canStart()) return false;
            if (y != null && (y < mc.world.getBottomY() || y >= mc.world.getTopYInclusive() + 1)) return fail("Travel Y is outside this dimension's build height");
            goal = y == null ? new baritone.api.pathing.goals.GoalXZ(x, z) : new baritone.api.pathing.goals.GoalBlock(x, y, z);
            initialDistance = distanceToGoal(x, y, z);
            var process = engine.getCustomGoalProcess();
            begin(Kind.TRAVEL, process, y == null ? "Travel X/Z" : "Travel X/Y/Z");
            process.setGoalAndPath(goal);
            signature = process.displayName();
            tick();
            return true;
        }
        private double distanceToGoal(int x, Integer y, int z) {
            double dx = mc.player.getX() - (x + .5), dz = mc.player.getZ() - (z + .5);
            double dy = y == null ? 0 : mc.player.getY() - y;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        private boolean mine(int quantity, String[] names) {
            if (quantity < 1 || quantity > 4096 || names == null || names.length < 1 || names.length > 16) return fail("Use 1 to 4096 items and 1 to 16 block IDs");
            if (!canStart()) return false;
            String[] normalized = new String[names.length];
            for (int i = 0; i < names.length; i++) {
                Identifier id = names[i] == null ? null : Identifier.tryParse(names[i]);
                if (id == null || !Registries.BLOCK.containsId(id) || Registries.BLOCK.get(id).getDefaultState().isAir()) return fail("Unknown mining block: " + names[i]);
                normalized[i] = id.toString();
            }
            try { mineFilter = new baritone.api.utils.BlockOptionalMetaLookup(normalized); }
            catch (RuntimeException ex) { return fail("Baritone could not resolve the requested mining blocks"); }
            this.quantity = quantity;
            var process = engine.getMineProcess();
            begin(Kind.MINE, process, "Mine " + normalized[0]);
            if (matchingInventory() >= quantity) { finish(State.COMPLETE, "Inventory already contains the requested quantity", 1); return true; }
            process.mineByName(quantity, normalized);
            signature = process.displayName();
            return true;
        }
        private int matchingInventory() {
            int count = 0;
            if (mineFilter == null || mc.player == null) return 0;
            var inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                var stack = inventory.getStack(i);
                if (!stack.isEmpty() && mineFilter.has(stack)) count += stack.getCount();
            }
            return count;
        }
        private boolean build(BlockPos origin, String materialId) {
            if (origin == null) return fail("Choose the shelter origin first");
            if (!canStart()) return false;
            Identifier id = materialId == null ? null : Identifier.tryParse(materialId);
            if (id == null || !Registries.BLOCK.containsId(id)) return fail("Unknown shelter material");
            var material = Registries.BLOCK.get(id).getDefaultState();
            if (!material.isFullCube(mc.world, origin) || material.hasBlockEntity() || !material.getFluidState().isEmpty()
                    || material.getBlock() instanceof net.minecraft.block.FallingBlock
                    || material.getHardness(mc.world, origin) < 0) return fail("Choose a stable, solid shelter block such as cobblestone or planks");
            shelter = new ShelterSchematic(material); this.origin = origin.toImmutable();
            String problem = shelter.siteProblem(mc.world, this.origin);
            if (!problem.isEmpty()) return fail(problem);
            int available = 0;
            var inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                var stack = inventory.getStack(i);
                if (stack.isOf(material.getBlock().asItem())) available += stack.getCount();
            }
            int missing = shelter.progress(mc.world, this.origin).missing();
            if (available < missing) return fail("The shelter needs " + missing + " more " + id.getPath() + " blocks; inventory has " + available);
            int hotbar = 0;
            for (int i = 0; i < 9; i++) {
                var stack = inventory.getStack(i);
                if (stack.isOf(material.getBlock().asItem())) hotbar += stack.getCount();
            }
            if (!baritone.api.BaritoneAPI.getSettings().allowInventory.value && hotbar < missing)
                return fail("Move " + missing + " " + id.getPath() + " blocks onto the hotbar; Baritone inventory access is disabled");
            var process = engine.getBuilderProcess();
            begin(Kind.BUILD, process, "Build starter shelter");
            process.build("Analyzer starter shelter", shelter, this.origin);
            signature = process.displayName();
            return true;
        }

        private boolean leaseValid() {
            if (owner == null) return false;
            if (anotherProcessActive()) { yieldToManual("Another Baritone process took control"); return false; }
            if (kind == Kind.TRAVEL && engine.getCustomGoalProcess().mostRecentGoal() != goal) { yieldToManual("Baritone travel goal changed outside Analyzer"); return false; }
            if (owner.isActive() && !signature.isEmpty() && !owner.displayName().equals(signature) && !(kind == Kind.BUILD && engine.getBuilderProcess().isPaused())) { yieldToManual("Baritone process changed outside Analyzer"); return false; }
            return true;
        }
        private void yieldToManual(String message) {
            paused = false; owner = null;
            bridge.status = new Status(State.INTERRUPTED, title, message, bridge.status.progress(), false);
        }
        private boolean pause() {
            if (!leaseValid()) return false;
            paused = true;
            bridge.status = new Status(State.PAUSED, title, "Paused; resume continues the current Baritone process", bridge.status.progress(), true);
            return true;
        }
        private boolean resume() {
            if (!leaseValid()) return false;
            paused = false;
            bridge.status = new Status(State.RUNNING, title, "Resuming the current Baritone process", bridge.status.progress(), true);
            return true;
        }
        private boolean cancel(String message) {
            if (!leaseValid()) return false;
            stopOwned();
            bridge.status = new Status(State.IDLE, title, message, 0, false);
            return true;
        }
        private void stopOwned() {
            paused = false;
            if (owner != null) {
                // The lease and competing processes are checked before this method. Never forceCancel.
                owner.onLostControl();
                if (!anotherProcessActive()) engine.getPathingBehavior().cancelEverything();
            }
            owner = null;
        }
        private void finish(State state, String message, double progress) {
            stopOwned();
            bridge.status = new Status(state, title, message, progress, false);
        }
        private void tick() {
            if (owner == null) return;
            if (mc.world != world || mc.player == null || mc.player.isDead()) {
                // World/death shutdown owns the old process; leave any replacement world's process alone.
                if (mc.world == world && !anotherProcessActive()) stopOwned();
                else { paused = false; owner = null; }
                bridge.status = new Status(State.INTERRUPTED, title, "World changed or player died; start the mission again explicitly", bridge.status.progress(), false);
                return;
            }
            if (!leaseValid()) return;
            ticks++;
            double progress = 0;
            String message = owner.isActive() ? owner.displayName() : "Baritone process stopped";
            boolean complete = false;
            if (kind == Kind.TRAVEL) {
                BlockPos pos = mc.player.getBlockPos();
                complete = goal.isInGoal(pos.getX(), pos.getY(), pos.getZ());
                if (goal instanceof baritone.api.pathing.goals.GoalXZ g) progress = 1 - distanceToGoal(g.getX(), null, g.getZ()) / Math.max(1, initialDistance);
                else if (goal instanceof baritone.api.pathing.goals.GoalBlock g) progress = 1 - distanceToGoal(g.x, g.y, g.z) / Math.max(1, initialDistance);
                message = "Traveling" + (pathEvent.isEmpty() ? "" : " / " + pathEvent.toLowerCase(Locale.ROOT).replace('_', ' '));
            } else if (kind == Kind.MINE) {
                int count = matchingInventory();
                progress = count / (double) quantity; complete = count >= quantity;
                message = "Inventory " + count + " / " + quantity + " matching items";
            } else if (kind == Kind.BUILD) {
                var p = shelter.progress(mc.world, origin);
                progress = p.fraction(); complete = p.complete();
                message = "Shelter " + p.placed() + " / " + p.required() + " blocks; " + p.matching() + " / " + p.volume() + " cells verified";
            }
            progress = Math.max(0, Math.min(1, progress));
            if (complete) { finish(State.COMPLETE, "Verified: " + message, 1); return; }
            // Give newly requested processes time to become active; inactive alone never proves success.
            if (ticks > 2 && !owner.isActive()) { finish(State.FAILED, message + "; target not completed" + (failures > 0 ? " after path calculation failed" : ""), progress); return; }
            bridge.status = new Status(paused ? State.PAUSED : State.RUNNING, title, paused ? "Paused / " + message : message, progress, true);
        }

        private final class PauseProcess implements baritone.api.process.IBaritoneProcess {
            @Override public boolean isActive() { return paused && owner != null; }
            @Override public boolean isTemporary() { return true; }
            @Override public double priority() { return 100; }
            @Override public String displayName0() { return "Analyzer mission paused"; }
            @Override public void onLostControl() { paused = false; }
            @Override public baritone.api.process.PathingCommand onTick(boolean calcFailed, boolean safe) {
                return new baritone.api.process.PathingCommand(null, baritone.api.process.PathingCommandType.REQUEST_PAUSE);
            }
        }
    }
}
