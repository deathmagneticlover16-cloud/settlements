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
        boolean canDivorce,
        int spouseCount,
        int maxSpouses,
        boolean canMarryAdditional,
        List<AdditionalSpouse> additionalSpouses,
        String preferredGender,
        UUID pendingProposer,
        String pendingProposerName,
        List<PlayerCandidate> playerCandidates,
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
        long day = player.serverLevel().getDayTime() / 24000L;
        boolean canDivorce = marriage != null && day - marriage.marriedDay() >= 7L
                && campaign.getAdditionalNpcMarriages(player.getUUID()).stream().allMatch(value -> day - value.marriedDay() >= 7L);
        int maxSpouses = character != null && character.getFaith().equalsIgnoreCase("Islam") ? 4 : 1;
        int spouseCount = campaign.spouseCount(player.getUUID());
        List<AdditionalSpouse> additionalSpouses = new ArrayList<>();
        int secondaryIndex = 1;
        for (CampaignData.MarriageRecord additional : campaign.getAdditionalNpcMarriages(player.getUUID())) {
            additionalSpouses.add(new AdditionalSpouse(additional.spouseName(), additional.spouseFaith(), additional.spouseTraits(), additional.spouseEffect(), CampaignData.spouseEffectPercent(secondaryIndex++)));
        }
        boolean canMarryAdditional = marriage != null && maxSpouses > spouseCount;
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        Campaign23Data.CandidateHistory history = extra.candidateHistory(player.getUUID());
        String preferredGender = history.preferredGender();
        int spouseQuality = character == null ? 0 : CampaignDefinitions.totalEffect(character.getTraits(), CampaignDefinitions.EffectKind.SPOUSE_QUALITY);
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

        List<PlayerCandidate> playerCandidates = player.getServer().getPlayerList().getPlayers().stream()
                .filter(other -> !other.getUUID().equals(player.getUUID()))
                .filter(other -> campaign.getMarriage(other.getUUID()) == null)
                .map(other -> {
                    PrototypeData.CharacterRecord otherCharacter = characters.getCharacter(other.getUUID());
                    return otherCharacter != null && otherCharacter.isCreated()
                            ? new PlayerCandidate(other.getUUID(), otherCharacter.getDisplayName()) : null;
                })
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(PlayerCandidate::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<CampaignData.NpcSpouse> generatedCandidates = campaign.candidatesFor(player.getUUID(), day, history.rejectedIds(), history.seenIds(), spouseQuality);
        generatedCandidates.forEach(value -> extra.markCandidateSeen(player.getUUID(), value.name()));
        List<Candidate> candidates = generatedCandidates.stream()
                .filter(value -> history.rejectedIds().stream().noneMatch(rejected -> rejected.equalsIgnoreCase(value.id()) || rejected.equalsIgnoreCase(value.name())))
                .filter(value -> canMarryAdditional ? value.gender().equalsIgnoreCase("FEMALE") : (preferredGender.equals("ANY") || value.gender().equalsIgnoreCase(preferredGender)))
                .map(value -> new Candidate(
                        value.id(), value.name(), value.gender(), value.faith(), value.culture(), value.traitsText(), value.effect(),
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
                canDivorce,
                spouseCount,
                maxSpouses,
                canMarryAdditional,
                List.copyOf(additionalSpouses),
                preferredGender,
                pending,
                pendingName,
                playerCandidates,
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
        buffer.writeBoolean(canDivorce);
        buffer.writeVarInt(spouseCount);
        buffer.writeVarInt(maxSpouses);
        buffer.writeBoolean(canMarryAdditional);
        buffer.writeVarInt(additionalSpouses.size());
        additionalSpouses.forEach(value -> value.encode(buffer));
        buffer.writeUtf(preferredGender, 12);
        buffer.writeBoolean(pendingProposer != null);
        if (pendingProposer != null) buffer.writeUUID(pendingProposer);
        buffer.writeUtf(pendingProposerName, 80);
        buffer.writeVarInt(playerCandidates.size());
        playerCandidates.forEach(candidate -> candidate.encode(buffer));
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
        boolean canDivorce = buffer.readBoolean();
        int spouseCount = buffer.readVarInt();
        int maxSpouses = buffer.readVarInt();
        boolean canMarryAdditional = buffer.readBoolean();
        int additionalCount = Math.min(3, buffer.readVarInt());
        List<AdditionalSpouse> additionalSpouses = new ArrayList<>(additionalCount);
        for (int i = 0; i < additionalCount; i++) additionalSpouses.add(AdditionalSpouse.decode(buffer));
        String preferredGender = buffer.readUtf(12);
        UUID pending = buffer.readBoolean() ? buffer.readUUID() : null;
        String pendingName = buffer.readUtf(80);
        int playerCount = Math.min(64, buffer.readVarInt());
        List<PlayerCandidate> playerCandidates = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) playerCandidates.add(PlayerCandidate.decode(buffer));
        int count = Math.min(64, buffer.readVarInt());
        List<Candidate> candidates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) candidates.add(Candidate.decode(buffer));
        long currentDay = buffer.readLong();
        long nextRefreshDay = buffer.readLong();
        boolean canRefresh = buffer.readBoolean();
        return new FamilySnapshot(name, married, spouseName, spouseFaith, spouseCulture, spouseTraits,
                spouseEffect, playerSpouse, canDivorce, spouseCount, maxSpouses, canMarryAdditional, additionalSpouses, preferredGender, pending, pendingName, playerCandidates, candidates, currentDay, nextRefreshDay,
                canRefresh, buffer.readUtf(256));
    }

    public record AdditionalSpouse(String name, String faith, String traits, String effect, int effectPercent) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(name, 80); buffer.writeUtf(faith, 48); buffer.writeUtf(traits, 128); buffer.writeUtf(effect, 160); buffer.writeVarInt(effectPercent);
        }
        private static AdditionalSpouse decode(FriendlyByteBuf buffer) {
            return new AdditionalSpouse(buffer.readUtf(80), buffer.readUtf(48), buffer.readUtf(128), buffer.readUtf(160), buffer.readVarInt());
        }
    }

    public record PlayerCandidate(UUID id, String name) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeUUID(id); buffer.writeUtf(name, 80); }
        private static PlayerCandidate decode(FriendlyByteBuf buffer) { return new PlayerCandidate(buffer.readUUID(), buffer.readUtf(80)); }
    }

    public record Candidate(
            String id,
            String name,
            String gender,
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
            buffer.writeUtf(gender, 12);
            buffer.writeUtf(faith, 48);
            buffer.writeUtf(culture, 32);
            buffer.writeUtf(traits, 128);
            buffer.writeUtf(effect, 160);
            buffer.writeInt(stabilityBonus);
            buffer.writeInt(wealthPerDay);
            buffer.writeInt(moraleBonus);
        }

        private static Candidate decode(FriendlyByteBuf buffer) {
            return new Candidate(buffer.readUtf(64), buffer.readUtf(80), buffer.readUtf(12), buffer.readUtf(48), buffer.readUtf(32),
                    buffer.readUtf(128), buffer.readUtf(160), buffer.readInt(), buffer.readInt(), buffer.readInt());
        }
    }
}
