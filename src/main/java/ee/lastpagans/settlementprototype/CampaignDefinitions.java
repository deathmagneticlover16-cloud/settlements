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
        register(new TraitDefinition("charismatic", "Charismatic", "minecraft:emerald", TraitTone.POSITIVE,
                "Naturally persuasive and more likely to attract desirable political marriage candidates.",
                List.of(new TraitEffect("Spouse candidate quality", "+1 tier", 1, EffectKind.SPOUSE_QUALITY))));
        register(new TraitDefinition("healthy", "Healthy", "minecraft:apple", TraitTone.POSITIVE,
                "Robust and energetic; useful for future health and event systems.", List.of()));
        register(new TraitDefinition("rousing_orator", "Rousing Orator", "minecraft:goat_horn", TraitTone.POSITIVE,
                "Can mobilise the population faster through forceful speeches.",
                List.of(new TraitEffect("Levy muster time", "-20%", -20, EffectKind.LEVY_MUSTER))));
        register(new TraitDefinition("war_leader", "War Leader", "minecraft:iron_helmet", TraitTone.POSITIVE,
                "Experienced at preparing irregular troops for battle.",
                List.of(new TraitEffect("Levy maximum health", "+1", 1, EffectKind.LEVY_HEALTH),
                        new TraitEffect("Levy starting XP", "+20", 20, EffectKind.LEVY_XP))));
        register(new TraitDefinition("beloved_commander", "Beloved Commander", "minecraft:white_banner", TraitTone.POSITIVE,
                "Soldiers are more willing to endure hardship under this leader.",
                List.of(new TraitEffect("Recruit morale", "+5", 5, EffectKind.MORALE))));
        register(new TraitDefinition("ferocious_leader", "Ferocious Leader", "minecraft:netherite_sword", TraitTone.MIXED,
                "A frightening battlefield presence who drives levies into action quickly.",
                List.of(new TraitEffect("Recruit morale", "+4", 4, EffectKind.MORALE),
                        new TraitEffect("Levy muster time", "-20%", -20, EffectKind.LEVY_MUSTER))));
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

    /** Traits offered to newly-created player characters. Legacy reliability/fertility remain readable but are not rolled. */
    public static List<TraitDefinition> playableTraits() {
        return TRAITS.values().stream()
                .filter(value -> !value.id().equals("reliable") && !value.id().equals("fertile"))
                .toList();
    }

    /** Deterministic three-card roll. The server uses the same method to reject modified-client trait cheating. */
    public static List<String> traitChoices(UUID playerId, String seedText, int round, boolean rerolled, List<String> excluded) {
        List<TraitDefinition> pool = new ArrayList<>(playableTraits());
        if (excluded != null) pool.removeIf(value -> excluded.stream().anyMatch(id -> value.id().equals(normalize(id))));
        long seed = playerId == null ? 0L : playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
        if (seedText != null) seed ^= (long) seedText.toLowerCase(Locale.ROOT).hashCode() * 31L;
        seed ^= 0x9E3779B97F4A7C15L * (Math.max(0, round) + 1L);
        if (rerolled) seed ^= 0xD1B54A32D192ED03L;
        java.util.Collections.shuffle(pool, new Random(seed));
        List<TraitDefinition> picked = new ArrayList<>(pool.stream().limit(3).toList());
        boolean allBad = !picked.isEmpty() && picked.stream().allMatch(value -> value.tone() == TraitTone.NEGATIVE);
        if (allBad) {
            TraitDefinition safer = pool.stream().filter(value -> value.tone() != TraitTone.NEGATIVE && !picked.contains(value)).findFirst().orElse(null);
            if (safer != null) picked.set(0, safer);
        }
        return picked.stream().map(TraitDefinition::id).toList();
    }

    public static boolean validateTraitSelection(UUID playerId, String seedText, List<String> selected, int rerollRound) {
        if (selected == null || selected.size() != 3 || selected.stream().map(CampaignDefinitions::normalize).distinct().count() != 3) return false;
        List<String> previous = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            String choice = normalize(selected.get(round));
            List<String> options = traitChoices(playerId, seedText, round, rerollRound == round, previous);
            if (!options.contains(choice)) return false;
            previous.add(choice);
        }
        return rerollRound >= -1 && rerollRound <= 2;
    }

    /** Legacy fallback for old callers; new character creation uses the three-card chooser. */
    public static List<String> assignStartingTraits(UUID playerId, String seedText) {
        List<String> selected = new ArrayList<>();
        for (int round = 0; round < 3; round++) selected.add(traitChoices(playerId, seedText, round, false, selected).get(0));
        return List.copyOf(selected);
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
    public enum EffectKind { STABILITY, WEALTH, MORALE, RELATIONS, RELIABILITY, SPOUSE_QUALITY, LEVY_HEALTH, LEVY_XP, LEVY_MUSTER }

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
