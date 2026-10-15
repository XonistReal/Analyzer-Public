package dev.analyzer.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Bounded local language rules. Text becomes reviewable goals, never executable commands or an AI prompt. */
public final class LocalTaskIntent {
    public record Gather(String resource, int count, boolean explicitCount) { }
    public record Parsed(List<Gather> gathers, String actionText) { public Parsed { gathers = List.copyOf(gathers); } }
    public record Schematic(String name) { }
    private static final Pattern VERB = Pattern.compile("\\b(?:get|gather|collect|mine|fetch|bring|obtain|dig|harvest|find|grab|acquire|retrieve|pick\\s+up|stock\\s+up|need|want|have)\\b");
    private static Pattern nouns() { return Pattern.compile("(?<![a-z_])(?:" + ResourceCatalog.aliases().keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed()).map(name -> Pattern.quote(name).replace(" ", "\\E[ _-]+\\Q"))
            .collect(Collectors.joining("|")) + ")(?![a-z_])", Pattern.CASE_INSENSITIVE); }
    private static final Pattern FILLER = Pattern.compile("\\b(?:please|me|my|some|a|an|the|just|only|exactly|up|to|total|of|blocks?|items?|pieces?|can|could|would|will|you|mind|kindly|for|inventory|in|thanks|thank|i|like|hey|also|as|well|x)\\b");
    private static final Map<String, Integer> SMALL = Map.ofEntries(Map.entry("zero", 0), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12), Map.entry("thirteen", 13), Map.entry("fourteen", 14), Map.entry("fifteen", 15), Map.entry("sixteen", 16), Map.entry("seventeen", 17), Map.entry("eighteen", 18), Map.entry("nineteen", 19), Map.entry("twenty", 20), Map.entry("thirty", 30), Map.entry("forty", 40), Map.entry("fifty", 50), Map.entry("sixty", 60), Map.entry("seventy", 70), Map.entry("eighty", 80), Map.entry("ninety", 90));
    private LocalTaskIntent() { }

    public static List<Gather> gathers(String input) { return parse(input).gathers(); }
    public static Parsed parse(String input) {
        String text = normalized(input).replaceAll("\\b(hundred|thousand)\\s+and\\s+", "$1 ");
        text = Pattern.compile("\\b(?:to|at|coordinates?)\\s+-?\\d+\\s*,\\s*-?\\d+(?:\\s*,\\s*-?\\d+)?")
                .matcher(text).replaceAll(match -> match.group().replace(',', ' '));
        var requests = new ArrayList<Gather>(); var resources = new HashSet<String>();
        Pattern noun = nouns();
        var actions = new ArrayList<String>();
        boolean inherited = false;
        for (String raw : text.split("(?<!\\d),|,(?!\\d)|;|\\band(?:\\s+then)?\\b|\\bthen\\b|\\bplus\\b")) {
            String clause = raw.strip(); if (clause.isEmpty()) continue;
            if (FILLER.matcher(clause).replaceAll(" ").replaceAll("[.!?]", " ").isBlank()) continue;
            boolean verb = VERB.matcher(clause).find();
            boolean progression = Pattern.compile("\\b(?:armor|armour|kit|geared|kitted|enchant(?:ed|ing)?|shelter|base|house|expedition)\\b").matcher(clause).find();
            var match = noun.matcher(clause);
            if (!match.find()) {
                if (progression) { actions.add(clause); inherited = false; continue; }
                if (verb && !clause.matches(".*\\b(?:spawn|nether|overworld|end|portal)\\b.*"))
                    throw new IllegalArgumentException("I understood a gathering request, but could not identify its item. Use the displayed vanilla item name or minecraft:item_id. You do not need a preset sentence.");
                if (!verb && !otherAction(clause)) throw new IllegalArgumentException("I cannot turn ‘" + clause + "’ into a supported local task yet. Try a material and quantity, travel coordinates, an armor goal, or select a schematic.");
                actions.add(clause); inherited = false; continue;
            }
            int start = match.start(), end = match.end(); String name = match.group();
            int secondNoun = match.find() ? match.start() : -1;
            String before = clause.substring(0, start), after = clause.substring(end);
            var purpose = Pattern.compile("\\b(?:for|to\\s+(?:build|make|craft|enchant))\\b").matcher(after);
            boolean hasPurpose = purpose.find();
            if (secondNoun >= 0 && (!hasPurpose || secondNoun < end + purpose.start()))
                throw new IllegalArgumentException("Separate different material goals with ‘and’, for example ‘get 64 sand and 32 gravel’.");
            if (progression && !hasPurpose) {
                String proposedCount = remainder(before, true);
                if (quantityText(proposedCount)) throw new IllegalArgumentException("Resource counts describe inventory items, not multiple armor sets or builds. Review a material target and an armor/build goal separately.");
                actions.add(clause); inherited = false; continue;
            }
            if (hasPurpose) after = after.substring(0, purpose.start());
            String beforeCount = remainder(before, true), afterCount = remainder(after, false);
            if (!verb && !inherited && !beforeCount.isEmpty() && !quantityText(beforeCount)) { actions.add(clause); continue; }
            if (!beforeCount.isEmpty() && !afterCount.isEmpty())
                throw new IllegalArgumentException("Give one total quantity for " + name + ", for example ‘get 600 " + name + "’.");
            String quantity = beforeCount.isEmpty() ? afterCount : beforeCount;
            String key = ResourceCatalog.canonical(name);
            int count = quantity.isEmpty() ? 32 : quantity(quantity, ResourceCatalog.stackSize(key));
            if (!resources.add(key)) throw new IllegalArgumentException("Give one total inventory target for " + key.replace('_', ' ') + "; it appears more than once in this request.");
            requests.add(new Gather(key, count, !quantity.isEmpty())); inherited = true;
            if (requests.size() > 16) throw new IllegalArgumentException("Review at most 16 different material targets in one task.");
        }
        return new Parsed(requests, String.join(" and ", actions));
    }
    private static boolean otherAction(String clause) {
        return clause.matches(".*\\b(?:escape|spawn|travel|walk|move|go|enter|visit|return|nether|overworld|end|build|place|construct|schematic|enchant|armor|armour|kit|expedition)\\b.*");
    }
    private static String remainder(String text, boolean prefix) {
        String remaining = prefix ? VERB.matcher(text).replaceAll(" ") : text;
        remaining = FILLER.matcher(remaining).replaceAll(" ").replaceAll("[.!?]+$", " ").replaceAll("^\\s*[:=x]\\s*(?=\\d)", "").strip();
        if (Pattern.compile("\\b(?:more|additional|extra)\\b").matcher(remaining).find())
            throw new IllegalArgumentException("Gather quantities are total inventory targets. Give the final total, for example ‘have 600 sand’ or ‘get 600 sand’.");
        return remaining.replaceAll("\\s+", " ");
    }
    private static boolean quantityText(String text) { return text.matches("[-+]?\\d[\\d,.]*") || SMALL.containsKey(text.split(" ")[0]); }
    static int quantity(String input) {
        return quantity(input, 64);
    }
    private static int quantity(String input, int stackSize) {
        // Reject a sign before treating hyphens as number-word separators or recursing on stacks.
        if (input.strip().startsWith("-")) throw countError();
        String value = input.strip().toLowerCase(Locale.ROOT).replace('-', ' ');
        if (value.equals("stack")) return stackSize;
        var stacks = Pattern.compile("^(?:(half|quarter)(?:\\s+(?:a|of a))?|(.+?))\\s+stacks?$").matcher(value);
        if (stacks.matches()) {
            if (stacks.group(1) != null) {
                int divisor = stacks.group(1).equals("half") ? 2 : 4;
                if (stackSize % divisor != 0) throw new IllegalArgumentException("This item's stack size does not divide into a whole number of items. Give an item count.");
                return bounded(stackSize / divisor);
            }
            String amount = stacks.group(2).strip();
            int stackCount = amount.equals("a") || amount.equals("one") ? 1 : quantity(amount);
            long result = (long) stackSize * stackCount;
            if (result > 4096) throw countError();
            return bounded((int) result);
        }
        if (value.matches("[0-9]+(?:,[0-9]{3})*")) {
            try { return bounded(Integer.parseInt(value.replace(",", ""))); }
            catch (NumberFormatException failure) { throw countError(); }
        }
        if (value.matches(".*[0-9].*")) throw countError();
        int total = 0, group = 0, previous = -1; boolean multiplied = false;
        for (String word : value.split("\\s+")) {
            Integer number = SMALL.get(word);
            if (number != null) {
                if (previous >= 0 && !(previous >= 20 && previous % 10 == 0 && number > 0 && number < 10)) throw countError();
                group += number; previous = number;
            } else if (word.equals("hundred") && group > 0 && group < 10 && !multiplied) { group *= 100; previous = -1; multiplied = true; }
            else if (word.equals("thousand") && group > 0 && total == 0) { total = group * 1000; group = 0; previous = -1; multiplied = false; }
            else throw new IllegalArgumentException("Use a whole-number quantity such as 37, 600 or ‘six hundred’. Vague amounts such as ‘lots’ are not enough to review a goal.");
            if (total + group > 4096) throw countError();
        }
        return bounded(total + group);
    }
    private static int bounded(int count) { if (count < 1 || count > 4096) throw countError(); return count; }
    private static IllegalArgumentException countError() { return new IllegalArgumentException("Choose a whole-number inventory target between 1 and 4096 items, for example ‘get me 600 sand’."); }

    public static Optional<Schematic> schematic(String input) {
        String text = normalized(input);
        if (!text.matches(".*\\b(?:build|place|construct)\\b.*") || !text.matches(".*(?:\\bschematics?\\b|\\.litematic\\b|\\.schem(?:atic)?\\b).*")) return Optional.empty();
        if (!gathers(text).isEmpty()) throw new IllegalArgumentException("Review one schematic on its own. Its material list already gathers every required supply before construction.");
        String name = text.replaceFirst("^.*?\\b(?:build|place|construct)\\b", "").strip();
        name = name.replaceFirst("^(?:(?:only|just|the|my|selected|current|please|one|1|a|an)\\s+)+", "");
        if (name.matches("^(?:two|three|multiple|several|all|[2-9]|[1-9][0-9]+)\\s+schematics?\\b.*"))
            throw new IllegalArgumentException("Review one schematic at a time. Choose one file from Schematics → Browse before starting.");
        name = name.replaceFirst("^(?:one|1|a|an)\\s+", "").replaceFirst("^schematics?\\b\\s*(?:(?:called|named)\\s+)?", "");
        name = name.replaceFirst("\\s+schematics?\\s*$", "").replaceFirst("\\s+please[.!?]*$", "").strip();
        name = name.replaceAll("^[\\s\"'“”]+|[\\s\"'“”!?]+$", "").replaceAll("\\s+", " ");
        return Optional.of(new Schematic(name));
    }
    private static String normalized(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT).replace('’', '\'').replace('\n', ' ').replace('\r', ' ').strip()
                .replaceAll("\\bgetting\\b", "get").replaceAll("\\bgrabbing\\b", "grab")
                .replaceAll("\\bcollecting\\b", "collect").replaceAll("\\bgathering\\b", "gather")
                .replaceAll("\\bfetching\\b", "fetch").replaceAll("\\bmining\\b", "mine")
                .replaceFirst("^(?:i'd|i would)\\s+like\\s+(?:you\\s+)?to\\s+", "")
                .replaceFirst("^(?:(?:could|can|would)\\s+you\\s+)?(?:please\\s+)?go\\s+and\\s+(?=(?:get|gather|collect|mine|fetch|bring|obtain)\\b)", "");
    }
}
