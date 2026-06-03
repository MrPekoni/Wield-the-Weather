package net.fentbusgaming.localweather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fentbusgaming.localweather.api.LocalWeatherAPI;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.CompletableFuture;

public final class WeatherCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("localweather")
                    .requires(source -> source.hasPermissionLevel(2)) // Requires Operator status

                    // Subcommand 1: /localweather status
                    .then(CommandManager.literal("status")
                            .executes(WeatherCommands::getZoneStatus))

                    // Subcommand 2: /localweather set <weather_type>
                    .then(CommandManager.literal("set")
                            .then(CommandManager.argument("type", StringArgumentType.word())
                                    .suggests(WeatherCommands::suggestWeatherTypes)
                                    .executes(WeatherCommands::setZoneWeather)))
            );
        });
    }

    /**
     * Prints out the current transition data for the zone the player is standing in.
     */
    private static int getZoneStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        BlockPos pos = player.getBlockPos();

        int[] zoneCoords = LocalWeatherAPI.toZoneCoords(pos.getX(), pos.getZ());
        WeatherZone zone = WeatherZoneManager.getOrCreateZone(source.getWorld(), zoneCoords[0], zoneCoords[1]);

        if (zone != null) {
            String msg = String.format(
                    "§b[Zone %d, %d]§r Current: §e%s§r ➔ Target: §a%s§r | Progress: §6%.1f%%§r | Remaining: §d%d ticks§r",
                    zone.getZoneX(), zone.getZoneZ(),
                    zone.getCurrentWeather().name(),
                    zone.getTargetWeather().name(),
                    zone.getTransitionProgress() * 100.0f,
                    zone.getWeatherDuration()
            );
            source.sendFeedback(() -> Text.literal(msg), false);
            return 1;
        } else {
            source.sendError(Text.literal("Could not look up a weather zone at your current coordinates."));
            return 0;
        }
    }

    /**
     * Commands the API layer to forcefully switch the local zone weather.
     */
    private static int setZoneWeather(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String typeStr = StringArgumentType.getString(context, "type").toUpperCase();

        try {
            WeatherZone.WeatherType type = WeatherZone.WeatherType.valueOf(typeStr);
            BlockPos pos = player.getBlockPos();

            // Calls the newly expanded API hook
            LocalWeatherAPI.setWeatherAt(source.getWorld(), pos, type);

            source.sendFeedback(() -> Text.literal("§aSetting target weather to §e" + type + "§a for this zone.§r"), true);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid weather type! Pick from: CLEAR, RAIN, THUNDER, SNOW"));
            return 0;
        }
    }

    /**
     * Generates tab-completion options dynamically based on the WeatherType enum.
     */
    private static CompletableFuture<Suggestions> suggestWeatherTypes(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        for (WeatherZone.WeatherType type : WeatherZone.WeatherType.values()) {
            builder.suggest(type.name().toLowerCase());
        }
        return builder.buildFuture();
    }
}