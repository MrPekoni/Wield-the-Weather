package net.fentbusgaming.localweather.api;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.fentbusgaming.localweather.weather.WindState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Public API for other mods to query localized weather information.
 *
 * <p>All methods are thread-safe and can be called from any context that has
 * access to a {@link ServerWorld}.</p>
 *
 * <h2>Usage example:</h2>
 * <pre>{@code
 * WeatherZone.WeatherType weather = LocalWeatherAPI.getWeatherAt(world, pos);
 * if (weather == WeatherZone.WeatherType.THUNDER) {
 *     // lightning protection logic
 * }
 * }</pre>
 */
public final class LocalWeatherAPI {

    private LocalWeatherAPI() {}

    /**
     * Get the current weather type at a specific block position.
     *
     * @param world the server world
     * @param pos   the block position to check
     * @return the current weather type at that position
     */
    public static WeatherZone.WeatherType getWeatherAt(ServerWorld world, BlockPos pos) {
        int zoneX = (pos.getX() >> 4) >> 4; // blockX -> chunkX -> zoneX
        int zoneZ = (pos.getZ() >> 4) >> 4;
        return getWeatherInZone(world, zoneX, zoneZ);
    }

    /**
     * Get the current weather type for a specific zone.
     *
     * @param world the server world
     * @param zoneX the zone X coordinate
     * @param zoneZ the zone Z coordinate
     * @return the current weather type, or CLEAR if the zone hasn't been loaded
     */
    public static WeatherZone.WeatherType getWeatherInZone(ServerWorld world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return zone.getCurrentWeather();
    }

    /**
     * Get the target weather a zone is transitioning toward.
     *
     * @param world the server world
     * @param zoneX the zone X coordinate
     * @param zoneZ the zone Z coordinate
     * @return the target weather type, or CLEAR if the zone hasn't been loaded
     */
    public static WeatherZone.WeatherType getTargetWeatherInZone(ServerWorld world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return zone.getTargetWeather();
    }

    /**
     * Get the weather transition progress for a zone (0.0 = start, 1.0 = complete).
     *
     * @param world the server world
     * @param zoneX the zone X coordinate
     * @param zoneZ the zone Z coordinate
     * @return the transition progress, or 1.0 if the zone hasn't been loaded
     */
    public static float getTransitionProgress(ServerWorld world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return 1.0f;
        }
        return zone.getTransitionProgress();
    }

    /**
     * Check if it is raining at a specific position (includes thunderstorms).
     *
     * @param world the server world
     * @param pos   the block position
     * @return true if rain or thunder is active at this position
     */
    public static boolean isRainingAt(ServerWorld world, BlockPos pos) {
        WeatherZone.WeatherType weather = getWeatherAt(world, pos);
        return weather == WeatherZone.WeatherType.RAIN
                || weather == WeatherZone.WeatherType.THUNDER
                || weather == WeatherZone.WeatherType.SNOW;
    }

    /**
     * Check if there is a thunderstorm at a specific position.
     *
     * @param world the server world
     * @param pos   the block position
     * @return true if thunder is active at this position
     */
    public static boolean isThunderingAt(ServerWorld world, BlockPos pos) {
        return getWeatherAt(world, pos) == WeatherZone.WeatherType.THUNDER;
    }

    /**
     * Get the current global wind direction X component (unit vector).
     * Wind direction controls which way weather fronts drift.
     *
     * @return X component of the wind direction
     */
    public static double getWindDirectionX() {
        return WindState.getWindDirX();
    }

    /**
     * Get the current global wind direction Z component (unit vector).
     *
     * @return Z component of the wind direction
     */
    public static double getWindDirectionZ() {
        return WindState.getWindDirZ();
    }

    /**
     * Convert block coordinates to zone coordinates.
     *
     * @param blockX the block X coordinate
     * @param blockZ the block Z coordinate
     * @return int array [zoneX, zoneZ]
     */
    public static int[] toZoneCoords(int blockX, int blockZ) {
        int zoneX = (blockX >> 4) >> 4; // blockX -> chunkX -> zoneX
        int zoneZ = (blockZ >> 4) >> 4;
        return new int[]{zoneX, zoneZ};
    }

    /**
     * Get the size of a weather zone in blocks.
     *
     * @return zone size in blocks (256 = 16 chunks * 16 blocks)
     */
    public static int getZoneSizeBlocks() {
        return WeatherZoneManager.CHUNKS_PER_ZONE * 16;
    }
    /**
     * Set the target weather type at a specific block position.
     * This will initiate a transition to the new weather type in that zone.
     *
     * @param world the server world
     * @param pos   the block position where the weather should change
     * @param type  the new weather type to set
     */
    /**
     * Set the weather type at a specific block position by finding its weather zone.
     *
     * @param world the server world
     * @param pos   the block position to change weather at
     * @param type  the new weather type to set
     */
    public static void setWeatherAt(ServerWorld world, BlockPos pos, WeatherZone.WeatherType type) {
        int zoneX = (pos.getX() >> 4) >> 4; // blockX -> chunkX -> zoneX
        int zoneZ = (pos.getZ() >> 4) >> 4;
        setWeatherInZone(world, zoneX, zoneZ, type);
    }

    /**
     * Set the weather type for a specific zone using zone coordinates.
     *
     * @param world the server world
     * @param zoneX the zone X coordinate
     * @param zoneZ the zone Z coordinate
     * @param type  the new weather type to set
     */
    public static void setWeatherInZone(ServerWorld world, int zoneX, int zoneZ, WeatherZone.WeatherType type) {
        WeatherZone zone = WeatherZoneManager.getOrCreateZone(world, zoneX, zoneZ);
        if (zone != null) {
            zone.setTargetWeather(type);

            // Give it a fresh duration if it was previously expired or at 0
            if (zone.getWeatherDuration() <= 0) {
                zone.setWeatherDuration(12000); // Default to ~10 minutes
            }

            // Pack coordinates and mark dirty so WeatherZoneManager syncs it automatically
            long packed = ((long) zoneX << 32) | (zoneZ & 0xFFFFFFFFL);
            WeatherZoneManager.markDirty(world.getRegistryKey(), packed);
        }
    }

    /**
     * Set the global wind direction vector and speed multiplier.
     *
     * @param dirX  the X direction component
     * @param dirZ  the Z direction component
     * @param speed the wind speed multiplier
     */
    public static void setGlobalWind(double dirX, double dirZ, float speed) {
        WindState.setWindDirection(dirX, dirZ);
        WindState.setWindSpeed(speed);
    }

    /**
     * Get the current global wind speed multiplier.
     *
     * @return the wind speed multiplier
     */
    public static float getWindSpeed() {
        return WindState.getWindSpeed();
    }
}
