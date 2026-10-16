package dev.analyzer.privacy;

import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.TextVisitFactory;

import java.util.ArrayList;
import java.util.List;

/** Disposable styled presentation copies. Account identity, source components and network text stay real. */
public final class NameChanger {
    private static final ThreadLocal<Integer> EDITABLE = ThreadLocal.withInitial(() -> 0);
    private static String chatPolicy = "";
    private record Policy(String realName, String alias) { }
    private record Glyph(int point, Style style) { }
    private NameChanger() { }

    private static Policy policy() {
        if (EDITABLE.get() > 0) return null;
        var core = AnalyzerClient.CORE;
        if (core == null || !core.isEnabled("name-changer")) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null) return null;
        String real = client.getSession().getUsername(), alias = core.getText("name-changer", "alias");
        return real == null || real.isEmpty() || real.equalsIgnoreCase(alias) ? null : new Policy(real, alias);
    }

    public static String policyKey() {
        Policy policy = policy(); return policy == null ? "" : policy.realName() + '\0' + policy.alias();
    }

    /** Refresh cached visual chat lines from their unmodified originals after live edits or disable. */
    public static void refreshChat(MinecraftClient client) {
        String key = policyKey();
        if (key.equals(chatPolicy)) return;
        chatPolicy = key;
        if (client != null && client.inGameHud != null) client.inGameHud.getChatHud().reset();
    }

    public static String render(String source) {
        Policy policy = policy();
        return policy == null ? source : NameMaskPolicy.replace(source, policy.realName(), policy.alias());
    }

    public static StringVisitable render(StringVisitable source) {
        Policy policy = policy();
        if (source instanceof MaskedVisitable masked) {
            if (policy != null && policy.equals(masked.policy())) return source;
            source = masked.original();
        }
        if (policy == null || source == null) return source;
        StyledInput input = new StyledInput();
        source.visit((style, text) -> {
            TextVisitFactory.visitFormatted(text, style, input::add);
            return input.truncated ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty();
        }, Style.EMPTY);
        List<Glyph> masked = input.masked(policy);
        if (masked == null) return source;
        List<StringVisitable> pieces = new ArrayList<>();
        StringBuilder run = new StringBuilder(); Style style = null;
        for (Glyph glyph : masked) {
            if (style != null && !style.equals(glyph.style())) { pieces.add(StringVisitable.styled(run.toString(), style)); run.setLength(0); }
            style = glyph.style(); run.appendCodePoint(glyph.point());
        }
        if (style != null) pieces.add(StringVisitable.styled(run.toString(), style));
        return new MaskedVisitable(source, policy, StringVisitable.concat(pieces));
    }

    public static OrderedText render(OrderedText source) {
        Policy policy = policy();
        if (source instanceof MaskedOrderedText masked) {
            if (policy != null && policy.equals(masked.policy())) return source;
            source = masked.original();
        }
        if (policy == null || source == null) return source;
        StyledInput input = new StyledInput(); source.accept(input::add);
        List<Glyph> masked = input.masked(policy);
        return masked == null ? source : new MaskedOrderedText(source, policy, masked);
    }

    private record MaskedVisitable(StringVisitable original, Policy policy, StringVisitable display) implements StringVisitable {
        @Override public <T> java.util.Optional<T> visit(Visitor<T> visitor) { return display.visit(visitor); }
        @Override public <T> java.util.Optional<T> visit(StyledVisitor<T> visitor, Style base) { return display.visit(visitor, base); }
    }

    private record MaskedOrderedText(OrderedText original, Policy policy, List<Glyph> glyphs) implements OrderedText {
        @Override public boolean accept(CharacterVisitor visitor) {
            int index = 0;
            for (Glyph glyph : glyphs) {
                if (!visitor.accept(index, glyph.style(), glyph.point())) return false;
                index += Character.charCount(glyph.point());
            }
            return true;
        }
    }

    private static final class StyledInput {
        private final StringBuilder text = new StringBuilder();
        private final List<Glyph> glyphs = new ArrayList<>();
        private boolean truncated;
        boolean add(int index, Style style, int point) {
            if (text.length() >= NameMaskPolicy.MAX_INPUT) { truncated = true; return false; }
            text.appendCodePoint(point); glyphs.add(new Glyph(point, style)); return true;
        }
        List<Glyph> masked(Policy policy) {
            // A bounded privacy pass must not expose a real name just beyond its cap.
            if (truncated) return NameMaskPolicy.OVERSIZE.codePoints().mapToObj(point -> new Glyph(point, Style.EMPTY)).toList();
            List<NameMaskPolicy.Span> spans = NameMaskPolicy.matches(text.toString(), policy.realName(), policy.alias());
            if (spans.isEmpty()) return null;
            List<Glyph> result = new ArrayList<>(); int offset = 0, spanIndex = 0;
            for (Glyph glyph : glyphs) {
                var span = spanIndex < spans.size() ? spans.get(spanIndex) : null;
                if (span != null && offset == span.start()) {
                    for (int point : policy.alias().codePoints().toArray()) result.add(new Glyph(point, glyph.style()));
                }
                if (span == null || offset < span.start() || offset >= span.end()) result.add(glyph);
                offset += Character.charCount(glyph.point());
                if (span != null && offset >= span.end()) spanIndex++;
            }
            return List.copyOf(result);
        }
    }

    /** Native editing uses raw indices and contents, including commands that the player may submit. */
    public static EditableScope editable() { return new EditableScope(); }
    public static final class EditableScope implements AutoCloseable {
        private boolean closed;
        private EditableScope() { EDITABLE.set(EDITABLE.get() + 1); }
        @Override public void close() { if (!closed) { EDITABLE.set(Math.max(0, EDITABLE.get() - 1)); closed = true; } }
    }
}
