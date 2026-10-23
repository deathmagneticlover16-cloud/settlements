package ee.lastpagans.settlements;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server/common balance knobs for mechanics that Recruits itself cannot configure low enough. */
public final class SettlementConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_RECRUIT_LEVEL_OVERRIDE;
    public static final ForgeConfigSpec.IntValue RECRUIT_LEVEL_CAP;
    public static final ForgeConfigSpec.IntValue PLAYER_LEVY_CAP;
    public static final ForgeConfigSpec.IntValue FACTION_LEVY_CAP;
    public static final ForgeConfigSpec.IntValue SMALL_LEVY_HEALTH;
    public static final ForgeConfigSpec.IntValue LARGE_LEVY_HEALTH;
    public static final ForgeConfigSpec.IntValue LEVY_LEVEL_CAP;
    public static final ForgeConfigSpec.IntValue SMALL_LEVY_SIZE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue LEVY_SECONDS_PER_SOLDIER;
    public static final ForgeConfigSpec.IntValue LEVY_MIN_MUSTER_SECONDS;
    public static final ForgeConfigSpec.IntValue LEVY_MAX_MUSTER_MINUTES;
    public static final ForgeConfigSpec.IntValue LEVY_FULL_COOLDOWN_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_MIN_COOLDOWN_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_EXTENDED_SERVICE_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_WEARY_SERVICE_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_EXHAUSTION_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_OUTRAGE_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_EMERGENCY_RELIEF_PERCENT;
    public static final ForgeConfigSpec.BooleanValue BLOCK_LEVY_ARMOR_STAND_XP;
    public static final ForgeConfigSpec.IntValue DEFAULT_MAX_SPOUSES;
    public static final ForgeConfigSpec.IntValue ISLAM_MAX_SPOUSES;
    public static final ForgeConfigSpec.IntValue SECOND_SPOUSE_EFFECT_PERCENT;
    public static final ForgeConfigSpec.IntValue THIRD_SPOUSE_EFFECT_PERCENT;
    public static final ForgeConfigSpec.IntValue LATER_SPOUSE_EFFECT_PERCENT;
    public static final ForgeConfigSpec.IntValue PRAYER_DEVOTION_GAIN;
    public static final ForgeConfigSpec.IntValue CURRENCY_DONATION_DEVOTION_GAIN;
    public static final ForgeConfigSpec.IntValue CURRENCY_ITEM_TREASURY_VALUE;
    public static final ForgeConfigSpec.IntValue CHARACTER_RENAME_CURRENCY_COST;
    public static final ForgeConfigSpec.IntValue UPKEEP_CHEST_TARGET_DAYS;
    public static final ForgeConfigSpec.IntValue UPKEEP_CHEST_MAX_ITEMS_PER_SWEEP;
    public static final ForgeConfigSpec.IntValue TAX_POPULATION_PER_COIN;
    public static final ForgeConfigSpec.IntValue RESIDENT_BOUNDARY_CHECK_TICKS;
    public static final ForgeConfigSpec.BooleanValue SPAWN_FIRST_SETTLERS;
    public static final ForgeConfigSpec.IntValue FIRST_SETTLER_COUNT;
    public static final ForgeConfigSpec.IntValue FIRST_SETTLER_MIN_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue FIRST_SETTLER_MAX_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue SETTLER_CONTRACT_FASTEST_MINUTES;
    public static final ForgeConfigSpec.IntValue SETTLER_CONTRACT_SLOWEST_MINUTES;
    public static final ForgeConfigSpec.IntValue SETTLER_CONTRACT_MIN_STABILITY;
    public static final ForgeConfigSpec.IntValue SETTLER_CONTRACT_FOOD_RESERVE;
    public static final ForgeConfigSpec.IntValue SETTLER_CONTRACT_FOOD_COST;
    public static final ForgeConfigSpec.IntValue SETTLER_CONTRACT_MAX_PENDING;
    public static final ForgeConfigSpec.IntValue SETTLEMENT_FOUNDING_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue RESEARCH_FALLBACK_HOURS;
    public static final ForgeConfigSpec.IntValue SCHOLARLY_RESEARCH_BONUS_PERCENT;
    public static final ForgeConfigSpec.IntValue RESERVED_RELIABILITY_MAGNITUDE_PERCENT;
    public static final ForgeConfigSpec.IntValue PLAGUE_DAILY_CHANCE_PER_10000;
    public static final ForgeConfigSpec.IntValue HEALTHY_PLAGUE_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue PLAGUE_DURATION_HOURS;
    public static final ForgeConfigSpec.IntValue CAPTURE_ZONE_MIN_SIZE;
    public static final ForgeConfigSpec.IntValue CAPTURE_ZONE_MIN_SPACING;
    public static final ForgeConfigSpec.IntValue CAPTURE_SECONDARY_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_MAIN_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_MAIN_MAJOR_CITY_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_MAX_SPEED_TROOPS;
    public static final ForgeConfigSpec.IntValue MAJOR_CITY_MIN_POPULATION;
    public static final ForgeConfigSpec.IntValue MAJOR_CITY_MIN_STABILITY;
    public static final ForgeConfigSpec.BooleanValue FACTION_FOUNDING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FACTION_FOUNDING_REQUIRE_TOWN;
    public static final ForgeConfigSpec.IntValue FACTION_FOUNDING_MIN_POPULATION;
    public static final ForgeConfigSpec.IntValue FACTION_FOUNDING_MIN_STABILITY;
    public static final ForgeConfigSpec.IntValue FACTION_FOUNDING_TREASURY_COST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("recruits_compatibility");
        ENABLE_RECRUIT_LEVEL_OVERRIDE = builder.comment(
                "Temporarily overrides Villager Recruits' effective maximum level. Disable this when another addon takes over the level cap.")
                .define("enableRecruitLevelOverride", true);
        RECRUIT_LEVEL_CAP = builder.comment("Maximum XP level for loaded recruits while the override is enabled.")
                .defineInRange("recruitLevelCap", 5, 1, 100);
        builder.pop();

        builder.push("levies");
        PLAYER_LEVY_CAP = builder.comment("Maximum deployed levymen owned by one player.")
                .defineInRange("playerLevyCap", 300, 1, 5000);
        FACTION_LEVY_CAP = builder.comment("Maximum deployed levymen across one faction.")
                .defineInRange("factionLevyCap", 500, 1, 10000);
        SMALL_LEVY_HEALTH = builder.comment("Maximum health of levies smaller than 100 men.")
                .defineInRange("smallLevyHealth", 12, 1, 100);
        LARGE_LEVY_HEALTH = builder.comment("Maximum health of levies of 100 men or larger.")
                .defineInRange("largeLevyHealth", 10, 1, 100);
        LEVY_LEVEL_CAP = builder.comment("Maximum XP level for levy-tagged recruits. Also constrained by recruitLevelCap when that override is enabled.")
                .defineInRange("levyLevelCap", 3, 0, 100);
        SMALL_LEVY_SIZE_THRESHOLD = builder.comment("Levies below this many men use smallLevyHealth; this size and above use largeLevyHealth.")
                .defineInRange("smallLevySizeThreshold", 100, 2, 5000);
        LEVY_SECONDS_PER_SOLDIER = builder.comment("Base real-time mobilisation seconds per levyman before leader-trait modifiers.")
                .defineInRange("secondsPerSoldier", 4.0D, 0.1D, 60.0D);
        LEVY_MIN_MUSTER_SECONDS = builder.comment("Minimum real-time mobilisation duration for any levy.")
                .defineInRange("minimumMusterSeconds", 30, 1, 3600);
        LEVY_MAX_MUSTER_MINUTES = builder.comment("Maximum real-time mobilisation duration for one levy.")
                .defineInRange("maximumMusterMinutes", 15, 1, 1440);
        LEVY_FULL_COOLDOWN_HOURS = builder.comment("Cooldown for a full-size 300-man levy. Smaller levies scale down proportionally.")
                .defineInRange("fullLevyCooldownHours", 72, 1, 24 * 30);
        LEVY_MIN_COOLDOWN_HOURS = builder.comment("Minimum cooldown after returning any levy.")
                .defineInRange("minimumLevyCooldownHours", 12, 0, 24 * 30);
        LEVY_EXTENDED_SERVICE_HOURS = builder.comment("Hours deployed before Extended Service unrest begins.")
                .defineInRange("extendedServiceHours", 72, 1, 24 * 90);
        LEVY_WEARY_SERVICE_HOURS = builder.comment("Hours deployed before Weary Levies replaces Extended Service.")
                .defineInRange("wearyServiceHours", 120, 1, 24 * 90);
        LEVY_EXHAUSTION_HOURS = builder.comment("Hours deployed before Levy Exhaustion begins.")
                .defineInRange("levyExhaustionHours", 168, 1, 24 * 90);
        LEVY_OUTRAGE_HOURS = builder.comment("Hours deployed before Popular Outrage begins.")
                .defineInRange("popularOutrageHours", 240, 1, 24 * 90);
        LEVY_EMERGENCY_RELIEF_PERCENT = builder.comment("Percentage of remaining mobilisation/cooldown time removed when the faction is newly attacked. Never removes the timer entirely.")
                .defineInRange("emergencyReliefPercent", 40, 0, 90);
        BLOCK_LEVY_ARMOR_STAND_XP = builder.comment("Prevents levy-tagged recruits from gaining Recruits combat XP by repeatedly attacking armor stands. Real combat XP is unaffected.")
                .define("blockLevyArmorStandXp", true);
        builder.pop();

        builder.push("marriage");
        DEFAULT_MAX_SPOUSES = builder.comment("Maximum active spouses for faiths without a special rule.")
                .defineInRange("defaultMaximumSpouses", 1, 1, 8);
        ISLAM_MAX_SPOUSES = builder.comment("Maximum active spouses for characters whose faith is Islam.")
                .defineInRange("islamMaximumSpouses", 4, 1, 8);
        SECOND_SPOUSE_EFFECT_PERCENT = builder.comment("Settlement-effect strength of the second spouse.")
                .defineInRange("secondSpouseEffectPercent", 50, 0, 100);
        THIRD_SPOUSE_EFFECT_PERCENT = builder.comment("Settlement-effect strength of the third spouse.")
                .defineInRange("thirdSpouseEffectPercent", 25, 0, 100);
        LATER_SPOUSE_EFFECT_PERCENT = builder.comment("Settlement-effect strength of the fourth and later spouses.")
                .defineInRange("laterSpouseEffectPercent", 10, 0, 100);
        builder.pop();

        builder.push("religion");
        PRAYER_DEVOTION_GAIN = builder.comment("Devotion gained once per Minecraft day by visiting an eligible settlement Prayer Site.")
                .defineInRange("prayerDevotionGain", 3, 0, 100);
        CURRENCY_DONATION_DEVOTION_GAIN = builder.comment("Devotion gained when donating one item from #settlements:currency to the character's faith.")
                .defineInRange("currencyDonationDevotionGain", 2, 0, 100);
        builder.pop();

        builder.push("economy");
        CURRENCY_ITEM_TREASURY_VALUE = builder.comment("State Treasury value represented by one item in #settlements:currency when depositing or withdrawing physical currency.")
                .defineInRange("currencyItemTreasuryValue", 10, 1, 100000);
        CHARACTER_RENAME_CURRENCY_COST = builder.comment("Number of #settlements:currency items consumed when a created character changes their personal name. Main House edits remain free.")
                .defineInRange("characterRenameCurrencyCost", 10, 0, 100000);
        UPKEEP_CHEST_TARGET_DAYS = builder.comment("Automatic city-upkeep chest tries to maintain this many days of Food for civilians and tracked soldiers.")
                .defineInRange("upkeepChestTargetDays", 3, 1, 30);
        UPKEEP_CHEST_MAX_ITEMS_PER_SWEEP = builder.comment("Maximum edible item count consumed from a city-upkeep chest per 30-second maintenance sweep.")
                .defineInRange("upkeepChestMaxItemsPerSweep", 64, 1, 4096);
        builder.pop();

        builder.push("taxation");
        TAX_POPULATION_PER_COIN = builder.comment("Civilian population represented by one point of base daily State Treasury revenue before the tax rate is applied.")
                .defineInRange("populationPerCoin", 10, 1, 1000);
        builder.pop();

        builder.push("residents");
        RESIDENT_BOUNDARY_CHECK_TICKS = builder.comment("How often loaded registered villagers are checked against their settlement's linked Recruits claims. 100 ticks = 5 seconds.")
                .defineInRange("boundaryCheckIntervalTicks", 100, 20, 1200);
        SPAWN_FIRST_SETTLERS = builder.comment("Automatically sends the first civilian settlers to newly founded settlements.")
                .define("spawnFirstSettlers", true);
        FIRST_SETTLER_COUNT = builder.comment("Number of villagers in the first-settlers arrival. Set to 0 to disable without changing spawnFirstSettlers.")
                .defineInRange("firstSettlerCount", 2, 0, 16);
        FIRST_SETTLER_MIN_DELAY_SECONDS = builder.comment("Minimum real-time delay after founding before the first settlers can arrive.")
                .defineInRange("firstSettlerMinDelaySeconds", 10, 0, 300);
        FIRST_SETTLER_MAX_DELAY_SECONDS = builder.comment("Maximum real-time delay after founding before the first settlers can arrive.")
                .defineInRange("firstSettlerMaxDelaySeconds", 60, 0, 600);
        SETTLER_CONTRACT_FASTEST_MINUTES = builder.comment("Fastest possible Settler Contract interval when Stability, free Housing and Food are all excellent.")
                .defineInRange("settlerContractFastestMinutes", 2, 1, 1440);
        SETTLER_CONTRACT_SLOWEST_MINUTES = builder.comment("Slowest eligible Settler Contract interval when the settlement only barely meets the minimum requirements.")
                .defineInRange("settlerContractSlowestMinutes", 12, 1, 1440);
        SETTLER_CONTRACT_MIN_STABILITY = builder.comment("Minimum settlement Stability required to attract a new Settler Contract.")
                .defineInRange("settlerContractMinimumStability", 60, 0, 100);
        SETTLER_CONTRACT_FOOD_RESERVE = builder.comment("Minimum Food stockpile required before a new Settler Contract can be generated.")
                .defineInRange("settlerContractFoodReserve", 20, 0, 1000000);
        SETTLER_CONTRACT_FOOD_COST = builder.comment("Food consumed when a new Settler Contract is generated.")
                .defineInRange("settlerContractFoodCost", 10, 0, 1000000);
        SETTLER_CONTRACT_MAX_PENDING = builder.comment("Maximum unclaimed Settler Contracts that one settlement can accumulate.")
                .defineInRange("settlerContractMaxPending", 3, 1, 64);
        SETTLEMENT_FOUNDING_COOLDOWN_MINUTES = builder.comment("Active-server minutes one player must wait between founding settlements. Prevents joke-spam without imposing a hard daily limit.")
                .defineInRange("settlementFoundingCooldownMinutes", 10, 0, 1440);
        builder.pop();

        builder.push("character_traits");
        RESERVED_RELIABILITY_MAGNITUDE_PERCENT = builder.comment("Percentage of normal Reliability changes applied while the faction ruler has Reserved. Applies to both gains and losses.")
                .defineInRange("reservedReliabilityMagnitudePercent", 65, 1, 100);
        HEALTHY_PLAGUE_CHANCE_PERCENT = builder.comment("Percentage of normal plague chance retained when the settlement ruler has Healthy. 50 means half the normal chance.")
                .defineInRange("healthyPlagueChancePercent", 50, 0, 100);
        builder.pop();


        builder.push("capture_zones");
        CAPTURE_ZONE_MIN_SIZE = builder.comment("Minimum width and depth of a settlement Capture Zone.")
                .defineInRange("minimumSize", 12, 6, 128);
        CAPTURE_ZONE_MIN_SPACING = builder.comment("Minimum horizontal distance between Capture Zone centres in the same settlement.")
                .defineInRange("minimumSpacing", 24, 0, 512);
        CAPTURE_SECONDARY_SECONDS = builder.comment("Base uncontested seconds required to capture a secondary objective.")
                .defineInRange("secondaryCaptureSeconds", 120, 10, 3600);
        CAPTURE_MAIN_SECONDS = builder.comment("Base uncontested seconds required to capture a normal Main objective after secondaries fall.")
                .defineInRange("mainCaptureSeconds", 180, 10, 7200);
        CAPTURE_MAIN_MAJOR_CITY_SECONDS = builder.comment("Base uncontested seconds required to capture a Major City's Main objective.")
                .defineInRange("majorCityMainCaptureSeconds", 420, 10, 14400);
        CAPTURE_MAX_SPEED_TROOPS = builder.comment("Attacker count at which extra troops stop accelerating a Capture Zone.")
                .defineInRange("maximumCaptureSpeedTroops", 20, 1, 200);
        builder.pop();

        builder.push("major_city");
        MAJOR_CITY_MIN_POPULATION = builder.comment("Minimum civilian population for Research Tier IV / Major City status.")
                .defineInRange("minimumPopulation", 500, 0, 1000000);
        MAJOR_CITY_MIN_STABILITY = builder.comment("Minimum Stability for Major City research status.")
                .defineInRange("minimumStability", 65, 0, 100);
        builder.pop();

        builder.push("events");
        PLAGUE_DAILY_CHANCE_PER_10000 = builder.comment("Base chance per settlement per Minecraft day for a plague event, in ten-thousandths. 35 = 0.35% per day.")
                .defineInRange("plagueDailyChancePer10000", 35, 0, 10000);
        PLAGUE_DURATION_HOURS = builder.comment("Active-server hours a naturally occurring Plague modifier lasts.")
                .defineInRange("plagueDurationHours", 12, 1, 24 * 30);
        builder.pop();

        builder.push("research");
        RESEARCH_FALLBACK_HOURS = builder.comment("Fallback active-server research duration when History Stages reports no duration for a technology.")
                .defineInRange("fallbackResearchHours", 6, 1, 24 * 30);
        SCHOLARLY_RESEARCH_BONUS_PERCENT = builder.comment("Research-speed bonus supplied by the Scholarly ruler trait when History Stages research is active.")
                .defineInRange("scholarlyResearchBonusPercent", 15, 0, 200);
        builder.pop();

        builder.push("faction_founding");
        FACTION_FOUNDING_ENABLED = builder.comment("Allow independent rulers to found full political factions through Settlements. Disable for servers that want factions to be admin/story controlled only.")
                .define("enabled", true);
        FACTION_FOUNDING_REQUIRE_TOWN = builder.comment("Require an established Town/City-State before an independent ruler may form a full political faction.")
                .define("requireTown", true);
        FACTION_FOUNDING_MIN_POPULATION = builder.comment("Minimum settlement population required to found a political faction.")
                .defineInRange("minimumPopulation", 75, 0, 100000);
        FACTION_FOUNDING_MIN_STABILITY = builder.comment("Minimum stability of the founding settlement.")
                .defineInRange("minimumStability", 60, 0, 100);
        FACTION_FOUNDING_TREASURY_COST = builder.comment("State Treasury spent when upgrading from an independent polity into a full faction. Recruits' own configured faction-creation item cost still applies too.")
                .defineInRange("treasuryCost", 500, 0, 1000000);
        builder.pop();
        SPEC = builder.build();
    }

    private SettlementConfig() {}

    public static int effectiveLevyLevelCap() {
        int cap = LEVY_LEVEL_CAP.get();
        return ENABLE_RECRUIT_LEVEL_OVERRIDE.get() ? Math.min(cap, RECRUIT_LEVEL_CAP.get()) : cap;
    }

    public static int maxSpousesForFaith(String faith) {
        return faith != null && faith.equalsIgnoreCase("Islam") ? ISLAM_MAX_SPOUSES.get() : DEFAULT_MAX_SPOUSES.get();
    }

    public static long hoursMillis(ForgeConfigSpec.IntValue hours) {
        return hoursMillis(hours.get().longValue());
    }

    public static long hoursMillis(long hours) {
        return Math.max(0L, hours) * 60L * 60L * 1000L;
    }
}
