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
    public static final ForgeConfigSpec.IntValue LEVY_SERVICE_GRACE_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_SERVICE_PRESSURE_STEP_HOURS;
    public static final ForgeConfigSpec.IntValue LEVY_EMERGENCY_RELIEF_PERCENT;
    public static final ForgeConfigSpec.BooleanValue BLOCK_LEVY_ARMOR_STAND_XP;
    public static final ForgeConfigSpec.IntValue PRAYER_DEVOTION_GAIN;
    public static final ForgeConfigSpec.IntValue SILVER_DONATION_DEVOTION_GAIN;
    public static final ForgeConfigSpec.IntValue SILVER_TREASURY_VALUE;
    public static final ForgeConfigSpec.IntValue CHARACTER_RENAME_SILVER_COST;
    public static final ForgeConfigSpec.IntValue SETTLEMENT_RENAME_SILVER_COST;
    public static final ForgeConfigSpec.IntValue UPKEEP_CHEST_TARGET_DAYS;
    public static final ForgeConfigSpec.IntValue UPKEEP_CHEST_MAX_ITEMS_PER_SWEEP;
    public static final ForgeConfigSpec.IntValue TAX_POPULATION_PER_COIN;
    public static final ForgeConfigSpec.IntValue FOREIGN_MANPOWER_SELL_VALUE;
    public static final ForgeConfigSpec.IntValue RELATION_NAP_MINIMUM;
    public static final ForgeConfigSpec.IntValue RELATION_WAR_AID_MINIMUM;
    public static final ForgeConfigSpec.IntValue RELATION_DEFENSIVE_ALLIANCE_MINIMUM;
    public static final ForgeConfigSpec.IntValue RELATION_MILITARY_ALLIANCE_MINIMUM;
    public static final ForgeConfigSpec.IntValue RELATION_VASSALAGE_MINIMUM;
    public static final ForgeConfigSpec.IntValue RESIDENT_BOUNDARY_CHECK_TICKS;
    public static final ForgeConfigSpec.BooleanValue SPAWN_FIRST_SETTLERS;
    public static final ForgeConfigSpec.IntValue FIRST_SETTLER_COUNT;
    public static final ForgeConfigSpec.IntValue FIRST_SETTLER_MIN_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue FIRST_SETTLER_MAX_DELAY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue IMMIGRATION_ENABLED;
    public static final ForgeConfigSpec.IntValue IMMIGRATION_MIN_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue IMMIGRATION_MAX_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue IMMIGRATION_MIN_STABILITY;
    public static final ForgeConfigSpec.IntValue IMMIGRATION_FOOD_RESERVE;
    public static final ForgeConfigSpec.IntValue IMMIGRATION_FOOD_COST;
    public static final ForgeConfigSpec.IntValue IMMIGRATION_MAX_PHYSICAL_RESIDENTS;
    public static final ForgeConfigSpec.IntValue SETTLEMENT_FOUNDING_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue RESEARCH_FALLBACK_HOURS;
    public static final ForgeConfigSpec.IntValue SCHOLARLY_RESEARCH_BONUS_PERCENT;
    public static final ForgeConfigSpec.IntValue RESERVED_RELIABILITY_MAGNITUDE_PERCENT;
    public static final ForgeConfigSpec.IntValue PLAGUE_DAILY_CHANCE_PER_10000;
    public static final ForgeConfigSpec.IntValue HEALTHY_PLAGUE_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue PLAGUE_DURATION_HOURS;
    public static final ForgeConfigSpec.IntValue CAPTURE_ZONE_MIN_SIZE;
    public static final ForgeConfigSpec.IntValue TOWN_SQUARE_CAPTURE_RADIUS;
    public static final ForgeConfigSpec.IntValue TOWN_SQUARE_CAPTURE_HEIGHT;
    public static final ForgeConfigSpec.IntValue CAPTURE_ZONE_MIN_SPACING;
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
        LEVY_SERVICE_GRACE_HOURS = builder.comment("Hours a levy may remain deployed before domestic pressure begins.")
                .defineInRange("serviceGraceHours", 72, 1, 24 * 90);
        LEVY_SERVICE_PRESSURE_STEP_HOURS = builder.comment("Hours of overdue service per additional point of domestic pressure.")
                .defineInRange("servicePressureStepHours", 24, 1, 24 * 30);
        LEVY_EMERGENCY_RELIEF_PERCENT = builder.comment("Percentage of remaining mobilisation/cooldown time removed when the faction is newly attacked. Never removes the timer entirely.")
                .defineInRange("emergencyReliefPercent", 40, 0, 90);
        BLOCK_LEVY_ARMOR_STAND_XP = builder.comment("Prevents levy-tagged recruits from gaining Recruits combat XP by repeatedly attacking armor stands. Real combat XP is unaffected.")
                .define("blockLevyArmorStandXp", true);
        builder.pop();

        builder.push("religion");
        PRAYER_DEVOTION_GAIN = builder.comment("Devotion gained once per Minecraft day by visiting an eligible settlement Prayer Site.")
                .defineInRange("prayerDevotionGain", 3, 0, 100);
        SILVER_DONATION_DEVOTION_GAIN = builder.comment("Devotion gained when donating one Silver Coin to the character's faith.")
                .defineInRange("silverDonationDevotionGain", 2, 0, 100);
        builder.pop();

        builder.push("economy");
        SILVER_TREASURY_VALUE = builder.comment("State Treasury value represented by one Silver Coin placed in an upkeep chest.")
                .defineInRange("silverTreasuryValue", 10, 1, 100000);
        CHARACTER_RENAME_SILVER_COST = builder.comment("Silver Coins consumed when a created character changes their personal name. Main House edits remain free.")
                .defineInRange("characterRenameSilverCost", 10, 0, 100000);
        SETTLEMENT_RENAME_SILVER_COST = builder.comment("Silver deducted from the State Treasury when a settlement is renamed.")
                .defineInRange("settlementRenameSilverCost", 5000, 5000, 5000);
        UPKEEP_CHEST_TARGET_DAYS = builder.comment("Automatic city-upkeep chest tries to maintain this many days of Food for civilians and tracked soldiers.")
                .defineInRange("upkeepChestTargetDays", 3, 1, 30);
        UPKEEP_CHEST_MAX_ITEMS_PER_SWEEP = builder.comment("Maximum edible item count consumed from a city-upkeep chest per 30-second maintenance sweep.")
                .defineInRange("upkeepChestMaxItemsPerSweep", 64, 1, 4096);
        builder.pop();

        builder.push("taxation");
        TAX_POPULATION_PER_COIN = builder.comment("Civilian population represented by one point of base daily State Treasury revenue before the tax rate is applied.")
                .defineInRange("populationPerCoin", 10, 1, 1000);
        FOREIGN_MANPOWER_SELL_VALUE = builder.comment("Silver added to the State Treasury when one Foreign Manpower is sold.")
                .defineInRange("foreignManpowerSellValue", 10, 1, 1000000);
        builder.pop();

        builder.push("diplomacy");
        RELATION_NAP_MINIMUM = builder.defineInRange("nonAggressionMinimum", -25, -100, 100);
        RELATION_WAR_AID_MINIMUM = builder.defineInRange("warAidMinimum", 10, -100, 100);
        RELATION_DEFENSIVE_ALLIANCE_MINIMUM = builder.defineInRange("defensiveAllianceMinimum", 15, -100, 100);
        RELATION_MILITARY_ALLIANCE_MINIMUM = builder.defineInRange("militaryAllianceMinimum", 35, -100, 100);
        RELATION_VASSALAGE_MINIMUM = builder.defineInRange("voluntaryVassalageMinimum", 25, -100, 100);
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
        IMMIGRATION_ENABLED = builder.comment("Global switch for automatic settlement immigration. Individual settlements also have an owner-controlled toggle.")
                .define("automaticImmigration", true);
        IMMIGRATION_MIN_DELAY_SECONDS = builder.comment("Shortest randomized delay between immigrant arrivals. The timer is never shown to players.")
                .defineInRange("immigrationMinDelaySeconds", 15, 1, 86400);
        IMMIGRATION_MAX_DELAY_SECONDS = builder.comment("Longest randomized delay between immigrant arrivals. The timer is never shown to players.")
                .defineInRange("immigrationMaxDelaySeconds", 600, 1, 86400);
        IMMIGRATION_MIN_STABILITY = builder.comment("Minimum settlement Stability required for immigration.")
                .defineInRange("immigrationMinimumStability", 60, 0, 100);
        IMMIGRATION_FOOD_RESERVE = builder.comment("Minimum Food stockpile required before an immigrant can arrive.")
                .defineInRange("immigrationFoodReserve", 20, 0, 1000000);
        IMMIGRATION_FOOD_COST = builder.comment("Food consumed when an immigrant arrives.")
                .defineInRange("immigrationFoodCost", 10, 0, 1000000);
        IMMIGRATION_MAX_PHYSICAL_RESIDENTS = builder.comment("Hard cap on registered villager entities created by immigration in one settlement.")
                .defineInRange("immigrationMaxPhysicalResidents", 128, 1, 4096);
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
        TOWN_SQUARE_CAPTURE_RADIUS = builder.comment("Horizontal radius of the automatic Main objective anchored on every Town Square.")
                .defineInRange("townSquareMainRadius", 8, 3, 64);
        TOWN_SQUARE_CAPTURE_HEIGHT = builder.comment("Vertical height of the automatic Town Square Main objective.")
                .defineInRange("townSquareMainHeight", 6, 3, 64);
        CAPTURE_ZONE_MIN_SPACING = builder.comment("Minimum horizontal distance between Capture Zone centres in the same settlement.")
                .defineInRange("minimumSpacing", 24, 0, 512);
        CAPTURE_MAIN_SECONDS = builder.comment("Base uncontested seconds required to capture a normal Main objective.")
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

    public static long hoursMillis(ForgeConfigSpec.IntValue hours) {
        return hoursMillis(hours.get().longValue());
    }

    public static long hoursMillis(long hours) {
        return Math.max(0L, hours) * 60L * 60L * 1000L;
    }
}
