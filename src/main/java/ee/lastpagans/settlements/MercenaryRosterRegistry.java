package ee.lastpagans.settlements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-owned, data-driven mercenary market definitions. The generated JSON lives in config so
 * server owners can reskin/rebalance companies without recompiling Settlements.
 */
public final class MercenaryRosterRegistry {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("settlements-mercenary-rosters.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Definition> definitions = Map.of();
    private static long loadedModified = Long.MIN_VALUE;

    private MercenaryRosterRegistry() {}

    public static synchronized List<Definition> definitions() {
        reloadIfChanged();
        return definitions.values().stream().sorted(Comparator.comparing(Definition::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static synchronized Definition get(String rawId) {
        reloadIfChanged();
        return definitions.get(SettlementData.normalizeId(rawId));
    }

    public static synchronized void forceReload() {
        loadedModified = Long.MIN_VALUE;
        reloadIfChanged();
    }

    private static void reloadIfChanged() {
        try {
            if (!Files.exists(FILE)) writeDefaultFile();
            long modified = Files.getLastModifiedTime(FILE).toMillis();
            if (modified == loadedModified && !definitions.isEmpty()) return;
            Map<String, Definition> parsed = parse(Files.readString(FILE, StandardCharsets.UTF_8));
            definitions = parsed.isEmpty() ? builtinDefaults() : Map.copyOf(parsed);
            loadedModified = modified;
        } catch (Exception exception) {
            if (definitions.isEmpty()) definitions = Map.copyOf(builtinDefaults());
        }
    }

    private static Map<String, Definition> parse(String json) {
        Map<String, Definition> values = new LinkedHashMap<>();
        JsonElement root = JsonParser.parseString(json);
        JsonArray array = root.isJsonObject() && root.getAsJsonObject().has("companies")
                ? root.getAsJsonObject().getAsJsonArray("companies")
                : root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        for (JsonElement raw : array) {
            if (!raw.isJsonObject()) continue;
            JsonObject object = raw.getAsJsonObject();
            String id = SettlementData.normalizeId(string(object, "id", ""));
            if (id.isBlank()) continue;
            String name = SettlementData.cleanText(string(object, "name", id), id, 64);
            String icon = string(object, "icon_item", "minecraft:iron_sword");
            String templateRoster = SettlementData.normalizeId(string(object, "template_roster", "default"));
            int hireCost = integer(object, "hire_cost", 250, 0, 1_000_000);
            int upkeep = integer(object, "upkeep", 40, 1, 1_000_000);
            int interval = integer(object, "payment_interval_days", 1, 1, 365);
            int startingLoyalty = integer(object, "starting_loyalty", 35, 0, 100);
            List<UnitStack> units = new ArrayList<>();
            if (object.has("units") && object.get("units").isJsonArray()) {
                for (JsonElement unitRaw : object.getAsJsonArray("units")) {
                    if (!unitRaw.isJsonObject()) continue;
                    JsonObject unitObject = unitRaw.getAsJsonObject();
                    CampaignData.UnitType type = CampaignData.UnitType.parse(string(unitObject, "type", ""));
                    int count = integer(unitObject, "count", 0, 0, 500);
                    if (type != null && type != CampaignData.UnitType.LEVY && count > 0) units.add(new UnitStack(type, count));
                }
            }
            if (units.isEmpty()) units.add(new UnitStack(CampaignData.UnitType.RECRUIT, 10));
            values.put(id, new Definition(id, name, icon, templateRoster, hireCost, upkeep, interval, startingLoyalty, List.copyOf(units)));
        }
        return values;
    }

    private static void writeDefaultFile() throws IOException {
        Files.createDirectories(FILE.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Mercenary companies shown in Settlements > Military > Mercenary Rosters. template_roster references a Settlements unit roster configured with /settlements admin roster. icon_item may be any installed item ID.");
        JsonArray companies = new JsonArray();
        for (Definition definition : builtinDefaults().values()) companies.add(toJson(definition));
        root.add("companies", companies);
        Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static JsonObject toJson(Definition definition) {
        JsonObject object = new JsonObject();
        object.addProperty("id", definition.id()); object.addProperty("name", definition.name());
        object.addProperty("icon_item", definition.iconItem()); object.addProperty("template_roster", definition.templateRoster());
        object.addProperty("hire_cost", definition.hireCost()); object.addProperty("upkeep", definition.upkeep());
        object.addProperty("payment_interval_days", definition.paymentIntervalDays()); object.addProperty("starting_loyalty", definition.startingLoyalty());
        JsonArray units = new JsonArray();
        for (UnitStack stack : definition.units()) {
            JsonObject entry = new JsonObject(); entry.addProperty("type", stack.type().name()); entry.addProperty("count", stack.count()); units.add(entry);
        }
        object.add("units", units);
        return object;
    }

    private static Map<String, Definition> builtinDefaults() {
        Map<String, Definition> values = new LinkedHashMap<>();
        values.put("free_spears", new Definition("free_spears", "Free Spears", "minecraft:iron_sword", "default", 350, 45, 1, 35,
                List.of(new UnitStack(CampaignData.UnitType.RECRUIT, 12), new UnitStack(CampaignData.UnitType.SHIELDMAN, 8))));
        values.put("marcher_bowmen", new Definition("marcher_bowmen", "Marcher Bowmen", "minecraft:bow", "default", 300, 40, 1, 30,
                List.of(new UnitStack(CampaignData.UnitType.BOWMAN, 16), new UnitStack(CampaignData.UnitType.RECRUIT, 4))));
        values.put("riders_of_fortune", new Definition("riders_of_fortune", "Riders of Fortune", "minecraft:saddle", "default", 500, 65, 1, 40,
                List.of(new UnitStack(CampaignData.UnitType.HORSEMAN, 10))));
        return values;
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private static int integer(JsonObject object, String key, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, object.has(key) ? object.get(key).getAsInt() : fallback)); }
        catch (RuntimeException exception) { return fallback; }
    }

    public record Definition(String id, String name, String iconItem, String templateRoster, int hireCost,
                             int upkeep, int paymentIntervalDays, int startingLoyalty, List<UnitStack> units) {
        public Definition {
            id = SettlementData.normalizeId(id);
            name = SettlementData.cleanText(name, id, 64);
            iconItem = iconItem == null || iconItem.isBlank() ? "minecraft:iron_sword" : iconItem;
            templateRoster = SettlementData.normalizeId(templateRoster);
            if (templateRoster.isBlank()) templateRoster = "default";
            hireCost = Math.max(0, hireCost); upkeep = Math.max(1, upkeep); paymentIntervalDays = Math.max(1, paymentIntervalDays);
            startingLoyalty = Math.max(0, Math.min(100, startingLoyalty)); units = units == null ? List.of() : List.copyOf(units);
        }
        public int size() { return units.stream().mapToInt(UnitStack::count).sum(); }
        public String composition() {
            return units.stream().map(value -> value.count() + " " + value.type().displayName()).reduce((a, b) -> a + ", " + b).orElse("No troops");
        }
    }

    public record UnitStack(CampaignData.UnitType type, int count) {
        public UnitStack { count = Math.max(0, count); }
    }
}
