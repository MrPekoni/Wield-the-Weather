package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts and alters vanilla sky colors (1.20.1 Backport).
 * Smoothly blends vanilla sky color into a dark overcast grey as a storm front
 * approaches.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class SkyColorMixin {

    /**
     * In 1.20.1, sky colors are evaluated using a Vec3d vector containing float weights.
     * We target the method return state to modify this vector before rendering logic captures it.
     */
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void localweather$darkenStormSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);

        // Let vanilla carry on unchanged if there's no storm
        if (factor < 0.01f) return;

        // Retrieve the clean vector vanilla computed
        Vec3d originalColor = cir.getReturnValue();
        double r = originalColor.x;
        double g = originalColor.y;
        double b = originalColor.z;

        // Target storm overlays: Overcast is a muted grey, Thunder pulls deeper down
        double stormR = 0.55f - thunder * 0.25f;
        double stormG = 0.57f - thunder * 0.27f;
        double stormB = 0.60f - thunder * 0.25f;

        // Linear interpolation (Lerp) towards target storm color boundaries
        r = r + (stormR - r) * factor;
        g = g + (stormG - g) * factor;
        b = b + (stormB - b) * factor;

        // Construct and inject our altered colors back into the execution loop
        cir.setReturnValue(new Vec3d(
                Math.max(0.0, Math.min(1.0, r)),
                Math.max(0.0, Math.min(1.0, g)),
                Math.max(0.0, Math.min(1.0, b))
        ));
    }
}