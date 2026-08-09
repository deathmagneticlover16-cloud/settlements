package ee.lastpagans.settlements.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class ClientNameEvents {
    private ClientNameEvents() {
    }

    public static void onNameFormat(PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) return;
        String name = ClientProfileCache.get(player.getUUID());
        if (name == null || name.isBlank()) return;
        ChatFormatting color = player.getTeam() == null ? ChatFormatting.WHITE : player.getTeam().getColor();
        event.setDisplayname(Component.literal(name).withStyle(color));
    }
}
