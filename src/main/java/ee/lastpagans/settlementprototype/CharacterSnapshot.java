package ee.lastpagans.settlementprototype;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record CharacterSnapshot(
        String accountName,
        String displayName,
        String titleLine,
        String faith,
        String address,
        String dynasty,
        String house,
        String faction,
        String factionRank,
        String primarySeat,
        String spouse,
        List<String> holdings,
        List<TraitEntry> traits
) {
    public static CharacterSnapshot from(PrototypeData data, ServerPlayer player) {
        PrototypeData.CharacterRecord character = data.getCharacter(player.getUUID());
        if (character == null) {
            return new CharacterSnapshot(player.getGameProfile().getName(), "", "", "", "", "", "",
                    "Independent", "", "", "", List.of(), List.of());
        }

        CampaignData campaign = CampaignData.get(player.serverLevel());
        String factionId = player.getTeam() == null ? "" : player.getTeam().getName();
        String factionName = "Independent";
        boolean factionLeader = false;
        if (!factionId.isBlank()) {
            factionName = factionId;
            if (FactionEvents.recruitsFactionManager != null) {
                RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
                if (faction != null) {
                    factionName = faction.getTeamDisplayName();
                    factionLeader = player.getUUID().equals(faction.getTeamLeaderUUID());
                }
            }
        }
        String factionRank = factionId.isBlank() ? "Independent" : campaign.factionRankTitle(factionId, player.getUUID(), factionLeader);

        String seatName = "";
        PrototypeData.SettlementRecord seat = data.getSettlement(character.getPrimarySeat());
        if (seat != null && player.getUUID().equals(seat.getOwnerId())) seatName = seat.getName();

        String titleLine = character.getTitle();
        if (!seatName.isBlank()) titleLine += " of " + seatName;

        String spouseName = "";
        CampaignData.MarriageRecord marriage = campaign.getMarriage(player.getUUID());
        if (marriage != null) {
            if (marriage.kind() == CampaignData.MarriageKind.PLAYER && marriage.spousePlayerId() != null) {
                PrototypeData.CharacterRecord spouseCharacter = data.getCharacter(marriage.spousePlayerId());
                spouseName = spouseCharacter != null && spouseCharacter.isCreated()
                        ? spouseCharacter.getDisplayName()
                        : marriage.spousePlayerId().toString();
            } else {
                spouseName = marriage.spouseName();
            }
        }

        List<String> holdings = data.getOwnedSettlements(player.getUUID()).stream()
                .map(settlement -> settlement.getName() + " — " + settlement.getRank().displayName())
                .toList();

        List<TraitEntry> traits = character.getTraits().stream().map(id -> {
            CampaignDefinitions.TraitDefinition definition = CampaignDefinitions.trait(id);
            if (definition == null) return new TraitEntry(id, id, "minecraft:book", "NEUTRAL", "Unknown trait", List.of());
            List<String> effects = definition.effects().stream()
                    .map(effect -> effect.label() + " " + effect.displayValue())
                    .toList();
            return new TraitEntry(definition.id(), definition.name(), definition.iconItem(), definition.tone().name(), definition.description(), effects);
        }).toList();

        return new CharacterSnapshot(
                player.getGameProfile().getName(),
                character.getDisplayName(),
                titleLine,
                character.getFaith(),
                character.getAddress(),
                character.getDynasty(),
                character.getHouse(),
                factionName,
                factionRank,
                seatName,
                spouseName,
                holdings,
                traits
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(accountName, 32);
        buffer.writeUtf(displayName, 80);
        buffer.writeUtf(titleLine, 96);
        buffer.writeUtf(faith, 48);
        buffer.writeUtf(address, 16);
        buffer.writeUtf(dynasty, 32);
        buffer.writeUtf(house, 32);
        buffer.writeUtf(faction, 64);
        buffer.writeUtf(factionRank, 32);
        buffer.writeUtf(primarySeat, 64);
        buffer.writeUtf(spouse, 80);
        buffer.writeVarInt(holdings.size());
        holdings.forEach(value -> buffer.writeUtf(value, 100));
        buffer.writeVarInt(traits.size());
        traits.forEach(value -> value.encode(buffer));
    }

    public static CharacterSnapshot decode(FriendlyByteBuf buffer) {
        String accountName = buffer.readUtf(32);
        String displayName = buffer.readUtf(80);
        String titleLine = buffer.readUtf(96);
        String faith = buffer.readUtf(48);
        String address = buffer.readUtf(16);
        String dynasty = buffer.readUtf(32);
        String house = buffer.readUtf(32);
        String faction = buffer.readUtf(64);
        String factionRank = buffer.readUtf(32);
        String primarySeat = buffer.readUtf(64);
        String spouse = buffer.readUtf(80);
        int size = Math.min(32, buffer.readVarInt());
        List<String> holdings = new ArrayList<>(size);
        for (int i = 0; i < size; i++) holdings.add(buffer.readUtf(100));
        int traitCount = Math.min(32, buffer.readVarInt());
        List<TraitEntry> traits = new ArrayList<>(traitCount);
        for (int i = 0; i < traitCount; i++) traits.add(TraitEntry.decode(buffer));
        return new CharacterSnapshot(accountName, displayName, titleLine, faith, address, dynasty, house,
                faction, factionRank, primarySeat, spouse, holdings, traits);
    }

    public record TraitEntry(String id, String name, String iconItem, String tone, String description, List<String> effects) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 32);
            buffer.writeUtf(name, 48);
            buffer.writeUtf(iconItem, 64);
            buffer.writeUtf(tone, 16);
            buffer.writeUtf(description, 180);
            buffer.writeVarInt(effects.size());
            effects.forEach(value -> buffer.writeUtf(value, 96));
        }

        private static TraitEntry decode(FriendlyByteBuf buffer) {
            String id = buffer.readUtf(32);
            String name = buffer.readUtf(48);
            String icon = buffer.readUtf(64);
            String tone = buffer.readUtf(16);
            String description = buffer.readUtf(180);
            int count = Math.min(16, buffer.readVarInt());
            List<String> effects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) effects.add(buffer.readUtf(96));
            return new TraitEntry(id, name, icon, tone, description, effects);
        }
    }
}
