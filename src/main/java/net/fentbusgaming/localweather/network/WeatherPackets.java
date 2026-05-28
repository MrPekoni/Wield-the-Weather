package net.fentbusgaming.localweather.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fentbusgaming.localweather.LocalWeatherMod;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Handles all network packet definitions and server-side sending for LocalWeather (1.20.1 Backport).
 *
 * Packet: WeatherUpdate
 * → Server → Client
 * → Tells the client what weather type their current zone has and the transition progress.
 */
public final class WeatherPackets {

    // 1.20.1 standard constructor mapping for Identifiers
    public static final Identifier WEATHER_UPDATE_ID =
            new Identifier(LocalWeatherMod.MOD_ID, "weather_update");

    public static final Identifier WIND_UPDATE_ID =
            new Identifier(LocalWeatherMod.MOD_ID, "wind_update");

    private WeatherPackets() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * In 1.20.1 Fabric, S2C (Server-to-Client) channels do not require explicit
     * registration on the server side. The client simply listens to the raw channel Identifier.
     */
    public static void registerServerPackets() {
        LocalWeatherMod.LOGGER.info("[LocalWeather] Registered S2C weather identifiers.");
    }

    // -------------------------------------------------------------------------
    // Sending (Server → Client)
    // -------------------------------------------------------------------------

    public static void sendWeatherUpdate(ServerPlayerEntity player, WeatherZone zone) {
        // Allocate a fresh 1.20.1 packet byte buffer
        PacketByteBuf buf = PacketByteBufs.create();

        // Write raw primitives sequentially to match client-side buffer decoding exactly
        buf.writeVarInt(zone.getEffectiveWeather().ordinal());
        buf.writeFloat(zone.getTransitionProgress());
        buf.writeVarInt(zone.getZoneX());
        buf.writeVarInt(zone.getZoneZ());

        // Send raw packet over the standard network pipeline
        ServerPlayNetworking.send(player, WEATHER_UPDATE_ID, buf);
    }

    public static void sendWindUpdate(ServerPlayerEntity player, double windDirX, double windDirZ) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeFloat((float) windDirX);
        buf.writeFloat((float) windDirZ);

        ServerPlayNetworking.send(player, WIND_UPDATE_ID, buf);
    }
}