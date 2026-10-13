package dev.analyzer.privacy;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.analyzer.AnalyzerClient;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.DeprecatedLanguageData;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Synchronous native acceptance; uses memory-only packs/codecs/sign construction and sends no packets. */
public final class PrivacyTextSmoke {
    private static final String PRIVATE = "analyzer.privacy_fixture.local_label";
    private static final String SERVER = "analyzer.privacy_fixture.server_label";
    private static final String SHADOWED = "analyzer.privacy_fixture.server_shadowed";
    private static int assertions;
    private PrivacyTextSmoke() { }

    /** Call from a dev-only fixture on Minecraft's client thread, outside an integrated world. */
    public static int run(MinecraftClient client) {
        assertions = 0;
        require(client.isOnThread(), "Native privacy text runs on Minecraft's client thread");
        require(client.getNetworkHandler() == null || !client.getNetworkHandler().getConnection().isLocal(),
                "Native sign-origin acceptance is outside an integrated/local connection");
        var core = AnalyzerClient.CORE;
        boolean enabled = core.isEnabled("server-privacy"), probes = core.getBoolean("server-privacy", "block-key-probes");
        Language originalLanguage = Language.getInstance();
        Set<String> serverKeys = PrivacyTextProtection.serverTranslationSnapshot();
        try {
            core.setEnabled("server-privacy", true);
            core.setValue("server-privacy", "block-key-probes", true);
            PrivacyTextProtection.initialize(client);
            require(PrivacyTextProtection.vanillaTranslationCount() > 1000, "Vanilla translation baseline came from Minecraft's own pack");
            require(PrivacyTextProtection.isVanillaBinding("key.jump"), "Actual vanilla binding is accepted");
            require(!PrivacyTextProtection.isVanillaBinding("key.analyzer.open"), "A mod binding is outside the actual vanilla allowlist");
            require(!PrivacyTextProtection.isVanillaBinding("key.privacy_fixture.fake_vanilla"), "Unknown key.* names are not whitelisted by prefix");

            JsonObject local = new JsonObject();
            // Use the complete unmodified language file, so the real loader can apply its
            // compatibility renames without emitting warnings from an artificial tiny dictionary.
            var languageResource = client.getDefaultResourcePack().open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla("lang/en_us.json"));
            try (InputStream stream = languageResource.get()) { Language.load(stream, local::addProperty); }
            catch (Exception error) { throw new IllegalStateException("Native language fixture could not read vanilla data", error); }
            local.addProperty(PRIVATE, "PRIVATE LOCAL VALUE");
            local.addProperty("key.analyzer.open", "LOCAL MENU LABEL");
            local.addProperty("gui.done", "Done fixture");
            local.addProperty("chat.type.text", "<%s> %s");
            JsonObject server = new JsonObject();
            server.addProperty(SERVER, "Pack says %s");
            server.addProperty(SHADOWED, "Server before local override");
            JsonObject override = new JsonObject();
            override.addProperty(SHADOWED, "LOCAL OVERRIDE VALUE");
            var localPack = new MemoryPack("privacy-fixture-local", ResourcePackSource.NONE, local);
            var serverPack = new MemoryPack("privacy-fixture-server", ResourcePackSource.SERVER, server);
            var overridePack = new MemoryPack("privacy-fixture-override", ResourcePackSource.NONE, override);
            try (var resources = new LifecycledResourceManagerImpl(ResourceType.CLIENT_RESOURCES, List.of(localPack, serverPack, overridePack))) {
                Language.setInstance(TranslationStorage.load(resources, List.of("en_us"), false));
            }
            require(PrivacyTextProtection.serverTranslationSnapshot().contains(SERVER), "Real language-loader provenance accepts an active SERVER pack key");
            require(!PrivacyTextProtection.serverTranslationSnapshot().contains(PRIVATE), "Local pack translations are not treated as server declarations");
            require(!PrivacyTextProtection.serverTranslationSnapshot().contains(SHADOWED), "The winning local override revokes server-key provenance");

            MutableText localLabel = Text.translatable(PRIVATE);
            require(localLabel.getString().equals("PRIVATE LOCAL VALUE"), "Locally created mod translations remain usable");
            require(Text.translatable("key.analyzer.open").getString().equals("LOCAL MENU LABEL"), "Local Analyzer menu labels are unchanged");
            require(!PrivacyTextProtection.hasRemote(localLabel), "Local creation does not receive a remote marker");
            JsonObject serialized = new JsonObject(); serialized.addProperty("translate", PRIVATE);
            Text diskText = TextCodecs.CODEC.parse(JsonOps.INSTANCE, serialized).getOrThrow();
            require(!PrivacyTextProtection.hasRemote(diskText) && diskText.getString().equals("PRIVATE LOCAL VALUE"), "Generic JSON/disk Text decoding is not marked as network input");

            Text remote = packet(Text.translatable(PRIVATE));
            require(PrivacyTextProtection.hasRemote(remote), "The actual packet codec marks origin before deferred resolution");
            require(remote.getString().equals(PRIVATE), "Remote local-only translation is returned as its unresolved key");
            require(localLabel.getString().equals("PRIVATE LOCAL VALUE"), "Encoding a local Text does not alter the source object");
            require(packet(Text.translatableWithFallback(PRIVATE, "SERVER FALLBACK")).getString().equals("SERVER FALLBACK"), "An explicit fallback is preserved exactly");
            require(packet(Text.translatableWithFallback(PRIVATE, "Hello %s / %%", "Alice")).getString().equals("Hello Alice / %"), "Fallback arguments and percent escapes use Minecraft's normal formatter");
            require(packet(Text.translatableWithFallback(PRIVATE, "")).getString().isEmpty(), "An explicit empty fallback stays empty");
            require(packet(Text.translatableWithFallback(PRIVATE, "100%z")).getString().equals("100%z"), "Malformed format fallback retains Minecraft's literal behavior");
            require(packet(Text.translatable("gui.done")).getString().equals("Done fixture"), "Known vanilla translations keep the selected language/pack text");
            var renamed = DeprecatedLanguageData.create().renamed().values().stream()
                    .filter(key -> Language.getInstance().hasTranslation(key)).findFirst().orElseThrow();
            String renamedValue = Language.getInstance().get(renamed);
            require(!renamedValue.equals(renamed) && packet(Text.translatable(renamed)).getString().equals(renamedValue),
                    "Genuine vanilla keys produced by compatibility renames remain allowed");
            require(packet(Text.translatableWithFallback(SERVER, "Unused", "Bob")).getString().equals("Pack says Bob"), "An accepted server pack key resolves its genuine pack translation");
            require(packet(Text.translatable(SHADOWED)).getString().equals(SHADOWED), "A local override cannot masquerade as a server-declared translation");

            Text nested = packet(Text.translatable("chat.type.text", Text.literal("Player"), Text.translatableWithFallback(PRIVATE, "Safe message")));
            require(nested.getString().equals("<Player> Safe message"), "Vanilla chat formatting remains normal and nested remote mod arguments are protected");
            Text siblings = packet(Text.literal("Prefix / ").append(Text.translatableWithFallback(PRIVATE, "Safe sibling")));
            require(siblings.getString().equals("Prefix / Safe sibling"), "Remote sibling components receive the same origin protection");
            Text hover = packet(Text.literal("Hover").styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.translatableWithFallback(PRIVATE, "Safe tooltip")))));
            Text tooltip = hover.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
            require(PrivacyTextProtection.hasRemote(tooltip) && tooltip.getString().equals("Safe tooltip"), "Hover text is marked before delayed tooltip resolution");
            Text entityHover = PrivacyTextProtection.markRemote(Text.literal("Entity hover").styled(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityContent(EntityType.PIG,
                            new UUID(0, 1), Text.translatableWithFallback(PRIVATE, "Safe entity name"))))));
            Text entityName = entityHover.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_ENTITY).name.orElseThrow();
            require(PrivacyTextProtection.hasRemote(entityName) && entityName.getString().equals("Safe entity name"), "Entity hover names receive remote origin before tooltip creation");
            var hoverStack = new ItemStack(Items.STONE);
            hoverStack.set(DataComponentTypes.CUSTOM_NAME, Text.translatableWithFallback(PRIVATE, "Safe item name"));
            Text itemHover = PrivacyTextProtection.markRemote(Text.literal("Item hover").styled(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(hoverStack)))));
            Text itemName = itemHover.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_ITEM).asStack().get(DataComponentTypes.CUSTOM_NAME);
            require(PrivacyTextProtection.hasRemote(itemName) && itemName.getString().equals("Safe item name"), "Item hover custom names receive remote origin before tooltip creation");

            KeyBinding analyzer = KeyBinding.byId("key.analyzer.open");
            require(analyzer != null, "Analyzer's actual registered keybinding is present");
            String actualAnalyzerKey = analyzer.getBoundKeyLocalizedText().getString();
            require(Text.keybind("key.analyzer.open").getString().equals(actualAnalyzerKey), "Local mod keybinding labels retain the actual selected key");
            require(packet(Text.keybind("key.analyzer.open")).getString().equals("key.analyzer.open"), "Remote mod keybinding components do not resolve the installed binding");
            require(packet(Text.keybind("key.privacy_fixture.fake_vanilla")).getString().equals("key.privacy_fixture.fake_vanilla"), "Unknown binding identifiers remain unresolved");
            require(packet(Text.keybind("key.jump")).getString().equals(client.options.jumpKey.getBoundKeyLocalizedText().getString()), "Actual vanilla keybindings retain their native labels");

            registryCodecs();
            Text reusable = packet(Text.translatable(PRIVATE));
            Text copy = reusable.copy();
            MutableText composed = Text.literal("Copied / ").append(copy);
            require(composed.getString().equals("Copied / " + PRIVATE), "Ordinary Text copying retains content origin");
            require(ordered(composed.asOrderedText()).equals("Copied / " + PRIVATE), "Ordered text initially follows the protected policy");
            core.setValue("server-privacy", "block-key-probes", false);
            require(reusable.getString().equals("PRIVATE LOCAL VALUE"), "Disabling the setting restores normal resolution on an existing component");
            require(ordered(composed.asOrderedText()).equals("Copied / PRIVATE LOCAL VALUE"), "Ordered-text caches refresh when protection is disabled");
            core.setValue("server-privacy", "block-key-probes", true);
            require(reusable.getString().equals(PRIVATE), "Re-enabling the setting invalidates the existing resolved translation cache");
            require(ordered(composed.asOrderedText()).equals("Copied / " + PRIVATE), "Copied/composed ordered-text caches also refresh when re-enabled");
            core.setEnabled("server-privacy", false);
            require(packet(Text.keybind("key.analyzer.open")).getString().equals(actualAnalyzerKey), "Disabling the whole module restores native remote keybind behavior");
            require(reusable.getString().equals("PRIVATE LOCAL VALUE"), "Disabling the whole module restores native remote translations");
            core.setEnabled("server-privacy", true);

            Text[] signLines = { Text.translatableWithFallback(PRIVATE, "Sign fallback"), Text.keybind("key.analyzer.open"),
                    Text.translatable("gui.done"), Text.translatableWithFallback(SERVER, "Unused", "Sign") };
            var sign = new MemorySign(new SignText(signLines, signLines.clone(), DyeColor.BLACK, false));
            require(!PrivacyTextProtection.hasRemote(sign.getFrontText().getMessage(0, false)), "Native block-entity NBT decoding does not accidentally mark generic disk text");
            var editor = new SignEditScreen(sign, true, false);
            String[] initial = ((PrivacyTextSignAccess) editor).analyzer$privacyLines();
            require(initial[0].equals("Sign fallback"), "Native sign construction protects NBT translation fallback before flattening");
            require(initial[1].equals("key.analyzer.open"), "Native sign construction does not flatten a mod binding into a leaked key");
            require(initial[2].equals("Done fixture"), "Native sign construction keeps vanilla translations readable");
            require(initial[3].equals("Pack says Sign"), "Native sign construction keeps genuine accepted server-pack text");
            require(PrivacyTextProtection.hasRemote(sign.getFrontText().getMessage(0, false)), "NBT sign text receives provenance before any sign update can echo it");

            Text oldServer = packet(Text.translatableWithFallback(SERVER, "No active pack", "Bob"));
            require(oldServer.getString().equals("Pack says Bob"), "An existing server component resolves while its pack provenance is active");
            try (var resources = new LifecycledResourceManagerImpl(ResourceType.CLIENT_RESOURCES, List.of(localPack))) {
                TranslationStorage.load(resources, List.of("en_us"), false);
            }
            require(!PrivacyTextProtection.serverTranslationSnapshot().contains(SERVER), "A reload without the server pack clears its allowed keys");
            require(oldServer.getString().equals("No active pack"), "The provenance epoch revokes cached server translation resolution even before Language replacement");
            require(PrivacyTextProtection.blockedTranslations() > 0 && PrivacyTextProtection.blockedKeybinds() > 0, "Both protection paths record aggregate counters without storing message contents");
            AnalyzerClient.LOGGER.info("ANALYZER_PRIVACY_TEXT_PASS: {} native assertions", assertions);
            return assertions;
        } finally {
            Language.setInstance(originalLanguage);
            PrivacyTextProtection.restoreServerTranslations(serverKeys);
            core.setValue("server-privacy", "block-key-probes", probes);
            core.setEnabled("server-privacy", enabled);
        }
    }

    private static Text packet(Text text) {
        var buffer = Unpooled.buffer();
        try {
            TextCodecs.PACKET_CODEC.encode(buffer, text);
            return TextCodecs.PACKET_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }
    private static void registryCodecs() {
        var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buffer, Text.translatableWithFallback(PRIVATE, "Registry fallback"));
            Text first = TextCodecs.REGISTRY_PACKET_CODEC.decode(buffer);
            require(PrivacyTextProtection.hasRemote(first) && first.getString().equals("Registry fallback"), "Limited registry packet Text is marked before return");
            buffer.clear();
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buffer, Text.translatableWithFallback(PRIVATE, "Unlimited fallback"));
            Text second = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buffer);
            require(PrivacyTextProtection.hasRemote(second) && second.getString().equals("Unlimited fallback"), "Unlimited registry packet Text is marked before return");
            buffer.clear();
            TextCodecs.OPTIONAL_PACKET_CODEC.encode(buffer, Optional.of(Text.translatableWithFallback(PRIVATE, "Optional fallback")));
            Text third = TextCodecs.OPTIONAL_PACKET_CODEC.decode(buffer).orElseThrow();
            require(PrivacyTextProtection.hasRemote(third) && third.getString().equals("Optional fallback"), "Optional registry Text preserves origin");
            buffer.clear();
            TextCodecs.OPTIONAL_UNLIMITED_REGISTRY_PACKET_CODEC.encode(buffer, Optional.of(Text.keybind("key.analyzer.open")));
            Text fourth = TextCodecs.OPTIONAL_UNLIMITED_REGISTRY_PACKET_CODEC.decode(buffer).orElseThrow();
            require(PrivacyTextProtection.hasRemote(fourth) && fourth.getString().equals("key.analyzer.open"), "Optional unlimited Text preserves keybind origin");
            buffer.clear();
            TextCodecs.OPTIONAL_PACKET_CODEC.encode(buffer, Optional.empty());
            require(TextCodecs.OPTIONAL_PACKET_CODEC.decode(buffer).isEmpty(), "An empty optional packet remains empty");
        } finally { buffer.release(); }
    }
    private static String ordered(OrderedText text) {
        var value = new StringBuilder();
        text.accept((index, style, codePoint) -> { value.appendCodePoint(codePoint); return true; });
        return value.toString();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
        assertions++;
    }
    private static final class MemoryPack implements ResourcePack {
        final ResourcePackInfo info;
        final byte[] language;
        MemoryPack(String id, ResourcePackSource source, JsonObject values) {
            info = new ResourcePackInfo(id, Text.literal(id), source, Optional.empty());
            language = values.toString().getBytes(StandardCharsets.UTF_8);
        }
        @Override public ResourcePackInfo getInfo() { return info; }
        @Override public Set<String> getNamespaces(ResourceType type) { return Set.of("minecraft"); }
        @Override public InputSupplier<InputStream> openRoot(String... path) { return null; }
        @Override public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
            return type == ResourceType.CLIENT_RESOURCES && id.equals(Identifier.ofVanilla("lang/en_us.json"))
                    ? () -> new ByteArrayInputStream(language) : null;
        }
        @Override public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) { }
        @Override public <T> T parseMetadata(ResourceMetadataSerializer<T> metadata) { return null; }
        @Override public void close() { }
    }
    /** readNbt assigns native SignText without setText's world listener notification. */
    private static final class MemorySign extends SignBlockEntity {
        MemorySign(SignText text) {
            super(BlockPos.ORIGIN, Blocks.OAK_SIGN.getDefaultState());
            var nbt = new NbtCompound();
            nbt.put("front_text", SignText.CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow());
            super.readNbt(nbt, DynamicRegistryManager.EMPTY);
        }
    }
}
