package net.fentbusgaming.localweather.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brings in fog when a storm is approaching (1.20.1 Backport).
 */
@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public abstract class FogMixin {

    /**
     * Target simplified to name matching. The Mixin annotation processor will
     * parse the parameter list below to identify the exact target overload.
     */
    @Inject(
            method = "applyFog",
            at = @At("RETURN")
    )
    private static void localweather$stormFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {

        // Ensure we only affect standard visibility fields
        if (fogType != BackgroundRenderer.FogType.FOG_SKY && fogType != BackgroundRenderer.FogType.FOG_TERRAIN) {
            return;
        }

        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);

        if (factor < 0.02f) return;

        // Fetch running values set by vanilla immediately before return execution
        float vanillaStart = RenderSystem.getShaderFogStart();
        float vanillaEnd = RenderSystem.getShaderFogEnd();

        // Pull fog bounds inwards relative to local storm metrics
        float fogReduction = factor * (0.40f + thunder * 0.15f);
        float stormEnd = vanillaEnd * (1.0f - fogReduction);
        float stormStart = vanillaStart * (1.0f - factor * 0.25f);

        if (stormEnd < vanillaEnd) {
            RenderSystem.setShaderFogEnd(stormEnd);
            RenderSystem.setShaderFogStart(Math.min(stormStart, stormEnd - 10f));
        }
    }
}