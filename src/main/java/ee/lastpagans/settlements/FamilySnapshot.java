package ee.lastpagans.settlements;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-authoritative player-to-player marriage state. */
public record FamilySnapshot(
        String characterName,
        boolean married,
        String spouseName,
        String spouseFaith,
        String spouseTraits,
        String spouseEffect,
        boolean canDivorce,
        UUID pendingProposer,
        String pendingProposerName,
        List<PlayerCandidate> playerCandidates,
        long currentDay,
        String message
) {
    public static FamilySnapshot from(ServerPlayer player, String message) {
        CampaignData campaign = CampaignData.get(player.serverLevel());
        SettlementData characters = SettlementData.get(player.serverLevel());
        SettlementData.CharacterRecord character = characters.getCharacter(player.getUUID());
        String characterName = character != null && character.isCreated()
                ? character.getDisplayName() : player.getGameProfile().getName();

        CampaignData.MarriageRecord marriage = campaign.getMarriage(player.getUUID());
        String spouseName = "";
        String spouseFaith = "";
        String spouseTraits = "";
        String spouseEffect = "";
        long day = player.serverLevel().getDayTime() / 24000L;
        boolean canDivorce = marriage != null && day - marriage.marriedDay() >= 7L;
        if (marriage != null && marriage.spousePlayerId() != null) {
            SettlementData.CharacterRecord spouse = characters.getCharacter(marriage.spousePlayerId());
            spouseName = spouse != null && spouse.isCreated() ? spouse.getDisplayName() : marriage.spousePlayerId().toString();
            spouseFaith = spouse != null && spouse.isCreated() ? spouse.getFaith() : "Unknown";
            spouseTraits = spouse != null ? traitNames(spouse.getTraits()) : "Unknown";
            if (spouse != null && spouse.isCreated()) {
                int stability = CampaignDefinitions.totalEffect(spouse.getTraits(), CampaignDefinitions.EffectKind.STABILITY);
                int treasury = CampaignDefinitions.totalEffect(spouse.getTraits(), CampaignDefinitions.EffectKind.TREASURY);
                int morale = CampaignDefinitions.totalEffect(spouse.getTraits(), CampaignDefinitions.EffectKind.MORALE);
                List<String> effects = new ArrayList<>();
                if (stability != 0) effects.add((stability > 0 ? "+" : "") + stability + " stability/day");
                if (treasury != 0) effects.add((treasury > 0 ? "+" : "") + treasury + " State Treasury/day");
                if (morale != 0) effects.add((morale > 0 ? "+" : "") + morale + " recruit morale");
                spouseEffect = effects.isEmpty() ? "Marriage alliance; no direct capital trait effects"
                        : "Marriage alliance; capital receives " + String.join(", ", effects);
            } else {
                spouseEffect = "Player marriage alliance";
            }
        }

        UUID pending = campaign.pendingMarriageProposer(player.getUUID());
        String pendingName = "";
        if (pending != null) {
            SettlementData.CharacterRecord proposer = characters.getCharacter(pending);
            pendingName = proposer != null && proposer.isCreated() ? proposer.getDisplayName() : pending.toString();
        }

        List<PlayerCandidate> playerCandidates = marriage == null
                ? player.getServer().getPlayerList().getPlayers().stream()
                .filter(other -> !other.getUUID().equals(player.getUUID()))
                .filter(other -> campaign.getMarriage(other.getUUID()) == null)
                .map(other -> {
                    SettlementData.CharacterRecord other = characters.getCharacter(other.getUUID());
                    return other != null && other.isCreated() ? new PlayerCandidate(other.getUUID(), other.getDisplayName()) : null;
                })
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(PlayerCandidate::name, String.CASE_INSENSITIVE_ORDER))
                .toList()
                : List.of();

        return new FamilySnapshot(characterName, marriage != null, spouseName, spouseFaith, spouseTraits,
                spouseEffect, canDivorce, pending, pendingName, playerCandidates, day,
                message == null ? "" : message);
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
        buffer.writeUtf(spouseTraits, 128);
        buffer.writeUtf(spouseEffect, 160);
        buffer.writeBoolean(canDivorce);
        buffer.writeBoolean(pendingProposer != null);
        if (pendingProposer != null) buffer.writeUUID(pendingProposer);
        buffer.writeUtf(pendingProposerName, 80);
        buffer.writeVarInt(playerCandidates.size());
        playerCandidates.forEach(candidate -> candidate.encode(buffer));
        buffer.writeLong(currentDay);
        buffer.writeUtf(message, 256);
    }

    public static FamilySnapshot decode(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(80);
        boolean married = buffer.readBoolean();
        String spouseName = buffer.readUtf(80);
        String spouseFaith = buffer.readUtf(48);
        String spouseTraits = buffer.readUtf(128);
        String spouseEffect = buffer.readUtf(160);
        boolean canDivorce = buffer.readBoolean();
        UUID pending = buffer.readBoolean() ? buffer.readUUID() : null;
        String pendingName = buffer.readUtf(80);
        int playerCount = Math.min(64, buffer.readVarInt());
        List<PlayerCandidate> candidates = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) candidates.add(PlayerCandidate.decode(buffer));
        return new FamilySnapshot(name, married, spouseName, spouseFaith, spouseTraits, spouseEffect,
                canDivorce, pending, pendingName, candidates, buffer.readLong(), buffer.readUtf(256));
    }

    public record PlayerCandidate(UUID id, String name) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeUUID(id); buffer.writeUtf(name, 80); }
        private static PlayerCandidate decode(FriendlyByteBuf buffer) { return new PlayerCandidate(buffer.readUUID(), buffer.readUtf(80)); }
    }
}
