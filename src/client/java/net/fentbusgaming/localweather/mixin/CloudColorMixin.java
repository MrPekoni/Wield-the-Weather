package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Darkens vanilla cloud color during storms (1.20.1 Backport).
 * Automatically skipped when Better Clouds is installed, since it replaces
 * the cloud system entirely.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class CloudColorMixin {

    @Unique
    private static final boolean BETTER_CLOUDS_LOADED =
            FabricLoader.getInstance().isModLoaded("betterclouds");

    /**
     * Redirects cloud color mapping inside ClientWorld. By altering the returned
     * color Vec3d, vanilla's WorldRenderer will draw darkened clouds automatically.
     */
    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void localweather$darkenStormClouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        // Exit early if Better Clouds handles rendering or if there's no storm front nearby
        if (BETTER_CLOUDS_LOADED) return;

        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);

        if (factor < 0.01f) return;

        // Fetch the base vector calculated by vanilla
        Vec3d originalColor = cir.getReturnValue();
        double r = originalColor.x;
        double g = originalColor.y;
        double b = originalColor.z;

        // Rain clouds: dark grey. Thunder clouds: very dark.
        double stormR = 0.35f - thunder * 0.15f;
        double stormG = 0.37f - thunder * 0.17f;
        double stormB = 0.40f - thunder * 0.15f;

        // Linearly interpolate color states based on proximity metrics
        r = r + (stormR - r) * factor;
        g = g + (stormG - g) * factor;
        b = b + (stormB - b) * factor;

        // Pass modified Vector values back out to the renderer
        cir.setReturnValue(new Vec3d(
                Math.max(0.0, Math.min(1.0, r)),
                Math.max(0.0, Math.min(1.0, g)),
                Math.max(0.0, Math.min(1.0, b))
        ));
    }
}