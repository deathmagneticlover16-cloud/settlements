package ee.lastpagans.settlements;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;

/** Canonical culture dossier text supplied with the Attila diplomacy specification. */
public record CultureDefinition(String id, String name, String primaryFaith, List<String> secondaryFaiths,
                                List<String> advantages, List<String> drawbacks) {
    private static final Map<String, CultureDefinition> DEFINITIONS = definitions();

    public CultureDefinition {
        secondaryFaiths = List.copyOf(secondaryFaiths);
        advantages = List.copyOf(advantages);
        drawbacks = List.copyOf(drawbacks);
    }

    public static List<CultureDefinition> values() { return List.copyOf(DEFINITIONS.values()); }
    public static CultureDefinition get(String raw) { return DEFINITIONS.get(normalize(raw)); }
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static Map<String, CultureDefinition> definitions() {
        Map<String, CultureDefinition> values = new LinkedHashMap<>();
        add(values, "norse", "Norse", "Norse Paganism", List.of(),
                List.of("Early game access to a selection of longships", "0.75x long-distance trading penalty", "+7,5% melee damage and melee skill for all melee units"),
                List.of("-25% carry capacity in warm provinces", "-7,5% archer accuracy"));
        add(values, "celtic", "Celtic", "Celtic Paganism", List.of("Catholic Christianity"),
                List.of("+7,5% charge bonus when for infantry units", "+10% charge speed on all units", "+7,5% morale bonus for all melee units", "-15% levy loss penalties", "+7,5% higher maximum manpower pool"),
                List.of("0.80x carry capacity for all units", "1.85x diplomatic penalty with all Anglian states"));
        add(values, "anglian", "Anglian", "Catholic Christianity", List.of(),
                List.of("Early game access to longbows", "+7,5% melee defense in Shieldwall", "+10% levy size", "+15% commander bodyguard morale"),
                List.of("0.85x melee skill for horsemen units", "15% diplomatic penalty with non-Christian states", "+7.5% morale shock received when charged at"));
        add(values, "berber", "Berber", "Islam", List.of("Orthodox Christianity"),
                List.of("Early game access to dhows", "1.35x income from all trade agreements", "No attrition while moving through deserts", "+10% campaign map movement speed in deserts", "Access to camel spearmen", "Able to propose trade agreements without any resource"),
                List.of("-7,5% stability in controlled provinces that are not desert"));
        add(values, "byzantine", "Byzantine", "Orthodox Christianity", List.of(),
                List.of("Early game access to galleys", "0.925x stability loss in distant provinces", "+10% research speed bonus", "+10% more tax income from Mediterranean provinces"),
                List.of("1.20x higher upkeep for all settlements", "+7,5% diplomatic penalty for all Muslim states", "+12,5% diplomatic penalty for all catholic states", "1.15x higher troop recruitment costs"));
        add(values, "finnic", "Finnic", "Finnic Paganism", List.of(),
                List.of("+15% furs production in controlled provinces", "+10% amber production in controlled provinces", "+17% timber production in controlled provinces", "No unit attrition when moving through marshes and bogs", "+10% campaign map movement speed in forest biomes", "+12,5% melee skill in forest biomes", "+12,5% morale bonus when fighting in a forest biome of a controlled province", "+10% charge bonus when fighting in forest biome", "+50% hiddenness in forest biomes", "+15% enemy attrition in Finnic lands", "Early game access to primitive long ships"),
                List.of("0.80x stability for cities and major cities", "0.95x levy size", "-7,5% research speed", "1.10x diplomatic penalty with Finnic states"));
        add(values, "baltic", "Baltic", "Baltic Paganism", List.of("Catholic Christianity"),
                List.of("+10% timber production in controlled provinces", "+10% cavalry charge speed", "+7,5% charge bonus for cavalry units", "+7,5% morale when at war with a Christian state", "-7,5% morale shock received from cavalry charges", "1.15x levy upkeep time", "+7,5% melee skill for all infantry units", "+7,5% morale when fighting in controlled province", "+15% enemy attrition in Baltic lands"),
                List.of("1.35x unrest gain in religiously incompatible settlements", "1.15x diplomatic penalty with Christian states", "1.05x diplomatic penalty with Finnic states", "1.50x unrest growth in all lands except Baltic and Slavic", "1.65x morale loss for recruits when killing villagers", "0.80x trade range", "0.95x morale for recruits when fighting pagan force"));
        add(values, "slavic", "Slavic", "Slavic Paganism", List.of("Orthodox Christianity"),
                List.of("+5% trade income per every owned province with a river", "-7,5% levy muster time", "+1,25% population growth modifier per owned agricultural province", "+2,5% tax income per agricultural province owned", "+5% food generated from agricultural provinces", "0.50x religious incompatibility penalties", "+10% morale for all units while fighting defensive war on controlled Slavic province"),
                List.of("+10% unrest growth during offensive wars in non-Slavic provinces", "1.20x diplomatic penalty with Finnic states", "1.25x diplomatic penalty with Byzantine states", "0.90x trade range"));
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static void add(Map<String, CultureDefinition> values, String id, String name, String primary,
                            List<String> secondary, List<String> advantages, List<String> drawbacks) {
        values.put(id, new CultureDefinition(id, name, primary, secondary, advantages, drawbacks));
    }
}
