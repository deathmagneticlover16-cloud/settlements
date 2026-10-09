package ee.lastpagans.settlements.character;

import net.minecraft.network.FriendlyByteBuf;

public final class CharacterBodyCodec {
    private CharacterBodyCodec() {}

    public static void write(FriendlyByteBuf buffer, CharacterBodyData body) {
        CharacterBodyData value = body == null ? CharacterBodyData.defaults() : body;
        BreastAppearance breasts = value.breasts() == null ? BreastAppearance.defaults() : value.breasts();
        buffer.writeVarInt(value.version());
        buffer.writeUtf(value.gender() == null ? "" : value.gender().name(), 16);
        buffer.writeBoolean(breasts.enabled());
        buffer.writeFloat(breasts.bustSize());
        buffer.writeFloat(breasts.xOffset());
        buffer.writeFloat(breasts.yOffset());
        buffer.writeFloat(breasts.zOffset());
        buffer.writeFloat(breasts.cleavage());
        buffer.writeBoolean(breasts.uniboob());
        buffer.writeBoolean(value.hurtSounds());
        buffer.writeBoolean(value.breastPhysics());
        buffer.writeBoolean(value.showBreastsInArmor());
        buffer.writeBoolean(value.armorPhysicsOverride());
        buffer.writeFloat(value.bounceMultiplier());
        buffer.writeFloat(value.floppiness());
    }

    public static CharacterBodyData read(FriendlyByteBuf buffer) {
        int version = buffer.readVarInt();
        CharacterGender gender = CharacterGender.parse(buffer.readUtf(16));
        BreastAppearance breasts = new BreastAppearance(buffer.readBoolean(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean());
        return new CharacterBodyData(version, gender, breasts, buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(), buffer.readFloat());
    }
}
