package ee.lastpagans.settlementprototype;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Compact server-authoritative settlement state used by the settlement and military screens. */
public record PrototypeSnapshot(
        String settlementId, String settlementName, String rank, String rankSubtitle, String role, boolean canManage, boolean canContribute,
        String factionId, String factionName, CompoundTag factionBanner,
        String bannerMode, CompoundTag settlementBannerStack, String rosterName, List<UnitTemplateEntry> unitTemplates,
        int villagerCount, double populationMultiplier, int civilianPopulation, int militaryPopulation, int totalPopulation,
        int housingCapacity, int freeHousing, int stability, int food, int wealth,
        int maximumManpower, int availableManpower, int foreignManpower, int usedManpower, int levyReservedManpower, int supportedManpower,
        int professionals, int levymen, int pendingContracts,
        boolean townSquareSet, boolean musterGroundSet, boolean barracksSet, boolean barracksChestSet,
        int barracksRadius, int barracksCapacity, int averageMorale, int mercenaries, int mercenaryWages, long mercenaryNextPaymentDay, String mercenaryStatus, LevyStatusEntry levyStatus,
        String factionTeam, boolean claimLinked, int claimCount, String claimName,
        String notice, List<EffectEntry> effects, List<String> chronicle, List<String> contributions,
        List<MemberEntry> members, List<PlayerChoice> memberCandidates, String message
) {
    public static PrototypeSnapshot from(PrototypeData.SettlementRecord settlement, ServerPlayer player, String message) {
        String factionId = settlement.getFactionTeam();
        String factionName = factionId.isBlank() ? "Independent" : factionId;
        CompoundTag factionBanner = new CompoundTag();
        if (!factionId.isBlank() && FactionEvents.recruitsFactionManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
            if (faction != null) {
                factionName = faction.getTeamDisplayName();
                if (faction.getBanner() != null) factionBanner = faction.getBanner().copy();
            }
        }

        CampaignData campaign = CampaignData.get(player.serverLevel());
        CampaignData.SettlementVisualRecord visual = campaign.visualFor(settlement.getId());
        CompoundTag settlementBanner = new CompoundTag();
        ItemStack custom = visual.getCustomBanner();
        if (!custom.isEmpty()) custom.save(settlementBanner);

        List<UnitTemplateEntry> templates = Arrays.stream(CampaignData.UnitType.values())
                .<UnitTemplateEntry>map(type -> UnitTemplateEntry.from(type, campaign.resolveUnitTemplate(settlement, type)))
                .toList();

        boolean limitedView = !player.createCommandSourceStack().hasPermission(2)
                && settlement.getRole(player.getUUID()) == PrototypeData.SettlementRole.OUTSIDER;
        MilitarySummary military = limitedView ? new MilitarySummary(0, 0, 0, -1L, "Hidden") : militarySummary(player, settlement);
        LevyStatusEntry levyStatus = limitedView ? new LevyStatusEntry(0, 0, 0, 0, 0, 0, 0L, 0L, "Hidden") : levyStatus(player, settlement);
        List<EffectEntry> effects = limitedView ? List.of() : buildEffects(player, settlement, campaign, military.averageMorale());
        List<MemberEntry> members = PrototypeManager.canManage(player, settlement) ? memberEntries(player, settlement) : List.of();
        List<PlayerChoice> memberCandidates = PrototypeManager.canManage(player, settlement) ? memberCandidates(player, settlement) : List.of();

        return new PrototypeSnapshot(
                settlement.getId(), settlement.getName(), settlement.getRank().displayName(), settlement.getRank().subtitle(), settlement.getRole(player.getUUID()).name(),
                PrototypeManager.canManage(player, settlement), PrototypeManager.canContribute(player, settlement),
                factionId, factionName, factionBanner, visual.getBannerMode().name(), settlementBanner,
                limitedView ? "Hidden" : campaign.assignedRosterName(settlement), limitedView ? List.of() : templates,
                limitedView ? 0 : settlement.getVillagerCount(), limitedView ? 0.0D : settlement.getPopulationMultiplier(), limitedView ? 0 : settlement.getCivilianPopulation(), limitedView ? 0 : settlement.getMilitaryPopulation(), settlement.getDisplayedPopulation(),
                limitedView ? 0 : settlement.getHousingCapacity(), limitedView ? 0 : settlement.getFreeHousing(), limitedView ? 0 : settlement.getStability(), limitedView ? 0 : settlement.getFood(), limitedView ? 0 : settlement.getWealth(),
                limitedView ? 0 : settlement.getMaximumManpower(), limitedView ? 0 : settlement.getAvailableManpower(), limitedView ? 0 : Campaign23Data.get(player.serverLevel()).foreignManpower(settlement.getId()), limitedView ? 0 : settlement.getUsedManpower(), limitedView ? 0 : settlement.getLevyReservedManpower(), limitedView ? 0 : settlement.getSupportedManpower(),
                limitedView ? 0 : settlement.getProfessionalCount(), limitedView ? 0 : settlement.getLivingLevymen(), limitedView ? 0 : settlement.getPendingContracts(),
                !limitedView && settlement.hasTownSquare(), !limitedView && settlement.hasMusterGround(), !limitedView && settlement.hasBarracks(), !limitedView && settlement.getBarracksChest() != null,
                limitedView ? 0 : settlement.getBarracksRadius(), limitedView ? 0 : settlement.getBarracksCapacity(), military.averageMorale(), military.mercenaries(), military.totalWages(), military.nextPaymentDay(), military.status(), levyStatus,
                settlement.getFactionTeam(), !limitedView && settlement.hasClaim(), limitedView ? 0 : settlement.getClaimCount(), limitedView ? "" : settlement.getClaimSummary(), settlement.getNotice(), effects,
                limitedView ? List.of() : settlement.getChronicle().stream().limit(8).toList(), limitedView ? List.of() : settlement.getContributions().stream().limit(8).toList(),
                members, memberCandidates, message == null ? "" : message
        );
    }

    private static List<MemberEntry> memberEntries(ServerPlayer viewer, PrototypeData.SettlementRecord settlement) {
        PrototypeData data = PrototypeData.get(viewer.serverLevel());
        List<MemberEntry> values = new ArrayList<>();
        settlement.getMembers().forEach((uuid, role) -> {
            ServerPlayer online = viewer.getServer() == null ? null : viewer.getServer().getPlayerList().getPlayer(uuid);
            PrototypeData.CharacterRecord character = data.getCharacter(uuid);
            String account = online != null ? online.getGameProfile().getName() : uuid.toString().substring(0, 8);
            String characterName = character != null && character.isCreated() ? character.getDisplayName() : account;
            values.add(new MemberEntry(uuid, account, characterName, role.name(), online != null));
        });
        values.sort(java.util.Comparator.comparingInt((MemberEntry value) -> switch (value.role()) {
            case "OWNER" -> 0; case "CO_OWNER" -> 1; case "CITIZEN" -> 2; default -> 3;
        }).thenComparing(MemberEntry::characterName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(values);
    }

    private static List<PlayerChoice> memberCandidates(ServerPlayer viewer, PrototypeData.SettlementRecord settlement) {
        if (viewer.getServer() == null) return List.of();
        PrototypeData data = PrototypeData.get(viewer.serverLevel());
        String faction = settlement.getFactionTeam();
        return viewer.getServer().getPlayerList().getPlayers().stream()
                .filter(candidate -> settlement.getRole(candidate.getUUID()) == PrototypeData.SettlementRole.OUTSIDER)
                .filter(candidate -> faction.isBlank() ? candidate.getTeam() == null
                        : candidate.getTeam() != null && faction.equals(candidate.getTeam().getName()))
                .map(candidate -> {
                    PrototypeData.CharacterRecord character = data.getCharacter(candidate.getUUID());
                    String account = candidate.getGameProfile().getName();
                    String characterName = character != null && character.isCreated() ? character.getDisplayName() : account;
                    return new PlayerChoice(candidate.getUUID(), account, characterName);
                })
                .sorted(java.util.Comparator.comparing(PlayerChoice::characterName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static MilitarySummary militarySummary(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        int moraleTotal = 0;
        int moraleCount = 0;
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        List<Campaign23Data.MercenaryContractRecord> contracts = extra.mercenariesForSettlement(settlement.getId());
        int mercenaries = contracts.size();
        int wages = contracts.stream().mapToInt(Campaign23Data.MercenaryContractRecord::wage).sum();
        long nextPayment = contracts.stream().mapToLong(Campaign23Data.MercenaryContractRecord::nextPaymentDay).min().orElse(-1L);
        int worstMissed = contracts.stream().mapToInt(Campaign23Data.MercenaryContractRecord::missedPayments).max().orElse(0);
        boolean deserting = contracts.stream().anyMatch(Campaign23Data.MercenaryContractRecord::desertPending);
        String status = mercenaries == 0 ? "None" : deserting || worstMissed >= 3 ? "Deserting"
                : worstMissed == 2 ? "Unpaid" : worstMissed == 1 ? "Discontented" : "Paid";
        for (UUID uuid : settlement.getAllMilitaryUnits()) {
            Entity found = null;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                found = level.getEntity(uuid);
                if (found != null) break;
            }
            if (!(found instanceof AbstractRecruitEntity recruit)) continue;
            moraleTotal += Math.round(recruit.getMorale());
            moraleCount++;
        }
        return new MilitarySummary(moraleCount == 0 ? 0 : Math.round((float) moraleTotal / moraleCount), mercenaries, wages, nextPayment, status);
    }

    private static LevyStatusEntry levyStatus(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        long now = extra.nowMillis(settlement.getId());
        int playerCap = SettlementConfig.PLAYER_LEVY_CAP.get();
        int factionCap = settlement.getFactionTeam().isBlank() ? playerCap : SettlementConfig.FACTION_LEVY_CAP.get();
        int playerDeployed = extra.levyDeployments().stream()
                .filter(value -> player.getUUID().equals(value.ownerId))
                .mapToInt(value -> Math.max(0, value.initialUnits - value.returnedUnits)).sum()
                + extra.levyMobilizations().stream().filter(value -> player.getUUID().equals(value.ownerId))
                .mapToInt(value -> value.totalUnits).sum();
        int factionDeployed;
        if (settlement.getFactionTeam().isBlank()) factionDeployed = playerDeployed;
        else factionDeployed = extra.levyDeployments().stream().filter(value -> settlement.getFactionTeam().equals(value.factionId))
                .mapToInt(value -> Math.max(0, value.initialUnits - value.returnedUnits)).sum()
                + extra.levyMobilizations().stream().filter(value -> settlement.getFactionTeam().equals(value.factionId))
                .mapToInt(value -> value.totalUnits).sum();

        Campaign23Data.LevyMobilization mobilization = extra.levyMobilizations().stream()
                .filter(value -> player.getUUID().equals(value.ownerId) && settlement.getId().equals(value.settlementId))
                .findFirst().orElse(null);
        int mobilizingTotal = mobilization == null ? 0 : mobilization.totalUnits;
        int mobilizingSpawned = mobilization == null ? 0 : mobilization.spawnedUnits;
        long mobilizationRemaining = mobilization == null ? 0L : Math.max(0L, mobilization.finishesAtMillis - now);
        long cooldownRemaining = Math.max(0L, extra.levyCooldownUntil(player.getUUID()) - now);
        String service = extra.modifiers(settlement.getId()).stream()
                .filter(value -> value.id.equals("extended_service") || value.id.equals("weary_levies")
                        || value.id.equals("levy_exhaustion") || value.id.equals("popular_outrage"))
                .max(java.util.Comparator.comparingInt(value -> switch (value.id) {
                    case "extended_service" -> 1; case "weary_levies" -> 2; case "levy_exhaustion" -> 3; case "popular_outrage" -> 4; default -> 0;
                })).map(value -> value.name).orElse("Normal service");
        return new LevyStatusEntry(playerDeployed, playerCap, factionDeployed, factionCap, mobilizingTotal, mobilizingSpawned,
                mobilizationRemaining, cooldownRemaining, service);
    }

    private static List<EffectEntry> buildEffects(ServerPlayer player, PrototypeData.SettlementRecord settlement,
                                                   CampaignData campaign, int morale) {
        List<EffectEntry> values = new ArrayList<>();
        int mouths = settlement.getVillagerCount() + settlement.getProfessionalCount();
        if (mouths > 0 && settlement.getFood() >= mouths) {
            values.add(new EffectEntry("Well supplied", "Food stockpile", "minecraft:bread", "POSITIVE",
                    "+2 daily stability while food covers civilian and professional upkeep.", "Daily"));
        } else if (mouths > 0 && settlement.getFood() == 0) {
            values.add(new EffectEntry("Food shortage", "Food stockpile", "minecraft:rotten_flesh", "NEGATIVE",
                    "-5 daily stability while no food is available.", "Until supplied"));
        }
        if (settlement.getVillagerCount() > settlement.getHousingCapacity()) {
            values.add(new EffectEntry("Overcrowded", "Housing", "minecraft:oak_door", "NEGATIVE",
                    "-3 daily stability because registered villagers exceed housing capacity.", "Until housing improves"));
        } else if (settlement.getFreeHousing() > 0) {
            values.add(new EffectEntry("Housing available", "Housing", "minecraft:white_bed", "POSITIVE",
                    settlement.getFreeHousing() + " registered villager spaces remain available.", "Current"));
        }
        if (morale > 0) {
            String tone = morale >= 65 ? "POSITIVE" : morale < 40 ? "NEGATIVE" : "MIXED";
            values.add(new EffectEntry("Recruit morale: " + morale + "%", "Recruits morale system", "minecraft:iron_sword", tone,
                    morale >= 65 ? "The settlement's loaded soldiers are steady or confident." : morale < 40
                            ? "Loaded soldiers are suffering Recruits' low-morale penalties." : "Loaded soldiers have middling morale.", "Current"));
        }

        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.CharacterRecord ruler = settlement.getOwnerId() == null ? null : data.getCharacter(settlement.getOwnerId());
        if (ruler != null) {
            for (String traitId : ruler.getTraits()) {
                CampaignDefinitions.TraitDefinition definition = CampaignDefinitions.trait(traitId);
                if (definition == null) continue;
                int stability = definition.effect(CampaignDefinitions.EffectKind.STABILITY);
                int wealth = definition.effect(CampaignDefinitions.EffectKind.WEALTH);
                int unitMorale = definition.effect(CampaignDefinitions.EffectKind.MORALE);
                if (stability == 0 && wealth == 0 && unitMorale == 0) continue;
                values.add(new EffectEntry(definition.name(), "Ruler trait", definition.iconItem(), definition.tone().name(),
                        definition.effectsText(), "While ruler governs"));
            }
        }
        CampaignData.MarriageRecord marriage = settlement.getOwnerId() == null ? null : campaign.getMarriage(settlement.getOwnerId());
        if (marriage != null && (settlement.getRank() == PrototypeData.SettlementRank.CAPITAL || settlement.getRank() == PrototypeData.SettlementRank.CITY_STATE)) {
            int spouseStability = marriage.effectiveStabilityBonus(data);
            int spouseWealth = marriage.effectiveWealthPerDay(data);
            int spouseMorale = marriage.effectiveMoraleBonus(data);
            if (spouseStability != 0 || spouseWealth != 0 || spouseMorale != 0) {
                String details = joinEffects(spouseStability, spouseWealth, spouseMorale);
                String tone = spouseStability + spouseWealth + spouseMorale >= 0 ? "POSITIVE" : "NEGATIVE";
                String spouseName = marriage.spouseName();
                if (marriage.kind() == CampaignData.MarriageKind.PLAYER && marriage.spousePlayerId() != null) {
                    PrototypeData.CharacterRecord spouseCharacter = data.getCharacter(marriage.spousePlayerId());
                    spouseName = spouseCharacter != null && spouseCharacter.isCreated() ? spouseCharacter.getDisplayName() : "Player spouse";
                    details += " — derived from spouse traits";
                }
                values.add(new EffectEntry("Spouse: " + spouseName, "Marriage", "minecraft:rose_bush", tone, details, "While married"));
            }
        }
        if (settlement.getOwnerId() != null && (settlement.getRank() == PrototypeData.SettlementRank.CAPITAL || settlement.getRank() == PrototypeData.SettlementRank.CITY_STATE)) {
            int spouseIndex = 1;
            for (CampaignData.MarriageRecord additional : campaign.getAdditionalNpcMarriages(settlement.getOwnerId())) {
                int stability = CampaignData.scaledSpouseEffect(additional.stabilityBonus(), spouseIndex);
                int wealth = CampaignData.scaledSpouseEffect(additional.wealthPerDay(), spouseIndex);
                int extraMorale = CampaignData.scaledSpouseEffect(additional.moraleBonus(), spouseIndex);
                String details = joinEffects(stability, wealth, extraMorale) + " (" + CampaignData.spouseEffectPercent(spouseIndex) + "% spouse scaling)";
                String tone = stability + wealth + extraMorale >= 0 ? "POSITIVE" : "NEGATIVE";
                values.add(new EffectEntry("Additional spouse: " + additional.spouseName(), "Marriage", "minecraft:rose_bush", tone, details, "While married"));
                spouseIndex++;
            }
        }

        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        int foreignMp = extra.foreignManpower(settlement.getId());
        int levyTradition = extra.levyTradition(settlement.getId());
        if (levyTradition > 0) {
            values.add(new EffectEntry("Levy Tradition: " + levyTradition + "/50", "Repeated levy service", "minecraft:iron_helmet", "POSITIVE",
                    "New levies from this settlement begin with +" + levyTradition + " XP. Tradition only grows after meaningful service with enough survivors returning home.", "Persistent"));
        }
        if (foreignMp > 0) {
            values.add(new EffectEntry("Foreign manpower: " + foreignMp, "Peace treaty", "minecraft:iron_sword", "MIXED",
                    "Treaty-transferred manpower can be used for professional recruitment. It does not increase native maximum manpower and causes Local Foreign Soldiers unrest until spent.", "Until recruited"));
        }
        for (Campaign23Data.ModifierRecord modifier : extra.modifiers(settlement.getId())) {
            long remaining = modifier.expiresAtMillis <= 0L ? -1L : Math.max(0L, modifier.expiresAtMillis - extra.nowMillis(settlement.getId()));
            String duration = remaining < 0L ? "Persistent" : formatDuration(remaining);
            String detail = modifier.detail;
            List<String> mechanical = new ArrayList<>();
            if (modifier.stabilityPerDay != 0) mechanical.add(signed(modifier.stabilityPerDay) + " daily stability");
            if (modifier.prosperity != 0) mechanical.add(signed(modifier.prosperity) + " prosperity");
            if (modifier.morale != 0) mechanical.add(signed(modifier.morale) + " recruit morale");
            if (!mechanical.isEmpty()) detail = detail.isBlank() ? String.join(", ", mechanical) : detail + " — " + String.join(", ", mechanical);
            values.add(new EffectEntry(modifier.name, modifier.source, modifier.iconItem, modifier.tone, detail, duration));
        }
        int prosperity = extra.prosperity(settlement.getId());
        long currentDay = player.serverLevel().getDayTime() / 24000L;
        int averageDeposit = extra.averageWealthDeposit(settlement.getId(), currentDay, 7);
        String prosperityDetail = "Recent wealth entering the settlement is the main driver. 7-day average deposit: " + averageDeposit + " wealth/day. "
                + (prosperity >= 65 ? "A strong economy supports settlement growth and advancement."
                : prosperity < 30 ? "Weak economic activity is holding the settlement back."
                : "The settlement economy is stable but unexceptional.");
        values.add(new EffectEntry("Prosperity: " + prosperity + "/100", "Settlement economy", "minecraft:emerald",
                prosperity >= 65 ? "POSITIVE" : prosperity < 30 ? "NEGATIVE" : "MIXED", prosperityDetail, "Current"));
        return values.stream().limit(32).toList();
    }

    private static String joinEffects(int stability, int wealth, int morale) {
        List<String> parts = new ArrayList<>();
        if (stability != 0) parts.add(signed(stability) + " daily stability");
        if (wealth != 0) parts.add(signed(wealth) + " daily wealth");
        if (morale != 0) parts.add(signed(morale) + " recruit morale target");
        return String.join(", ", parts);
    }

    private static String formatDuration(long millis) {
        long totalMinutes = Math.max(1L, millis / 60000L);
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes / 60L) % 24L;
        long minutes = totalMinutes % 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    public List<String> unitTypes() {
        return unitTemplates.stream().filter(value -> !"LEVY".equals(value.type())).map(UnitTemplateEntry::type).toList();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(settlementId, 64); buffer.writeUtf(settlementName, 64); buffer.writeUtf(rank, 32); buffer.writeUtf(rankSubtitle, 48); buffer.writeUtf(role, 16); buffer.writeBoolean(canManage); buffer.writeBoolean(canContribute);
        buffer.writeUtf(factionId, 64); buffer.writeUtf(factionName, 64); buffer.writeNbt(factionBanner);
        buffer.writeUtf(bannerMode, 16); buffer.writeNbt(settlementBannerStack); buffer.writeUtf(rosterName, 64);
        writeList(buffer, unitTemplates, UnitTemplateEntry::encode);
        buffer.writeVarInt(villagerCount); buffer.writeDouble(populationMultiplier); buffer.writeVarInt(civilianPopulation); buffer.writeVarInt(militaryPopulation); buffer.writeVarInt(totalPopulation);
        buffer.writeVarInt(housingCapacity); buffer.writeVarInt(freeHousing); buffer.writeVarInt(stability); buffer.writeVarInt(food); buffer.writeVarInt(wealth);
        buffer.writeVarInt(maximumManpower); buffer.writeVarInt(availableManpower); buffer.writeVarInt(foreignManpower); buffer.writeVarInt(usedManpower); buffer.writeVarInt(levyReservedManpower); buffer.writeVarInt(supportedManpower);
        buffer.writeVarInt(professionals); buffer.writeVarInt(levymen); buffer.writeVarInt(pendingContracts);
        buffer.writeBoolean(townSquareSet); buffer.writeBoolean(musterGroundSet); buffer.writeBoolean(barracksSet); buffer.writeBoolean(barracksChestSet);
        buffer.writeVarInt(barracksRadius); buffer.writeVarInt(barracksCapacity); buffer.writeVarInt(averageMorale); buffer.writeVarInt(mercenaries); buffer.writeVarInt(mercenaryWages); buffer.writeLong(mercenaryNextPaymentDay); buffer.writeUtf(mercenaryStatus, 24); levyStatus.encode(buffer);
        buffer.writeUtf(factionTeam, 64); buffer.writeBoolean(claimLinked); buffer.writeVarInt(claimCount); buffer.writeUtf(claimName, 180); buffer.writeUtf(notice, 160);
        writeList(buffer, effects, EffectEntry::encode); writeStrings(buffer, chronicle); writeStrings(buffer, contributions);
        writeList(buffer, members, MemberEntry::encode); writeList(buffer, memberCandidates, PlayerChoice::encode); buffer.writeUtf(message, 256);
    }

    public static PrototypeSnapshot decode(FriendlyByteBuf buffer) {
        return new PrototypeSnapshot(
                buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32), buffer.readUtf(48), buffer.readUtf(16), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readUtf(64), buffer.readUtf(64), safe(buffer.readNbt()), buffer.readUtf(16), safe(buffer.readNbt()), buffer.readUtf(64),
                readList(buffer, UnitTemplateEntry::decode, 16),
                buffer.readVarInt(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(), buffer.readUtf(24), LevyStatusEntry.decode(buffer),
                buffer.readUtf(64), buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(180), buffer.readUtf(160),
                readList(buffer, EffectEntry::decode, 32), readStrings(buffer), readStrings(buffer),
                readList(buffer, MemberEntry::decode, 64), readList(buffer, PlayerChoice::decode, 64), buffer.readUtf(256)
        );
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        values.forEach(value -> buffer.writeUtf(value, 220));
    }

    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int count = Math.min(64, buffer.readVarInt());
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readUtf(220));
        return values;
    }

    private static <T> void writeList(FriendlyByteBuf buffer, List<T> values, Encoder<T> encoder) {
        buffer.writeVarInt(values.size());
        values.forEach(value -> encoder.encode(value, buffer));
    }

    private static <T> List<T> readList(FriendlyByteBuf buffer, Decoder<T> decoder, int maximum) {
        int count = Math.min(maximum, buffer.readVarInt());
        List<T> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(decoder.decode(buffer));
        return values;
    }

    private static CompoundTag safe(CompoundTag tag) { return tag == null ? new CompoundTag() : tag; }

    public record UnitTemplateEntry(String type, String displayName, boolean configured, boolean mercenary,
                                    int wage, int paymentIntervalDays) {
        private static UnitTemplateEntry from(CampaignData.UnitType type, ItemStack stack) {
            CompoundTag tag = stack.isEmpty() ? null : stack.getTag();
            boolean mercenary = tag != null && tag.getBoolean(PrototypeManager.TAG_MERCENARY);
            int wage = tag == null ? 0 : Math.max(0, tag.getInt(PrototypeManager.TAG_MERCENARY_WAGE));
            int interval = tag == null ? 0 : Math.max(1, tag.getInt(PrototypeManager.TAG_MERCENARY_INTERVAL_DAYS));
            return new UnitTemplateEntry(type.name(), type.displayName(), !stack.isEmpty(), mercenary, wage, interval);
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(type, 24); buffer.writeUtf(displayName, 40); buffer.writeBoolean(configured); buffer.writeBoolean(mercenary);
            buffer.writeVarInt(wage); buffer.writeVarInt(paymentIntervalDays);
        }

        private static UnitTemplateEntry decode(FriendlyByteBuf buffer) {
            return new UnitTemplateEntry(buffer.readUtf(24), buffer.readUtf(40), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record EffectEntry(String name, String source, String iconItem, String tone, String detail, String duration) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(name, 64); buffer.writeUtf(source, 64); buffer.writeUtf(iconItem, 80); buffer.writeUtf(tone, 16);
            buffer.writeUtf(detail, 220); buffer.writeUtf(duration, 64);
        }

        private static EffectEntry decode(FriendlyByteBuf buffer) {
            return new EffectEntry(buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(80), buffer.readUtf(16), buffer.readUtf(220), buffer.readUtf(64));
        }
    }

    public record MemberEntry(UUID playerId, String accountName, String characterName, String role, boolean online) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(playerId); buffer.writeUtf(accountName, 40); buffer.writeUtf(characterName, 80); buffer.writeUtf(role, 16); buffer.writeBoolean(online);
        }
        private static MemberEntry decode(FriendlyByteBuf buffer) {
            return new MemberEntry(buffer.readUUID(), buffer.readUtf(40), buffer.readUtf(80), buffer.readUtf(16), buffer.readBoolean());
        }
    }

    public record PlayerChoice(UUID playerId, String accountName, String characterName) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeUUID(playerId); buffer.writeUtf(accountName, 40); buffer.writeUtf(characterName, 80); }
        private static PlayerChoice decode(FriendlyByteBuf buffer) { return new PlayerChoice(buffer.readUUID(), buffer.readUtf(40), buffer.readUtf(80)); }
    }

    public record LevyStatusEntry(int playerDeployed, int playerCap, int factionDeployed, int factionCap,
                                  int mobilizingTotal, int mobilizingSpawned, long mobilizationRemainingMillis,
                                  long cooldownRemainingMillis, String serviceState) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(playerDeployed); buffer.writeVarInt(playerCap); buffer.writeVarInt(factionDeployed); buffer.writeVarInt(factionCap);
            buffer.writeVarInt(mobilizingTotal); buffer.writeVarInt(mobilizingSpawned); buffer.writeLong(mobilizationRemainingMillis);
            buffer.writeLong(cooldownRemainingMillis); buffer.writeUtf(serviceState == null ? "Normal service" : serviceState, 48);
        }
        private static LevyStatusEntry decode(FriendlyByteBuf buffer) {
            return new LevyStatusEntry(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(), buffer.readLong(), buffer.readUtf(48));
        }
    }

    private record MilitarySummary(int averageMorale, int mercenaries, int totalWages, long nextPaymentDay, String status) {}

    @FunctionalInterface private interface Encoder<T> { void encode(T value, FriendlyByteBuf buffer); }
    @FunctionalInterface private interface Decoder<T> { T decode(FriendlyByteBuf buffer); }
}
