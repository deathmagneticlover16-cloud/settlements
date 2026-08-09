package ee.lastpagans.settlements.client;

import com.mojang.blaze3d.platform.InputConstants;
import ee.lastpagans.settlements.Settlements;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories." + Settlements.MOD_ID;
    public static final KeyMapping MAIN = new KeyMapping("key.settlements.campaign", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping CHARACTER = new KeyMapping("key.settlements.character", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping SETTLEMENT = new KeyMapping("key.settlements.settlement", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    private ClientKeyMappings() {}
    public static void register(RegisterKeyMappingsEvent event) { event.register(MAIN); event.register(CHARACTER); event.register(SETTLEMENT); }
}
