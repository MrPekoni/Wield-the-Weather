package net.fentbusgaming.localweather.render;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders blocky, Minecraft-style storm clouds over weather zones with smooth global bilerp blending.
 */
@Environment(EnvType.CLIENT)
public class StormCloudRenderer {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;

    private static final int CELL_SIZE = 16;
    private static final float CLOUD_THICKNESS = 6.0f;
    private static final float CLOUD_BASE = 191.0f;
    private static final float MAX_DIST = ZONE_SIZE * 4.5f;
    private static final float DRIFT_SPEED = 0.1f;

    private static final int CELLS_PER_ZONE = ZONE_SIZE / CELL_SIZE;

    private static final float COVERAGE_RAIN = 0.55f;
    private static final float COVERAGE_THUNDER = 0.70f;
    private static final float COVERAGE_SNOW = 0.50f;

    private static final float ALPHA_RAIN = 0.65f;
    private static final float ALPHA_THUNDER = 0.82f;
    private static final float ALPHA_SNOW = 0.55f;

    private static final Identifier BLANK_TEXTURE = Identifier.of("minecraft", "textures/misc/white.png");

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(StormCloudRenderer::render);
    }

    private static int cellHash(int cx, int cz, int layer) {
        int h = cx * 374761393 + cz * 668265263 + layer * 2147483647;
        h = (h ^ (h >> 13)) * 1274126177;
        h = h ^ (h >> 16);
        return h;
    }

    private static float cellNoise(int cx, int cz, int layer) {
        return (cellHash(cx, cz, layer) & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || context.consumers() == null) return;

        Map<Long, ClientWeatherHandler.ZoneState> zones = ClientWeatherHandler.getZoneStates();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getPos();

        // Process rendering if any tracked zones are present
        boolean anyVisible = !zones.isEmpty();

        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        RenderLayer targetLayer = RenderLayer.getBeaconBeam(BLANK_TEXTURE, true);

        if (anyVisible) {
            float tickDelta = client.getTickDelta();
            long worldTime = client.world != null ? client.world.getTime() : 0;

            // Calculate absolute continuous drift values
            float driftMag = (worldTime + tickDelta) * DRIFT_SPEED;
            float rawDriftX = (float) (driftMag * ClientWeatherHandler.getWindDirX());
            float rawDriftZ = (float) (driftMag * ClientWeatherHandler.getWindDirZ());

            // Break drift into block-jumps and sub-block smooth offsets
            int cellOffsetX = (int) Math.floor(rawDriftX / CELL_SIZE);
            float smoothOffsetX = rawDriftX - (cellOffsetX * CELL_SIZE);

            int cellOffsetZ = (int) Math.floor(rawDriftZ / CELL_SIZE);
            float smoothOffsetZ = rawDriftZ - (cellOffsetZ * CELL_SIZE);

            matrices.push();
            Matrix4f mat = matrices.peek().getPositionMatrix();
            VertexConsumer cloudBuffer = consumers.getBuffer(targetLayer);

            for (ClientWeatherHandler.ZoneState s : zones.values()) {
                double zoneCX = (s.zoneX + 0.5) * ZONE_SIZE;
                double zoneCZ = (s.zoneZ + 0.5) * ZONE_SIZE;
                double zDistX = zoneCX - cam.x;
                double zDistZ = zoneCZ - cam.z;
                double zoneDist = Math.sqrt(zDistX * zDistX + zDistZ * zDistZ);

                // Keep processing active grids nearby to let edges blend out naturally
                if (zoneDist > MAX_DIST + ZONE_SIZE) continue;

                float zoneWorldX = s.zoneX * ZONE_SIZE;
                float zoneWorldZ = s.zoneZ * ZONE_SIZE;

                // Loop bounds are strictly bounded [0, CELLS_PER_ZONE). Hand-offs are continuous!
                for (int cx = 0; cx < CELLS_PER_ZONE; cx++) {
                    for (int cz = 0; cz < CELLS_PER_ZONE; cz++) {

                        // Vertex geometry handles smooth local drifting shifts
                        float cellWX = zoneWorldX + (cx * CELL_SIZE) + smoothOffsetX;
                        float cellWZ = zoneWorldZ + (cz * CELL_SIZE) + smoothOffsetZ;

                        double cdx = cellWX + CELL_SIZE * 0.5f - cam.x;
                        double cdz = cellWZ + CELL_SIZE * 0.5f - cam.z;
                        double cellDist = Math.sqrt(cdx * cdx + cdz * cdz);
                        if (cellDist > MAX_DIST) continue;

                        // --- CELL POSITION-BASED BILINEAR INTERPOLATION (BILERP) ---
                        float evalX = cellWX + CELL_SIZE * 0.5f;
                        float evalZ = cellWZ + CELL_SIZE * 0.5f;

                        int cellZoneX = Math.floorDiv((int) Math.floor(evalX), ZONE_SIZE);
                        int cellZoneZ = Math.floorDiv((int) Math.floor(evalZ), ZONE_SIZE);

                        float fx = (evalX - (cellZoneX * ZONE_SIZE)) / (float) ZONE_SIZE;
                        float fz = (evalZ - (cellZoneZ * ZONE_SIZE)) / (float) ZONE_SIZE;
                        fx = Math.max(0f, Math.min(1f, fx));
                        fz = Math.max(0f, Math.min(1f, fz));

                        // Sample the properties of the 4 surrounding weather zones
                        ClientWeatherHandler.ZoneState z00 = zones.get(ClientWeatherHandler.pack(cellZoneX,     cellZoneZ));
                        ClientWeatherHandler.ZoneState z10 = zones.get(ClientWeatherHandler.pack(cellZoneX + 1, cellZoneZ));
                        ClientWeatherHandler.ZoneState z01 = zones.get(ClientWeatherHandler.pack(cellZoneX,     cellZoneZ + 1));
                        ClientWeatherHandler.ZoneState z11 = zones.get(ClientWeatherHandler.pack(cellZoneX + 1, cellZoneZ + 1));

                        float c00, a00, r00, g00, b00;
                        float p00 = (z00 != null && z00.weather != WeatherZone.WeatherType.CLEAR) ? z00.transitionProgress : 0f;
                        c00 = p00 * (z00 != null && z00.weather == WeatherZone.WeatherType.THUNDER ? COVERAGE_THUNDER : (z00 != null && z00.weather == WeatherZone.WeatherType.SNOW ? COVERAGE_SNOW : COVERAGE_RAIN));
                        a00 = p00 * (z00 != null && z00.weather == WeatherZone.WeatherType.THUNDER ? ALPHA_THUNDER : (z00 != null && z00.weather == WeatherZone.WeatherType.SNOW ? ALPHA_SNOW : ALPHA_RAIN));
                        if (z00 != null && z00.weather == WeatherZone.WeatherType.THUNDER) { r00 = 42; g00 = 42; b00 = 50; }
                        else if (z00 != null && z00.weather == WeatherZone.WeatherType.SNOW) { r00 = 194; g00 = 199; b00 = 207; }
                        else { r00 = 106; g00 = 111; b00 = 120; }

                        float c10, a10, r10, g10, b10;
                        float p10 = (z10 != null && z10.weather != WeatherZone.WeatherType.CLEAR) ? z10.transitionProgress : 0f;
                        c10 = p10 * (z10 != null && z10.weather == WeatherZone.WeatherType.THUNDER ? COVERAGE_THUNDER : (z10 != null && z10.weather == WeatherZone.WeatherType.SNOW ? COVERAGE_SNOW : COVERAGE_RAIN));
                        a10 = p10 * (z10 != null && z10.weather == WeatherZone.WeatherType.THUNDER ? ALPHA_THUNDER : (z10 != null && z10.weather == WeatherZone.WeatherType.SNOW ? ALPHA_SNOW : ALPHA_RAIN));
                        if (z10 != null && z10.weather == WeatherZone.WeatherType.THUNDER) { r10 = 42; g10 = 42; b10 = 50; }
                        else if (z10 != null && z10.weather == WeatherZone.WeatherType.SNOW) { r10 = 194; g10 = 199; b10 = 207; }
                        else { r10 = 106; g10 = 111; b10 = 120; }

                        float c01, a01, r01, g01, b01;
                        float p01 = (z01 != null && z01.weather != WeatherZone.WeatherType.CLEAR) ? z01.transitionProgress : 0f;
                        c01 = p01 * (z01 != null && z01.weather == WeatherZone.WeatherType.THUNDER ? COVERAGE_THUNDER : (z01 != null && z01.weather == WeatherZone.WeatherType.SNOW ? COVERAGE_SNOW : COVERAGE_RAIN));
                        a01 = p01 * (z01 != null && z01.weather == WeatherZone.WeatherType.THUNDER ? ALPHA_THUNDER : (z01 != null && z01.weather == WeatherZone.WeatherType.SNOW ? ALPHA_SNOW : ALPHA_RAIN));
                        if (z01 != null && z01.weather == WeatherZone.WeatherType.THUNDER) { r01 = 42; g01 = 42; b01 = 50; }
                        else if (z01 != null && z01.weather == WeatherZone.WeatherType.SNOW) { r01 = 194; g01 = 199; b01 = 207; }
                        else { r01 = 106; g01 = 111; b01 = 120; }

                        float c11, a11, r11, g11, b11;
                        float p11 = (z11 != null && z11.weather != WeatherZone.WeatherType.CLEAR) ? z11.transitionProgress : 0f;
                        c11 = p11 * (z11 != null && z11.weather == WeatherZone.WeatherType.THUNDER ? COVERAGE_THUNDER : (z11 != null && z11.weather == WeatherZone.WeatherType.SNOW ? COVERAGE_SNOW : COVERAGE_RAIN));
                        a11 = p11 * (z11 != null && z11.weather == WeatherZone.WeatherType.THUNDER ? ALPHA_THUNDER : (z11 != null && z11.weather == WeatherZone.WeatherType.SNOW ? ALPHA_SNOW : ALPHA_RAIN));
                        if (z11 != null && z11.weather == WeatherZone.WeatherType.THUNDER) { r11 = 42; g11 = 42; b11 = 50; }
                        else if (z11 != null && z11.weather == WeatherZone.WeatherType.SNOW) { r11 = 194; g11 = 199; b11 = 207; }
                        else { r11 = 106; g11 = 111; b11 = 120; }

                        // Complete Bilinear Blending Math
                        float blendedCoverage = c00 + (c10 - c00) * fx + (c01 - c00) * fz + (c11 - c10 - c01 + c00) * fx * fz;
                        float blendedAlpha    = a00 + (a10 - a00) * fx + (a01 - a00) * fz + (a11 - a10 - a01 + a00) * fx * fz;
                        float blendedR        = r00 + (r10 - r00) * fx + (r01 - r00) * fz + (r11 - r10 - r01 + r00) * fx * fz;
                        float blendedG        = g00 + (g10 - g00) * fx + (g01 - g00) * fz + (g11 - g10 - g01 + g00) * fx * fz;
                        float blendedB        = b00 + (b10 - b00) * fx + (b01 - b00) * fz + (b11 - b10 - b01 + b00) * fx * fz;

                        // Shift noise sampling position opposite to smooth sliding offset
                        int worldCellX = s.zoneX * CELLS_PER_ZONE + cx - cellOffsetX;
                        int worldCellZ = s.zoneZ * CELLS_PER_ZONE + cz - cellOffsetZ;

                        // Evaluate noise against the continuous coverage calculation
                        if (cellNoise(worldCellX, worldCellZ, 0) > blendedCoverage) continue;

                        float distFade = cellDist < MAX_DIST * 0.6f ? 1f :
                                Math.max(0f, 1f - (float) ((cellDist - MAX_DIST * 0.6f) / (MAX_DIST * 0.4f)));

                        float alpha = blendedAlpha * distFade;
                        if (alpha < 0.01f) continue;

                        int ai = (int) (alpha * 255);
                        int sideAi = (int) (alpha * 0.85f * 255);
                        int botAi = (int) (alpha * 0.75f * 255);

                        float x1 = (float) (cellWX - cam.x);
                        float z1 = (float) (cellWZ - cam.z);
                        float x2 = x1 + CELL_SIZE;
                        float z2 = z1 + CELL_SIZE;
                        float yBot = (float) (CLOUD_BASE - cam.y);
                        float yTop = yBot + CLOUD_THICKNESS;

                        int ri = (int) blendedR; int gi = (int) blendedG; int bi = (int) blendedB;
                        int sideR = (int) (ri * 0.7f); int sideG = (int) (gi * 0.7f); int sideB = (int) (bi * 0.7f);
                        int botR = (int) (ri * 0.55f); int botG = (int) (gi * 0.55f); int botB = (int) (bi * 0.55f);

                        boolean drawNorth = cellNoise(worldCellX, worldCellZ - 1, 0) >= blendedCoverage;
                        boolean drawSouth = cellNoise(worldCellX, worldCellZ + 1, 0) >= blendedCoverage;
                        boolean drawWest  = cellNoise(worldCellX - 1, worldCellZ, 0) >= blendedCoverage;
                        boolean drawEast  = cellNoise(worldCellX + 1, worldCellZ, 0) >= blendedCoverage;

                        // Top face
                        cloudBuffer.vertex(mat, x1, yTop, z1).color(ri, gi, bi, ai).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();
                        cloudBuffer.vertex(mat, x1, yTop, z2).color(ri, gi, bi, ai).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();
                        cloudBuffer.vertex(mat, x2, yTop, z2).color(ri, gi, bi, ai).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();
                        cloudBuffer.vertex(mat, x2, yTop, z1).color(ri, gi, bi, ai).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();

                        // Bottom face
                        cloudBuffer.vertex(mat, x2, yBot, z1).color(botR, botG, botB, botAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();
                        cloudBuffer.vertex(mat, x2, yBot, z2).color(botR, botG, botB, botAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();
                        cloudBuffer.vertex(mat, x1, yBot, z2).color(botR, botG, botB, botAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();
                        cloudBuffer.vertex(mat, x1, yBot, z1).color(botR, botG, botB, botAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();

                        // North face
                        if (drawNorth) {
                            cloudBuffer.vertex(mat, x1, yBot, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
                            cloudBuffer.vertex(mat, x1, yTop, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
                            cloudBuffer.vertex(mat, x2, yTop, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
                            cloudBuffer.vertex(mat, x2, yBot, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
                        }

                        // South face
                        if (drawSouth) {
                            cloudBuffer.vertex(mat, x2, yBot, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
                            cloudBuffer.vertex(mat, x2, yTop, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
                            cloudBuffer.vertex(mat, x1, yTop, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
                            cloudBuffer.vertex(mat, x1, yBot, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
                        }

                        // West face
                        if (drawWest) {
                            cloudBuffer.vertex(mat, x1, yBot, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
                            cloudBuffer.vertex(mat, x1, yTop, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
                            cloudBuffer.vertex(mat, x1, yTop, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
                            cloudBuffer.vertex(mat, x1, yBot, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
                        }

                        // East face
                        if (drawEast) {
                            cloudBuffer.vertex(mat, x2, yBot, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();
                            cloudBuffer.vertex(mat, x2, yTop, z1).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();
                            cloudBuffer.vertex(mat, x2, yTop, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();
                            cloudBuffer.vertex(mat, x2, yBot, z2).color(sideR, sideG, sideB, sideAi).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();
                        }
                    }
                }
            }
            matrices.pop();
        }

        renderDebugCube(matrices, consumers, cam, targetLayer);

        if (consumers instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate immediate) {
            immediate.draw(targetLayer);
        }
    }

    private static void renderDebugCube(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam, RenderLayer layer) {
        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = consumers.getBuffer(layer);

        float x1 = (float) (-2.5 - cam.x); float x2 = (float) (2.5 - cam.x);
        float y1 = (float) (97.5 - cam.y); float y2 = (float) (102.5 - cam.y);
        float z1 = (float) (-2.5 - cam.z); float z2 = (float) (2.5 - cam.z);

        int r = 255, g = 0, b = 0, a = 100;

        // Top
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f).next();
        // Bottom
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, -1f, 0f).next();
        // North
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, -1f).next();
        // South
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next();
        // West
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(-1f, 0f, 0f).next();
        // East
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a).texture(0f, 0f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(1f, 0f, 0f).next();

        matrices.pop();
    }
}