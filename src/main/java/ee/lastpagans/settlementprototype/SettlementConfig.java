package ee.lastpagans.settlementprototype;

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
        builder.pop();
        SPEC = builder.build();
    }

    private SettlementConfig() {}

    public static int effectiveLevyLevelCap() {
        int cap = LEVY_LEVEL_CAP.get();
        return ENABLE_RECRUIT_LEVEL_OVERRIDE.get() ? Math.min(cap, RECRUIT_LEVEL_CAP.get()) : cap;
    }

    public static long hoursMillis(ForgeConfigSpec.IntValue hours) {
        return Math.max(0L, hours.get().longValue()) * 60L * 60L * 1000L;
    }
}
