package ee.lastpagans.settlements.mixin;

import com.wildfire.gui.WildfireButton;
import ee.lastpagans.settlements.client.wildfire.SettlementsWildfirePreviewSession;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wildfire.gui.screen.WildfireBreastCustomizationScreen", remap = false)
public abstract class WildfireDraftEditorMixin {
    @Shadow private WildfireButton btnPresets;
    @Shadow private WildfireButton btnAddPreset;
    @Shadow private WildfireButton btnDeletePreset;

    @Inject(method = {"init()V", "m_7856_()V"}, at = @At("TAIL"), remap = false)
    private void settlements$keepEditorInDraftFlow(CallbackInfo ci) {
        if (!SettlementsWildfirePreviewSession.isAuthorized((Screen) (Object) this)) return;
        // Preset management writes global files and opens additional screens; appearance sliders stay available.
        btnPresets.active = false;
        btnAddPreset.active = false;
        btnDeletePreset.active = false;
    }
}
