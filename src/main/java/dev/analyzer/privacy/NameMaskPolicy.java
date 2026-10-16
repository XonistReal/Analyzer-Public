package dev.analyzer.privacy;

import java.util.ArrayList;
import java.util.List;

/** Presentation-only username matching. Never used for packets, profiles, source Text or saved chat. */
public final class NameMaskPolicy {
    public static final int MAX_INPUT = 65_536;
    public static final String OVERSIZE = "[Long text hidden]";
    public record Span(int start, int end) { }
    private NameMaskPolicy() { }

    public static List<Span> matches(String text, String realName, String alias) {
        if (text == null || realName == null || realName.isEmpty() || alias == null || alias.equalsIgnoreCase(realName)) return List.of();
        List<Span> result = new ArrayList<>();
        for (int index = 0, end = Math.min(text.length(), MAX_INPUT) - realName.length(); index <= end;) {
            if (text.regionMatches(true, index, realName, 0, realName.length())
                    && (index == 0 || !nameCharacter(text.codePointBefore(index)))
                    && (index + realName.length() == text.length() || !nameCharacter(text.codePointAt(index + realName.length())))) {
                result.add(new Span(index, index + realName.length())); index += realName.length();
            } else index += Character.charCount(text.codePointAt(index));
        }
        return List.copyOf(result);
    }

    private static boolean nameCharacter(int codePoint) { return codePoint == '_' || Character.isLetterOrDigit(codePoint); }

    /** Retains native legacy formatting codes; a match can cross color-code boundaries. */
    public static String replace(String source, String realName, String alias) {
        if (source == null || source.isEmpty()) return source;
        if (source.length() > MAX_INPUT) return OVERSIZE;
        if (source.indexOf('\u00a7') < 0) {
            List<Span> spans = matches(source, realName, alias);
            if (spans.isEmpty()) return source;
            StringBuilder output = new StringBuilder(source.length()); int copied = 0;
            for (Span span : spans) { output.append(source, copied, span.start()).append(alias); copied = span.end(); }
            return output.append(source, copied, source.length()).toString();
        }
        StringBuilder visible = new StringBuilder(source.length());
        List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        int limit = Math.min(source.length(), MAX_INPUT);
        for (int index = 0; index < limit;) {
            if (formatCode(source, index)) { index += 2; continue; }
            int point = source.codePointAt(index), count = Character.charCount(point);
            visible.appendCodePoint(point);
            for (int unit = 0; unit < count; unit++) { starts.add(index); ends.add(index + count); }
            index += count;
        }
        List<Span> spans = matches(visible.toString(), realName, alias);
        if (spans.isEmpty()) return source;
        StringBuilder output = new StringBuilder(source.length()); int copied = 0;
        for (Span span : spans) {
            int first = starts.get(span.start()), last = ends.get(span.end() - 1);
            output.append(source, copied, first).append(alias);
            // Codes within the hidden name still establish the style of following source characters.
            for (int index = first; index < last; index++) if (formatCode(source, index)) { output.append(source, index, index + 2); index++; }
            copied = last;
        }
        return output.append(source, copied, source.length()).toString();
    }

    private static boolean formatCode(String source, int index) {
        return source.charAt(index) == '\u00a7' && index + 1 < source.length()
                && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(source.charAt(index + 1))) >= 0;
    }
}
