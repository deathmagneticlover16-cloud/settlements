package ee.lastpagans.settlements;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative settlement, economy, military and campaign systems. */
public final class CampaignSystemsManager {
    /** Documented defaults; live balance values come from SettlementConfig. */
    public static final int PLAYER_LEVY_CAP = 300;
    public static final int FACTION_LEVY_CAP = 500;
    public static final int LEVY_LEVEL_CAP = 3;

    private static final Map<UUID, BlockPos> REGION_POS1 = new HashMap<>();
    private static final Map<UUID, String> REGION_DIM = new HashMap<>();

    private CampaignSystemsManager() {}

    public static void setRegionPos1(ServerPlayer player) {
        REGION_POS1.put(player.getUUID(), player.blockPosition());
        REGION_DIM.put(player.getUUID(), player.serverLevel().dimension().location().toString());
    }

    public static CampaignSystemsData.RegionRecord completeRegion(ServerPlayer player) {
        BlockPos first = REGION_POS1.remove(player.getUUID());
        String dimension = REGION_DIM.remove(player.getUUID());
        if (first == null || dimension == null || !dimension.equals(player.serverLevel().dimension().location().toString())) return null;
        return new CampaignSystemsData.RegionRecord(dimension, first, player.blockPosition());
    }

    public static void clearPlayerState(UUID playerId) {
        if (playerId == null) return;
        REGION_POS1.remove(playerId);
        REGION_DIM.remove(playerId);
    }

    public static void clearRuntimeState() {
        REGION_POS1.clear();
        REGION_DIM.clear();
    }

    public static int recalculateHousing(ServerLevel level, SettlementData.SettlementRecord settlement) {
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        int beds = 0;
        for (CampaignSystemsData.RegionRecord region : extra.housingAreas(settlement.getId())) {
            if (!region.valid() || region.volume() > CampaignSystemsData.MAX_HOUSING_VOLUME) continue;
            ServerLevel regionLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.tryParse(region.dimension())));
            if (regionLevel == null) continue;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = region.min().getX(); x <= region.max().getX(); x++) {
                for (int z = region.min().getZ(); z <= region.max().getZ(); z++) {
                    cursor.set(x, region.min().getY(), z);
                    if (!regionLevel.hasChunkAt(cursor)) continue;
                    for (int y = region.min().getY(); y <= region.max().getY(); y++) {
                        cursor.setY(y);
                        BlockState state = regionLevel.getBlockState(cursor);
                        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)
                                && state.getValue(BedBlock.PART) == BedPart.HEAD) beds++;
                    }
                }
            }
        }
        settlement.setHousingCapacity(beds);
        return beds;
    }

    public static void processCampaignSystems(MinecraftServer server, long day) {
        SettlementData settlements = SettlementData.get(server.overworld());
        CampaignSystemsData extra = CampaignSystemsData.get(server.overworld());
        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            extra.expireModifiers(settlement.getId());
            updateReligiousUnrest(server, settlement, extra, settlements);
            updateRulerDevotionBenefit(settlement, extra, settlements);
            refreshTaxPolicyModifier(settlement, extra);
            processTaxes(server.overworld(), settlement, extra, day);
            applyDailyModifierEffects(settlement, extra, day);
            updateSettlementTier(server.overworld(), settlement, extra);
        }
        processLevyMobilizations(server, extra, settlements);
        processLevyServicePenalties(server, extra, settlements);
        processForcedNapExpiry(server, extra, day);
        processPassiveRelations(server, extra, day);
        processDevotionSites(server, extra, settlements, day);
    }

    /**
     * Advances only settlement-local campaign simulation for admin testing. Global diplomacy
     * clocks (forced NAPs and other campaign-day obligations) are deliberately excluded so
     * testing a five-day levy modifier in one town cannot age the entire server campaign.
     */
    public static void processDebugSettlement(MinecraftServer server, SettlementData.SettlementRecord settlement, long simulatedDay) {
        if (server == null || settlement == null) return;
        SettlementData settlements = SettlementData.get(server.overworld());
        CampaignSystemsData extra = CampaignSystemsData.get(server.overworld());
        extra.expireModifiers(settlement.getId());
        updateReligiousUnrest(server, settlement, extra, settlements);
        updateRulerDevotionBenefit(settlement, extra, settlements);
        refreshTaxPolicyModifier(settlement, extra);
        processTaxes(server.overworld(), settlement, extra, simulatedDay);
        applyDailyModifierEffects(settlement, extra, simulatedDay);
        updateSettlementTier(server.overworld(), settlement, extra);
        updateLevyServicePenaltyForSettlement(server, extra, settlement);
    }

    public static void refreshTaxPolicyModifier(SettlementData.SettlementRecord settlement, CampaignSystemsData extra) {
        String modifierId = "tax_policy";
        int rate = extra.taxRatePercent(settlement.getId());
        int stability = extra.taxStabilityPerDay(settlement.getId());
        if (stability == 0 || settlement.getCivilianPopulation() <= 0) {
            extra.removeModifier(settlement.getId(), modifierId);
            return;
        }
        String tone = rate < 100 ? "POSITIVE" : "NEGATIVE";
        String detail = rate < 100
                ? "Lighter taxation improves public order at the cost of State Treasury income."
                : "Heavier taxation raises State Treasury income but strains public order.";
        extra.addModifier(settlement.getId(), new CampaignSystemsData.ModifierRecord(
                modifierId, extra.taxDisplayName(settlement.getId()) + " Taxation (" + rate + "%)",
                "settlements:textures/gui/icons/treasury.png", tone, "Tax policy", detail,
                stability, 0, 0L));
    }

    private static void processTaxes(ServerLevel level, SettlementData.SettlementRecord settlement, CampaignSystemsData extra, long day) {
        if (!extra.beginTaxDay(settlement.getId(), day)) return;
        int population = Math.max(0, settlement.getCivilianPopulation());
        if (population <= 0) {
            extra.setLastTaxIncome(settlement.getId(), 0);
            return;
        }
        int populationPerCoin = Math.max(1, SettlementConfig.TAX_POPULATION_PER_COIN.get());
        int base = Math.max(1, (population + populationPerCoin - 1) / populationPerCoin);
        int income = (int)Math.round(base * (extra.taxRatePercent(settlement.getId()) / 100.0D));
        income = Math.max(0, income);
        if (income > 0) {
            StateTreasury.change(level, settlement, income);
        }
        extra.setLastTaxIncome(settlement.getId(), income);
    }

    public static int projectedTaxIncome(SettlementData.SettlementRecord settlement, CampaignSystemsData extra) {
        if (settlement == null || extra == null || settlement.getCivilianPopulation() <= 0) return 0;
        int populationPerCoin = Math.max(1, SettlementConfig.TAX_POPULATION_PER_COIN.get());
        int base = Math.max(1, (settlement.getCivilianPopulation() + populationPerCoin - 1) / populationPerCoin);
        return Math.max(0, (int)Math.round(base * (extra.taxRatePercent(settlement.getId()) / 100.0D)));
    }

    private static void updateRulerDevotionBenefit(SettlementData.SettlementRecord settlement, CampaignSystemsData extra, SettlementData settlements) {
        UUID owner = settlement.getOwnerId();
        int devotion = owner == null ? 0 : extra.devotion(owner);
        String id = "devotion_guidance";
        if (devotion < 20) { extra.removeModifier(settlement.getId(), id); return; }
        int stability = devotion >= 70 ? 1 : 0;
        int morale = devotion >= 90 ? 5 : devotion >= 70 ? 3 : devotion >= 45 ? 2 : 1;
        String tier = devotion >= 90 ? "Exalted" : devotion >= 70 ? "Devout" : devotion >= 45 ? "Faithful" : "Observant";
        String detail = "The ruler's " + tier + " standing with their faith gives the settlement "
                + (stability == 0 ? "" : "+" + stability + " daily stability, ")
                + "+" + morale + " recruit morale.";
        extra.addModifier(settlement.getId(), new CampaignSystemsData.ModifierRecord(id, tier + " Ruler", "minecraft:golden_apple", "POSITIVE",
                "Ruler devotion", detail, stability, morale, 0L));
    }

    private static void updateReligiousUnrest(MinecraftServer server, SettlementData.SettlementRecord settlement, CampaignSystemsData extra, SettlementData settlements) {
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

    private static String factionLeaderFaith(String factionId, SettlementData settlements) {
        if (factionId == null || factionId.isBlank() || com.talhanation.recruits.FactionEvents.recruitsFactionManager == null) return "";
        com.talhanation.recruits.world.RecruitsFaction faction = com.talhanation.recruits.FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null || faction.getTeamLeaderUUID() == null) return "";
        SettlementData.CharacterRecord leader = settlements.getCharacter(faction.getTeamLeaderUUID());
        return leader == null ? "" : leader.getFaith();
    }

    private static void processDevotionSites(MinecraftServer server, CampaignSystemsData extra, SettlementData settlements, long day) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SettlementData.CharacterRecord character = settlements.getCharacter(player.getUUID());
            if (character == null || !character.isCreated()) continue;
            String dim = player.serverLevel().dimension().location().toString();
            BlockPos pos = player.blockPosition();
            if (extra.lastHolyVisitDay(player.getUUID()) < day) {
                for (CampaignSystemsData.HolySiteRecord site : extra.holySites()) {
                    if (site.region().contains(dim, pos) && site.faith().equalsIgnoreCase(character.getFaith())) {
                        extra.changeDevotion(player.getUUID(), site.devotionGain());
                        extra.markHolyVisit(player.getUUID(), day);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Holy Site visited: +" + site.devotionGain() + " Devotion (" + extra.devotion(player.getUUID()) + "/100)."));
                        break;
                    }
                }
            }
            if (extra.lastPrayerDay(player.getUUID()) < day) {
                for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
                    CampaignSystemsData.RegionRecord site = extra.prayerSite(settlement.getId());
                    if (site == null || !site.contains(dim, pos)) continue;
                    boolean allowed = settlement.getFactionTeam().isBlank() ? settlement.getRole(player.getUUID()) != SettlementData.SettlementRole.OUTSIDER
                            : settlement.getFactionTeam().equals(CampaignManager.factionId(player));
                    if (!allowed) continue;
                    int gain = SettlementConfig.PRAYER_DEVOTION_GAIN.get();
                    extra.changeDevotion(player.getUUID(), gain);
                    extra.markPrayer(player.getUUID(), day);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Prayer at " + settlement.getName() + ": +" + gain + " Devotion (" + extra.devotion(player.getUUID()) + "/100)."));
                    break;
                }
            }
        }
    }

    private static void applyDailyModifierEffects(SettlementData.SettlementRecord settlement, CampaignSystemsData extra, long day) {
        if (!extra.beginModifierDay(settlement.getId(), day)) return;
        int stability = extra.modifiers(settlement.getId()).stream().mapToInt(value -> value.stabilityPerDay).sum();
        if (stability != 0) settlement.changeStability(stability);
    }

    private static void updateSettlementTier(ServerLevel level, SettlementData.SettlementRecord settlement, CampaignSystemsData extra) {
        if (settlement.getRank() == SettlementData.SettlementRank.VILLAGE
                && settlement.getCivilianPopulation() >= 100
                && settlement.getStability() >= 45 && settlement.getHousingCapacity() >= settlement.getVillagerCount()
                && CaptureZoneManager.requirementsMet(level, settlement, SettlementData.SettlementRank.TOWN)) {
            settlement.setRank(SettlementData.SettlementRank.TOWN);
            extra.addModifier(settlement.getId(), standardModifier(level, settlement.getId(), "civic_pride", 2L * 24L * 60L * 60L * 1000L));
            settlement.addChronicle("The settlement grew into a Town. Civic Pride spread through the population.");
            long day = level.getDayTime() / 24000L;
            CampaignData.get(level).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.NORMAL,
                    "Settlement advanced", settlement.getName() + " has grown from a Village into a Town.",
                    day, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
            CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "SETTLEMENT ADVANCED",
                    settlement.getName() + " is now a Town. Civic Pride is temporarily boosting the settlement.");
        }
        if (settlement.getRank() == SettlementData.SettlementRank.TOWN
                && settlement.getCivilianPopulation() >= 250 && settlement.getStability() >= 55
                && settlement.getHousingCapacity() >= settlement.getVillagerCount()
                && CaptureZoneManager.requirementsMet(level, settlement, SettlementData.SettlementRank.CITY)) {
            settlement.setRank(SettlementData.SettlementRank.CITY);
            extra.addModifier(settlement.getId(), standardModifier(level, settlement.getId(), "civic_pride", 3L * 24L * 60L * 60L * 1000L));
            settlement.addChronicle("The settlement developed into a City.");
            long day = level.getDayTime() / 24000L;
            CampaignData.get(level).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.MAJOR,
                    "City emerged", settlement.getName() + " has developed into a City.",
                    day, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
            CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "CITY",
                    settlement.getName() + " has developed into a City.");
        }
        if (settlement.getRank() == SettlementData.SettlementRank.CITY
                && settlement.getCivilianPopulation() >= SettlementConfig.MAJOR_CITY_MIN_POPULATION.get()
                && settlement.getStability() >= SettlementConfig.MAJOR_CITY_MIN_STABILITY.get()
                && settlement.getHousingCapacity() >= settlement.getVillagerCount()
                && CaptureZoneManager.requirementsMet(level, settlement, SettlementData.SettlementRank.MAJOR_CITY)) {
            settlement.setRank(SettlementData.SettlementRank.MAJOR_CITY);
            extra.addModifier(settlement.getId(), standardModifier(level, settlement.getId(), "civic_pride", 4L * 24L * 60L * 60L * 1000L));
            settlement.addChronicle("The settlement became a Major City.");
            long day = level.getDayTime() / 24000L;
            CampaignData.get(level).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.MAJOR,
                    "Major City", settlement.getName() + " has become a Major City.",
                    day, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
            CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "MAJOR CITY",
                    settlement.getName() + " is now a Major City and can support Tier IV research.");
        }
    }

    public static CampaignSystemsData.ModifierRecord standardModifier(String settlementId, String raw, long durationMs) {
        String id = SettlementData.normalizeId(raw);
        long expires = 0L;
        return switch (id) {
            case "civic_pride" -> new CampaignSystemsData.ModifierRecord(id, "Civic Pride", "minecraft:bell", "POSITIVE", "Settlement progression", "+5 stability and +3 morale while citizens celebrate advancement.", 5, 3, expires);
            case "local_religious_unrest" -> new CampaignSystemsData.ModifierRecord(id, "Local Religious Unrest", "minecraft:rotten_flesh", "NEGATIVE", "Religious diplomacy", "The population dislikes close political ties with a strongly incompatible faith.", -3, -2, expires);
            case "foreign_soldiers" -> new CampaignSystemsData.ModifierRecord(id, "Foreign Soldiers", "minecraft:iron_sword", "MIXED", "Peace treaty", "Foreign manpower is available, but locals resent relying on outside soldiers.", -8, 0, expires);
            case "plague" -> new CampaignSystemsData.ModifierRecord(id, "Plague", "minecraft:spider_eye", "NEGATIVE", "Campaign event", "Disease is disrupting settlement life and military confidence.", -3, -5, expires);
            default -> new CampaignSystemsData.ModifierRecord(id, pretty(id), "minecraft:book", "NEUTRAL", "Debug", "Administrative test modifier.", 0, 0, expires);
        };
    }

    /** Actual timestamp helper for standard modifiers. */
    public static CampaignSystemsData.ModifierRecord standardModifier(ServerLevel level, String settlementId, String raw, long durationMs) {
        String id = SettlementData.normalizeId(raw);
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        long expires = durationMs <= 0 ? 0 : extra.nowMillis(settlementId) + durationMs;
        CampaignSystemsData.ModifierRecord base = standardModifier(settlementId, id, 0);
        return new CampaignSystemsData.ModifierRecord(base.id, base.name, base.iconItem, base.tone, base.source, base.detail,
                base.stabilityPerDay, base.morale, expires);
    }

    public static String raiseLevy(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        long now = extra.nowMillis(settlement.getId());
        if (extra.levyCooldownUntil(player.getUUID()) > now) {
            return "Your levy is still on cooldown for " + formatDuration(extra.levyCooldownUntil(player.getUUID()) - now) + ".";
        }
        if (extra.levyMobilizations().stream().anyMatch(value -> player.getUUID().equals(value.ownerId))) {
            return "You are already mustering a levy.";
        }
        CampaignSystemsData.RegionRecord area = extra.musterArea(settlement.getId());
        if (area == null || !area.valid()) return "This settlement has no rectangular Muster Ground. Set pos1 and pos2 first.";
        int available = settlement.getAvailableManpower();
        if (available <= 0) return "No available manpower to mobilise.";
        double share = settlement.getCivilianPopulation() < SettlementManager.SMALL_SETTLEMENT_POPULATION
                ? SettlementManager.SMALL_LEVY_SHARE : SettlementManager.NORMAL_LEVY_SHARE;
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
        long duration = levyMusterDurationMs(allowedUnits, SettlementData.get(player.serverLevel()).getCharacter(player.getUUID()));
        UUID id = UUID.randomUUID();
        extra.putLevyMobilization(new CampaignSystemsData.LevyMobilization(id, player.getUUID(), settlement.getId(),
                settlement.getFactionTeam(), manpowerSpent, allowedUnits, 0, now, now + duration, batch));
        settlement.addChronicle("A levy of " + allowedUnits + " men began mustering under " + SettlementManager.characterOrAccountName(player) + ".");
        String capped = allowedUnits < desiredUnits ? " Requested levy was reduced to the maximum currently available." : "";
        return "Mobilisation ordered: " + allowedUnits + " levymen will assemble over " + formatDuration(duration) + "." + capped;
    }

    private static long levyMusterDurationMs(int units, SettlementData.CharacterRecord character) {
        double secondsPerUnit = SettlementConfig.LEVY_SECONDS_PER_SOLDIER.get();
        int traitPercent = character == null ? 0 : CampaignDefinitions.totalEffect(character.getTraits(), CampaignDefinitions.EffectKind.LEVY_MUSTER);
        // LEVY_MUSTER effects are stored as percentages; negative values reduce mobilisation time.
        secondsPerUnit *= Math.max(0.10D, 1.0D + traitPercent / 100.0D);
        long minimum = SettlementConfig.LEVY_MIN_MUSTER_SECONDS.get().longValue() * 1000L;
        long maximum = SettlementConfig.LEVY_MAX_MUSTER_MINUTES.get().longValue() * 60L * 1000L;
        return Math.max(minimum, Math.min(maximum, Math.round(units * secondsPerUnit * 1000.0D)));
    }

    private static int deployedForPlayer(CampaignSystemsData extra, UUID player) {
        return extra.levyDeployments().stream().filter(value -> player.equals(value.ownerId)).mapToInt(value -> Math.max(0, value.initialUnits - value.returnedUnits)).sum()
                + extra.levyMobilizations().stream().filter(value -> player.equals(value.ownerId)).mapToInt(value -> value.totalUnits).sum();
    }

    private static int deployedForFaction(CampaignSystemsData extra, String faction) {
        String id = faction == null ? "" : faction;
        return extra.levyDeployments().stream().filter(value -> id.equals(value.factionId)).mapToInt(value -> Math.max(0, value.initialUnits - value.returnedUnits)).sum()
                + extra.levyMobilizations().stream().filter(value -> id.equals(value.factionId)).mapToInt(value -> value.totalUnits).sum();
    }

    private static void processLevyMobilizations(MinecraftServer server, CampaignSystemsData extra, SettlementData settlements) {
        List<CampaignSystemsData.LevyMobilization> values = new ArrayList<>(extra.levyMobilizations());
        for (CampaignSystemsData.LevyMobilization mobilization : values) {
            SettlementData.SettlementRecord settlement = settlements.getSettlement(mobilization.settlementId);
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
            CampaignSystemsData.RegionRecord region = extra.musterArea(settlement.getId());
            if (region != null && !level.dimension().location().toString().equals(region.dimension())) {
                level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.tryParse(region.dimension())));
            }
            if (level == null) continue;
            PlayerTeam team = settlement.getFactionTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getFactionTeam());
            RecruitsGroup group = levyGroup(owner, level, settlement);
            for (int i = 0; i < needed && mobilization.spawnedUnits < mobilization.totalUnits; i++) {
                AbstractRecruitEntity recruit = SettlementManager.spawnRecruit(level, owner, settlement, CampaignData.UnitType.LEVY,
                        mobilization.spawnedUnits, team, true, mobilization.batchId);
                if (recruit == null) break;
                assignLevyOwner(recruit, owner, group, level);
                int traitHealth = 0;
                SettlementData.CharacterRecord commander = settlements.getCharacter(owner.getUUID());
                if (commander != null) traitHealth = CampaignDefinitions.totalEffect(commander.getTraits(), CampaignDefinitions.EffectKind.LEVY_HEALTH);
                int maxHealth = (mobilization.totalUnits < SettlementConfig.SMALL_LEVY_SIZE_THRESHOLD.get()
                        ? SettlementConfig.SMALL_LEVY_HEALTH.get() : SettlementConfig.LARGE_LEVY_HEALTH.get()) + traitHealth;
                AttributeInstance health = recruit.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) health.setBaseValue(maxHealth);
                recruit.setHealth(maxHealth);
                int startXp = commander == null ? 0
                        : CampaignDefinitions.totalEffect(commander.getTraits(), CampaignDefinitions.EffectKind.LEVY_XP);
                if (startXp > 0) recruit.addXp(startXp);
                int levyCap = SettlementConfig.effectiveLevyLevelCap();
                if (recruit.getXpLevel() > levyCap) recruit.setXpLevel(levyCap);
                settlement.addLevymanToBatch(mobilization.batchId, recruit.getUUID());
                mobilization.spawnedUnits++;
            }
            extra.setDirty();
            if (mobilization.spawnedUnits >= mobilization.totalUnits) {
                extra.putLevyDeployment(new CampaignSystemsData.LevyDeployment(mobilization.batchId, mobilization.ownerId,
                        mobilization.settlementId, mobilization.factionId, mobilization.totalUnits, mobilization.manpowerSpent, 0, now));
                extra.removeLevyMobilization(mobilization.id);
                settlement.addChronicle("The levy finished assembling: " + mobilization.totalUnits + " levymen entered service.");
                owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your " + settlement.getName() + " levy has finished mustering (" + mobilization.totalUnits + " men)."));
            }
        }
    }

    private static RecruitsGroup levyGroup(ServerPlayer player, ServerLevel level, SettlementData.SettlementRecord settlement) {
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

    public static String returnLevy(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        List<CampaignSystemsData.LevyDeployment> deployments = extra.levyDeployments().stream()
                .filter(value -> player.getUUID().equals(value.ownerId) && settlement.getId().equals(value.settlementId)).toList();
        if (deployments.isEmpty()) return "You have no active levy from this settlement to return.";
        int totalInitial = 0, totalSurvivors = 0, totalManpower = 0;
        long oldestServiceAge = 0L;
        long now = extra.nowMillis(settlement.getId());
        for (CampaignSystemsData.LevyDeployment deployment : deployments) {
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
        CampaignSystemsData.ModifierRecord casualtyModifier = applyLevyCasualtyModifier(player.serverLevel(), settlement, extra, lossRate);
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
        long fullCooldown = SettlementConfig.hoursMillis(SettlementConfig.LEVY_FULL_COOLDOWN_HOURS);
        long minimumCooldown = SettlementConfig.hoursMillis(SettlementConfig.LEVY_MIN_COOLDOWN_HOURS);
        long cooldown = Math.max(minimumCooldown, Math.round(fullCooldown * Math.min(1.0D, totalInitial / 300.0D)));
        extra.setLevyCooldown(player.getUUID(), now + cooldown);
        // Returned/dead levy MP becomes vacant and recovers through the normal manpower checker, preserving the existing design.
        settlement.addChronicle("The levy returned: " + totalSurvivors + "/" + totalInitial + " men came home (" + losses + " lost). Cooldown: " + formatDuration(cooldown) + ".");
        return "Returned " + totalSurvivors + " surviving levymen. Losses: " + losses + ". Levy cooldown: " + formatDuration(cooldown) + ".";
    }

    /** Administrative hard reset for a player's levy state. Loaded levy entities are removed,
     * unloaded ones are retired so they cannot reappear later, partial mobilisations are refunded,
     * and settlement batch reservations are cleared before the persistent levy records are reset. */
    public static int forceRetirePlayerLevies(ServerLevel level, UUID ownerId) {
        if (level == null || ownerId == null) return 0;
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        SettlementData settlements = SettlementData.get(level);
        MinecraftServer server = level.getServer();
        int retired = 0;
        Set<String> touchedSettlements = new HashSet<>();

        for (CampaignSystemsData.LevyMobilization mobilization : new ArrayList<>(extra.levyMobilizations())) {
            if (!ownerId.equals(mobilization.ownerId)) continue;
            SettlementData.SettlementRecord settlement = settlements.getSettlement(mobilization.settlementId);
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

        for (CampaignSystemsData.LevyDeployment deployment : new ArrayList<>(extra.levyDeployments())) {
            if (!ownerId.equals(deployment.ownerId)) continue;
            SettlementData.SettlementRecord settlement = settlements.getSettlement(deployment.settlementId);
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
            if (!stillMobilised) extra.removeModifier(settlementId, "levy_service_pressure");
        }
        return retired;
    }

    private static CampaignSystemsData.ModifierRecord applyLevyCasualtyModifier(ServerLevel level, SettlementData.SettlementRecord settlement, CampaignSystemsData extra, double lossRate) {
        double safeRate = Math.max(0.0D, Math.min(1.0D, lossRate));
        if (safeRate < 0.05D) return null;
        int stability = -Math.max(1, (int)Math.round(20.0D * safeRate * safeRate));
        int morale = -Math.max(1, (int)Math.round(15.0D * safeRate * safeRate));
        long duration = SettlementConfig.hoursMillis(24L + Math.round(144.0D * safeRate));
        int percent = (int)Math.round(safeRate * 100.0D);
        long expires = extra.nowMillis(settlement.getId()) + duration;
        CampaignSystemsData.ModifierRecord existing = extra.modifiers(settlement.getId()).stream()
                .filter(value -> value.id.equals("levy_casualties")).findFirst().orElse(null);
        if (existing != null) {
            stability = Math.min(stability, existing.stabilityPerDay);
            morale = Math.min(morale, existing.morale);
            expires = Math.max(expires, existing.expiresAtMillis);
        }
        CampaignSystemsData.ModifierRecord record = new CampaignSystemsData.ModifierRecord(
                "levy_casualties", "Levy Casualties", "minecraft:wither_rose", "NEGATIVE",
                "Levy casualties", percent + "% of the levy was lost. Penalties scale with the casualty rate.",
                stability, morale, expires);
        extra.addModifier(settlement.getId(), record);
        return record;
    }

    private static int levyServicePressure(long age) {
        long grace = SettlementConfig.hoursMillis(SettlementConfig.LEVY_SERVICE_GRACE_HOURS);
        if (age < grace) return 0;
        long step = Math.max(1L, SettlementConfig.hoursMillis(SettlementConfig.LEVY_SERVICE_PRESSURE_STEP_HOURS));
        return (int)Math.min(10L, 1L + Math.max(0L, age - grace) / step);
    }

    private static void settleLevyServicePenaltyAfterReturn(ServerLevel level, SettlementData.SettlementRecord settlement,
                                                              CampaignSystemsData extra, long oldestServiceAge) {
        boolean stillMobilised = extra.levyDeployments().stream().anyMatch(value -> settlement.getId().equals(value.settlementId));
        if (stillMobilised) return;
        if (extra.removeModifier(settlement.getId(), "levy_service_pressure") && levyServicePressure(oldestServiceAge) > 0)
            settlement.addChronicle("Domestic pressure eased when the levy returned.");
    }

    private static void processLevyServicePenalties(MinecraftServer server, CampaignSystemsData extra, SettlementData settlements) {
        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            updateLevyServicePenaltyForSettlement(server, extra, settlement);
        }
    }

    private static void updateLevyServicePenaltyForSettlement(MinecraftServer server, CampaignSystemsData extra,
                                                                SettlementData.SettlementRecord settlement) {
        int pressure = 0;
        for (CampaignSystemsData.LevyDeployment deployment : extra.levyDeployments()) {
            if (!settlement.getId().equals(deployment.settlementId)) continue;
            long age = extra.nowMillis(settlement.getId()) - deployment.deployedAtMillis;
            pressure = Math.max(pressure, levyServicePressure(age));

            // Enforce the low level cap while preserving XP earned in real combat below that cap.
            for (UUID unitId : settlement.getLevyUnitsForBatch(deployment.batchId)) {
                Entity entity = findEntity(server, unitId);
                if (entity instanceof AbstractRecruitEntity recruit) {
                    int cap = SettlementConfig.effectiveLevyLevelCap();
                    if (recruit.getXpLevel() > cap) recruit.setXpLevel(cap);
                }
            }
        }

        CampaignSystemsData.ModifierRecord current = extra.modifiers(settlement.getId()).stream()
                .filter(value -> value.id.equals("levy_service_pressure")).findFirst().orElse(null);
        if (pressure <= 0) {
            if (current != null) extra.removeModifier(settlement.getId(), "levy_service_pressure");
            return;
        }
        int stability = -Math.min(20, pressure * 2);
        int morale = -Math.min(15, pressure + (pressure / 2));
        if (current == null || current.stabilityPerDay != stability || current.morale != morale) {
            extra.addModifier(settlement.getId(), new CampaignSystemsData.ModifierRecord(
                    "levy_service_pressure", "Levy Service Pressure", "minecraft:clock", "NEGATIVE",
                    "Prolonged mobilisation", "Domestic pressure increases for every configured interval beyond the service grace period.",
                    stability, morale, 0L));
            settlement.addChronicle(current == null ? "Domestic pressure began over prolonged levy service."
                    : "Domestic pressure increased as levy service continued.");
        }
    }


    private static void processPassiveRelations(MinecraftServer server, CampaignSystemsData extra, long day) {
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

    private static void processForcedNapExpiry(MinecraftServer server, CampaignSystemsData extra, long day) {
        CampaignData campaign = CampaignData.get(server.overworld());
        if (com.talhanation.recruits.FactionEvents.recruitsFactionManager == null) return;
        List<com.talhanation.recruits.world.RecruitsFaction> factions = new ArrayList<>(com.talhanation.recruits.FactionEvents.recruitsFactionManager.getFactions());
        for (int i = 0; i < factions.size(); i++) for (int j = i + 1; j < factions.size(); j++) {
            String a = factions.get(i).getStringID(), b = factions.get(j).getStringID();
            long until = extra.forcedNapUntil(a, b);
            if (until > 0 && until <= day && campaign.relation(a, b).state() == CampaignData.TreatyState.NON_AGGRESSION) {
                campaign.relation(a, b).setState(CampaignData.TreatyState.NEUTRAL, day);
                // Both factions saw the compulsory peace period through without breaking it.
                CampaignManager.changeReliability(server.overworld(), campaign, a, 5);
                CampaignManager.changeReliability(server.overworld(), campaign, b, 5);
                extra.setForcedNap(a, b, 0);
                CampaignManager.syncRelation(server.overworld(), a, b, CampaignData.TreatyState.NEUTRAL);
                CampaignToastSender.relationChanged(server, a, b, "Neutral — non-aggression obligation fulfilled");
            }
        }
    }

    public static void onMilitaryDeath(ServerLevel level, Entity entity, SettlementData.SettlementRecord settlement) {
        if (level == null || entity == null || settlement == null) return;
        CampaignData campaign = CampaignData.get(level);
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        String faction = settlement.getFactionTeam();
        boolean levy = "levy".equals(entity.getPersistentData().getString(SettlementManager.TAG_KIND));
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
        SettlementData.SettlementRecord settlement = SettlementData.get(level).getSettlement(settlementId);
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
        if (entity == null || !entity.getPersistentData().getBoolean(SettlementManager.TAG_MANAGED)
                || !"levy".equals(entity.getPersistentData().getString(SettlementManager.TAG_KIND))
                || !entity.getPersistentData().hasUUID(SettlementManager.TAG_BATCH)) return;
        UUID batch = entity.getPersistentData().getUUID(SettlementManager.TAG_BATCH);
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        CampaignSystemsData.LevyDeployment deployment = extra.levyDeployment(batch);
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
    public static void onLevyBatchDestroyed(ServerLevel level, SettlementData.SettlementRecord settlement, UUID batchId) {
        if (level == null || settlement == null || batchId == null) return;
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        CampaignSystemsData.LevyDeployment deployment = extra.levyDeployment(batchId);
        if (deployment == null) return;

        long now = extra.nowMillis(settlement.getId());
        long serviceAge = Math.max(0L, now - deployment.deployedAtMillis);
        CampaignSystemsData.ModifierRecord massacre = applyLevyCasualtyModifier(level, settlement, extra, 1.0D);
        CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "LEVY DESTROYED",
                "The levy has been annihilated. 0 of " + deployment.initialUnits + " men returned; " + (massacre == null ? "the settlement is in mourning." : massacre.detail));
        CampaignData.get(level).addNews(CampaignData.NewsCategory.MILITARY, CampaignData.NewsImportance.MAJOR,
                "Levy destroyed", settlement.getName() + " lost its entire levy of " + deployment.initialUnits + " men.",
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
        SettlementData settlements = SettlementData.get(level);
        Set<UUID> affected = new HashSet<>();
        for (SettlementData.SettlementRecord settlement : settlements.settlementsForFaction(factionId)) {
            settlement.getMembers().forEach((member, role) -> {
                if (role.canManage()) affected.add(member);
            });
        }
        applyEmergencyMobilization(level, affected, mobilization -> factionId.equals(mobilization.factionId));
    }

    /** Emergency relief for an independent settlement. Only people allowed to raise its levy receive relief. */
    public static void applyEmergencyMobilization(ServerLevel level, SettlementData.SettlementRecord settlement) {
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
                                                     java.util.function.Predicate<CampaignSystemsData.LevyMobilization> mobilizationFilter) {
        if (affected == null || affected.isEmpty()) return;
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        long now = System.currentTimeMillis();
        double remainingMultiplier = 1.0D - SettlementConfig.LEVY_EMERGENCY_RELIEF_PERCENT.get() / 100.0D;
        for (UUID member : affected) {
            long until = extra.levyCooldownUntil(member);
            if (until > now) extra.setLevyCooldown(member, now + Math.round((until - now) * remainingMultiplier));
        }
        for (CampaignSystemsData.LevyMobilization mobilization : extra.levyMobilizations()) {
            if (!affected.contains(mobilization.ownerId) || !mobilizationFilter.test(mobilization)) continue;
            long remaining = Math.max(0L, mobilization.finishesAtMillis - now);
            // Mobilization timestamps are immutable, so relief advances only this settlement's campaign clock.
            extra.advanceDebugTime(mobilization.settlementId, Math.round(remaining * (1.0D - remainingMultiplier)));
        }
    }

    private static ServerLevel levelForRegion(MinecraftServer server, CampaignSystemsData.RegionRecord region) {
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
    public static int usableMusterCapacity(ServerLevel level, CampaignSystemsData.RegionRecord area, int stopAfter) {
        if (level == null || area == null || !area.valid()) return 0;
        int wanted = stopAfter <= 0 ? Integer.MAX_VALUE : stopAfter;
        int capacity = 0;
        for (int x = area.min().getX(); x <= area.max().getX(); x++) {
            for (int z = area.min().getZ(); z <= area.max().getZ(); z++) {
                if (!area.containsHorizontal(new BlockPos(x, area.min().getY(), z))) continue;
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

    public static BlockPos findMusterSpawn(ServerLevel level, SettlementData.SettlementRecord settlement, int index) {
        CampaignSystemsData.RegionRecord area = CampaignSystemsData.get(level).musterArea(settlement.getId());
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
            if (!area.containsHorizontal(new BlockPos(x, area.min().getY(), z))) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = Math.max(area.min().getY(), Math.min(area.max().getY() + 2, surface));
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafeStanding(level, pos)) return pos;
        }
        BlockPos center = new BlockPos((area.min().getX() + area.max().getX()) / 2, area.min().getY(),
                (area.min().getZ() + area.max().getZ()) / 2);
        return oldGridSafe(level, center, index);
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
