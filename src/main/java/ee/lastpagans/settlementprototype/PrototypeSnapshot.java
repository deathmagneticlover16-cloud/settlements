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
        int maximumManpower, int availableManpower, int usedManpower, int levyReservedManpower, int supportedManpower,
        int professionals, int levymen, int pendingContracts,
        boolean townSquareSet, boolean musterGroundSet, boolean barracksSet, boolean barracksChestSet,
        int barracksRadius, int barracksCapacity, int averageMorale, int mercenaries, int mercenaryWages,
        String factionTeam, boolean claimLinked, int claimCount, String claimName,
        String notice, List<EffectEntry> effects, List<String> chronicle, List<String> contributions, String message
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

        MilitarySummary military = militarySummary(player, settlement);
        List<EffectEntry> effects = buildEffects(player, settlement, campaign, military.averageMorale());

        return new PrototypeSnapshot(
                settlement.getId(), settlement.getName(), settlement.getRank().displayName(), settlement.getRank().subtitle(), settlement.getRole(player.getUUID()).name(),
                PrototypeManager.canManage(player, settlement), PrototypeManager.canContribute(player, settlement),
                factionId, factionName, factionBanner, visual.getBannerMode().name(), settlementBanner,
                campaign.assignedRosterName(settlement), templates,
                settlement.getVillagerCount(), settlement.getPopulationMultiplier(), settlement.getCivilianPopulation(), settlement.getMilitaryPopulation(), settlement.getDisplayedPopulation(),
                settlement.getHousingCapacity(), settlement.getFreeHousing(), settlement.getStability(), settlement.getFood(), settlement.getWealth(),
                settlement.getMaximumManpower(), settlement.getAvailableManpower(), settlement.getUsedManpower(), settlement.getLevyReservedManpower(), settlement.getSupportedManpower(),
                settlement.getProfessionalCount(), settlement.getLivingLevymen(), settlement.getPendingContracts(),
                settlement.hasTownSquare(), settlement.hasMusterGround(), settlement.hasBarracks(), settlement.getBarracksChest() != null,
                settlement.getBarracksRadius(), settlement.getBarracksCapacity(), military.averageMorale(), military.mercenaries(), military.totalWages(),
                settlement.getFactionTeam(), settlement.hasClaim(), settlement.getClaimCount(), settlement.getClaimSummary(), settlement.getNotice(), effects,
                settlement.getChronicle().stream().limit(8).toList(), settlement.getContributions().stream().limit(8).toList(), message == null ? "" : message
        );
    }

    private static MilitarySummary militarySummary(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        int moraleTotal = 0;
        int moraleCount = 0;
        int mercenaries = 0;
        int wages = 0;
        for (UUID uuid : settlement.getAllMilitaryUnits()) {
            Entity found = null;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                found = level.getEntity(uuid);
                if (found != null) break;
            }
            if (!(found instanceof AbstractRecruitEntity recruit)) continue;
            moraleTotal += Math.round(recruit.getMorale());
            moraleCount++;
            if (found.getPersistentData().getBoolean(PrototypeManager.TAG_MERCENARY)) {
                mercenaries++;
                wages += Math.max(0, found.getPersistentData().getInt(PrototypeManager.TAG_MERCENARY_WAGE));
            }
        }
        return new MilitarySummary(moraleCount == 0 ? 0 : Math.round((float) moraleTotal / moraleCount), mercenaries, wages);
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

        PrototypeData.CharacterRecord ruler = player.serverLevel() == null ? null
                : PrototypeData.get(player.serverLevel()).getCharacter(settlement.getOwnerId());
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
        if (marriage != null && (settlement.getRank() == PrototypeData.SettlementRank.CAPITAL || settlement.getRank() == PrototypeData.SettlementRank.CITY_STATE)
                && (marriage.stabilityBonus() != 0 || marriage.wealthPerDay() != 0 || marriage.moraleBonus() != 0)) {
            String details = joinEffects(marriage.stabilityBonus(), marriage.wealthPerDay(), marriage.moraleBonus());
            String tone = marriage.stabilityBonus() + marriage.wealthPerDay() + marriage.moraleBonus() >= 0 ? "POSITIVE" : "NEGATIVE";
            values.add(new EffectEntry("Spouse: " + marriage.spouseName(), "Marriage", "minecraft:rose_bush", tone, details, "While married"));
        }

        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
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
        values.add(new EffectEntry("Prosperity: " + prosperity + "/100", "Settlement economy", "minecraft:emerald",
                prosperity >= 65 ? "POSITIVE" : prosperity < 30 ? "NEGATIVE" : "MIXED",
                prosperity >= 65 ? "A strong economy supports settlement growth and advancement." : prosperity < 30 ? "Weak economic activity is holding the settlement back." : "The settlement economy is stable but unexceptional.", "Current"));
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
        buffer.writeVarInt(maximumManpower); buffer.writeVarInt(availableManpower); buffer.writeVarInt(usedManpower); buffer.writeVarInt(levyReservedManpower); buffer.writeVarInt(supportedManpower);
        buffer.writeVarInt(professionals); buffer.writeVarInt(levymen); buffer.writeVarInt(pendingContracts);
        buffer.writeBoolean(townSquareSet); buffer.writeBoolean(musterGroundSet); buffer.writeBoolean(barracksSet); buffer.writeBoolean(barracksChestSet);
        buffer.writeVarInt(barracksRadius); buffer.writeVarInt(barracksCapacity); buffer.writeVarInt(averageMorale); buffer.writeVarInt(mercenaries); buffer.writeVarInt(mercenaryWages);
        buffer.writeUtf(factionTeam, 64); buffer.writeBoolean(claimLinked); buffer.writeVarInt(claimCount); buffer.writeUtf(claimName, 180); buffer.writeUtf(notice, 160);
        writeList(buffer, effects, EffectEntry::encode); writeStrings(buffer, chronicle); writeStrings(buffer, contributions); buffer.writeUtf(message, 256);
    }

    public static PrototypeSnapshot decode(FriendlyByteBuf buffer) {
        return new PrototypeSnapshot(
                buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32), buffer.readUtf(48), buffer.readUtf(16), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readUtf(64), buffer.readUtf(64), safe(buffer.readNbt()), buffer.readUtf(16), safe(buffer.readNbt()), buffer.readUtf(64),
                readList(buffer, UnitTemplateEntry::decode, 16),
                buffer.readVarInt(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(64), buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(180), buffer.readUtf(160),
                readList(buffer, EffectEntry::decode, 32), readStrings(buffer), readStrings(buffer), buffer.readUtf(256)
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

    private record MilitarySummary(int averageMorale, int mercenaries, int totalWages) {}

    @FunctionalInterface private interface Encoder<T> { void encode(T value, FriendlyByteBuf buffer); }
    @FunctionalInterface private interface Decoder<T> { T decode(FriendlyByteBuf buffer); }
}
