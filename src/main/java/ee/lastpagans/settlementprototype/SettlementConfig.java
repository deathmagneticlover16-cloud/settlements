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
        builder.pop();
        SPEC = builder.build();
    }

    private SettlementConfig() {}

    public static int effectiveLevyLevelCap() {
        int cap = LEVY_LEVEL_CAP.get();
        return ENABLE_RECRUIT_LEVEL_OVERRIDE.get() ? Math.min(cap, RECRUIT_LEVEL_CAP.get()) : cap;
    }
}
