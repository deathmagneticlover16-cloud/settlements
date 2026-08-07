package ee.lastpagans.settlementprototype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Data-driven defaults used by Settlements 2.0.0. Server owners can later replace
 * these lists with JSON without changing any save format because saves store IDs.
 */
public final class CampaignDefinitions {
    public static final List<String> RELIGIONS = List.of(
            "Catholic Christianity",
            "Orthodox Christianity",
            "Baltic Paganism",
            "Finnic Paganism",
            "Norse Paganism",
            "Slavic Paganism",
            "Islam",
            "Judaism",
            "Other"
    );

    public static final List<String> ADDRESSES = List.of("Male", "Female", "Neutral");
    public static final List<String> PERSONAL_TITLES = List.of(
            "Lord", "Lady", "Baron", "Baroness", "Duke", "Duchess",
            "Chieftain", "Elder", "Thane", "Commander"
    );

    private static final Map<String, TraitDefinition> TRAITS = new LinkedHashMap<>();

    static {
        register(new TraitDefinition("responsible", "Responsible", "minecraft:writable_book", TraitTone.POSITIVE,
                "A careful administrator who keeps the household and settlement in order.",
                List.of(new TraitEffect("Settlement stability", "+1 per day", 1, EffectKind.STABILITY))));
        register(new TraitDefinition("brave", "Brave", "minecraft:iron_sword", TraitTone.POSITIVE,
                "Steady in danger and inspiring to soldiers.",
                List.of(new TraitEffect("Recruit morale", "+6", 6, EffectKind.MORALE))));
        register(new TraitDefinition("diplomatic", "Diplomatic", "minecraft:paper", TraitTone.POSITIVE,
                "Skilled at compromise and maintaining useful relations.",
                List.of(new TraitEffect("Diplomatic relations", "+8", 8, EffectKind.RELATIONS))));
        register(new TraitDefinition("frugal", "Frugal", "minecraft:emerald", TraitTone.POSITIVE,
                "Avoids waste and stretches settlement funds further.",
                List.of(new TraitEffect("Settlement wealth", "+1 per day", 1, EffectKind.WEALTH))));
        register(new TraitDefinition("warlike", "Warlike", "minecraft:shield", TraitTone.MIXED,
                "A forceful military character whose confidence can also strain peace.",
                List.of(
                        new TraitEffect("Recruit morale", "+8", 8, EffectKind.MORALE),
                        new TraitEffect("Diplomatic relations", "-4", -4, EffectKind.RELATIONS)
                )));
        register(new TraitDefinition("reserved", "Reserved", "minecraft:book", TraitTone.NEUTRAL,
                "Quiet and difficult to read; neither a clear boon nor a serious flaw.", List.of()));
        register(new TraitDefinition("extravagant", "Extravagant", "minecraft:gold_ingot", TraitTone.NEGATIVE,
                "Spends freely on display, gifts and household comforts.",
                List.of(new TraitEffect("Settlement wealth", "-1 per day", -1, EffectKind.WEALTH))));
        register(new TraitDefinition("frail", "Frail", "minecraft:spider_eye", TraitTone.NEGATIVE,
                "Poor health and limited ability to withstand long campaigns.",
                List.of(new TraitEffect("Recruit morale", "-3", -3, EffectKind.MORALE))));
        register(new TraitDefinition("fertile", "Fertile", "minecraft:wheat_seeds", TraitTone.POSITIVE,
                "A healthy dynastic match, valuable for future inheritance systems.", List.of()));
        register(new TraitDefinition("reliable", "Reliable", "minecraft:chain", TraitTone.POSITIVE,
                "Known for keeping promises and honouring agreements.",
                List.of(new TraitEffect("Diplomatic reliability", "+8", 8, EffectKind.RELIABILITY))));
        register(new TraitDefinition("zealous", "Zealous", "minecraft:golden_apple", TraitTone.MIXED,
                "Deeply committed to faith, strengthening co-religionists but worsening religious friction.",
                List.of(new TraitEffect("Same-faith relations", "+6", 6, EffectKind.RELATIONS))));
        register(new TraitDefinition("merciful", "Merciful", "minecraft:white_wool", TraitTone.POSITIVE,
                "A restrained ruler whose subjects are less fearful of punishment.",
                List.of(new TraitEffect("Settlement stability", "+1 per day", 1, EffectKind.STABILITY))));
    }

    private CampaignDefinitions() {}

    private static void register(TraitDefinition definition) {
        TRAITS.put(definition.id(), definition);
    }

    public static TraitDefinition trait(String id) {
        return TRAITS.get(normalize(id));
    }

    public static List<TraitDefinition> traits() {
        return List.copyOf(TRAITS.values());
    }

    public static List<String> assignStartingTraits(UUID playerId, String seedText) {
        List<String> pool = new ArrayList<>(List.of(
                "responsible", "brave", "diplomatic", "frugal", "warlike",
                "reserved", "extravagant", "frail", "reliable", "merciful", "zealous"
        ));
        long seed = playerId == null ? 0L : playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
        if (seedText != null) seed ^= seedText.toLowerCase(Locale.ROOT).hashCode() * 31L;
        Random random = new Random(seed);
        String first = pool.remove(random.nextInt(pool.size()));
        String second = pool.remove(random.nextInt(pool.size()));
        return List.of(first, second);
    }

    public static int totalEffect(List<String> traitIds, EffectKind kind) {
        if (traitIds == null) return 0;
        int total = 0;
        for (String id : traitIds) {
            TraitDefinition definition = trait(id);
            if (definition == null) continue;
            for (TraitEffect effect : definition.effects()) {
                if (effect.kind() == kind) total += effect.amount();
            }
        }
        return total;
    }

    public static int religionRelationModifier(String first, String second) {
        if (first == null || second == null || first.isBlank() || second.isBlank()) return 0;
        if (first.equalsIgnoreCase(second)) return 10;
        boolean firstChristian = isChristian(first);
        boolean secondChristian = isChristian(second);
        if (firstChristian && secondChristian) return 3;
        boolean firstPagan = isPagan(first);
        boolean secondPagan = isPagan(second);
        if (firstPagan && secondPagan) return 2;
        return -8;
    }

    public static boolean isValidReligion(String value) {
        return value != null && RELIGIONS.stream().anyMatch(option -> option.equalsIgnoreCase(value.trim()));
    }

    public static boolean isValidAddress(String value) {
        return value != null && ADDRESSES.stream().anyMatch(option -> option.equalsIgnoreCase(value.trim()));
    }

    public static boolean isValidPersonalTitle(String value) {
        return value != null && PERSONAL_TITLES.stream().anyMatch(option -> option.equalsIgnoreCase(value.trim()));
    }

    public static String defaultRankTitle(int tier) {
        return switch (Math.max(0, Math.min(4, tier))) {
            case 4 -> "Leader";
            case 3 -> "Noble";
            case 2 -> "Officer";
            case 1 -> "Freeman";
            default -> "Commoner";
        };
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static boolean isChristian(String value) {
        return value.toLowerCase(Locale.ROOT).contains("christian");
    }

    private static boolean isPagan(String value) {
        return value.toLowerCase(Locale.ROOT).contains("pagan");
    }

    public enum TraitTone { POSITIVE, NEGATIVE, MIXED, NEUTRAL }
    public enum EffectKind { STABILITY, WEALTH, MORALE, RELATIONS, RELIABILITY }

    public record TraitEffect(String label, String displayValue, int amount, EffectKind kind) {}

    public record TraitDefinition(
            String id,
            String name,
            String iconItem,
            TraitTone tone,
            String description,
            List<TraitEffect> effects
    ) {
        public int effect(EffectKind kind) {
            if (kind == null) return 0;
            return effects.stream().filter(value -> value.kind() == kind).mapToInt(TraitEffect::amount).sum();
        }

        public String effectsText() {
            if (effects.isEmpty()) return "No direct mechanical effect";
            return effects.stream()
                    .map(value -> value.label() + " " + value.displayValue())
                    .reduce((first, second) -> first + "; " + second)
                    .orElse("No direct mechanical effect");
        }
    }
}
