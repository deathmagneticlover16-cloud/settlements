package ee.lastpagans.settlements;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record CampaignSnapshot(
        String characterName,
        String titleLine,
        String faith,
        String factionId,
        String factionName,
        CompoundTag factionBanner,
        int settlementCount,
        int totalPopulation,
        int totalTreasury,
        int totalFood,
        int maximumManpower,
        int availableManpower,
        int averageStability,
        String selectedSettlementId,
        String selectedSettlementName,
        int pendingDiplomacy,
        int pendingMarriage,
        int activeWars
) {
    public static CampaignSnapshot from(ServerPlayer player) {
        SettlementData data = SettlementData.get(player.serverLevel());
        CampaignData campaign = CampaignData.get(player.serverLevel());
        SettlementData.CharacterRecord character = data.getCharacter(player.getUUID());
        String factionId = CampaignManager.factionId(player);
        List<SettlementData.SettlementRecord> holdings = factionId.isBlank()
                ? data.getOwnedSettlements(player.getUUID())
                : data.getSettlements().stream().filter(settlement -> factionId.equals(settlement.getFactionTeam())).toList();

        String factionName = factionId.isBlank() ? "Independent" : factionId;
        CompoundTag banner = new CompoundTag();
        if (!factionId.isBlank() && FactionEvents.recruitsFactionManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
            if (faction != null) {
                factionName = faction.getTeamDisplayName();
                if (faction.getBanner() != null) banner = faction.getBanner().copy();
            }
        }

        int population = holdings.stream().mapToInt(SettlementData.SettlementRecord::getDisplayedPopulation).sum();
        int treasury = holdings.isEmpty()
                ? CampaignSystemsData.get(player.serverLevel()).treasuryBalance(factionId)
                : StateTreasury.balance(player.serverLevel(), holdings.get(0));
        int food = holdings.stream().mapToInt(SettlementData.SettlementRecord::getFood).sum();
        int maximum = holdings.stream().mapToInt(SettlementData.SettlementRecord::getMaximumManpower).sum();
        int available = holdings.stream().mapToInt(SettlementData.SettlementRecord::getAvailableManpower).sum();
        int stability = holdings.isEmpty()
                ? 0
                : (int) Math.round(holdings.stream().mapToInt(SettlementData.SettlementRecord::getStability).average().orElse(0));

        SettlementData.SettlementRecord selected = data.getSelectedSettlement(player.getUUID());
        String displayName = character != null && character.isCreated()
                ? character.getDisplayName()
                : player.getGameProfile().getName();
        SettlementData.SettlementRecord primarySeat = character == null ? null : data.getSettlement(character.getPrimarySeat());
        String titleLine = character != null && character.isCreated()
                ? character.getTitle() + (primarySeat == null ? "" : " of " + primarySeat.getName())
                : "Uncreated character";
        String faith = character != null && character.isCreated() ? character.getFaith() : "Unknown";

        return new CampaignSnapshot(
                displayName,
                titleLine,
                faith,
                factionId,
                factionName,
                banner,
                holdings.size(),
                population,
                treasury,
                food,
                maximum,
                available,
                stability,
                selected == null ? "" : selected.getId(),
                selected == null ? "None selected" : selected.getName(),
                factionId.isBlank() ? 0 : campaign.proposalsFor(factionId).size(),
                campaign.pendingMarriageProposer(player.getUUID()) == null ? 0 : 1,
                factionId.isBlank() ? 0 : campaign.activeWarsFor(factionId).size()
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(characterName, 80);
        buffer.writeUtf(titleLine, 96);
        buffer.writeUtf(faith, 32);
        buffer.writeUtf(factionId, 64);
        buffer.writeUtf(factionName, 64);
        buffer.writeNbt(factionBanner);
        buffer.writeVarInt(settlementCount);
        buffer.writeVarInt(totalPopulation);
        buffer.writeVarInt(totalTreasury);
        buffer.writeVarInt(totalFood);
        buffer.writeVarInt(maximumManpower);
        buffer.writeVarInt(availableManpower);
        buffer.writeVarInt(averageStability);
        buffer.writeUtf(selectedSettlementId, 64);
        buffer.writeUtf(selectedSettlementName, 64);
        buffer.writeVarInt(pendingDiplomacy);
        buffer.writeVarInt(pendingMarriage);
        buffer.writeVarInt(activeWars);
    }

    public static CampaignSnapshot decode(FriendlyByteBuf buffer) {
        return new CampaignSnapshot(
                buffer.readUtf(80),
                buffer.readUtf(96),
                buffer.readUtf(32),
                buffer.readUtf(64),
                buffer.readUtf(64),
                safe(buffer.readNbt()),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private static CompoundTag safe(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }
}
