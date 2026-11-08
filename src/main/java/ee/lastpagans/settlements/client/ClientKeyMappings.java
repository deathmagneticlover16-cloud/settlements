package ee.lastpagans.settlements.client;

import com.mojang.blaze3d.platform.InputConstants;
import ee.lastpagans.settlements.Settlements;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories." + Settlements.MOD_ID;
    public static final KeyMapping MENU = new KeyMapping("key.settlements.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    private ClientKeyMappings() {}
    public static void register(RegisterKeyMappingsEvent event) { event.register(MENU); }
}
