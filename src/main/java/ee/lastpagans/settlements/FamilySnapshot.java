package ee.lastpagans.settlements;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative player-to-player marriage state. */
public record FamilySnapshot(
        String characterName,
        boolean married,
        String spouseName,
        String spouseFaith,
        String spouseTraits,
        String spouseEffect,
        long marriageDurationDays,
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

        return new FamilySnapshot(characterName, marriage != null, spouseName, spouseFaith, spouseTraits,
                spouseEffect, marriage == null ? 0L : Math.max(0L, day - marriage.marriedDay()),
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
        buffer.writeLong(marriageDurationDays);
        buffer.writeUtf(message, 256);
    }

    public static FamilySnapshot decode(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(80);
        boolean married = buffer.readBoolean();
        String spouseName = buffer.readUtf(80);
        String spouseFaith = buffer.readUtf(48);
        String spouseTraits = buffer.readUtf(128);
        String spouseEffect = buffer.readUtf(160);
        return new FamilySnapshot(name, married, spouseName, spouseFaith, spouseTraits, spouseEffect,
                buffer.readLong(), buffer.readUtf(256));
    }
}
