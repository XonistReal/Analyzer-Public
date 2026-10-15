package dev.analyzer.automation;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validate real recipe layouts and batch yields against the pinned Minecraft data, without launching a client. */
public final class MaterialRecipesSelfTest {
    private static int assertions;
    private static final Map<String, JsonObject> DATA = new HashMap<>();
    public static void main(String[] args) throws Exception {
        int recipes = 0;
        for (var material : BuildingMaterialCatalog.all()) {
            if (material.job() != BuildingMaterialCatalog.Job.CRAFT) continue;
            JsonObject data = data("recipe/" + material.itemId());
            var result = data.getAsJsonObject("result");
            check(result.get("id").getAsString().equals("minecraft:" + material.itemId()), "Actual output item: " + material.itemId());
            check((result.has("count") ? result.get("count").getAsInt() : 1) == material.outputPerRecipe(), "Actual batch yield: " + material.itemId());
            boolean matches = data.get("type").getAsString().equals("minecraft:crafting_shapeless") ? shapeless(material, data) : shaped(material, data);
            check(matches, "Actual vanilla ingredient layout: " + material.itemId()); recipes++;
        }
        check(BuildingMaterialCatalog.manualInputs("sticky_piston").equals(Set.of("slime_ball")), "Unimplemented mob acquisition stays visible through crafted dependencies");
        check(BuildingMaterialCatalog.manualInputs("copper_bulb").equals(Set.of("blaze_rod")), "Bulb construction discloses real blaze-rod requirement");
        System.out.println("Vanilla material recipes passed " + assertions + " assertions across " + recipes + " recipes.");
    }
    private static boolean shapeless(BuildingMaterialCatalog.Material material, JsonObject data) throws Exception {
        List<String> actual = new ArrayList<>(material.cells().stream().filter(cell -> !cell.isEmpty()).toList());
        for (JsonElement ingredient : data.getAsJsonArray("ingredients")) {
            int found = -1;
            for (int i = 0; i < actual.size(); i++) if (accepts(ingredient, actual.get(i), new HashSet<>())) { found = i; break; }
            if (found < 0) return false;
            actual.remove(found);
        }
        return actual.isEmpty();
    }
    private static boolean shaped(BuildingMaterialCatalog.Material material, JsonObject data) throws Exception {
        if (!data.get("type").getAsString().equals("minecraft:crafting_shaped")) return false;
        var pattern = data.getAsJsonArray("pattern"); var keys = data.getAsJsonObject("key");
        int width = pattern.get(0).getAsString().length(), height = pattern.size(), grid = material.width();
        for (int y = 0; y <= grid - height; y++) for (int x = 0; x <= grid - width; x++) for (boolean mirror : new boolean[]{false, true}) {
            boolean match = true;
            for (int gy = 0; gy < grid; gy++) for (int gx = 0; gx < grid; gx++) {
                int dx = gx - x, dy = gy - y;
                char symbol = dx < 0 || dx >= width || dy < 0 || dy >= height ? ' '
                        : pattern.get(dy).getAsString().charAt(mirror ? width - dx - 1 : dx);
                String cell = material.cells().get(gy * grid + gx);
                if (symbol == ' ' ? !cell.isEmpty() : !accepts(keys.get(String.valueOf(symbol)), cell, new HashSet<>())) match = false;
            }
            if (match) return true;
        }
        return false;
    }
    private static boolean accepts(JsonElement ingredient, String item, Set<String> visited) throws Exception {
        if (ingredient == null || item.isEmpty()) return false;
        if (ingredient.isJsonArray()) {
            for (JsonElement entry : ingredient.getAsJsonArray()) if (accepts(entry, item, visited)) return true;
            return false;
        }
        String name = ingredient.getAsString();
        if (!name.startsWith("#")) return name.equals("minecraft:" + item);
        if (!visited.add(name)) return false;
        var tag = data("tags/item/" + name.substring(name.indexOf(':') + 1));
        for (JsonElement entry : tag.getAsJsonArray("values")) {
            if (entry.isJsonObject()) entry = entry.getAsJsonObject().get("id");
            if (accepts(entry, item, new HashSet<>(visited))) return true;
        }
        return false;
    }
    private static JsonObject data(String name) throws Exception {
        if (DATA.containsKey(name)) return DATA.get(name);
        try (var input = MaterialRecipesSelfTest.class.getResourceAsStream("/data/minecraft/" + name + ".json")) {
            if (input == null) throw new AssertionError("Missing pinned Minecraft data: " + name);
            JsonObject value = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject(); DATA.put(name, value); return value;
        }
    }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
}
