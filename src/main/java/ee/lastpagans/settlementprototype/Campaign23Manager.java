package ee.lastpagans.settlementprototype;

import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative systems introduced by the 2.3 campaign overhaul. */
public final class Campaign23Manager {
    /** Documented defaults; live balance values come from SettlementConfig. */
    public static final int PLAYER_LEVY_CAP = 300;
    public static final int FACTION_LEVY_CAP = 500;
    public static final int LEVY_LEVEL_CAP = 3;

    private static final Map<UUID, BlockPos> REGION_POS1 = new HashMap<>();
    private static final Map<UUID, String> REGION_DIM = new HashMap<>();

    private Campaign23Manager() {}

    public static void setRegionPos1(ServerPlayer player) {
        REGION_POS1.put(player.getUUID(), player.blockPosition());
        REGION_DIM.put(player.getUUID(), player.serverLevel().dimension().location().toString());
    }

    public static Campaign23Data.RegionRecord completeRegion(ServerPlayer player) {
        BlockPos first = REGION_POS1.get(player.getUUID());
        String dimension = REGION_DIM.get(player.getUUID());
        if (first == null || dimension == null || !dimension.equals(player.serverLevel().dimension().location().toString())) return null;
        return new Campaign23Data.RegionRecord(dimension, first, player.blockPosition());
    }

    public static int recalculateHousing(ServerLevel level, PrototypeData.SettlementRecord settlement) {
        Campaign23Data extra = Campaign23Data.get(level);
        int beds = 0;
        for (Campaign23Data.RegionRecord region : extra.housingAreas(settlement.getId())) {
            ServerLevel regionLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.tryParse(region.dimension())));
            if (regionLevel == null || region.volume() > 500_000L) continue;
            for (int x = region.min().getX(); x <= region.max().getX(); x++) {
                for (int y = region.min().getY(); y <= region.max().getY(); y++) {
                    for (int z = region.min().getZ(); z <= region.max().getZ(); z++) {
                        BlockState state = regionLevel.getBlockState(new BlockPos(x, y, z));
                        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)
                                && state.getValue(BedBlock.PART) == BedPart.HEAD) beds++;
                    }
                }
            }
        }
        settlement.setHousingCapacity(beds);
        return beds;
    }

    public static int recalculateProsperity(PrototypeData.SettlementRecord settlement, Campaign23Data extra, long currentDay) {
        int population = Math.max(1, settlement.getCivilianPopulation());
        int averageDeposit = extra.averageWealthDeposit(settlement.getId(), currentDay, 7);
        // Prosperity is primarily about money actually entering the local economy, not a ruler hoarding a static treasury.
        int economicFlow = extra.hasWealthDepositHistory(settlement.getId())
                ? Math.min(60, (averageDeposit * 25) / population)
                : Math.min(40, (settlement.getWealth() * 10) / population); // migration fallback for old saves
        int reserve = Math.min(10, settlement.getWealth() / Math.max(10, population / 10));
        int foodSecurity = Math.min(10, settlement.getFood() / Math.max(1, settlement.getVillagerCount() * 2));
        int stability = settlement.getStability() / 5;
        int housing = settlement.getFreeHousing() > 0 ? 5 : settlement.getVillagerCount() > settlement.getHousingCapacity() ? -10 : 0;
        int modifiers = extra.modifiers(settlement.getId()).stream().mapToInt(value -> value.prosperity).sum();
        int result = Math.max(0, Math.min(100, economicFlow + reserve + foodSecurity + stability + housing + modifiers));
        extra.setProsperity(settlement.getId(), result);
        return result;
    }

    public static void processCampaignSystems(MinecraftServer server, long day) {
        PrototypeData settlements = PrototypeData.get(server.overworld());
        Campaign23Data extra = Campaign23Data.get(server.overworld());
        for (PrototypeData.SettlementRecord settlement : settlements.getSettlements()) {
            extra.expireModifiers(settlement.getId());
            updateReligiousUnrest(server, settlement, extra, settlements);
            int prosperity = recalculateProsperity(settlement, extra, day);
            applyDailyModifierEffects(settlement, extra, day);
            updateSettlementTier(server.overworld(), settlement, prosperity, extra);
        }
        processLevyMobilizations(server, extra, settlements);
        processLevyServicePenalties(server, extra, settlements);
        processRecurringWarAid(server, extra, settlements, day);
        processReparations(server, extra, day);
        processForcedNapExpiry(server, extra, day);
        processPassiveRelations(server, extra, day);
        processDevotionSites(server, extra, settlements, day);
    }

    /**
     * Advances only settlement-local campaign simulation for admin testing. Global diplomacy
     * clocks (reparations, forced NAPs, spouse refreshes, etc.) are deliberately excluded so
     * testing a five-day levy modifier in one town cannot age the entire server campaign.
     */
    public static void processDebugSettlement(MinecraftServer server, PrototypeData.SettlementRecord settlement, long simulatedDay) {
        if (server == null || settlement == null) return;
        PrototypeData settlements = PrototypeData.get(server.overworld());
        Campaign23Data extra = Campaign23Data.get(server.overworld());
        extra.expireModifiers(settlement.getId());
        updateReligiousUnrest(server, settlement, extra, settlements);
        int prosperity = recalculateProsperity(settlement, extra, simulatedDay);
        applyDailyModifierEffects(settlement, extra, simulatedDay);
        updateSettlementTier(server.overworld(), settlement, prosperity, extra);
        updateLevyServicePenaltyForSettlement(server, extra, settlement);
    }

    private static void updateReligiousUnrest(MinecraftServer server, PrototypeData.SettlementRecord settlement, Campaign23Data extra, PrototypeData settlements) {
        String factionId = settlement.getFactionTeam();
        boolean unrest = false;
        if (!factionId.isBlank()) {
            String ownFaith = factionLeaderFaith(factionId, settlements);
            CampaignData campaign = CampaignData.get(server.overworld());
            for (CampaignData.DiplomacyRecord relation : campaign.getDiplomacy()) {
                if (!relation.state().isAlliance()) continue;
                String other = factionId.equals(relation.firstFaction()) ? relation.secondFaction()
                        : factionId.equals(relation.secondFaction()) ? relation.firstFaction() : "";
                if (other.isBlank()) continue;
                String otherFaith = factionLeaderFaith(other, settlements);
                if (CampaignDefinitions.religionRelationModifier(ownFaith, otherFaith) <= -8) { unrest = true; break; }
            }
            // Marriage Alliances are intentionally a separate diplomatic bond rather than a
            // full military alliance, but the population still notices a ruler marrying into
            // a strongly incompatible faith. Include those political marriages in unrest too.
            if (!unrest && com.talhanation.recruits.FactionEvents.recruitsFactionManager != null) {
                for (com.talhanation.recruits.world.RecruitsFaction otherFaction : com.talhanation.recruits.FactionEvents.recruitsFactionManager.getFactions()) {
                    if (otherFaction == null || otherFaction.getStringID().equals(factionId)
                            || !extra.marriageAlliance(factionId, otherFaction.getStringID())) continue;
                    String otherFaith = factionLeaderFaith(otherFaction.getStringID(), settlements);
                    if (CampaignDefinitions.religionRelationModifier(ownFaith, otherFaith) <= -8) { unrest = true; break; }
                }
            }
        }
        boolean present = extra.modifiers(settlement.getId()).stream().anyMatch(value -> value.id.equals("local_religious_unrest"));
        if (unrest && !present) extra.addModifier(settlement.getId(), standardModifier(server.overworld(), settlement.getId(), "local_religious_unrest", 0));
        else if (!unrest && present) extra.removeModifier(settlement.getId(), "local_religious_unrest");
    }

    private static String factionLeaderFaith(String factionId, PrototypeData settlements) {
        if (factionId == null || factionId.isBlank() || com.talhanation.recruits.FactionEvents.recruitsFactionManager == null) return "";
        com.talhanation.recruits.world.RecruitsFaction faction = com.talhanation.recruits.FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null || faction.getTeamLeaderUUID() == null) return "";
        PrototypeData.CharacterRecord leader = settlements.getCharacter(faction.getTeamLeaderUUID());
        return leader == null ? "" : leader.getFaith();
    }

    private static void processDevotionSites(MinecraftServer server, Campaign23Data extra, PrototypeData settlements, long day) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PrototypeData.CharacterRecord character = settlements.getCharacter(player.getUUID());
            if (character == null || !character.isCreated()) continue;
            String dim = player.serverLevel().dimension().location().toString();
            BlockPos pos = player.blockPosition();
            if (extra.lastHolyVisitDay(player.getUUID()) < day) {
                for (Campaign23Data.HolySiteRecord site : extra.holySites()) {
                    if (site.region().contains(dim, pos) && site.faith().equalsIgnoreCase(character.getFaith())) {
                        extra.changeDevotion(player.getUUID(), site.devotionGain());
                        extra.markHolyVisit(player.getUUID(), day);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Holy Site visited: +" + site.devotionGain() + " Devotion (" + extra.devotion(player.getUUID()) + "/100)."));
                        break;
                    }
                }
            }
            if (extra.lastPrayerDay(player.getUUID()) < day) {
                for (PrototypeData.SettlementRecord settlement : settlements.getSettlements()) {
                    Campaign23Data.RegionRecord site = extra.prayerSite(settlement.getId());
                    if (site == null || !site.contains(dim, pos)) continue;
                    boolean allowed = settlement.getFactionTeam().isBlank() ? settlement.getRole(player.getUUID()) != PrototypeData.SettlementRole.OUTSIDER
                            : player.getTeam() != null && settlement.getFactionTeam().equals(player.getTeam().getName());
                    if (!allowed) continue;
                    extra.changeDevotion(player.getUUID(), 3);
                    extra.markPrayer(player.getUUID(), day);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Prayer at " + settlement.getName() + ": +3 Devotion (" + extra.devotion(player.getUUID()) + "/100)."));
                    break;
                }
            }
        }
    }

    private static void applyDailyModifierEffects(PrototypeData.SettlementRecord settlement, Campaign23Data extra, long day) {
        if (!extra.beginModifierDay(settlement.getId(), day)) return;
        int stability = extra.modifiers(settlement.getId()).stream().mapToInt(value -> value.stabilityPerDay).sum();
        if (stability != 0) settlement.changeStability(stability);
    }

    private static void updateSettlementTier(ServerLevel level, PrototypeData.SettlementRecord settlement, int prosperity, Campaign23Data extra) {
        if (settlement.getRank() == PrototypeData.SettlementRank.CAPITAL) return;
        if (settlement.getRank() == PrototypeData.SettlementRank.VILLAGE
                && settlement.getCivilianPopulation() >= 100 && prosperity >= 35
                && settlement.getStability() >= 45 && settlement.getHousingCapacity() >= settlement.getVillagerCount()) {
            settlement.setRank(PrototypeData.SettlementRank.TOWN);
            extra.addModifier(settlement.getId(), standardModifier(level, settlement.getId(), "civic_pride", 2L * 24L * 60L * 60L * 1000L));
            settlement.addChronicle("The settlement grew into a Town. Civic Pride spread through the population.");
            long day = level.getDayTime() / 24000L;
            CampaignData.get(level).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.NORMAL,
                    "Settlement advanced", settlement.getName() + " has grown from a Village into a Town.",
                    day, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
            CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "SETTLEMENT ADVANCED",
                    settlement.getName() + " is now a Town. Civic Pride is temporarily boosting the settlement.");
        }
        if (settlement.getRank() == PrototypeData.SettlementRank.TOWN && settlement.getFactionTeam().isBlank()
                && settlement.getCivilianPopulation() >= 250 && prosperity >= 60 && settlement.getStability() >= 60
                && settlement.getHousingCapacity() >= settlement.getVillagerCount()) {
            settlement.setRank(PrototypeData.SettlementRank.CITY_STATE);
            extra.addModifier(settlement.getId(), standardModifier(level, settlement.getId(), "civic_pride", 3L * 24L * 60L * 60L * 1000L));
            settlement.addChronicle("The independent settlement emerged as a City-State.");
            long day = level.getDayTime() / 24000L;
            CampaignData.get(level).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.MAJOR,
                    "City-State emerged", settlement.getName() + " has become an independent City-State.",
                    day, "", settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
            CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "CITY-STATE",
                    settlement.getName() + " has grown powerful enough to become a City-State.");
        }
    }

    public static Campaign23Data.ModifierRecord standardModifier(String settlementId, String raw, long durationMs) {
        String id = PrototypeData.normalizeId(raw);
        long expires = 0L;
        return switch (id) {
            case "civic_pride" -> new Campaign23Data.ModifierRecord(id, "Civic Pride", "minecraft:bell", "POSITIVE", "Settlement progression", "+5 stability while citizens celebrate their settlement's advancement.", 5, 5, 3, expires);
            case "local_religious_unrest" -> new Campaign23Data.ModifierRecord(id, "Local Religious Unrest", "minecraft:rotten_flesh", "NEGATIVE", "Religious diplomacy", "The population dislikes close political ties with a strongly incompatible faith.", -3, -3, -2, expires);
            case "foreign_soldiers" -> new Campaign23Data.ModifierRecord(id, "Foreign Soldiers", "minecraft:iron_sword", "MIXED", "Peace treaty", "Foreign manpower is available, but locals resent relying on outside soldiers.", -8, -2, 0, expires);
            case "families_in_mourning" -> new Campaign23Data.ModifierRecord(id, "Families in Mourning", "minecraft:wither_rose", "NEGATIVE", "Levy casualties", "Families mourn levymen who did not return.", -3, -2, -2, expires);
            case "heavy_levy_losses" -> new Campaign23Data.ModifierRecord(id, "Heavy Levy Losses", "minecraft:wither_rose", "NEGATIVE", "Levy casualties", "A large share of the settlement's levy was lost.", -6, -5, -4, expires);
            case "devastating_losses" -> new Campaign23Data.ModifierRecord(id, "Devastating Losses", "minecraft:skeleton_skull", "NEGATIVE", "Levy casualties", "The settlement has suffered devastating losses among its common people.", -10, -10, -7, expires);
            case "a_generation_lost" -> new Campaign23Data.ModifierRecord(id, "A Generation Lost", "minecraft:skeleton_skull", "NEGATIVE", "Levy casualties", "An entire generation has been scarred by the levy disaster.", -15, -15, -10, expires);
            case "levy_massacre" -> new Campaign23Data.ModifierRecord(id, "Levy Massacre", "minecraft:wither_skeleton_skull", "NEGATIVE", "Levy casualties", "Almost nobody returned from the levy. The settlement is in social crisis.", -20, -20, -15, expires);
            case "extended_service" -> new Campaign23Data.ModifierRecord(id, "Extended Service", "minecraft:clock", "NEGATIVE", "Levy service", "Families are beginning to demand the return of the levy.", -2, -1, -2, expires);
            case "weary_levies" -> new Campaign23Data.ModifierRecord(id, "Weary Levies", "minecraft:clock", "NEGATIVE", "Levy service", "The levy has remained in the field for too long.", -5, -4, -6, expires);
            case "levy_exhaustion" -> new Campaign23Data.ModifierRecord(id, "Levy Exhaustion", "minecraft:clock", "NEGATIVE", "Levy service", "Prolonged mobilisation is seriously harming settlement life.", -10, -10, -10, expires);
            case "popular_outrage" -> new Campaign23Data.ModifierRecord(id, "Popular Outrage", "minecraft:fire_charge", "NEGATIVE", "Levy service", "The ruler's refusal to return the levy has caused crippling popular anger.", -20, -20, -15, expires);
            case "levy_recovery" -> new Campaign23Data.ModifierRecord(id, "Levy Recovery", "minecraft:clock", "NEGATIVE", "Returned levy", "The levy is home, but families and local work need time to recover from prolonged mobilisation.", -3, -2, -1, expires);
            default -> new Campaign23Data.ModifierRecord(id, pretty(id), "minecraft:book", "NEUTRAL", "Debug", "Administrative test modifier.", 0, 0, 0, expires);
        };
    }

    /** Actual timestamp helper for standard modifiers. */
    public static Campaign23Data.ModifierRecord standardModifier(ServerLevel level, String settlementId, String raw, long durationMs) {
        String id = PrototypeData.normalizeId(raw);
        Campaign23Data extra = Campaign23Data.get(level);
        long expires = durationMs <= 0 ? 0 : extra.nowMillis(settlementId) + durationMs;
        Campaign23Data.ModifierRecord base = standardModifier(settlementId, id, 0);
        return new Campaign23Data.ModifierRecord(base.id, base.name, base.iconItem, base.tone, base.source, base.detail,
                base.stabilityPerDay, base.prosperity, base.morale, expires);
    }

    public static String raiseLevy(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        long now = extra.nowMillis(settlement.getId());
        if (extra.levyCooldownUntil(player.getUUID()) > now) {
            return "Your levy is still on cooldown for " + formatDuration(extra.levyCooldownUntil(player.getUUID()) - now) + ".";
        }
        if (extra.levyMobilizations().stream().anyMatch(value -> player.getUUID().equals(value.ownerId))) {
            return "You are already mustering a levy.";
        }
        Campaign23Data.RegionRecord area = extra.musterArea(settlement.getId());
        if (area == null || !area.valid()) return "This settlement has no rectangular Muster Ground. Set pos1 and pos2 first.";
        int available = settlement.getAvailableManpower();
        if (available <= 0) return "No available manpower to mobilise.";
        double share = settlement.getCivilianPopulation() < PrototypeManager.SMALL_SETTLEMENT_POPULATION
                ? PrototypeManager.SMALL_LEVY_SHARE : PrototypeManager.NORMAL_LEVY_SHARE;
        int desiredManpower = Math.max(1, (int)Math.floor(available * share));
        int desiredUnits = desiredManpower * 2;
        int playerCap = SettlementConfig.PLAYER_LEVY_CAP.get();
        int factionCap = SettlementConfig.FACTION_LEVY_CAP.get();
        int playerRemaining = Math.max(0, playerCap - deployedForPlayer(extra, player.getUUID()));
        // Independent rulers are governed by their personal cap. Blank faction IDs must not accidentally
        // combine every independent settlement on the server into one artificial 500-man "faction" pool.
        int factionRemaining = settlement.getFactionTeam().isBlank()
                ? factionCap : Math.max(0, factionCap - deployedForFaction(extra, settlement.getFactionTeam()));
        ServerLevel musterLevel = levelForRegion(player.getServer(), area);
        if (musterLevel == null) return "The Muster Ground dimension is not available.";
        int groundRemaining = usableMusterCapacity(musterLevel, area, desiredUnits);
        int allowedUnits = Math.min(desiredUnits, Math.min(playerRemaining, Math.min(factionRemaining, groundRemaining)));
        allowedUnits -= allowedUnits % 2;
        if (allowedUnits <= 0) {
            if (groundRemaining <= 0) return "Your Muster Ground has no safe standing blocks. Clear the field and try again.";
            if (playerRemaining <= 0) return "You already have the maximum " + playerCap + " deployed levymen.";
            if (factionRemaining <= 0) return "Your faction already has the maximum " + factionCap + " deployed levymen.";
            return "This levy cannot be raised right now.";
        }
        int manpowerSpent = allowedUnits / 2;
        if (!settlement.spendAvailableManpower(manpowerSpent)) return "Not enough available manpower.";
        UUID batch = settlement.addLevyBatch(manpowerSpent, List.of());
        long duration = levyMusterDurationMs(allowedUnits, PrototypeData.get(player.serverLevel()).getCharacter(player.getUUID()));
        UUID id = UUID.randomUUID();
        extra.putLevyMobilization(new Campaign23Data.LevyMobilization(id, player.getUUID(), settlement.getId(),
                settlement.getFactionTeam(), manpowerSpent, allowedUnits, 0, now, now + duration, batch));
        settlement.addChronicle("A levy of " + allowedUnits + " men began mustering under " + PrototypeManager.characterOrAccountName(player) + ".");
        String capped = allowedUnits < desiredUnits ? " Requested levy was reduced to the maximum currently available." : "";
        return "Mobilisation ordered: " + allowedUnits + " levymen will assemble over " + formatDuration(duration) + "." + capped;
    }

    private static long levyMusterDurationMs(int units, PrototypeData.CharacterRecord character) {
        double secondsPerUnit = SettlementConfig.LEVY_SECONDS_PER_SOLDIER.get();
        int traitPercent = character == null ? 0 : CampaignDefinitions.totalEffect(character.getTraits(), CampaignDefinitions.EffectKind.LEVY_MUSTER);
        // LEVY_MUSTER effects are stored as percentages; negative values reduce mobilisation time.
        secondsPerUnit *= Math.max(0.10D, 1.0D + traitPercent / 100.0D);
        long minimum = SettlementConfig.LEVY_MIN_MUSTER_SECONDS.get().longValue() * 1000L;
        long maximum = SettlementConfig.LEVY_MAX_MUSTER_MINUTES.get().longValue() * 60L * 1000L;
        return Math.max(minimum, Math.min(maximum, Math.round(units * secondsPerUnit * 1000.0D)));
    }

    private static int deployedForPlayer(Campaign23Data extra, UUID player) {
        return extra.levyDeployments().stream().filter(value -> player.equals(value.ownerId)).mapToInt(value -> Math.max(0, value.initialUnits - value.returnedUnits)).sum()
                + extra.levyMobilizations().stream().filter(value -> player.equals(value.ownerId)).mapToInt(value -> value.totalUnits).sum();
    }

    private static int deployedForFaction(Campaign23Data extra, String faction) {
        String id = faction == null ? "" : faction;
        return extra.levyDeployments().stream().filter(value -> id.equals(value.factionId)).mapToInt(value -> Math.max(0, value.initialUnits - value.returnedUnits)).sum()
                + extra.levyMobilizations().stream().filter(value -> id.equals(value.factionId)).mapToInt(value -> value.totalUnits).sum();
    }

    private static void processLevyMobilizations(MinecraftServer server, Campaign23Data extra, PrototypeData settlements) {
        List<Campaign23Data.LevyMobilization> values = new ArrayList<>(extra.levyMobilizations());
        for (Campaign23Data.LevyMobilization mobilization : values) {
            PrototypeData.SettlementRecord settlement = settlements.getSettlement(mobilization.settlementId);
            if (settlement == null) { extra.removeLevyMobilization(mobilization.id); continue; }
            ServerPlayer owner = server.getPlayerList().getPlayer(mobilization.ownerId);
            if (owner == null) continue; // completed time is preserved; spawning resumes safely when the owner returns.
            long now = extra.nowMillis(settlement.getId());
            long span = Math.max(1L, mobilization.finishesAtMillis - mobilization.startedAtMillis);
            double progress = Math.max(0.0D, Math.min(1.0D, (double)(now - mobilization.startedAtMillis) / span));
            int target = now >= mobilization.finishesAtMillis ? mobilization.totalUnits : Math.max(1, (int)Math.floor(mobilization.totalUnits * progress));
            int needed = Math.min(24, Math.max(0, target - mobilization.spawnedUnits));
            if (needed <= 0) continue;
            ServerLevel level = owner.serverLevel();
            Campaign23Data.RegionRecord region = extra.musterArea(settlement.getId());
            if (region != null && !level.dimension().location().toString().equals(region.dimension())) {
                level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.tryParse(region.dimension())));
            }
            if (level == null) continue;
            PlayerTeam team = settlement.getFactionTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getFactionTeam());
            RecruitsGroup group = levyGroup(owner, level, settlement);
            for (int i = 0; i < needed && mobilization.spawnedUnits < mobilization.totalUnits; i++) {
                AbstractRecruitEntity recruit = PrototypeManager.spawnRecruit(level, owner, settlement, CampaignData.UnitType.LEVY,
                        mobilization.spawnedUnits, team, true, mobilization.batchId);
                if (recruit == null) break;
                assignLevyOwner(recruit, owner, group, level);
                int traitHealth = 0;
                PrototypeData.CharacterRecord commander = settlements.getCharacter(owner.getUUID());
                if (commander != null) traitHealth = CampaignDefinitions.totalEffect(commander.getTraits(), CampaignDefinitions.EffectKind.LEVY_HEALTH);
                int maxHealth = (mobilization.totalUnits < SettlementConfig.SMALL_LEVY_SIZE_THRESHOLD.get()
                        ? SettlementConfig.SMALL_LEVY_HEALTH.get() : SettlementConfig.LARGE_LEVY_HEALTH.get()) + traitHealth;
                AttributeInstance health = recruit.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) health.setBaseValue(maxHealth);
                recruit.setHealth(maxHealth);
                int startXp = extra.levyTradition(settlement.getId())
                        + (commander == null ? 0 : CampaignDefinitions.totalEffect(commander.getTraits(), CampaignDefinitions.EffectKind.LEVY_XP));
                if (startXp > 0) recruit.addXp(startXp);
                int levyCap = SettlementConfig.effectiveLevyLevelCap();
                if (recruit.getXpLevel() > levyCap) recruit.setXpLevel(levyCap);
                settlement.addLevymanToBatch(mobilization.batchId, recruit.getUUID());
                mobilization.spawnedUnits++;
            }
            extra.setDirty();
            if (mobilization.spawnedUnits >= mobilization.totalUnits) {
                extra.putLevyDeployment(new Campaign23Data.LevyDeployment(mobilization.batchId, mobilization.ownerId,
                        mobilization.settlementId, mobilization.factionId, mobilization.totalUnits, mobilization.manpowerSpent, 0, now));
                extra.removeLevyMobilization(mobilization.id);
                settlement.addChronicle("The levy finished assembling: " + mobilization.totalUnits + " levymen entered service.");
                owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your " + settlement.getName() + " levy has finished mustering (" + mobilization.totalUnits + " men)."));
            }
        }
    }

    private static RecruitsGroup levyGroup(ServerPlayer player, ServerLevel level, PrototypeData.SettlementRecord settlement) {
        if (RecruitEvents.recruitsGroupsManager == null) return null;
        String name = "Levy - " + settlement.getName();
        RecruitsGroup group = RecruitEvents.recruitsGroupsManager.getPlayersGroupByName(player, name);
        if (group == null) {
            group = new RecruitsGroup(name, player, 0);
            RecruitEvents.recruitsGroupsManager.addOrUpdateGroup(level, player, group);
        }
        return group;
    }

    private static void assignLevyOwner(AbstractRecruitEntity recruit, ServerPlayer owner, RecruitsGroup group, ServerLevel level) {
        if (group != null) {
            recruit.assignToPlayer(owner.getUUID(), group.getUUID());
            RecruitEvents.recruitsGroupsManager.addMember(group.getUUID(), recruit.getUUID(), level);
        } else {
            recruit.setOwnerUUID(Optional.of(owner.getUUID()));
            recruit.setIsOwned(true);
        }
    }

    public static String returnLevy(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        List<Campaign23Data.LevyDeployment> deployments = extra.levyDeployments().stream()
                .filter(value -> player.getUUID().equals(value.ownerId) && settlement.getId().equals(value.settlementId)).toList();
        if (deployments.isEmpty()) return "You have no active levy from this settlement to return.";
        int totalInitial = 0, totalSurvivors = 0, totalManpower = 0;
        long oldestServiceAge = 0L;
        long now = extra.nowMillis(settlement.getId());
        for (Campaign23Data.LevyDeployment deployment : deployments) {
            totalInitial += deployment.initialUnits;
            totalManpower += deployment.manpowerSpent;
            oldestServiceAge = Math.max(oldestServiceAge, Math.max(0L, now - deployment.deployedAtMillis));
            Set<UUID> units = settlement.getLevyUnitsForBatch(deployment.batchId);
            totalSurvivors += units.size();
            for (UUID unitId : units) {
                Entity entity = findEntity(player.getServer(), unitId);
                if (entity instanceof AbstractRecruitEntity recruit && player.getUUID().equals(recruit.getOwnerUUID())) {
                    recruit.discard();
                } else if (entity == null) {
                    // The recruit may be in an unloaded chunk/dimension. Remember it so returning a levy really
                    // returns the whole levy instead of letting unloaded stragglers reappear days later.
                    extra.markRetiredLevy(unitId);
                }
            }
            settlement.clearLevyBatch(deployment.batchId);
            extra.removeLevyDeployment(deployment.batchId);
        }
        int losses = Math.max(0, totalInitial - totalSurvivors);
        double lossRate = totalInitial <= 0 ? 0 : (double) losses / totalInitial;
        Campaign23Data.ModifierRecord casualtyModifier = applyLevyCasualtyModifier(player.serverLevel(), settlement, extra, lossRate);
        if (casualtyModifier != null) {
            int percentLost = (int) Math.round(lossRate * 100.0D);
            CampaignToastSender.settlementManagers(player.getServer(), settlement.getId(), casualtyModifier.name.toUpperCase(Locale.ROOT),
                    losses + " of " + totalInitial + " levymen were lost (" + percentLost + "%). " + casualtyModifier.detail);
            if (lossRate >= 0.50D) {
                CampaignData.get(player.serverLevel()).addNews(CampaignData.NewsCategory.MILITARY,
                        lossRate >= 0.75D ? CampaignData.NewsImportance.MAJOR : CampaignData.NewsImportance.NORMAL,
                        casualtyModifier.name, settlement.getName() + " returned from levy service after losing " + losses + " of " + totalInitial + " men.",
                        player.serverLevel().getDayTime() / 24000L, settlement.getFactionTeam(), settlement.getId(),
                        lossRate >= 0.75D ? CampaignData.NewsVisibility.PUBLIC : CampaignData.NewsVisibility.FACTION);
            }
        }
        settleLevyServicePenaltyAfterReturn(player.serverLevel(), settlement, extra, oldestServiceAge);
        int traditionGain = levyTraditionGain(totalInitial, totalSurvivors, oldestServiceAge);
        int tradition = extra.levyTradition(settlement.getId());
        if (traditionGain > 0) tradition = extra.changeLevyTradition(settlement.getId(), traditionGain);
        long fullCooldown = SettlementConfig.hoursMillis(SettlementConfig.LEVY_FULL_COOLDOWN_HOURS);
        long minimumCooldown = SettlementConfig.hoursMillis(SettlementConfig.LEVY_MIN_COOLDOWN_HOURS);
        long cooldown = Math.max(minimumCooldown, Math.round(fullCooldown * Math.min(1.0D, totalInitial / 300.0D)));
        extra.setLevyCooldown(player.getUUID(), now + cooldown);
        // Returned/dead levy MP becomes vacant and recovers through the normal manpower checker, preserving the existing design.
        String traditionText = traditionGain > 0 ? " Levy Tradition +" + traditionGain + " (" + tradition + "/50)." : "";
        settlement.addChronicle("The levy returned: " + totalSurvivors + "/" + totalInitial + " men came home (" + losses + " lost). Cooldown: " + formatDuration(cooldown) + "." + traditionText);
        return "Returned " + totalSurvivors + " surviving levymen. Losses: " + losses + ". Levy cooldown: " + formatDuration(cooldown) + "." + traditionText;
    }

    /** Administrative hard reset for a player's levy state. Loaded levy entities are removed,
     * unloaded ones are retired so they cannot reappear later, partial mobilisations are refunded,
     * and settlement batch reservations are cleared before the persistent levy records are reset. */
    public static int forceRetirePlayerLevies(ServerLevel level, UUID ownerId) {
        if (level == null || ownerId == null) return 0;
        Campaign23Data extra = Campaign23Data.get(level);
        PrototypeData settlements = PrototypeData.get(level);
        MinecraftServer server = level.getServer();
        int retired = 0;
        Set<String> touchedSettlements = new HashSet<>();

        for (Campaign23Data.LevyMobilization mobilization : new ArrayList<>(extra.levyMobilizations())) {
            if (!ownerId.equals(mobilization.ownerId)) continue;
            PrototypeData.SettlementRecord settlement = settlements.getSettlement(mobilization.settlementId);
            if (settlement != null) {
                touchedSettlements.add(settlement.getId());
                for (UUID unitId : settlement.getLevyUnitsForBatch(mobilization.batchId)) {
                    Entity entity = findEntity(server, unitId);
                    if (entity != null) entity.discard(); else extra.markRetiredLevy(unitId);
                    retired++;
                }
                settlement.clearLevyBatch(mobilization.batchId);
                // A mobilisation that never finished service is an administrative cancellation,
                // so return the reserved manpower immediately instead of leaving a phantom vacancy.
                settlement.changeAvailableManpower(mobilization.manpowerSpent);
            }
            extra.removeLevyMobilization(mobilization.id);
        }

        for (Campaign23Data.LevyDeployment deployment : new ArrayList<>(extra.levyDeployments())) {
            if (!ownerId.equals(deployment.ownerId)) continue;
            PrototypeData.SettlementRecord settlement = settlements.getSettlement(deployment.settlementId);
            if (settlement != null) {
                touchedSettlements.add(settlement.getId());
                for (UUID unitId : settlement.getLevyUnitsForBatch(deployment.batchId)) {
                    Entity entity = findEntity(server, unitId);
                    if (entity != null) entity.discard(); else extra.markRetiredLevy(unitId);
                    retired++;
                }
                settlement.clearLevyBatch(deployment.batchId);
            }
            extra.removeLevyDeployment(deployment.batchId);
        }

        extra.resetLevy(ownerId);
        for (String settlementId : touchedSettlements) {
            boolean stillMobilised = extra.levyDeployments().stream().anyMatch(value -> settlementId.equals(value.settlementId))
                    || extra.levyMobilizations().stream().anyMatch(value -> settlementId.equals(value.settlementId));
            if (!stillMobilised) clearLevyServiceModifiers(extra, settlementId, "");
        }
        return retired;
    }

    private static int levyTraditionGain(int initial, int survivors, long serviceAge) {
        // A levy must actually remain in service before a settlement learns anything from it.
        // The multi-day cooldown already makes this difficult to farm; the service/survivor
        // requirements also prevent raise-and-return loops and massacres from becoming bonuses.
        if (initial < 10 || serviceAge < 30L * 60L * 1000L || survivors <= 0) return 0;
        double survivalRate = Math.min(1.0D, (double) survivors / initial);
        if (survivalRate < 0.25D) return 0;
        return survivalRate < 0.50D ? 2 : 5;
    }

    private static Campaign23Data.ModifierRecord applyLevyCasualtyModifier(ServerLevel level, PrototypeData.SettlementRecord settlement, Campaign23Data extra, double lossRate) {
        String modifier;
        long duration;
        if (lossRate < 0.10D) return null;
        if (lossRate < 0.25D) { modifier = "families_in_mourning"; duration = 24L * 60L * 60L * 1000L; }
        else if (lossRate < 0.50D) { modifier = "heavy_levy_losses"; duration = 2L * 24L * 60L * 60L * 1000L; }
        else if (lossRate < 0.75D) { modifier = "devastating_losses"; duration = 3L * 24L * 60L * 60L * 1000L; }
        else if (lossRate < 0.90D) { modifier = "a_generation_lost"; duration = 5L * 24L * 60L * 60L * 1000L; }
        else { modifier = "levy_massacre"; duration = 7L * 24L * 60L * 60L * 1000L; }
        Campaign23Data.ModifierRecord record = standardModifier(level, settlement.getId(), modifier, duration);
        extra.addModifier(settlement.getId(), record);
        return record;
    }

    private static final List<String> LEVY_SERVICE_MODIFIER_IDS = List.of(
            "extended_service", "weary_levies", "levy_exhaustion", "popular_outrage"
    );

    private static String levyServiceModifier(long age) {
        long extendedAt = SettlementConfig.hoursMillis(SettlementConfig.LEVY_EXTENDED_SERVICE_HOURS);
        long wearyAt = Math.max(extendedAt, SettlementConfig.hoursMillis(SettlementConfig.LEVY_WEARY_SERVICE_HOURS));
        long exhaustedAt = Math.max(wearyAt, SettlementConfig.hoursMillis(SettlementConfig.LEVY_EXHAUSTION_HOURS));
        long outrageAt = Math.max(exhaustedAt, SettlementConfig.hoursMillis(SettlementConfig.LEVY_OUTRAGE_HOURS));
        return age >= outrageAt ? "popular_outrage" : age >= exhaustedAt ? "levy_exhaustion"
                : age >= wearyAt ? "weary_levies" : age >= extendedAt ? "extended_service" : "";
    }

    private static int levyServiceStage(String id) {
        return switch (PrototypeData.normalizeId(id)) {
            case "extended_service" -> 1;
            case "weary_levies" -> 2;
            case "levy_exhaustion" -> 3;
            case "popular_outrage" -> 4;
            default -> 0;
        };
    }

    private static void clearLevyServiceModifiers(Campaign23Data extra, String settlementId, String except) {
        for (String id : LEVY_SERVICE_MODIFIER_IDS) {
            if (!id.equals(except)) extra.removeModifier(settlementId, id);
        }
    }

    private static void settleLevyServicePenaltyAfterReturn(ServerLevel level, PrototypeData.SettlementRecord settlement,
                                                              Campaign23Data extra, long oldestServiceAge) {
        boolean stillMobilised = extra.levyDeployments().stream().anyMatch(value -> settlement.getId().equals(value.settlementId));
        if (stillMobilised) return;
        String endedStage = levyServiceModifier(oldestServiceAge);
        clearLevyServiceModifiers(extra, settlement.getId(), "");
        if (endedStage.isBlank()) return;
        int stage = levyServiceStage(endedStage);
        long recoveryHours = switch (stage) {
            case 1 -> 12L;
            case 2 -> 24L;
            case 3 -> 48L;
            default -> 72L;
        };
        extra.addModifier(settlement.getId(), standardModifier(level, settlement.getId(), "levy_recovery", SettlementConfig.hoursMillis(recoveryHours)));
        settlement.addChronicle("The overdue levy returned. Mobilisation unrest is now recovering for " + recoveryHours + " hours.");
    }

    private static void processLevyServicePenalties(MinecraftServer server, Campaign23Data extra, PrototypeData settlements) {
        for (PrototypeData.SettlementRecord settlement : settlements.getSettlements()) {
            updateLevyServicePenaltyForSettlement(server, extra, settlement);
        }
    }

    private static void updateLevyServicePenaltyForSettlement(MinecraftServer server, Campaign23Data extra,
                                                                PrototypeData.SettlementRecord settlement) {
        String desired = "";
        for (Campaign23Data.LevyDeployment deployment : extra.levyDeployments()) {
            if (!settlement.getId().equals(deployment.settlementId)) continue;
            long age = extra.nowMillis(settlement.getId()) - deployment.deployedAtMillis;
            String stage = levyServiceModifier(age);
            if (levyServiceStage(stage) > levyServiceStage(desired)) desired = stage;

            // Enforce the low level cap while preserving XP earned in real combat below that cap.
            for (UUID unitId : settlement.getLevyUnitsForBatch(deployment.batchId)) {
                Entity entity = findEntity(server, unitId);
                if (entity instanceof AbstractRecruitEntity recruit) {
                    int cap = SettlementConfig.effectiveLevyLevelCap();
                    if (recruit.getXpLevel() > cap) recruit.setXpLevel(cap);
                }
            }
        }

        String current = LEVY_SERVICE_MODIFIER_IDS.stream()
                .filter(id -> extra.modifiers(settlement.getId()).stream().anyMatch(value -> value.id.equals(id)))
                .max(Comparator.comparingInt(Campaign23Manager::levyServiceStage)).orElse("");
        final String desiredModifier = desired;
        clearLevyServiceModifiers(extra, settlement.getId(), desiredModifier);
        if (desiredModifier.isBlank()) return;
        if (!desiredModifier.equals(current) || extra.modifiers(settlement.getId()).stream().noneMatch(value -> value.id.equals(desiredModifier))) {
            extra.addModifier(settlement.getId(), standardModifier(server.overworld(), settlement.getId(), desiredModifier, 0));
            if (levyServiceStage(desiredModifier) > levyServiceStage(current)) {
                settlement.addChronicle("Levy service penalty began: " + pretty(desiredModifier) + ".");
            }
        }
    }


    private static void processRecurringWarAid(MinecraftServer server, Campaign23Data extra, PrototypeData settlements, long day) {
        CampaignData campaign = CampaignData.get(server.overworld());
        for (CampaignData.WarRecord war : campaign.getWars()) {
            List<Campaign23Data.AidRecord> current = extra.warAid(war.id());
            if (current.isEmpty()) continue;
            if (war.status() != CampaignData.WarStatus.ACTIVE) { extra.removeWarAid(war.id()); continue; }
            List<Campaign23Data.AidRecord> next = new ArrayList<>();
            for (Campaign23Data.AidRecord aid : current) {
                if (!aid.recurring()) continue;
                if (aid.nextDay() > day) { next.add(aid); continue; }
                PrototypeData.SettlementRecord target = settlements.getSettlement(aid.targetSettlement());
                if (target == null) continue;
                String error = CampaignManager.transferFactionAid(server.overworld(), aid.fromFaction(), target, aid.manpower(), aid.wealth(), aid.food());
                if (error == null) {
                    next.add(new Campaign23Data.AidRecord(aid.id(), aid.fromFaction(), aid.targetSettlement(), aid.manpower(), aid.wealth(), aid.food(), true, day + 1));
                    if (target.getFactionTeam().isBlank()) CampaignToastSender.settlementManagers(server, target.getId(), "AID RECEIVED", CampaignManager.factionName(aid.fromFaction()) + " sent recurring aid to " + target.getName() + ".");
                    else CampaignToastSender.faction(server, target.getFactionTeam(), "AID RECEIVED", CampaignManager.factionName(aid.fromFaction()) + " sent recurring aid to " + target.getName() + ".");
                } else {
                    CampaignToastSender.faction(server, aid.fromFaction(), "AID SUSPENDED", "Recurring aid to " + target.getName() + " stopped: " + error);
                }
            }
            extra.replaceWarAid(war.id(), next);
        }
    }

    private static void processReparations(MinecraftServer server, Campaign23Data extra, long day) {
        List<Campaign23Data.ReparationRecord> next = new ArrayList<>();
        for (Campaign23Data.ReparationRecord value : extra.reparations()) {
            if (value.remainingDays() <= 0) continue;
            if (value.nextDay() > day) { next.add(value); continue; }
            int available = extra.factionWealth(value.payerFaction());
            int paid = Math.min(available, value.wealthPerDay());
            if (paid > 0) { extra.changeFactionWealth(value.payerFaction(), -paid); extra.changeFactionWealth(value.receiverFaction(), paid); }
            int remaining = value.remainingDays() - 1;
            CampaignData campaign = CampaignData.get(server.overworld());
            if (paid < value.wealthPerDay()) {
                campaign.changeFactionReliability(value.payerFaction(), -2);
                CampaignToastSender.faction(server, value.receiverFaction(), "REPARATIONS MISSED", CampaignManager.factionName(value.payerFaction()) + " could not pay the full daily reparations.");
            } else if (remaining <= 0) {
                // Completing the imposed payment schedule earns back a modest amount of
                // reputation. Earlier missed instalments have already applied their own penalties.
                campaign.changeFactionReliability(value.payerFaction(), 6);
                CampaignToastSender.faction(server, value.payerFaction(), "REPARATIONS COMPLETE", "Your faction has completed its war-reparation obligations.");
                CampaignToastSender.faction(server, value.receiverFaction(), "REPARATIONS COMPLETE", CampaignManager.factionName(value.payerFaction()) + " completed its war-reparation obligations.");
            }
            if (remaining > 0) next.add(value.next(day + 1, remaining));
        }
        extra.replaceReparations(next);
    }

    private static void processPassiveRelations(MinecraftServer server, Campaign23Data extra, long day) {
        CampaignData campaign = CampaignData.get(server.overworld());
        for (CampaignData.DiplomacyRecord relation : campaign.getDiplomacy()) {
            if (relation.state() == CampaignData.TreatyState.WAR) continue;
            int cap = switch (relation.state()) {
                case NON_AGGRESSION -> 25;
                case DEFENSIVE_ALLIANCE -> 50;
                case MILITARY_ALLIANCE -> 70;
                default -> extra.marriageAlliance(relation.firstFaction(), relation.secondFaction()) ? 45 : 0;
            };
            String before = CampaignManager.relationshipCategory(relation.relationship());
            if (!relation.passiveImprove(cap, day)) continue;
            String after = CampaignManager.relationshipCategory(relation.relationship());
            if (!before.equals(after)) {
                CampaignToastSender.relationChanged(server, relation.firstFaction(), relation.secondFaction(),
                        after + " (" + relation.relationship() + ")");
            }
        }
    }

    private static void processForcedNapExpiry(MinecraftServer server, Campaign23Data extra, long day) {
        CampaignData campaign = CampaignData.get(server.overworld());
        if (com.talhanation.recruits.FactionEvents.recruitsFactionManager == null) return;
        List<com.talhanation.recruits.world.RecruitsFaction> factions = new ArrayList<>(com.talhanation.recruits.FactionEvents.recruitsFactionManager.getFactions());
        for (int i = 0; i < factions.size(); i++) for (int j = i + 1; j < factions.size(); j++) {
            String a = factions.get(i).getStringID(), b = factions.get(j).getStringID();
            long until = extra.forcedNapUntil(a, b);
            if (until > 0 && until <= day && campaign.relation(a, b).state() == CampaignData.TreatyState.NON_AGGRESSION) {
                campaign.relation(a, b).setState(CampaignData.TreatyState.NEUTRAL, day);
                // Both factions saw the compulsory peace period through without breaking it.
                campaign.changeFactionReliability(a, 5);
                campaign.changeFactionReliability(b, 5);
                extra.setForcedNap(a, b, 0);
                CampaignManager.syncRelation(server.overworld(), a, b, CampaignData.TreatyState.NEUTRAL);
                CampaignToastSender.relationChanged(server, a, b, "Neutral — non-aggression obligation fulfilled");
            }
        }
    }

    public static void onMilitaryDeath(ServerLevel level, Entity entity, PrototypeData.SettlementRecord settlement) {
        if (level == null || entity == null || settlement == null) return;
        CampaignData campaign = CampaignData.get(level);
        Campaign23Data extra = Campaign23Data.get(level);
        String faction = settlement.getFactionTeam();
        boolean levy = "levy".equals(entity.getPersistentData().getString(PrototypeManager.TAG_KIND));
        int impact = levy ? 1 : 2;
        for (CampaignData.WarRecord war : campaign.getWars()) {
            if (war.status() != CampaignData.WarStatus.ACTIVE) continue;
            boolean attacker = settlement.getId().equals(war.attackerSettlement())
                    || (!faction.isBlank() && (faction.equals(war.attackerFaction()) && (war.type() != CampaignData.WarType.SETTLEMENT_WAR || war.attackerFactionJoined())
                    || war.supportingAttackers().contains(faction)));
            boolean defender = settlement.getId().equals(war.defenderSettlement())
                    || (!faction.isBlank() && (faction.equals(war.defenderFaction()) && (war.type() != CampaignData.WarType.SETTLEMENT_WAR || war.defenderFactionJoined())
                    || war.supportingDefenders().contains(faction)));
            if (attacker == defender) continue;
            int before = extra.warScore(war.id());
            extra.changeWarScore(war.id(), attacker ? -impact : impact);
            int score = extra.warScore(war.id());
            if (before < 90 && score >= 90) {
                notifyWarSide(level.getServer(), war, true, "ENEMY CAPITULATING", "War score has reached +" + score + ". Compulsory peace terms are now available.");
                notifyWarSide(level.getServer(), war, false, "CAPITULATION", "Your side has been decisively defeated. The opposing side may now enforce valid peace terms.");
                campaign.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR, "War reaches capitulation",
                        war.attackerSettlement().isBlank() ? "The attacking side has forced its enemy to capitulate." : settlementDisplayName(level, war.attackerSettlement()) + " has forced the opposing side to capitulate.",
                        level.getDayTime() / 24000L, participatingWarFaction(war, true), war.attackerSettlement(), CampaignData.NewsVisibility.PUBLIC);
            } else if (before > -90 && score <= -90) {
                notifyWarSide(level.getServer(), war, false, "ENEMY CAPITULATING", "War score has reached " + score + ". Compulsory peace terms are now available.");
                notifyWarSide(level.getServer(), war, true, "CAPITULATION", "Your side has been decisively defeated. The opposing side may now enforce valid peace terms.");
                campaign.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR, "War reaches capitulation",
                        war.defenderSettlement().isBlank() ? "The defending side has forced its enemy to capitulate." : settlementDisplayName(level, war.defenderSettlement()) + " has forced the opposing side to capitulate.",
                        level.getDayTime() / 24000L, participatingWarFaction(war, false), war.defenderSettlement(), CampaignData.NewsVisibility.PUBLIC);
            }
        }
    }

    private static String settlementDisplayName(ServerLevel level, String settlementId) {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(level).getSettlement(settlementId);
        return settlement == null ? settlementId : settlement.getName();
    }

    private static String participatingWarFaction(CampaignData.WarRecord war, boolean attackerSide) {
        boolean factionJoined = war.type() != CampaignData.WarType.SETTLEMENT_WAR
                || (attackerSide ? war.attackerFactionJoined() : war.defenderFactionJoined());
        return factionJoined ? (attackerSide ? war.attackerFaction() : war.defenderFaction()) : "";
    }

    private static void notifyWarSide(MinecraftServer server, CampaignData.WarRecord war, boolean attackerSide, String title, String body) {
        String faction = participatingWarFaction(war, attackerSide);
        if (faction != null && !faction.isBlank()) {
            CampaignToastSender.faction(server, faction, title, body);
        } else {
            String settlement = attackerSide ? war.attackerSettlement() : war.defenderSettlement();
            if (settlement != null && !settlement.isBlank()) CampaignToastSender.settlementManagers(server, settlement, title, body);
        }
    }

    public static void onLevyDeath(ServerLevel level, Entity entity) {
        if (entity == null || !entity.getPersistentData().getBoolean(PrototypeManager.TAG_MANAGED)
                || !"levy".equals(entity.getPersistentData().getString(PrototypeManager.TAG_KIND))
                || !entity.getPersistentData().hasUUID(PrototypeManager.TAG_BATCH)) return;
        UUID batch = entity.getPersistentData().getUUID(PrototypeManager.TAG_BATCH);
        Campaign23Data extra = Campaign23Data.get(level);
        Campaign23Data.LevyDeployment deployment = extra.levyDeployment(batch);
        if (deployment != null) {
            deployment.returnedUnits = Math.min(deployment.initialUnits, deployment.returnedUnits + 1);
            extra.setDirty();
        }
    }

    /**
     * Finalizes a levy deployment when its final tracked soldier dies. Without this cleanup a completely
     * destroyed levy would remain "active" forever, continuing to consume deployment capacity and eventually
     * accumulating service-unrest modifiers even though no levymen remained in the world.
     */
    public static void onLevyBatchDestroyed(ServerLevel level, PrototypeData.SettlementRecord settlement, UUID batchId) {
        if (level == null || settlement == null || batchId == null) return;
        Campaign23Data extra = Campaign23Data.get(level);
        Campaign23Data.LevyDeployment deployment = extra.levyDeployment(batchId);
        if (deployment == null) return;

        long now = extra.nowMillis(settlement.getId());
        long serviceAge = Math.max(0L, now - deployment.deployedAtMillis);
        Campaign23Data.ModifierRecord massacre = applyLevyCasualtyModifier(level, settlement, extra, 1.0D);
        CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "LEVY MASSACRE",
                "The levy has been annihilated. 0 of " + deployment.initialUnits + " men returned; " + (massacre == null ? "the settlement is in mourning." : massacre.detail));
        CampaignData.get(level).addNews(CampaignData.NewsCategory.MILITARY, CampaignData.NewsImportance.MAJOR,
                "Levy Massacre", settlement.getName() + " lost its entire levy of " + deployment.initialUnits + " men.",
                level.getDayTime() / 24000L, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
        extra.removeLevyDeployment(batchId);
        settleLevyServicePenaltyAfterReturn(level, settlement, extra, serviceAge);

        long fullCooldown = SettlementConfig.hoursMillis(SettlementConfig.LEVY_FULL_COOLDOWN_HOURS);
        long minimumCooldown = SettlementConfig.hoursMillis(SettlementConfig.LEVY_MIN_COOLDOWN_HOURS);
        long cooldown = Math.max(minimumCooldown, Math.round(fullCooldown * Math.min(1.0D, deployment.initialUnits / 300.0D)));
        if (deployment.ownerId != null) {
            extra.setLevyCooldown(deployment.ownerId, Math.max(extra.levyCooldownUntil(deployment.ownerId), now + cooldown));
        }
        settlement.addChronicle("The levy was annihilated: 0/" + deployment.initialUnits
                + " men returned. A full casualty crisis and levy cooldown were applied.");
    }

    public static void applyEmergencyMobilization(ServerLevel level, String factionId) {
        if (factionId == null || factionId.isBlank()) return;
        PrototypeData settlements = PrototypeData.get(level);
        Set<UUID> affected = new HashSet<>();
        for (PrototypeData.SettlementRecord settlement : settlements.settlementsForFaction(factionId)) {
            settlement.getMembers().forEach((member, role) -> {
                if (role.canManage()) affected.add(member);
            });
        }
        applyEmergencyMobilization(level, affected, mobilization -> factionId.equals(mobilization.factionId));
    }

    /** Emergency relief for an independent settlement. Only people allowed to raise its levy receive relief. */
    public static void applyEmergencyMobilization(ServerLevel level, PrototypeData.SettlementRecord settlement) {
        if (settlement == null) return;
        if (!settlement.getFactionTeam().isBlank()) {
            applyEmergencyMobilization(level, settlement.getFactionTeam());
            return;
        }
        Set<UUID> affected = new HashSet<>();
        settlement.getMembers().forEach((member, role) -> {
            if (role.canManage()) affected.add(member);
        });
        if (settlement.getOwnerId() != null) affected.add(settlement.getOwnerId());
        applyEmergencyMobilization(level, affected, mobilization -> settlement.getId().equals(mobilization.settlementId));
    }

    private static void applyEmergencyMobilization(ServerLevel level, Set<UUID> affected,
                                                     java.util.function.Predicate<Campaign23Data.LevyMobilization> mobilizationFilter) {
        if (affected == null || affected.isEmpty()) return;
        Campaign23Data extra = Campaign23Data.get(level);
        long now = System.currentTimeMillis();
        double remainingMultiplier = 1.0D - SettlementConfig.LEVY_EMERGENCY_RELIEF_PERCENT.get() / 100.0D;
        for (UUID member : affected) {
            long until = extra.levyCooldownUntil(member);
            if (until > now) extra.setLevyCooldown(member, now + Math.round((until - now) * remainingMultiplier));
        }
        for (Campaign23Data.LevyMobilization mobilization : extra.levyMobilizations()) {
            if (!affected.contains(mobilization.ownerId) || !mobilizationFilter.test(mobilization)) continue;
            long remaining = Math.max(0L, mobilization.finishesAtMillis - now);
            // Mobilization timestamps are immutable, so relief advances only this settlement's campaign clock.
            extra.advanceDebugTime(mobilization.settlementId, Math.round(remaining * (1.0D - remainingMultiplier)));
        }
    }

    private static ServerLevel levelForRegion(MinecraftServer server, Campaign23Data.RegionRecord region) {
        if (server == null || region == null || region.dimension().isBlank()) return null;
        net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.tryParse(region.dimension());
        if (location == null) return null;
        return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location));
    }

    /**
     * Counts genuinely usable standing columns rather than treating every X/Z coordinate inside a rectangular
     * selection as an army slot. This keeps caves, roofs, water-filled yards and obstructed courtyards from
     * pretending to be a 300-man parade field. Stops as soon as the requested capacity is proven.
     */
    public static int usableMusterCapacity(ServerLevel level, Campaign23Data.RegionRecord area, int stopAfter) {
        if (level == null || area == null || !area.valid()) return 0;
        int wanted = stopAfter <= 0 ? Integer.MAX_VALUE : stopAfter;
        int capacity = 0;
        for (int x = area.min().getX(); x <= area.max().getX(); x++) {
            for (int z = area.min().getZ(); z <= area.max().getZ(); z++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (surface < area.min().getY() - 4 || surface > area.max().getY() + 8) continue;
                BlockPos feet = new BlockPos(x, surface, z);
                if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
                BlockPos floor = feet.below();
                if (!level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)) continue;
                capacity++;
                if (capacity >= wanted) return capacity;
            }
        }
        return capacity;
    }

    public static BlockPos findMusterSpawn(ServerLevel level, PrototypeData.SettlementRecord settlement, int index) {
        Campaign23Data.RegionRecord area = Campaign23Data.get(level).musterArea(settlement.getId());
        if (area == null || !area.valid() || !area.dimension().equals(level.dimension().location().toString())) {
            BlockPos base = settlement.getMusterGround() == null ? settlement.getTownSquare() : settlement.getMusterGround();
            return oldGridSafe(level, base == null ? BlockPos.ZERO : base, index);
        }
        int width = Math.max(1, area.max().getX() - area.min().getX() + 1);
        int depth = Math.max(1, area.max().getZ() - area.min().getZ() + 1);
        int total = width * depth;
        for (int attempt = 0; attempt < Math.min(total, 128); attempt++) {
            int slot = Math.floorMod(index + attempt, total);
            int x = area.min().getX() + (slot % width);
            int z = area.min().getZ() + ((slot / width) % depth);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = Math.max(area.min().getY(), Math.min(area.max().getY() + 2, surface));
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafeStanding(level, pos)) return pos;
        }
        return oldGridSafe(level, area.min(), index);
    }

    private static BlockPos oldGridSafe(ServerLevel level, BlockPos base, int index) {
        int columns = 12;
        int x = index % columns;
        int z = index / columns;
        BlockPos candidate = base.offset(x - columns / 2, 0, z);
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos value = candidate.offset(0, dy, 0);
            if (isSafeStanding(level, value)) return value;
        }
        return candidate;
    }

    private static boolean isSafeStanding(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    public static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L; seconds %= 86400L;
        long hours = seconds / 3600L; seconds %= 3600L;
        long minutes = seconds / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    public static String pretty(String raw) {
        String value = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "";
        String[] words = value.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
