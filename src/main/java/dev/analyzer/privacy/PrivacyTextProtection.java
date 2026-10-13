package dev.analyzer.privacy;

import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.DeprecatedLanguageData;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/** Original, source-scoped protection for remotely supplied translation and keybinding components. */
public final class PrivacyTextProtection {
    /** These are the actual Minecraft 1.21.4 GameOptions bindings, not a permissive key.* prefix. */
    private static final Set<String> VANILLA_BINDINGS = Set.of(
            "key.forward", "key.left", "key.back", "key.right", "key.jump", "key.sneak", "key.sprint",
            "key.inventory", "key.swapOffhand", "key.drop", "key.use", "key.attack", "key.pickItem",
            "key.chat", "key.playerlist", "key.command", "key.socialInteractions", "key.screenshot",
            "key.togglePerspective", "key.smoothCamera", "key.fullscreen", "key.spectatorOutlines",
            "key.advancements", "key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4",
            "key.hotbar.5", "key.hotbar.6", "key.hotbar.7", "key.hotbar.8", "key.hotbar.9",
            "key.saveToolbarActivator", "key.loadToolbarActivator");
    private static volatile Set<String> vanillaTranslations = Set.of(), serverTranslations = Set.of();
    private static volatile boolean initialized;
    private static final ThreadLocal<Map<String, Boolean>> LANGUAGE_ORIGINS = new ThreadLocal<>();
    private static final AtomicLong languageRevision = new AtomicLong(), blockedTranslations = new AtomicLong(), blockedKeybinds = new AtomicLong();

    private PrivacyTextProtection() { }

    /** Uses Minecraft's own default pack directly, bypassing mod and user-pack resource precedence. */
    public static synchronized void initialize(MinecraftClient client) {
        if (initialized || client == null || client.getDefaultResourcePack() == null) return;
        try {
            vanillaTranslations = Set.copyOf(loadVanillaTranslations(client).keySet());
            initialized = true;
            languageRevision.incrementAndGet();
        } catch (IOException | RuntimeException error) {
            AnalyzerClient.LOGGER.warn("Server text privacy could not load Minecraft's translation baseline; vanilla text remains readable", error);
        }
    }

    /** Match the vanilla loader's renamed/removed keys, including language compatibility aliases. */
    static Map<String, String> loadVanillaTranslations(MinecraftClient client) throws IOException {
        var pack = client.getDefaultResourcePack();
        var language = pack.open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla("lang/en_us.json"));
        if (language == null) throw new IOException("Minecraft's default English language resource is missing");
        Map<String, String> values = new HashMap<>();
        try (InputStream stream = language.get()) { Language.load(stream, values::put); }
        var aliases = pack.open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla("lang/deprecated.json"));
        if (aliases != null) {
            try (InputStream stream = aliases.get()) { DeprecatedLanguageData.fromInputStream(stream).apply(values); }
        } else DeprecatedLanguageData.create().apply(values);
        return values;
    }

    /** A packet codec calls this before its returned Text can be rendered, logged or flattened. */
    public static <T extends Text> T markRemote(T root) {
        walk(root, text -> {
            if (text instanceof PrivacyTextOrigin marker) marker.analyzer$markRemote();
            if (text.getContent() instanceof PrivacyTextOrigin marker) marker.analyzer$markRemote();
            return false;
        });
        return root;
    }

    /** Sign NBT is not decoded through packet Text codecs. Mark each line before the editor flattens it. */
    public static Text markSignLine(Text line) {
        var client = MinecraftClient.getInstance();
        var connection = client == null ? null : client.getNetworkHandler();
        if (connection != null && connection.getConnection().isLocal()) return line;
        return markRemote(line);
    }

    public static boolean hasRemote(Text root) {
        if (root == null) return false;
        if ((root instanceof PrivacyTextOrigin textMarker && textMarker.analyzer$isRemote())
                || (root.getContent() instanceof PrivacyTextOrigin contentMarker && contentMarker.analyzer$isRemote())) return true;
        if (root.getSiblings().isEmpty() && root.getStyle().getHoverEvent() == null) {
            boolean nested = false;
            if (root.getContent() instanceof TranslatableTextContent translated)
                for (Object argument : translated.getArgs()) if (argument instanceof Text) { nested = true; break; }
            if (!nested) return false;
        }
        return walk(root, text -> (text instanceof PrivacyTextOrigin textMarker && textMarker.analyzer$isRemote())
                || (text.getContent() instanceof PrivacyTextOrigin contentMarker && contentMarker.analyzer$isRemote()));
    }

    private interface Visit { boolean stop(Text text); }
    private static boolean walk(Text root, Visit visitor) {
        if (root == null) return false;
        var pending = new ArrayDeque<Text>();
        var seen = new IdentityHashMap<Text, Boolean>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Text text = pending.removeFirst();
            if (seen.put(text, true) != null) continue;
            if (visitor.stop(text)) return true;
            pending.addAll(text.getSiblings());
            if (text.getContent() instanceof TranslatableTextContent translated)
                for (Object argument : translated.getArgs()) if (argument instanceof Text child) pending.add(child);
            HoverEvent hover = text.getStyle().getHoverEvent();
            if (hover == null) continue;
            Text child = hover.getValue(HoverEvent.Action.SHOW_TEXT);
            if (child != null) pending.add(child);
            var entity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            if (entity != null) entity.name.ifPresent(pending::add);
            var item = hover.getValue(HoverEvent.Action.SHOW_ITEM);
            if (item != null) {
                var stack = item.asStack();
                Text name = stack.get(DataComponentTypes.CUSTOM_NAME), itemName = stack.get(DataComponentTypes.ITEM_NAME);
                if (name != null) pending.add(name);
                if (itemName != null) pending.add(itemName);
                var lore = stack.get(DataComponentTypes.LORE);
                if (lore != null) { pending.addAll(lore.lines()); pending.addAll(lore.styledLines()); }
            }
        }
        return false;
    }

    private static boolean active() {
        var client = MinecraftClient.getInstance();
        // The integrated server uses the same classes. Its language resolution remains unchanged.
        if (client == null || !client.isOnThread()) return false;
        if (!initialized) initialize(client);
        return initialized && ServerPrivacy.option("block-key-probes");
    }
    public static long policyEpoch() { return languageRevision.get() * 2 + (active() ? 1 : 0); }
    public static boolean hideTranslation(String key, boolean remote) {
        boolean hide = remote && active() && !vanillaTranslations.contains(key) && !serverTranslations.contains(key);
        if (hide) blockedTranslations.incrementAndGet();
        return hide;
    }
    public static boolean hideKeybind(String key, boolean remote) {
        boolean hide = remote && active() && !VANILLA_BINDINGS.contains(key);
        if (hide) blockedKeybinds.incrementAndGet();
        return hide;
    }
    public static long blockedTranslations() { return blockedTranslations.get(); }
    public static long blockedKeybinds() { return blockedKeybinds.get(); }
    public static int vanillaTranslationCount() { return vanillaTranslations.size(); }
    public static boolean isVanillaBinding(String key) { return VANILLA_BINDINGS.contains(key); }

    /** Called only by the language loader; metadata provenance never comes from server text. */
    public static void beginLanguageLoad() { LANGUAGE_ORIGINS.set(new HashMap<>()); }
    public static InputStream languageStream(Resource resource) throws IOException {
        return new LanguageStream(resource.getInputStream(), resource.getPack().getInfo().source() == ResourcePackSource.SERVER);
    }
    public static void loadLanguage(InputStream stream, BiConsumer<String, String> target) {
        boolean server = stream instanceof LanguageStream tagged && tagged.server;
        Language.load(stream, (key, value) -> {
            target.accept(key, value);
            var origins = LANGUAGE_ORIGINS.get();
            if (origins != null) origins.put(key, server);
        });
    }
    public static void finishLanguageLoad() {
        var origins = LANGUAGE_ORIGINS.get();
        LANGUAGE_ORIGINS.remove();
        if (origins == null) return;
        var keys = new HashSet<String>();
        origins.forEach((key, server) -> { if (server) keys.add(key); });
        serverTranslations = Set.copyOf(keys);
        languageRevision.incrementAndGet();
    }
    static Set<String> serverTranslationSnapshot() { return serverTranslations; }
    static void restoreServerTranslations(Set<String> keys) {
        LANGUAGE_ORIGINS.remove();
        serverTranslations = keys;
        languageRevision.incrementAndGet();
    }
    private static final class LanguageStream extends FilterInputStream {
        final boolean server;
        LanguageStream(InputStream input, boolean server) { super(input); this.server = server; }
    }
}
