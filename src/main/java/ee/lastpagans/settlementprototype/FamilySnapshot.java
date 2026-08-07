package ee.lastpagans.settlementprototype;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FamilySnapshot(
        String characterName,
        boolean married,
        String spouseName,
        String spouseFaith,
        String spouseCulture,
        String spouseTraits,
        String spouseEffect,
        boolean playerSpouse,
        UUID pendingProposer,
        String pendingProposerName,
        List<Candidate> candidates,
        long currentDay,
        long nextRefreshDay,
        boolean canRefresh,
        String message
) {
    public static FamilySnapshot from(ServerPlayer player, String message) {
        CampaignData campaign = CampaignData.get(player.serverLevel());
        PrototypeData characters = PrototypeData.get(player.serverLevel());
        PrototypeData.CharacterRecord character = characters.getCharacter(player.getUUID());
        String characterName = character != null && character.isCreated()
                ? character.getDisplayName()
                : player.getGameProfile().getName();

        CampaignData.MarriageRecord marriage = campaign.getMarriage(player.getUUID());
        String spouseName = "";
        String spouseFaith = "";
        String spouseCulture = "";
        String spouseTraits = "";
        String spouseEffect = "";
        boolean playerSpouse = false;
        if (marriage != null) {
            if (marriage.kind() == CampaignData.MarriageKind.PLAYER && marriage.spousePlayerId() != null) {
                PrototypeData.CharacterRecord spouse = characters.getCharacter(marriage.spousePlayerId());
                spouseName = spouse != null && spouse.isCreated() ? spouse.getDisplayName() : marriage.spousePlayerId().toString();
                spouseFaith = spouse != null && spouse.isCreated() ? spouse.getFaith() : "Unknown";
                spouseCulture = "Player character";
                spouseTraits = spouse != null ? traitNames(spouse.getTraits()) : "Unknown";
                spouseEffect = "Player marriage alliance";
                playerSpouse = true;
            } else {
                spouseName = marriage.spouseName();
                spouseFaith = marriage.spouseFaith();
                spouseCulture = marriage.spouseCulture();
                spouseTraits = marriage.spouseTraits();
                spouseEffect = marriage.spouseEffect();
            }
        }

        UUID pending = campaign.pendingMarriageProposer(player.getUUID());
        String pendingName = "";
        if (pending != null) {
            PrototypeData.CharacterRecord proposer = characters.getCharacter(pending);
            pendingName = proposer != null && proposer.isCreated() ? proposer.getDisplayName() : pending.toString();
        }

        long day = player.serverLevel().getDayTime() / 24000L;
        List<Candidate> candidates = campaign.candidatesFor(player.getUUID(), day).stream()
                .map(value -> new Candidate(
                        value.id(), value.name(), value.faith(), value.culture(), value.traitsText(), value.effect(),
                        value.stabilityBonus(), value.wealthPerDay(), value.moraleBonus()
                )).toList();

        return new FamilySnapshot(
                characterName,
                marriage != null,
                spouseName,
                spouseFaith,
                spouseCulture,
                spouseTraits,
                spouseEffect,
                playerSpouse,
                pending,
                pendingName,
                candidates,
                day,
                campaign.nextCandidateRefreshDay(player.getUUID()),
                campaign.canRefreshCandidates(player.getUUID(), day),
                message == null ? "" : message
        );
    }

    private static String traitNames(List<String> ids) {
        return ids.stream().map(id -> {
            CampaignDefinitions.TraitDefinition definition = CampaignDefinitions.trait(id);
            return definition == null ? id : definition.name();
        }).reduce((a, b) -> a + ", " + b).orElse("None");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(characterName, 80);
        buffer.writeBoolean(married);
        buffer.writeUtf(spouseName, 80);
        buffer.writeUtf(spouseFaith, 48);
        buffer.writeUtf(spouseCulture, 32);
        buffer.writeUtf(spouseTraits, 128);
        buffer.writeUtf(spouseEffect, 160);
        buffer.writeBoolean(playerSpouse);
        buffer.writeBoolean(pendingProposer != null);
        if (pendingProposer != null) buffer.writeUUID(pendingProposer);
        buffer.writeUtf(pendingProposerName, 80);
        buffer.writeVarInt(candidates.size());
        candidates.forEach(candidate -> candidate.encode(buffer));
        buffer.writeLong(currentDay);
        buffer.writeLong(nextRefreshDay);
        buffer.writeBoolean(canRefresh);
        buffer.writeUtf(message, 256);
    }

    public static FamilySnapshot decode(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(80);
        boolean married = buffer.readBoolean();
        String spouseName = buffer.readUtf(80);
        String spouseFaith = buffer.readUtf(48);
        String spouseCulture = buffer.readUtf(32);
        String spouseTraits = buffer.readUtf(128);
        String spouseEffect = buffer.readUtf(160);
        boolean playerSpouse = buffer.readBoolean();
        UUID pending = buffer.readBoolean() ? buffer.readUUID() : null;
        String pendingName = buffer.readUtf(80);
        int count = Math.min(64, buffer.readVarInt());
        List<Candidate> candidates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) candidates.add(Candidate.decode(buffer));
        long currentDay = buffer.readLong();
        long nextRefreshDay = buffer.readLong();
        boolean canRefresh = buffer.readBoolean();
        return new FamilySnapshot(name, married, spouseName, spouseFaith, spouseCulture, spouseTraits,
                spouseEffect, playerSpouse, pending, pendingName, candidates, currentDay, nextRefreshDay,
                canRefresh, buffer.readUtf(256));
    }

    public record Candidate(
            String id,
            String name,
            String faith,
            String culture,
            String traits,
            String effect,
            int stabilityBonus,
            int wealthPerDay,
            int moraleBonus
    ) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64);
            buffer.writeUtf(name, 80);
            buffer.writeUtf(faith, 48);
            buffer.writeUtf(culture, 32);
            buffer.writeUtf(traits, 128);
            buffer.writeUtf(effect, 160);
            buffer.writeInt(stabilityBonus);
            buffer.writeInt(wealthPerDay);
            buffer.writeInt(moraleBonus);
        }

        private static Candidate decode(FriendlyByteBuf buffer) {
            return new Candidate(buffer.readUtf(64), buffer.readUtf(80), buffer.readUtf(48), buffer.readUtf(32),
                    buffer.readUtf(128), buffer.readUtf(160), buffer.readInt(), buffer.readInt(), buffer.readInt());
        }
    }
}
